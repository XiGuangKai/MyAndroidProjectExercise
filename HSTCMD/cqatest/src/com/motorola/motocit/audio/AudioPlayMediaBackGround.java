/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AudioPlayMediaBackGround extends Test_Base
{
    private AudioManager audioManager;

    private MediaPlayer mediaPlayer = null;

    private int maxMediaVolume;

    private int mStreamType = AudioManager.STREAM_MUSIC;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_Playmediabackground";

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        moveTaskToBack(true); // move activity background.

        // Route audio to speaker
        audioManager = (AudioManager) AudioPlayMediaBackGround.this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
        dbgLog(TAG, "SetSpeakerOn = True", 'i');
        maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        audioManager.setMode(AudioManager.MODE_NORMAL);

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (maxMediaVolume * 0.75), 0);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        sendStartActivityPassed();
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("PLAY_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            String mediaFileName = "";
            boolean isSpecificMediaFile = false;

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 2)
                {
                    strReturnDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                if (strRxCmdDataList.get(0).equalsIgnoreCase("FILE_NAME"))
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
                    if (isSpecificMediaFile)
                    {
                        // Play media file which located in /mnt/sdcard/
                        mediaPlayer.setDataSource("/mnt/sdcard/" + mediaFileName);
                    }
                }
                catch (Exception e)
                {
                    strReturnDataList.add("UNABLE TO SET MEDIA DATA SOURCE");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                mediaPlayer.setAudioStreamType(mStreamType);

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
            }
            catch (IllegalStateException e)
            {
                strReturnDataList.add("UNABLE TO PLAY MEDIA FILE");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

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

    protected void setStreamVolume(int stream, int volume)
    {
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
    protected void printHelp()
    {

        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will play audio file in background");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("PLAY_MEDIA_FILE - FILE_NAME + audio_file_name which is in /mnt/sdcard/ folder");
        strHelpList.add("  ");
        strHelpList.add("STOP_MEDIA_FILE - Stop playing media file");
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

            contentRecord("testresult.txt", "Audio Playmediabackground:  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Audio Playmediabackground:  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

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
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Audio Playmediabackground:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Audio Playmediabackground:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

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
            systemExitWrapper(0);
        }
        return true;
    }
}
