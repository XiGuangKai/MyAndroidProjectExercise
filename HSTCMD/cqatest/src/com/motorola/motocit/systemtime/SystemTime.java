/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.systemtime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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

public class SystemTime extends Test_Base {
    private TextView mSystemTime;

    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;
    private String am_pm;
    private String formattedDate;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "SystemTime";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.systemtime);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        mSystemTime = (TextView) findViewById(com.motorola.motocit.R.id.system_time);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        getSystemTime();
        mSystemTime.setText(formattedDate);

        sendStartActivityPassed();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

    }

    protected void getSystemTime()
    {
        Calendar current = Calendar.getInstance();
        dbgLog(TAG, "Current time => " + current.getTime(), 'i');

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formattedDate = df.format(current.getTime());

        year = current.get(Calendar.YEAR);
        month = current.get(Calendar.MONTH);
        day = current.get(Calendar.DATE);
        hour = current.get(Calendar.HOUR_OF_DAY);
        minute = current.get(Calendar.MINUTE);
        second = current.get(Calendar.SECOND);

        if (current.get(Calendar.AM_PM) == Calendar.AM)
        {
            am_pm = "AM";
        }
        else if (current.get(Calendar.AM_PM) == Calendar.PM)
        {
            am_pm = "PM";
        }

        formattedDate = formattedDate + am_pm;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_SYSTEMTIME"))
        {
            List<String> strDataList = new ArrayList<String>();
            getSystemTime();
            UpdateUiTextView updateUiSystemTime = new UpdateUiTextView(mSystemTime, Color.WHITE, formattedDate);
            runOnUiThread(updateUiSystemTime);

            strDataList.add(String.format("YEAR=" + year));
            strDataList.add(String.format("MONTH=" + (month + 1)));
            strDataList.add(String.format("DAY=" + day));
            strDataList.add(String.format("HOUR=" + hour));
            strDataList.add(String.format("MINUTE=" + minute));
            strDataList.add(String.format("SECOND=" + second));
            strDataList.add(String.format("AM_PM=" + am_pm));

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
        strHelpList.add("This function will get current system time");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_SYSTEMTIME - Returns system time values");
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

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP))
        {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            {
                contentRecord("testresult.txt", "SystemTime Test: PASS" + "\r\n", MODE_APPEND);

                logTestResults(TAG, TEST_PASS, null, null);
            }
            else
            {
                contentRecord("testresult.txt", "SystemTime Test: FAILED" + "\r\n", MODE_APPEND);

                logTestResults(TAG, TEST_FAIL, null, null);
            }

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
        contentRecord("testresult.txt", "SystemTime Test: FAILED" + "\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "SystemTime Test: PASS" + "\r\n", MODE_APPEND);

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
