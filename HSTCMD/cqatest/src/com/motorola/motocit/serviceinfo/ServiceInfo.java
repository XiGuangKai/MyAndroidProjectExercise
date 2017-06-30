/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.serviceinfo;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.os.Bundle;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class ServiceInfo extends Test_Base {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "ServiceInfo";
        super.onCreate (savedInstanceState);
    }

    @Override
    protected void onStart()
    {
        super.onStart ();
    }

    @Override
    protected void onResume()
    {
        super.onResume ();

        sendStartActivityPassed ();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy ();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase ("GET_RUNNING_SERVICE_INFO"))
        {
            List<String> strDataList = new ArrayList<String> ();

            Context context = getApplicationContext ();
            ActivityManager manager = (ActivityManager) context.getSystemService (Context.ACTIVITY_SERVICE);

            // keep klocwork happy
            if (null == manager)
            {
                dbgLog (TAG, "Could not retrieve ActivityManager", 'e');
            }

            List<RunningServiceInfo> runningServices = manager.getRunningServices (Integer.MAX_VALUE);

            if (null == runningServices)
            {
                dbgLog (TAG, "Could not retrieve list of running services", 'e');
            }

            strDataList.add ("NUMBER_OF_RUNNING_SERVICES=" + String.valueOf (runningServices.size ()));

            int runningServiceNumber = 0;

            for (RunningServiceInfo service : runningServices)
            {
                // keep klockwork happy
                if (null == service)
                {
                    continue;
                }

                strDataList.add ("ACTIVE_SINCE_" + runningServiceNumber + "=" + service.activeSince);
                strDataList.add ("CLIENT_COUNT_" + runningServiceNumber + "=" + service.clientCount);
                strDataList.add ("CLIENT_LABEL_" + runningServiceNumber + "=" + service.clientLabel);
                strDataList.add ("CLIENT_PACKAGE_" + runningServiceNumber + "=" + service.clientPackage);
                strDataList.add ("CRASH_COUNT_" + runningServiceNumber + "=" + service.crashCount);
                strDataList.add ("LAST_ACTIVITY_TIME_" + runningServiceNumber + "=" + service.lastActivityTime);
                strDataList.add ("PID_" + runningServiceNumber + "=" + service.pid);
                strDataList.add ("PROCESS_" + runningServiceNumber + "=" + service.process);
                strDataList.add ("RESTARTING_" + runningServiceNumber + "=" + service.restarting);
                strDataList.add ("USER_ID_" + runningServiceNumber + "=" + service.uid);

                runningServiceNumber++;
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket (nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer (infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String> ();
            throw new CmdPassException (nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase ("GET_RUNNING_APP_INFO"))
        {
            List<String> strDataList = new ArrayList<String> ();

            Context context = getApplicationContext ();
            final ActivityManager am = (ActivityManager) context.getSystemService (Context.ACTIVITY_SERVICE);

            // Get info from the current running task
            final List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses ();

            strDataList.add ("NUMBER_OF_RUNNING_APPLICATIONS=" + String.valueOf (runningAppProcesses.size ()));

            int runningAppNumber = 0;

            for (RunningAppProcessInfo runningAppInfo : runningAppProcesses)
            {
                strDataList.add ("IMPORTANCE_" + runningAppNumber + "=" + runningAppInfo.importance);
                strDataList.add ("PID_" + runningAppNumber + "=" + runningAppInfo.pid);
                strDataList.add ("PROCESS_NAME_" + runningAppNumber + "=" + runningAppInfo.processName);
                strDataList.add ("USER_ID_" + runningAppNumber + "=" + runningAppInfo.uid);

                runningAppNumber++;
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket (nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer (infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String> ();
            throw new CmdPassException (nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase ("help"))
        {
            printHelp ();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String> ();
            strReturnDataList.add (String.format ("%s help printed", TAG));
            throw new CmdPassException (nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String> ();
            strErrMsgList.add (String.format ("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog (TAG, strErrMsgList.get (0), 'i');
            throw new CmdFailException (nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String> ();

        strHelpList.add (TAG);
        strHelpList.add ("");
        strHelpList.add ("This function is to get all running service information");
        strHelpList.add ("");

        strHelpList.addAll (getBaseHelp ());

        strHelpList.add ("Activity Specific Commands");
        strHelpList.add ("  ");
        strHelpList.add ("GET_RUNNING_SERVICE_INFO - Get all running service information.");
        strHelpList.add ("  ");
        strHelpList.add ("GET_RUNNING_APP_INFO - Get all running service information.");

        CommServerDataPacket infoPacket = new CommServerDataPacket (nRxSeqTag, strRxCmd, TAG, strHelpList);

        sendInfoPacketToCommServer (infoPacket);
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
