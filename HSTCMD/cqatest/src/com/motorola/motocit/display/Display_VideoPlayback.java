/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.VideoView;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Display_VideoPlayback extends Test_Base
{
    /** Called when the activity is first created. */
    private VideoView mVideoView;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Display_VideoPlayback";
        super.onCreate(savedInstanceState);

        // Hide soft keys on ice cream sandwich
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        {
            Window window = this.getWindow();  // keep klocwork happy
            if (null != window)
            {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION );
            }
        }

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.video_playback_layout, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, false);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        int width = 0;
        int height = 0;

        Uri uri;

        mVideoView = (VideoView) findViewById(com.motorola.motocit.R.id.surface_view);

        width = display.getWidth();
        height = display.getHeight();

        if (((width >= 800) && (height >= 1280)) || ((width >= 1280) && (height >= 800)))
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_800_1280_mpeg4);
        }
        else if (((width >= 540) && (height >= 960)) || ((width >= 960) && (height >= 540)))
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_540_960_mpeg4);
        }
        else if (((width >= 480) && (height >= 854)) || ((width >= 854) && (height >= 480)))
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_854_480_mpeg4);
        }
        else if (((width >= 480) && (height >= 320)) || ((width >= 320) && (height >= 480)))
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_320_480_mpeg4);
        }
        else if (((width >= 240) && (height >= 320)) || ((width >= 320) && (height >= 240)))
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_240_320_mpeg4);
        }
        else
        {
            uri = Uri.parse("android.resource://com.motorola.motocit/" + com.motorola.motocit.R.raw.white_240_320_mpeg4);
        }

        mVideoView.setVideoURI(uri);

        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
        {

            @Override
            public void onPrepared(MediaPlayer mp)
            {
                mVideoView.start();
                mVideoView.requestFocus();

                long startTime = System.currentTimeMillis();
                long endTime = startTime + (20 * 1000);
                while ((mVideoView.isPlaying() != true) && (System.currentTimeMillis() < endTime))
                {

                }

                if (mVideoView.isPlaying() == true)
                {
                    sendStartActivityPassed();
                }
                else
                {
                    sendStartActivityFailed("VIDEO_FAILED_TO_PLAY");
                }
            }
        });

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mp)
            {
                mVideoView.start();
                mVideoView.requestFocus();
            }
        });

    }

    @Override
    protected void onResume()
    {
        // Send start activity passed sent in onCreate when video starts playing
        super.onResume();

    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("IS_PLAYING"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (mVideoView.isPlaying() == true)
            {
                strDataList.add(String.format("VIDEO_PLAYING=TRUE"));
            }
            else
            {
                strDataList.add(String.format("VIDEO_PLAYING=FALSE"));
            }

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

        strHelpList.add("Display_VideoPlayback");
        strHelpList.add("");
        strHelpList
        .add("This activity plays a video which is a white background.  There have been cases where the hardware will allow still images to be displayed, but videos are distorted.  This activity should catch these.");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
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
            contentRecord("testresult.txt", "Display - VideoPlayback:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            contentRecord("testresult.txt", "Display - VideoPlayback:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display - VideoPlayback:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display - VideoPlayback:  PASS" + "\r\n\r\n", MODE_APPEND);

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
