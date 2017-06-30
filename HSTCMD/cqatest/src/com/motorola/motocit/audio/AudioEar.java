/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AudioEar extends Test_Base
{
    private MediaPlayer player = null;
    private AudioManager audioManager = null;
    private int maxVolume;
    private int initVolume;
    private Button rstCyclingButton;
    private Button earpieceVerificationButton;
    private int audioFilesCounter;
    private static final String[] audioFiles =
        { "alt_audio_100_1k_6sec_log_sweep.wav", "earpiece_buzz_test_speech_max_volume.mp3" };
    private static final int HANDLER_MESSAGE_NEXT = 1;
    private static final int TYPE_RST = 0;
    private static final int TYPE_EARPIECE = 1;
    private static final int TYPE_ALL = 2;
    private Timer timer;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_EarSpeaker";
        super.onCreate(savedInstanceState);

        init();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startTest(TYPE_RST);
        sendStartActivityPassed();
    }

    private void startTest(int type)
    {
        switch (type)
        {
        case TYPE_RST:
            stop(TYPE_EARPIECE);
            rstCyclingTest();
            break;
        case TYPE_EARPIECE:
            stop(TYPE_RST);
            earpieceVerificationTest();
            break;
        }
    }

    private void rstCyclingTest()
    {
        rstCyclingButton.setEnabled(false);
        earpieceVerificationButton.setEnabled(true);

        double toneLength = 0;
        int sampleRate = 0;
        int startFreq = 0;
        int stopFreq = 0;
        int volume = 0;

        AudioSettings audioparam = new AudioSettings();

        if (AudioFunctions.getAudioSettingsFromConfig(audioparam))
        {
            toneLength = audioparam.m_length;
            sampleRate = audioparam.m_sample_rate;
            startFreq = audioparam.m_start_freq;
            stopFreq = audioparam.m_end_freq;
            volume = audioparam.m_volume;
        }

        try
        {
            byte tone[] = AudioFunctions.createTone(toneLength, sampleRate, startFreq, stopFreq, volume, true);

            int numberTargetSamples = (int) (toneLength * sampleRate);
            audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 2 * numberTargetSamples, AudioTrack.MODE_STATIC);
            audioTrack.write(tone, 0, 2 * numberTargetSamples);

            audioTrack.setLoopPoints(0, numberTargetSamples, -1);

            audioTrack.play();
        }
        catch (Exception e)
        {
        }
    }

    private void earpieceVerificationTest()
    {
        earpieceVerificationButton.setEnabled(false);
        rstCyclingButton.setEnabled(true);
        try
        {
            playAudio(audioFiles[1]);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    private void startNext()
    {
        try
        {
            stopAudio();
            playAudio(audioFiles[audioFilesCounter]);
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
    }

    private void stop(int type)
    {
        switch (type)
        {
        case TYPE_RST:
            if (audioTrack != null)
            {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                {
                    audioTrack.stop();
                }
                audioTrack.release();
            }
            break;
        case TYPE_EARPIECE:
            try
            {
                if (timer != null)
                {
                    timer.cancel();
                    timer = null;
                }
            }
            catch (Exception e)
            {
            }
            stopAudio();
            break;
        case TYPE_ALL:
            try
            {
                if (timer != null)
                {
                    timer.cancel();
                    timer = null;
                }
            }
            catch (Exception e)
            {
            }
            stopAudio();
            break;
        }
    }

    private void playAudio(String audioFile) throws Throwable
    {
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);

        AssetFileDescriptor assetDescriptor = null;
        try
        {
            assetDescriptor = getAssets().openFd(audioFile);
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
        }
        long start = assetDescriptor.getStartOffset();
        long end = assetDescriptor.getLength();

        try
        {
            player.setDataSource(assetDescriptor.getFileDescriptor(), start, end);
            player.setLooping(true);
            player.prepare();
            player.start();
        }
        catch (Exception e)
        {
        }
    }

    private void stopAudio()
    {
        try
        {
            if ((player != null) && player.isPlaying())
            {
                player.stop();
            }
        }
        catch (Exception e)
        {
        }
    }

    private void sendMessage(int msgInt)
    {
        Message msg = new Message();
        msg.what = msgInt;
        handler.sendMessage(msg);
    }

    private Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if (msg.what == HANDLER_MESSAGE_NEXT)
            {
                if (audioFilesCounter < (audioFiles.length - 1))
                {
                    audioFilesCounter++;
                    startNext();
                }
                else
                {
                    audioFilesCounter = 0;
                    startNext();
                }
            }
        }
    };

    private void init()
    {
        try
        {
            setTitle("Ear Speaker");

            View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_ear);
            if (mGestureListener != null)
            {
                thisView.setOnTouchListener(mGestureListener);
            }

            audioManager = (AudioManager) AudioEar.this.getSystemService(Context.AUDIO_SERVICE);

            setAudioMode();
            setAudioInCallVolume();
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

            dbgLog(TAG, "AudioManager getMode: " + audioManager.getMode(), 'd');
            dbgLog(TAG, "AudioFile.len: " + audioFiles.length, 'd');

            rstCyclingButton = (Button) AudioEar.this.findViewById(com.motorola.motocit.R.id.function_audio_rst_cycling);
            earpieceVerificationButton = (Button) AudioEar.this.findViewById(com.motorola.motocit.R.id.function_audio_earpiece_verification);

            rstCyclingButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    startTest(TYPE_RST);
                }
            });
            earpieceVerificationButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    startTest(TYPE_EARPIECE);
                }
            });
        }
        catch (Exception e)
        {
        }
    }

    private void release()
    {
        try
        {
            if (audioTrack != null)
            {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                {
                    audioTrack.stop();
                }

                audioTrack.release();
                audioTrack = null;
            }
            if (player != null)
            {
                if (player.isPlaying())
                {
                    player.stop();
                    restoreAudio();
                }
                player.release();
                player = null;
            }
            if (audioManager != null)
            {
                dbgLog(TAG, "restore init voice call volume=" + initVolume, 'i');
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, initVolume, 0);
                audioManager = null;
            }
            if (timer != null)
            {
                timer.cancel();
                timer = null;
            }
        }
        catch (Exception e)
        {
        }
    }

    private void restoreAudio()
    {
        // Restore audio stream type
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    private void setAudioMode()
    {
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
        dbgLog(TAG, "SetSpeakerOn = False", 'i');
    }

    private void setAudioInCallVolume()
    {
        // Record current in-call volume, will restore it release()
        initVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        dbgLog(TAG, "init voice call volume=" + initVolume, 'i');
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        dbgLog(TAG, "max voice call volume=" + maxVolume, 'i');
        // Set In-call volume = maxVolume
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, AudioManager.FLAG_SHOW_UI);
    }

    @Override
    protected void onPause()
    {
        dbgLog(TAG, "onPause", 'd');
        release();
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        dbgLog(TAG, "onDestroy", 'd');
        release();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Audio - EarSpeaker:  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            release();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Audio - EarSpeaker:  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            release();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                release();
                finish();
            }
        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS"))
        {

        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will read perform a Earpiece Speaker Test");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Audio - EarSpeaker:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        release();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Audio - EarSpeaker:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        release();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeUp()
    {
        return true;
    }

    @Override
    public boolean onSwipeDown()
    {
        if (modeCheck("Seq"))
        {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

            return false;
        }
        else
        {
            release();
            finish();
        }
        return true;
    }
}
