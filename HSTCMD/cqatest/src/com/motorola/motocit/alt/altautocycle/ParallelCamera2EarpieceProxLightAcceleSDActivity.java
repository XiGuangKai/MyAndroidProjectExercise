/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.view.WindowManager;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Camera2Preview;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.SDCardTestHandler;

import java.util.List;

public class ParallelCamera2EarpieceProxLightAcceleSDActivity extends
        ALTBaseActivity implements SensorEventListener
{

    private SDCardTestHandler sdHandler;
    private SensorManager sensorManager;
    private boolean isRegister = false;
    private Camera2Preview mPreview;
    private MediaPlayer player;
    private AudioManager audioManager;
    private int maxVolume;
    private Context mContext;

    @Override
    void init()
    {
        try
        {
            this.TAG = "ParallelCamera2EarpieceProxLightAcceleSDActivity";
            setTitle("Camera/Earpiece/Prox/Light/Accele/SDCard");

            mContext = getApplicationContext();

            if (mBringupCycle)
            {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                setContentView(com.motorola.motocit.R.layout.alt_function_elpannel);
            }
            else
            {
                // initial camera function
                requestWindowFeature(Window.FEATURE_NO_TITLE);
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

                this.mPreview = new Camera2Preview(this);
                synchronized (mPreview)
                {
                    mPreview.switchCamera(mPreview.backCameraId);
                }
                setContentView(this.mPreview);
            }

            // initial earpiece function
            this.audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            this.audioManager.setSpeakerphoneOn(false);
            this.maxVolume = this.audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            int configvolume = ConfigTools.getPlaybackVolume("EARPIECE_PLAYBACK_VOLUME");
            TestUtils.dbgLog(TAG, "configvolume is " + configvolume + "maxVolume is " + this.maxVolume, 'i');
            if (configvolume > 0 && configvolume <= this.maxVolume)
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, configvolume, AudioManager.FLAG_SHOW_UI);
            }
            else
            {
                this.audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, this.maxVolume, AudioManager.FLAG_SHOW_UI);
            }

            // initial proximity function
            this.sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        }
        catch (Exception e)
        {}
    }

    @Override
    void start()
    {
        this.isRunning = true;
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    int timerCount = 0;
                    while (timerCount < realIntervalTime / 11000 && isRunning)
                    {
                        if (timerCount % 5 == 0)
                        {
                            sendMessage(handler,
                                    Constant.HANDLER_MESSAGE_SENSOR);
                        }

                        if (mBringupCycle)
                        {
                            try
                            {
                                Thread.sleep(5000);
                            }
                            catch (Exception e)
                            {}
                        }
                        else
                        {
                            // wait 5 seconds for switching camera
                            try
                            {
                                Thread.sleep(5000);
                            }
                            catch (Exception e)
                            {}
                            sendMessage(handler, Constant.HANDLER_MESSAGE_TAKE_PICTURE);

                            // wait 5 seconds for taking picture
                            try
                            {
                                Thread.sleep(5000);
                            }
                            catch (Exception e)
                            {}
                            sendMessage(handler, Constant.HANDLER_MESSAGE_SWITCH_CAMERA);
                        }

                        timerCount++;
                        if (timerCount % realLogTime == 0)
                        {
                            sendMessage(handler, Constant.HANDLER_MESSAGE_LOG);
                        }
                        if (timerCount % 10 == 0)
                        {
                            sendMessage(handler,
                                    Constant.HANDLER_MESSAGE_CHECK_PROCESS);
                        }
                    }
                }
                catch (Exception e)
                {}
                finally
                {
                    try
                    {
                        Thread.sleep(3000);
                    }
                    catch (Exception e)
                    {}
                    // release resource before stopping test
                    release();
                    sendMessage(handler, Constant.HANDLER_MESSAGE_STOP);
                }
            }
        }).start();
        playEarpiece();
        testSDCard();
    }

    private void testSensors()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    TestUtils.dbgLog(TAG, "test sensors", 'i');
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

    private void testSDCard()
    {
        try
        {
            TestUtils.dbgLog(TAG, "test SD card", 'i');
            this.sdHandler = new SDCardTestHandler(this);
            this.sdHandler.test();
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
                    TestUtils.dbgLog(TAG, "Play audio", 'i');
                }
                catch (Exception e)
                {
                    TestUtils.dbgLog(TAG, "Exception happen:" + e, 'e');
                }
            }
        }).start();
    }

    private void takePicture()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (mPreview != null)
                    {
                        synchronized (mPreview)
                        {
                            if (!mPreview.pictureFlag)
                            {
                                TestUtils.dbgLog(TAG, "Take picture", 'i');
                                mPreview.takePicture();
                            }
                        }
                    }
                    TestUtils.dbgLog(TAG, "mPreview = null", 'i');
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void switchCamera()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    if (mPreview != null)
                    {
                        synchronized (mPreview)
                        {
                            if (mPreview.defaultCameraId == mPreview.backCameraId)
                            {
                                if(mPreview.hasMonoCamera){
                                    TestUtils.dbgLog(TAG, "switch to mono camera", 'i');
                                    mPreview.switchCamera(mPreview.backMonoCameraId);
                                }else{
                                    TestUtils.dbgLog(TAG, "switch to front camera", 'i');
                                    mPreview.switchCamera(mPreview.frontCameraId);
                                }
                            }else if(mPreview.defaultCameraId == mPreview.backMonoCameraId){
                                TestUtils.dbgLog(TAG, "switch to front camera", 'i');
                                mPreview.switchCamera(mPreview.frontCameraId);
                            }
                            else
                            {
                                TestUtils.dbgLog(TAG, "switch to rear camera", 'i');
                                mPreview.switchCamera(mPreview.backCameraId);
                            }
                            sendMessage(handler, Constant.HANDLER_MESSAGE_SET_CONTENT_VIEW);
                        }
                    }
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
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_PROXIMITY);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register Prox sensor", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
            sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_LIGHT);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register Light sensor", 'i');
                this.sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
            }
            sensors = null;
            sensors = this.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
            if (sensors != null && sensors.size() > 0)
            {
                TestUtils.dbgLog(TAG, "Register Accel sensor", 'i');
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
                TestUtils.dbgLog(TAG, "Unregister sensors", 'i');
                this.sensorManager.unregisterListener(this);
                this.isRegister = false;
            }
        }
        catch (Exception e)
        {}
    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constant.HANDLER_MESSAGE_LOG:
                    showToast("Camera/Earpiece/Prox/Light/Accele/SDCard test");
                    logWriter(TAG, "Running");
                    break;
                case Constant.HANDLER_MESSAGE_SENSOR:
                    testSensors();
                    break;
                case Constant.HANDLER_MESSAGE_TAKE_PICTURE:
                    takePicture();
                    break;
                case Constant.HANDLER_MESSAGE_CHECK_PROCESS:
                    checkProcess();
                    break;
                case Constant.HANDLER_MESSAGE_STOP:
                    killActivity();
                    break;
                case Constant.HANDLER_MESSAGE_SWITCH_CAMERA:
                    switchCamera();
                    break;
                case Constant.HANDLER_MESSAGE_SET_CONTENT_VIEW:
                    if (mPreview != null)
                    {
                        synchronized (mPreview)
                        {
                            setContentView(mPreview);
                        }
                    }
                    break;
            }
        }
    };

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1)
    {}

    @Override
    public void onSensorChanged(SensorEvent arg0)
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
                        TestUtils.dbgLog(TAG, "stop playing audio", 'i');
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

            if (this.mPreview != null)
            {
                synchronized (mPreview)
                {
                    try
                    {
                        this.mPreview.release();
                    }
                    catch (Exception ex)
                    {}
                    this.mPreview = null;
                }
            }

            if (this.sensorManager != null)
            {
                unRegisterSenserListener();
                this.sensorManager = null;
            }

            if (this.sdHandler != null)
            {
                this.sdHandler = null;
            }

            TestUtils.dbgLog(TAG, "Test completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }
}
