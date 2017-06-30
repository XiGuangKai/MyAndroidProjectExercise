/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import java.util.List;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

public class ParallelEarpieceProxLightAcceleBTWiFiService extends
        ALTBaseService implements SensorEventListener
{
    private static final String TAG = "ParallelEarpieceProxLightAcceleBTWiFiService";
    private WifiManager wifiManager;
    private BluetoothAdapter btAdapter;
    private int BTWiFiCount;
    private SensorManager sensorManager;
    private boolean isRegister;
    private MediaPlayer player;
    private AudioManager audioManager;
    private int maxVolume;
    private boolean isRunning;
    private Context mContext;

    @Override
    void process()
    {
        init();
        start();
    }

    @Override
    void release()
    {
        try
        {
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(this, AlarmVibratorELPannelReceiver.class), 0));
        }
        catch (Exception e)
        {}
        try
        {
            if (this.player != null)
            {
                if (this.player.isPlaying())
                {
                    try
                    {
                        TestUtils.dbgLog(TAG, "player stop", 'i');
                        this.player.stop();
                    }
                    catch (Exception ex)
                    {}
                }
                try
                {
                    this.player.release();
                }
                catch (Exception ex)
                {}
                this.player = null;
            }
            if (this.audioManager != null)
            {
                this.audioManager = null;
            }
            if (this.sensorManager != null)
            {
                unRegisterSenserListener();
                this.sensorManager = null;
            }

            // release BlueTooth resource
            if (this.btAdapter != null)
            {
                try
                {
                    this.btAdapter.disable();
                    this.btAdapter.cancelDiscovery();
                }
                catch (Exception ex)
                {}
                this.btAdapter = null;
            }

            // release WiFi resource
            if (this.wifiManager != null)
            {
                if (this.wifiManager.isWifiEnabled())
                {
                    this.wifiManager.setWifiEnabled(false);
                }
                this.wifiManager = null;
            }

            TestUtils.dbgLog(TAG, "Test completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}

        logWriter("Reboot", "Completed");
    }

    private void start()
    {
        this.isRunning = true;
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    int timerCount = 0;
                    while (timerCount < realIntervalTime / 1000 && isRunning)
                    {
                        if (timerCount % 15 == 0)
                        {
                            testSensors();
                        }
                        if (timerCount % 60 == 0)
                        {
                            testBTWiFi();
                        }

                        try
                        {
                            Thread.sleep(1000);
                        }
                        catch (Exception ex)
                        {}
                        timerCount++;
                        if (timerCount % realLogTime == 0)
                        {
                            logWriter(TAG, "Running");
                        }
                    }
                }
                catch (Exception e)
                {}
                finally
                {
                    killService();
                }
            }
        }).start();
        playEarpiece();
    }

    private void init()
    {
        try
        {
            mContext = getApplicationContext();

            // initial earpiece function
            this.audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            this.audioManager.setSpeakerphoneOn(false);
            this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int configvolume = ConfigTools.getPlaybackVolume("EARPIECE_PLAYBACK_VOLUME");
            if (configvolume > 0 && configvolume <= this.maxVolume)
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, configvolume, AudioManager.FLAG_SHOW_UI);
            }
            else
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, this.maxVolume, AudioManager.FLAG_SHOW_UI);
            }

            // initial sensors function
            this.isRegister = false;
            this.sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

            // initial bluetooth function
            this.BTWiFiCount = 0;
            this.btAdapter = BluetoothAdapter.getDefaultAdapter();

            // initial WiFi function
            this.wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        }
        catch (Exception e)
        {}
    }

    private void testBTWiFi()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (BTWiFiCount > 1)
                    {
                        disableBTWiFi();
                        BTWiFiCount = 0;
                    }
                    else if (BTWiFiCount == 0)
                    {
                        enableBTWiFi();
                        BTWiFiCount++;
                    }
                    else
                    {
                        BTWiFiCount++;
                    }
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void enableBTWiFi()
    {
        try
        {
            if (this.wifiManager != null)
            {
                TestUtils.dbgLog(TAG, "WLAN test. Enable/Start WLAN scan", 'i');
                this.wifiManager.setWifiEnabled(true);
                this.wifiManager.startScan();
            }
            if (this.btAdapter != null)
            {
                TestUtils.dbgLog(TAG, "BT test. Enable/Start BT scan", 'i');
                this.btAdapter.enable();
                this.btAdapter.startDiscovery();
            }
        }
        catch (Exception e)
        {}
    }

    private void disableBTWiFi()
    {
        try
        {
            if (this.wifiManager != null && this.wifiManager.isWifiEnabled())
            {
                TestUtils.dbgLog(TAG, "WLAN test. Disable WLAN", 'i');
                this.wifiManager.setWifiEnabled(false);
            }
            if (this.btAdapter != null && this.btAdapter.isEnabled())
            {
                TestUtils.dbgLog(TAG, "BT test. Disable BT", 'i');
                this.btAdapter.cancelDiscovery();
                this.btAdapter.disable();
            }
        }
        catch (Exception e)
        {}
    }

    private void testSensors()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (isRegister)
                    {
                        unRegisterSenserListener();
                    }
                    else
                    {
                        registerSenserListener();
                    }
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void playEarpiece()
    {
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                try
                {
                    if (player == null)
                    {
                        player = new MediaPlayer();
                    }
                    player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                    player.setDataSource(mContext, Uri.parse("android.resource://" + "com.motorola.motocit" + "/" + R.raw.alt_audio_350_to_450));
                    player.setLooping(true);
                    player.prepare();
                    player.start();
                }
                catch (Exception e)
                {
                    Log.d(TAG, "Exception happen:" + e);
                }
            }
        }).start();
    }

    private void registerSenserListener()
    {
        try
        {
            List<Sensor> sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register sensor prox", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
            sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_LIGHT);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register sensor light", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
            sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register sensor accel", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
            this.isRegister = true;
        }
        catch (Exception e)
        {}
    }

    private void unRegisterSenserListener()
    {
        try
        {
            if (this.sensorManager != null)
            {
                TestUtils.dbgLog(TAG, "Unregister all sensors", 'i');
                this.sensorManager.unregisterListener(this);
                this.isRegister = false;
            }
        }
        catch (Exception e)
        {}
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1)
    {}

    @Override
    public void onSensorChanged(SensorEvent arg0)
    {}
}
