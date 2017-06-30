/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.ConfigTools;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

import java.io.File;

public class FunctionVideoTestActivity extends ALTBaseActivity
{
    private AudioManager audioManager;
    private int maxVolume;
    private VideoView mVideoView;
    private CountDownTimer cdTimer = null;

    @Override
    void init()
    {
        try
        {
            this.TAG = "FunctionVideoTestActivity";
            setTitle("Video Test");
            TestUtils.dbgLog(TAG, "Starting Video Test", 'i');
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            setContentView(com.motorola.motocit.R.layout.alt_function_video_videoview);
            audioManager = (AudioManager) FunctionVideoTestActivity.this.getSystemService(Context.AUDIO_SERVICE);
            maxVolume = (int) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int configvolume = ConfigTools.getPlaybackVolume("VIDEO_PLAYBACK_VOLUME");
            if (configvolume > 0 && configvolume <= maxVolume)
            {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, configvolume, AudioManager.FLAG_SHOW_UI);
            }
            else
            {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI);
            }

            this.audioManager.setSpeakerphoneOn(true);
            this.audioManager.setMode(AudioManager.MODE_NORMAL);

            this.mVideoView = (VideoView) this.findViewById(com.motorola.motocit.R.id.function_video_videoview);
            // this.mVideoView.setVideoURI(Uri.parse("android.resource://" +
            // "com.motorola.motocit" + "/" + R.raw.alt_autocycle_video));
            File videoFile = new File("/system/etc/motorola/12m/alt_autocycle_video.mp4");
            if (videoFile.exists())
            {
                this.mVideoView.setVideoURI(Uri.parse("/system/etc/motorola/12m/alt_autocycle_video.mp4"));
                this.mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp)
                    {
                        sendMessage(handler, Constant.HANDLER_MESSAGE_VIDEO);
                    }
                });
                this.mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what,
                            int extra)
                    {
                        logWriter("on error", "VideoView.setOnErrorListener");
                        return false;
                    }
                });
            }
            else
            {
                TestUtils.dbgLog(TAG, "No video media file exists", 'e');
                showToast("Video media file missing");
                killActivity();
                return;
            }
        }
        catch (Exception e)
        {
            TestUtils.dbgLog(TAG, "init exception: " + e.getMessage(), 'e');
            logWriter("init exception:", e.getMessage());
        }
    }

    @Override
    void start()
    {
        isRunning = true;
        TestUtils.dbgLog(TAG, "Start,isRunning="+isRunning, 'e');
        sendMessage(handler, Constant.HANDLER_MESSAGE_VIDEO);
        cdTimer = new CountDownTimer(realIntervalTime, 60000){
            @Override
            public void onTick(long millisUntilFinished) {
                sendMessage(handler, Constant.HANDLER_MESSAGE_VIDEO_CHECK);
                checkProcess();
                TestUtils.dbgLog(TAG, "millisUntilFinished="+millisUntilFinished, 'e');
                if(!isRunning){
                    cdTimer.cancel();
                }
            }
            @Override
            public void onFinish() {
                sendMessage(handler, Constant.HANDLER_MESSAGE_STOP);
                TestUtils.dbgLog(TAG, "End,isRunning="+isRunning, 'e');
            }
        }.start();
    }

    private void playVideo()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    mVideoView.requestFocus();
                    mVideoView.start();
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void videoCheck()
    {
        if (this.mVideoView.isPlaying())
        {
            showToast("Video Test");
            logWriter(TAG, "Running");
        }else{
            TestUtils.dbgLog(TAG, "Video is not playing, restart it", 'e');
            logWriter(TAG, "Not Running,Restart");
            playVideo();
        }
    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constant.HANDLER_MESSAGE_VIDEO:
                    playVideo();
                    break;
                case Constant.HANDLER_MESSAGE_VIDEO_CHECK:
                    videoCheck();
                    break;
                case Constant.HANDLER_MESSAGE_STOP:
                    killActivity();
                    TestUtils.dbgLog(TAG, "received stop handler message", 'i');
                    break;
            }
        }
    };

    @Override
    void release()
    {
        try
        {
            if (this.mVideoView != null)
            {
                this.mVideoView = null;
            }
            if (this.audioManager != null)
            {
                this.audioManager = null;
            }

            TestUtils.dbgLog(TAG, "Video test is completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }
}
