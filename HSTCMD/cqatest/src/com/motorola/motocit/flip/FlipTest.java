/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.flip;

import java.util.ArrayList;
import java.util.List;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class FlipTest extends Test_Base
{
    private TextView flipTestTextView = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "FlipTest";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.fliptest);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        flipTestTextView = (TextView) findViewById(com.motorola.motocit.R.id.fliptest);

        /**
         * Refer to Moto main-dev code
         * motorola/packages/blur/apps/Phone_umts/src
         * /com/android/phone/InCallScreen.java mIsFlipOpen =
         * getResources().getConfiguration().hardKeyboardHidden ==
         * Configuration.HARDKEYBOARDHIDDEN_NO;
         */
        Configuration config = getResources().getConfiguration();
        if (config.hardKeyboardHidden == Configuration.KEYBOARDHIDDEN_NO)
        {
            dbgLog(TAG, "FLIP Open", 'd');
            flipTestTextView.setTextColor(Color.GREEN);
            flipTestTextView.setTextSize(20f);
            flipTestTextView.setText("FLIP Open");
        }
        else
        {
            dbgLog(TAG, "FLIP Close", 'd');
            flipTestTextView.setTextColor(Color.RED);
            flipTestTextView.setTextSize(20f);
            flipTestTextView.setText("FLIP Close");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        newConfig = getResources().getConfiguration();
        if (newConfig.hardKeyboardHidden == Configuration.KEYBOARDHIDDEN_NO)
        {
            dbgLog(TAG, "FLIP Open", 'd');
            flipTestTextView.setTextColor(Color.GREEN);
            flipTestTextView.setTextSize(20f);
            flipTestTextView.setText("FLIP Open");
            flipTestTextView.postInvalidate();
        }
        else
        {
            dbgLog(TAG, "FLIP Close", 'd');
            flipTestTextView.setTextColor(Color.RED);
            flipTestTextView.setTextSize(20f);
            flipTestTextView.setText("FLIP Close");
            flipTestTextView.postInvalidate();
        }

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

            contentRecord("testresult.txt", "FLIP Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "FLIP Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_READING"))
        {
            List<String> strDataList = new ArrayList<String>();

            Configuration config = getResources().getConfiguration();

            if (config.hardKeyboardHidden == Configuration.KEYBOARDHIDDEN_NO)
            {
                strDataList.add(String.format("FLIP_STATE=OPEN"));
            }
            else
            {
                strDataList.add(String.format("FLIP_STATE=CLOSED"));
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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will read the state of the flip or slider");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Reads the current flip/slider state");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "FLIP Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "FLIP Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
