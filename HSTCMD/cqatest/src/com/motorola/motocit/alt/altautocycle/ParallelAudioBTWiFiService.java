/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;

import java.io.File;
import android.util.Log;

public class ParallelAudioBTWiFiService extends ALTBaseService
{
    private static final String TAG = "ParallelAudioBTWiFiService";
    private WifiManager wifiManager;
    private BluetoothAdapter btAdapter;
    private int BTWiFiCount;
    private MediaPlayer player;
    private AudioManager audioManager;
    private int maxVolume;
    private Context mContext;

    @Override
    void process()
    {
        init();
        start();
    }

    void start()
    {
        this.BTWiFiCount = 0;
        this.isRunning = true;
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    int timerCount = 0;
                    while (timerCount < realIntervalTime / 1000 && isRunning)
                    {
                        if (timerCount % 60 == 0)
                        {
                            testBTWiFi();
                        }

                        Thread.sleep(1000);
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
        playAudio();
    }

    private void init()
    {
        try
        {
            mContext = getApplicationContext();

            TestUtils.dbgLog(TAG, "Init test", 'i');
            // initial audio function
            this.audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager.setSpeakerphoneOn(true);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int configvolume = ConfigTools.getPlaybackVolume("LOUDSPEAKER_PLAYBACK_VOLUME");
            if (configvolume > 0 && configvolume <= this.maxVolume)
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, configvolume, AudioManager.FLAG_SHOW_UI);
            }
            else
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, this.maxVolume, AudioManager.FLAG_SHOW_UI);
            }

            logWriter(TAG, "audio volume for playback is" + configvolume + ", the max is " + this.maxVolume);
            // initial bluetooth function
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
                TestUtils.dbgLog(TAG, "WLAN testing. Enable/start WLAN scan", 'i');
                this.wifiManager.setWifiEnabled(true);
                this.wifiManager.startScan();
            }
            if (this.btAdapter != null)
            {
                TestUtils.dbgLog(TAG, "BT testing. Enable/start BT scan", 'i');
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
            if (wifiManager != null && wifiManager.isWifiEnabled())
            {
                TestUtils.dbgLog(TAG, "WLAN testing. Disable WLAN", 'i');
                wifiManager.setWifiEnabled(false);
            }
            if (this.btAdapter != null && this.btAdapter.isEnabled())
            {
                TestUtils.dbgLog(TAG, "BT testing. Disable BT", 'i');
                this.btAdapter.cancelDiscovery();
                this.btAdapter.disable();
            }
        }
        catch (Exception e)
        {}
    }

    private void playAudio()
    {
        new Thread(new Runnable() {
            @Override
            public void run()
            {
                try
                {
                    if (player == null)
                    {
                        player = MediaPlayer.create(mContext, com.motorola.motocit.R.raw.alt_audio_350_to_450);
                        player.setLooping(true);
                    }
                    TestUtils.dbgLog(TAG, "Loop playback audio", 'i');
                    player.start();
                }
                catch (Exception e)
                {}
            }
        }).start();
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
                        TestUtils.dbgLog(TAG, "Stop playing audio", 'i');
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

            // release BlueTooth resource
            if (this.btAdapter != null)
            {
                try
                {
                    TestUtils.dbgLog(TAG, "Releasing BT", 'i');
                    this.btAdapter.cancelDiscovery();
                    this.btAdapter.disable();
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
                    try
                    {
                        TestUtils.dbgLog(TAG, "Releasing WLAN", 'i');
                        this.wifiManager.setWifiEnabled(false);
                    }
                    catch (Exception ex)
                    {}
                }
                this.wifiManager = null;
            }

            TestUtils.dbgLog(TAG, "Test Completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }
}
