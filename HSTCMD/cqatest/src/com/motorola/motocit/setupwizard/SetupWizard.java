/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.setupwizard;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class SetupWizard extends Test_Base
{
    private boolean ret_value = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "SetupWizard";
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        moveTaskToBack(true); // move SetupWizard activity background.
        dbgLog(TAG, "move activty to back", 'i');
        sendStartActivityPassed();
    }

    protected boolean checkSetupWizardStatus()
    {
        Context context = getApplicationContext();
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        // Get info from the current running task
        final List<ActivityManager.RunningTaskInfo> runningTasks = am.getRunningTasks(1);

        if ((runningTasks != null) && (runningTasks.size() > 0))
        {
            String name = runningTasks.get(0).topActivity.getClassName();
            dbgLog(TAG, "task_name=" + name, 'i');

            if (name.contentEquals("com.motorola.blur.startup.CpuIdleWaitActivity"))
            {
                ret_value = true;
            }
            else
            {
                ret_value = false;
            }
        }

        return ret_value;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_SETUPWIZARD_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();

            checkSetupWizardStatus();

            if(ret_value == true)
            {
                strDataList.add(String.format("SETUPWIZARD_STATUS=RUNNING"));
            }
            else
            {
                strDataList.add(String.format("SETUPWIZARD_STATUS=COMPLETED"));
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
        strHelpList.add("This function is to verify SetupWizard animation running status");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_SETUPWIZARD_STATUS - Get current setup wizard animation status.");
        strHelpList.add("    SETUPWIZARD_STATUS - returns RUNNING or COMPLETED");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);

        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
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
        return true;
    }
}
