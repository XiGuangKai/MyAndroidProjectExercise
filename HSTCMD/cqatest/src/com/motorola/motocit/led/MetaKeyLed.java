/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.led;

import java.util.ArrayList;
import java.util.List;

import android.app.Instrumentation;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class MetaKeyLed extends Test_Base
{
    final static int INVALID_KEYCODE = -99999999;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "MetaKeyLed";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.metakeyled);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
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
            contentRecord("testresult.txt", "MetaKeyLed Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            contentRecord("testresult.txt", "MetaKeyLed:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        List<String> strReturnDataList = new ArrayList<String>();

        // see what cmd to execute
        if (strRxCmd.equalsIgnoreCase("TOGGLE_META_KEY_LED"))
        {
            // if list has no data .. return NACK
            if (strRxCmdDataList.size() == 0)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("No key=value pairs sent for %s cmd", strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            int ledKeyCode = INVALID_KEYCODE;

            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                if (key.equalsIgnoreCase("KEYCODE"))
                {
                    ledKeyCode = Integer.parseInt(value);
                }
            }

            if (ledKeyCode == INVALID_KEYCODE)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("KEY_CODE key=value pair missing for %s cmd", strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // generate keycode event
            Instrumentation inst = new Instrumentation();
            inst.sendKeyDownUpSync(ledKeyCode);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
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
        strHelpList.add("This function returns enables and disabled the meta key LEDs");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  TOGGLE_META_KEY_LED  KEYCODE=<meta_keycode> - toggle meta LED controlled by specified KEYCODE");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "MetaKeyLed:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "MetaKeyLed Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
