/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.util.List;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.SDCardTestHandler;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.widget.TextView;
import android.net.Uri;
import android.util.Log;

public class ParallelLCDEarpieceBarometerGPSSDActivity extends ALTBaseActivity
        implements SensorEventListener
{
    private SensorManager sensorManager;
    private int sensorCount;
    private MediaPlayer player;
    private AudioManager audioManager;
    private SDCardTestHandler sdHandler;
    private LocationManager locationManager;
    private boolean GPSEnabled;
    private String bestProviderName;
    private int maxVolume;
    private int LCDCount;

    private Context mContext;

    @Override
    void init()
    {
        try
        {
            this.TAG = "ParallelLCDEarpieceBarometerGPSSDActivity";
            setTitle("LCD/Earpiece/Barometer/GPS/SDCard");

            mContext = getApplicationContext();

            // initial earpiece function
            this.audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            this.audioManager.setSpeakerphoneOn(false);
            this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int configvolume = ConfigTools.getPlaybackVolume("EARPIECE_PLAYBACK_VOLUME");
            TestUtils.dbgLog(TAG, "configvolume is " + configvolume + " maxVolume is " + this.maxVolume, 'i');

            if (configvolume > 0 && configvolume <= this.maxVolume)
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, configvolume, AudioManager.FLAG_SHOW_UI);
            }
            else
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, this.maxVolume, AudioManager.FLAG_SHOW_UI);
            }

            // initial Sensor function
            this.sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);

            // initial GPS function
            this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            // this.gpsStatus = this.locationManager.getGpsStatus(null);
            if (this.locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
            {
                TestUtils.dbgLog(TAG, "GPS enabled", 'i');
                this.GPSEnabled = true;
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setAltitudeRequired(false);
                criteria.setBearingRequired(false);
                criteria.setCostAllowed(false);
                criteria.setPowerRequirement(Criteria.POWER_LOW);
                criteria.setSpeedRequired(false);
                this.bestProviderName = this.locationManager.getBestProvider(criteria, true);
            }
            else
            {
                this.GPSEnabled = false;
            }
        }
        catch (Exception e)
        {}
    }

    @Override
    void start()
    {
        this.isRunning = true;
        this.sensorCount = 0;
        this.LCDCount = 0;
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    int timerCount = 0;
                    while (timerCount < realIntervalTime / 1000 && isRunning)
                    {
                        if (timerCount % 120 == 0)
                        {
                            sendMessage(handler, Constant.HANDLER_MESSAGE_LCD);
                        }
                        if (timerCount % 30 == 0)
                        {
                            sendMessage(handler, Constant.HANDLER_MESSAGE_SENSOR);
                        }
                        if (timerCount % realLogTime == 0)
                        {
                            sendMessage(handler, Constant.HANDLER_MESSAGE_LOG);
                        }
                        try
                        {
                            Thread.sleep(1000);
                        }
                        catch (Exception ex)
                        {}
                        timerCount++;
                        if (timerCount % 60 == 0)
                        {
                            sendMessage(handler, Constant.HANDLER_MESSAGE_CHECK_PROCESS);
                        }
                    }
                }
                catch (Exception e)
                {}
                finally
                {
                    try
                    {
                        Thread.sleep(5000);
                    }
                    catch (Exception e)
                    {}
                    sendMessage(handler, Constant.HANDLER_MESSAGE_STOP);
                }
            }
        }).start();
        playEarpiece();
        testSDCard();
    }

    private void testSDCard()
    {
        try
        {
            TestUtils.dbgLog(TAG, "Testing SD card", 'i');
            this.sdHandler = new SDCardTestHandler(this);
            this.sdHandler.test();
        }
        catch (Exception e)
        {}
    }

    private void testSensorGPS()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (sensorCount > 2)
                    {
                        unRegisterSenserListener();
                        if (GPSEnabled)
                        {
                            unRegisterGPSListener();
                        }
                        sensorCount = 0;
                    }
                    else
                    {
                        if (sensorCount == 0)
                        {
                            registerSenserListener();
                            if (GPSEnabled)
                            {
                                registerGPSListener();
                            }
                        }
                        sensorCount++;
                    }
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void registerGPSListener()
    {
        try
        {
            if (this.locationManager != null)
            {
                TestUtils.dbgLog(TAG, "register GPS listener", 'i');
                this.locationManager.requestLocationUpdates(this.bestProviderName, 0, 0, this.locationListener);
                this.locationManager.addGpsStatusListener(new GpsStatus.Listener()
                {
                    @Override
                    public void onGpsStatusChanged(int event)
                    {}
                });
            }
        }
        catch (Exception e)
        {}
    }

    private void unRegisterGPSListener()
    {
        try
        {
            if (this.locationManager != null)
            {
                TestUtils.dbgLog(TAG, "un-register GPS listener", 'i');
                this.locationManager.removeUpdates(this.locationListener);
            }
        }
        catch (Exception e)
        {}
    }

    private void registerSenserListener()
    {
        try
        {
            List<Sensor> sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_PRESSURE);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "register Barometer sensor listener", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_NORMAL);
            }
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
                TestUtils.dbgLog(TAG, "un-register Barometer sensor listener", 'i');
                this.sensorManager.unregisterListener(this);
            }
        }
        catch (Exception e)
        {}
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
                    TestUtils.dbgLog(TAG, "play audio from earpiece", 'i');
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void testLCD()
    {
        switch (this.LCDCount)
        {
            case 0:
                testColorPattern();
                break;
            case 1:
                testWhiteDisplay();
                break;
            case 2:
                testDisplayBorder();
                break;
        }
        if (this.LCDCount >= 2)
        {
            this.LCDCount = 0;
        }
        else
        {
            this.LCDCount++;
        }
    }

    private void testDisplayBorder()
    {
        TestUtils.dbgLog(TAG, "start testing pattern - Black with White Border", 'i');
        Intent i = new Intent();
        i.setClass(ParallelLCDEarpieceBarometerGPSSDActivity.this, com.motorola.motocit.display.testPatterns.BlackWithWhiteBorder.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        // setContentView(com.motorola.motocit.R.layout.alt_function_display_border);
    }

    private void testWhiteDisplay()
    {
        TestUtils.dbgLog(TAG, "start testing pattern - White with Black Border", 'i');
        Intent i = new Intent();
        i.setClass(ParallelLCDEarpieceBarometerGPSSDActivity.this, com.motorola.motocit.display.testPatterns.WhiteWithBlackBorder.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        /*
         * setContentView(com.motorola.motocit.R.layout.alt_function_main_display
         * );
         * TextView tv = (TextView) this.findViewById(com.motorola.motocit.R.id.
         * function_main_display_textview);
         * tv.setBackgroundColor(Color.WHITE);
         */
    }

    private void testColorPattern()
    {

        TestUtils.dbgLog(TAG, "start testing pattern - Marbeth", 'i');
        Intent i = new Intent();
        i.setClass(ParallelLCDEarpieceBarometerGPSSDActivity.this, com.motorola.motocit.display.testPatterns.Macbeth.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        /*
         * try
         * {
         * setContentView(com.motorola.motocit.R.layout.alt_function_color_pattern
         * );
         * DisplayMetrics dm = new DisplayMetrics();
         * getWindowManager().getDefaultDisplay().getMetrics(dm);
         * int screenWidth = dm.widthPixels;
         * int screenHeight = dm.heightPixels;
         * int colorPatternWidth = screenWidth / 3;
         * int colorPatternHeigh = screenHeight / 5;
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern1,
         * Color.rgb(0, 255, 255),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern2,
         * Color.rgb(255, 0, 255),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern3,
         * Color.rgb(128, 128, 128), colorPatternWidth,
         * colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern4,
         * Color.rgb(0, 128, 0),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern5,
         * Color.rgb(0, 255, 0),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern6,
         * Color.rgb(128, 0, 0),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern7,
         * Color.rgb(0, 0, 128),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern8,
         * Color.rgb(128, 128, 0),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern9,
         * Color.rgb(128, 0, 128),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern10,
         * Color.rgb(255, 0, 0),
         * colorPatternWidth, colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern11,
         * Color.rgb(192, 192, 192), colorPatternWidth,
         * colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern12,
         * Color.rgb(0, 128, 128), colorPatternWidth,
         * colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern13,
         * Color.rgb(255, 255, 255), colorPatternWidth,
         * colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern14,
         * Color.rgb(255, 255, 0), colorPatternWidth,
         * colorPatternHeigh);
         * setColorPattern(com.motorola.motocit.R.id.FunctionColorPattern15,
         * Color.rgb(0, 0, 255),
         * colorPatternWidth, colorPatternHeigh);
         * }
         * catch (Exception e)
         * {}
         */
    }

    private void setColorPattern(int id, int color, int width, int heigh)
    {
        try
        {
            TextView tv = (TextView) this.findViewById(id);
            tv.setWidth(width);
            tv.setHeight(heigh);
            tv.setBackgroundColor(color);
        }
        catch (Exception e)
        {}
    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constant.HANDLER_MESSAGE_LCD:
                    testLCD();
                    break;
                case Constant.HANDLER_MESSAGE_SENSOR:
                    testSensorGPS();
                    break;
                case Constant.HANDLER_MESSAGE_LOG:
                    showToast("LCD/Earpiece/Barometer/GPS/SDCard test");
                    logWriter(TAG, "Running");
                case Constant.HANDLER_MESSAGE_CHECK_PROCESS:
                    checkProcess();
                    break;
                case Constant.HANDLER_MESSAGE_STOP:
                    killActivity();
                    break;
            }
        }
    };

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {}

    @Override
    public void onSensorChanged(SensorEvent event)
    {}

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location)
        {}

        @Override
        public void onProviderDisabled(String provider)
        {}

        @Override
        public void onProviderEnabled(String provider)
        {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {}
    };

    @Override
    void release()
    {
        try
        {
            if (this.player != null)
            {
                if (this.player.isPlaying())
                {
                    try
                    {
                        Log.d(TAG, "player stop");
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

            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }
}
