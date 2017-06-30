/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.util.List;

import com.fingerprints.service.FingerprintSensorTest;
import com.fingerprints.service.FingerprintSensorTest.FingerprintSensorTestListener;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.TextView;

import android.net.Uri;

public class ParallelLCDEarpieceGyroMagFPRActivity extends ALTBaseActivity
        implements SensorEventListener, FingerprintSensorTestListener
{

    private SensorManager sensorManager;
    private boolean sensorEnable;
    private MediaPlayer player;
    private AudioManager audioManager;
    private int maxVolume;
    private int LCDCount;
    private Context mContext;

    static final String SERVICE_NAME = "sensor_test";

    private final int FPC_SELFTEST_PASSED = 0;
    private final int FPC_SELFTEST_FAILED = 1;
    private final int FPC_SELFTEST_FAILED_POWER_WAKEUP = 2;
    private final int FPC_SELFTEST_FAILED_SENSOR_RESET = 3;
    private final int FPC_SELFTEST_FAILED_READ_HWID = 4;
    private final int FPC_SELFTEST_FAILED_CAPTURE_IMAGE = 5;
    private final int FPC_SELFTEST_FAILED_IRQ = 6;
    private final int FPC_SELFTEST_FAILED_SENSOR_COULD_NOT_BE_REACHED = 16;

    private final int FPC_CHECKERBOARD_PASSED = 0;
    private final int FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR = 1;
    private final int FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR = 2;
    private final int FPC_CHECKERBOARD_DEAD_PIXELS = 4;
    private final int FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT = 8;
    private static long FPS_TEST_TIMEOUT_MSECS = 10000;

    private boolean isTestResultReceived = false;

    private FingerprintSensorTest sTest;

    private String selftestStr = "";
    private String checkerboardtestStr = "";

    private FingerprintSensorTestListener mSensorTestListener = new FingerprintSensorTestListener()
    {

        @Override
        public void onSelfTestResult(final int result)
        {
            TestUtils.dbgLog(TAG, "Received fingerprint selfTest result = " + result, 'i');
            isTestResultReceived = true;
            if (result == FPC_SELFTEST_PASSED)
            {
                selftestStr = "FPC_SELFTEST_PASSED";
            }
            else
            {
                switch(result)
                {
                case FPC_SELFTEST_FAILED:
                    selftestStr = "FPC_SELFTEST_GENERIC_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_POWER_WAKEUP:
                     selftestStr = "FPC_SELFTEST_POWER_WAKEUP_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_SENSOR_RESET:
                     selftestStr = "FPC_SELFTEST_SENSOR_RESET_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_READ_HWID:
                     selftestStr = "FPC_SELFTEST_READ_HWID_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_CAPTURE_IMAGE:
                     selftestStr = "FPC_SELFTEST_CAPTURE_IMAGE_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_IRQ:
                     selftestStr = "FPC_SELFTEST_IRQ_FAILED";
                    break;
                case FPC_SELFTEST_FAILED_SENSOR_COULD_NOT_BE_REACHED:
                     selftestStr = "FPC_SELFTEST_SENOSR_COULD_NOT_BE_REACHED_FAILED";
                    break;
                }
            }
            TestUtils.dbgLog(TAG, "fingperprint selftest result=" + selftestStr, 'i');
            logWriter(TAG, "fingperprint selftest result=" + selftestStr);

        }

        @Override
        public void onCheckerboardTestResult(final int result)
        {
            TestUtils.dbgLog(TAG, "Received fingerprint CheckerboardTest result = " + result, 'i');
            isTestResultReceived = true;
            String str = "";
            switch (result)
            {
                case FPC_CHECKERBOARD_PASSED:
                    checkerboardtestStr = "FPC_CHECKERBOARD_PASSED";
                    break;
                case FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR:
                    checkerboardtestStr = "FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR";
                    break;

                case FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR:
                    checkerboardtestStr = "FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR";
                    break;

                case FPC_CHECKERBOARD_DEAD_PIXELS:
                    checkerboardtestStr = "FPC_CHECKERBOARD_DEAD_PIXELS";
                    break;

                case FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT:
                    checkerboardtestStr = "FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT";
                    break;

                default:
                    checkerboardtestStr = "FPC_CHECKERBOARD_UNKNOWN_ERROR";
            }

            TestUtils.dbgLog(TAG, "fingperprint checker board result=" + checkerboardtestStr, 'i');
            logWriter(TAG, "fingperprint checker board result=" + checkerboardtestStr);
        }

        @Override
        public void onImagequalityTestResult(int arg0)
        {

        }

        @Override
        public void onImagecapacitanceTestResult(int result){};
        @Override
        public void onImageresetpixelTestResult(int result){};
        @Override
        public void onAfdcalibrationTestResult(int result){};
        @Override
        public void onAfdcalibrationrubberstampTestResult(int result){};
        @Override
        public void onAfdrubberstampTestResult(int result){};

    };

    @Override
    void init()
    {
        this.TAG = "ParallelLCDEarpieceGyroMagFPRActivity";
        setTitle("LCD/Earpiece/Gyroscope/Compass/Fingerprint");

        mContext = getApplicationContext();

        if (isFingerprintSensorAvailable())
        {
            TestUtils.dbgLog(TAG, "Fingerprint is available", 'i');
            logWriter(TAG, "fingerprint sensor available");
            try
            {
                TestUtils.dbgLog(TAG, "creating sensor object", 'i');
                logWriter(TAG, "creating sensor object");
                sTest = new FingerprintSensorTest();
            }
            catch (Exception e)
            {
                TestUtils.dbgLog(TAG, "Failed to create sensor object", 'i');
                logWriter(TAG, "Failed to create sensor object");
                e.printStackTrace();
            }
        }
        else
        {
            TestUtils.dbgLog(TAG, "Fingerprint is not available", 'i');
            logWriter(TAG, "fingerprint sensor not available");
        }

        try
        {
            // initial earpiece function
            this.audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            this.audioManager.setSpeakerphoneOn(false);
            this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int configvolume = ConfigTools.getPlaybackVolume("EARPIECE_PLAYBACK_VOLUME");
            TestUtils.dbgLog(TAG, "configvolume is " + configvolume + " maxVolume is " + maxVolume, 'i');
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
        }
        catch (Exception e)
        {}
    }

    @Override
    void start()
    {
        this.LCDCount = 0;
        this.isRunning = true;
        this.sensorEnable = false;
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
    }

    private void testSensor()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (sensorEnable)
                    {
                        unRegisterSenserListener();
                        sensorEnable = false;
                    }
                    else
                    {
                        registerSenserListener();
                        sensorEnable = true;
                    }

                    testFingerrintSensor();
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void registerSenserListener()
    {
        try
        {
            List<Sensor> sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "register Gyroscope sensor", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }

            sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "register Mag sensor", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
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
                TestUtils.dbgLog(TAG, "un-register sensors", 'i');
                this.sensorManager.unregisterListener(this);
            }
        }
        catch (Exception e)
        {}
    }

    /**
     * @return whether fingerprint sensor is available or not.
     */
    private boolean isFingerprintSensorAvailable()
    {
        boolean isSupported = false;
        String propertyFPS = "false";
        propertyFPS = SystemProperties.get("ro.hw.fps", "false");

        // Currently, it's supported by Affinity 16MP and Vector
        if (Build.DEVICE.toLowerCase().contains("griffin") || propertyFPS.equalsIgnoreCase("true"))
        {
            isSupported = true;
        }
        return isSupported;
    }

    private void testFingerrintSensor()
    {
        if (sTest == null)
        {
            TestUtils.dbgLog(TAG, "Fingerprint object is null", 'i');
            return;
        }

        isTestResultReceived = false;
        // start selftest at first
        sTest.selfTest(mSensorTestListener);
        TestUtils.dbgLog(TAG, "starting fingperprint selftest", 'i');
        logWriter(TAG, "starting fingperprint selftest");

        // Start the selftest, timeout is 10s by default
        long startTimeSelftest = System.currentTimeMillis();
        while (!isTestResultReceived)
        {
            if ((System.currentTimeMillis() - startTimeSelftest) > FPS_TEST_TIMEOUT_MSECS)
            {
                TestUtils.dbgLog(TAG, "fingperprint selftest timeout", 'e');
                logWriter(TAG, "fingperprint selftest timeout");
                break;
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        if (isTestResultReceived && sTest != null)
        {
            isTestResultReceived = false;
            // start checker board test
            sTest.checkerboardTest(mSensorTestListener);
            TestUtils.dbgLog(TAG, "starting fingperprint checkerboard test", 'i');
            logWriter(TAG, "starting fingperprint checkerboard test");

            // Start the checkerboard test, timeout is 10s by default
            long startTimeCheckerboardTest = System.currentTimeMillis();
            while (!isTestResultReceived)
            {
                if ((System.currentTimeMillis() - startTimeCheckerboardTest) > FPS_TEST_TIMEOUT_MSECS)
                {
                    TestUtils.dbgLog(TAG, "start fingperprint checkerboard test timeout", 'e');
                    logWriter(TAG, "start fingperprint checkerboard test timeout");
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
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
                    TestUtils.dbgLog(TAG, "Exception happen:" + e, 'i');
                }
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
        i.setClass(ParallelLCDEarpieceGyroMagFPRActivity.this, com.motorola.motocit.display.testPatterns.BlackWithWhiteBorder.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void testWhiteDisplay()
    {
        TestUtils.dbgLog(TAG, "start testing pattern - White with Black Border", 'i');
        Intent i = new Intent();
        i.setClass(ParallelLCDEarpieceGyroMagFPRActivity.this, com.motorola.motocit.display.testPatterns.WhiteWithBlackBorder.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void testColorPattern()
    {

        TestUtils.dbgLog(TAG, "start testing pattern - Marbeth", 'i');
        Intent i = new Intent();
        i.setClass(ParallelLCDEarpieceGyroMagFPRActivity.this, com.motorola.motocit.display.testPatterns.Macbeth.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
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
                    testSensor();
                    break;
                case Constant.HANDLER_MESSAGE_LOG:
                    showToast("LCD/Earpiece/Gyro/Mag/Fingerprint test");
                    TestUtils.dbgLog(TAG, "Running", 'i');
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
            if (sTest != null)
            {
                sTest = null;
            }

            TestUtils.dbgLog(TAG, "Test Completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }

    @Override
    public void onCheckerboardTestResult(int result)
    {
        TestUtils.dbgLog(TAG, "Received CheckerboardTest result = " + result, 'i');
    }

    @Override
    public void onSelfTestResult(int result)
    {
        TestUtils.dbgLog(TAG, "Received selfTest result = " + result, 'i');
    }

    @Override
    public void onImagequalityTestResult(int arg0)
    {

    }

    @Override
    public void onImagecapacitanceTestResult(int result){};
    @Override
    public void onImageresetpixelTestResult(int result){};
    @Override
    public void onAfdcalibrationTestResult(int result){};
    @Override
    public void onAfdcalibrationrubberstampTestResult(int result){};
    @Override
    public void onAfdrubberstampTestResult(int result){};
}
