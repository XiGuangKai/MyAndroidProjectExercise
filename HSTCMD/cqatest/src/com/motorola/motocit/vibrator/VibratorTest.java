/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.vibrator;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class VibratorTest extends Test_Base
{
    private Vibrator vibratorService;
    private int mDelayBeforeVibratorStart = 0;
    private int mVibratorOnTime = 1000;
    private int mVibratorOffTime = 100;
    private ToggleButton vibratorToggleButton;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "VibratorTest";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.vibrator);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // Get Vibrator service
        vibratorService = (Vibrator) getApplication().getSystemService(Context.VIBRATOR_SERVICE);

        vibratorToggleButton = (ToggleButton) findViewById(com.motorola.motocit.R.id.vibratorToggleButton);

        vibratorToggleButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (vibratorToggleButton.isChecked())
                {

                    // Vibrate until cancel
                    vibratorService.vibrate(new long[]
                            { 100, 1000 }, 0);

                    // Show Vibration is turned ON in Toast
                    Toast.makeText(VibratorTest.this, getString(com.motorola.motocit.R.string.str_ok), Toast.LENGTH_SHORT).show();
                }
                else
                {
                    // Cancel Vibration
                    vibratorService.cancel();

                    // Show Vibration is cancelled in Toast
                    Toast.makeText(VibratorTest.this, getString(com.motorola.motocit.R.string.str_end), Toast.LENGTH_SHORT).show();
                }
            }
        });

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

            contentRecord("testresult.txt", "Vibrator Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Vibrator Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    public void onResume()
    {
        super.onResume();
        vibratorService.cancel();
        // Once the activity is resumed and the vibrator is disabled the
        // ToggleButton needs to have its status updated to false, otherwise
        // the button will show 'ON' when the vibrator is turned off.
        vibratorToggleButton.setChecked(false);
        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        vibratorService.cancel();
        vibratorToggleButton.setChecked(false);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        vibratorService.cancel();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("SET_VIBRATOR_SETTINGS"))
        {

            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (int i = 0; i < strRxCmdDataList.size(); i++)
                {

                    if (strRxCmdDataList.get(i).toUpperCase().contains("START_DELAY"))
                    {
                        mDelayBeforeVibratorStart = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));
                    }
                    else if (strRxCmdDataList.get(i).toUpperCase().contains("ON_TIME"))
                    {
                        mVibratorOnTime = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));
                    }
                    else if (strRxCmdDataList.get(i).toUpperCase().contains("OFF_TIME"))
                    {
                        mVibratorOffTime = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + strRxCmdDataList.get(i));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

        }
        else if (strRxCmd.equalsIgnoreCase("GET_VIBRATOR_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("START_DELAY=" + mDelayBeforeVibratorStart);
            strDataList.add("ON_TIME=" + mVibratorOnTime);
            strDataList.add("OFF_TIME=" + mVibratorOffTime);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_VIBRATOR"))
        {
            vibratorService.vibrate(new long[]
                    { mDelayBeforeVibratorStart, mVibratorOnTime, mVibratorOffTime }, 0);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_OFF_VIBRATOR"))
        {
            vibratorService.cancel();

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
        strHelpList.add("This function enables the vibrator");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("SET_VIBRATOR_SETTINGS - Change vibratory settings for START_DELAY, ON_TIME, and OFF_TIME");
        strHelpList.add("  ");
        strHelpList.add("  START_DELAY - Delay time in mS before Vibrator is turned on.");
        strHelpList.add("  ");
        strHelpList.add("  ON_TIME - Time in mS for vibrator to be on for each cycle.");
        strHelpList.add("  ");
        strHelpList.add("  OFF_TIME - Time in mS for vibrator to be off for each cycle.");
        strHelpList.add("  ");
        strHelpList.add("GET_VIBRATOR_SETTINGS - Read vibratory settings for START_DELAY, ON_TIME, and OFF_TIME");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_VIBRATOR - Turn On Vibrator.");
        strHelpList.add("  ");
        strHelpList.add("TURN_OFF_VIBRATOR - Turn off Vibrator");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Vibrator Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Vibrator Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
