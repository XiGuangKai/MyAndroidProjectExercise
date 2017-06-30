/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AudioPlayMediaFile extends Test_Base
{
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private Button playButton;
    private String buttonText;
    private int maxMediaVolume;
    private int initMediaVolume;
    private SeekBar volumeSeekbar = null;
    private boolean isPlayingCompleted = false;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_LoudSpeaker";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_mediaplay);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        playButton = (Button) findViewById(com.motorola.motocit.R.id.resourcesaudio);
        buttonText = getResources().getString(com.motorola.motocit.R.string.audio_mediaplay_res);
        volumeSeekbar = (SeekBar) findViewById(com.motorola.motocit.R.id.volumeseekbar);

        // Rout audio to speaker
        audioManager = (AudioManager) AudioPlayMediaFile.this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
        dbgLog(TAG, "SetSpeakerOn = True", 'i');

        audioManager.setMode(AudioManager.MODE_NORMAL);

        // Record current media volume, will restore it
        initMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, AudioManager.FLAG_SHOW_UI);

        volumeSeekbar.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeSeekbar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

        playButton.setOnClickListener(playMediaFileListener);
        volumeSeekbar.setOnSeekBarChangeListener(seekBarChangeListener);
    }

    private synchronized void playAudio()
    {
        isPlayingCompleted = false;
        isPlaying = false;

        mediaPlayer = new MediaPlayer();
        try
        {
            if (mediaPlayer != null)
            {
                mediaPlayer.release();
            }

            mediaPlayer = MediaPlayer.create(AudioPlayMediaFile.this, com.motorola.motocit.R.raw.moto);

            if (mediaPlayer == null)
            {
                return;
            }

            mediaPlayer.setOnCompletionListener(new OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    playButton.setText(buttonText);
                    playButton.setEnabled(true);
                    isPlayingCompleted = true;
                    isPlaying = false;
                }
            });

            mediaPlayer.start();
            isPlaying = true;

        }
        catch (IllegalStateException e)
        {
            e.printStackTrace();
        }
    }

    private OnClickListener playMediaFileListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            playButton.setText("Playing media file...");
            playButton.setEnabled(false);
            isPlayingCompleted = false;
            playAudio();
        }
    };

    private OnSeekBarChangeListener seekBarChangeListener = new OnSeekBarChangeListener()
    {
        @Override
        public void onStopTrackingTouch(SeekBar arg0)
        {}

        @Override
        public void onStartTrackingTouch(SeekBar arg0)
        {}

        @Override
        public void onProgressChanged(SeekBar arg0, int progress, boolean arg2)
        {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
        }
    };

    private void release()
    {
        if (mediaPlayer != null)
        {
            // Restore media volume;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initMediaVolume, 0);

            try
            {
                Thread.sleep(1000);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "sleep exceptions..." + e, 'd');
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
        finish();
    }

    protected void setStreamVolume(int stream, int volume)
    {
        dbgLog(TAG, "max volume=" + audioManager.getStreamMaxVolume(stream), 'i');
        if (volume > audioManager.getStreamMaxVolume(stream))
        {
            audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0);
        }
        else
        {
            audioManager.setStreamVolume(stream, volume, 0);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!wasActivityStartedByCommServer())
        {
            // do not play audio automatically when started by CommServer
            playButton.setText("Playing media file...");
            playButton.setEnabled(false);
            playAudio();
        }
        sendStartActivityPassed();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        isPlaying = false;
        isPlayingCompleted = false;
        release();
    }

    @Override
    public void onDestroy()
    {
        dbgLog(TAG, "onDestroy", 'd');

        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("PLAY_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            String mediaFileName = "";
            boolean isSpecificMediaFile = false;
            isPlayingCompleted = false;
            isPlaying = false;

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 2)
                {
                    strReturnDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                if (strRxCmdDataList.get(0).equalsIgnoreCase("MOTO_LOUDSPEAKER_TEST"))
                {
                    playAudio();
                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("FILE_NAME"))
                {
                    if (strRxCmdDataList.size() == 2)
                    {
                        mediaFileName = strRxCmdDataList.get(1).toString();

                        if (mediaFileName.isEmpty())
                        {
                            strReturnDataList.add("MEDIA FILE NAME IS EMPTY");
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }

                        isSpecificMediaFile = true;
                    }
                    else
                    {
                        strReturnDataList.add("NO_MEDIA_FILE_SPECIFIED");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
                else
                {
                    strReturnDataList.add("UNKNOWN: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }
            else
            {
                strReturnDataList.add("NO_MEDIA_FILE_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (isSpecificMediaFile)
            {
                if (mediaPlayer != null)
                {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }

                mediaPlayer = new MediaPlayer();

                try
                {
                    try
                    {
                        String path = "";
                        File path_12m = new File("/system/etc/motorola/12m/" + mediaFileName);
                        File path_sdcard = new File("/mnt/sdcard/" + mediaFileName);
                        if (path_sdcard.exists())
                        {
                            path = "/mnt/sdcard/";
                        }
                        else if (path_12m.exists())
                        {
                            path = "/system/etc/motorola/12m/";
                        }

                        dbgLog(TAG, "media file path:" + path + mediaFileName, 'i');
                        // Play media file
                        mediaPlayer.setDataSource(path + mediaFileName);
                    }
                    catch (Exception e)
                    {
                        strReturnDataList.add("UNABLE TO SET MEDIA DATA SOURCE");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

                    try
                    {
                        mediaPlayer.prepare();
                    }
                    catch (IOException e)
                    {
                        strReturnDataList.add("UNABLE TO PREPARE MEDIA FILE");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                    if (mediaPlayer == null)
                    {
                        strReturnDataList.add("MEDIA FILE NOT FOUND");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                    mediaPlayer.setLooping(true); // Loop playing media file

                    mediaPlayer.start();
                    isPlaying = true;
                }
                catch (IllegalStateException e)
                {
                    strReturnDataList.add("UNABLE TO PLAY MEDIA FILE");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            isPlaying = false;

            if (mediaPlayer != null)
            {
                if (mediaPlayer.isPlaying() == true)
                {
                    mediaPlayer.stop();
                }

                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_VOLUME_SETTINGS"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("MUSIC_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_MUSIC, Integer.parseInt(value));
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN KEY: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

        }
        else if (strRxCmd.equalsIgnoreCase("GET_VOLUME_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("MUSIC_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
            strDataList.add("MUSIC_VOLUME_MAX=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("GET_PLAYING_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();
            String status = "";
            if (isPlayingCompleted)
            {
                status = "COMPLETED";
            }
            else if (isPlaying)
            {
                status = "PLAYING";
            }
            else
            {
                status = "NOT_STARTED";
            }

            strDataList.add("PLAY_STATUS=" + status);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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
        strHelpList.add("This function will play a media file through the loudspeaker");
        strHelpList.add("");
        strHelpList.addAll(getBaseHelp());
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("PLAY_MEDIA_FILE - Play default Moto ogg file or specific media file");
        strHelpList.add("  ");
        strHelpList.add("  MOTO_LOUDSPEAKER_TEST - Play default moto ogg ringtone");
        strHelpList.add("  ");
        strHelpList.add("  FILE_NAME - Media file name. No path including in name");
        strHelpList.add("  ");
        strHelpList.add("STOP_MEDIA_FILE - Stop playing");
        strHelpList.add("  ");
        strHelpList.add("SET_VOLUME_SETTINGS - Set volume");
        strHelpList.add("  ");
        strHelpList.add("  MUSIC_VOLUME - Set music volume. The range is 0-15");
        strHelpList.add("  ");
        strHelpList.add("GET_VOLUME_SETTINGS - Get volume");
        strHelpList.add("  ");
        strHelpList.add("  Return MUSIC_VOLUME and MUSIC_VOLUME_MAX");
        strHelpList.add("  ");
        strHelpList.add("GET_PLAYING_STATUS - Get play status");
        strHelpList.add("  ");
        strHelpList.add("  Return NOT_STARTED or PLAYING or COMPLETED");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
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

            contentRecord("testresult.txt", "Audio - LoudSpeaker:  PASS" + "\r\n\r\n", MODE_APPEND);
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

            contentRecord("testresult.txt", "Audio - LoudSpeaker:  FAILED" + "\r\n\r\n", MODE_APPEND);
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
            }
        }

        return true;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Audio - LoudSpeaker:  FAILED" + "\r\n\r\n", MODE_APPEND);
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
        contentRecord("testresult.txt", "Audio - LoudSpeaker:  PASS" + "\r\n\r\n", MODE_APPEND);
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
        }
        return true;
    }
}
