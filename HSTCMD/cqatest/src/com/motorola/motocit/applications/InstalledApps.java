/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.applications;

import java.util.ArrayList;
import java.util.List;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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

public class InstalledApps extends Test_Base
{

    private List<ApplicationInfo> mApplicationInfo;
    private PackageManager mPackageManager;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Installed_Apps";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.installed_apps);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        TextView InstalledAppsText = (TextView) findViewById(com.motorola.motocit.R.id.numberOfInstalledApps);

        mPackageManager = getPackageManager();

        mApplicationInfo = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        InstalledAppsText.setText(String.valueOf(mApplicationInfo.size()));
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();

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

            contentRecord("testresult.txt", "Application Info" + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Installed Apps: " + String.valueOf(mApplicationInfo.size()) + "\r\n\r\n", MODE_APPEND);

            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            {
                logResults(TEST_PASS);
            }
            else
            {
                logResults(TEST_FAIL);
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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_NUMBER_OF_APPS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("NUMBER_OF_APPLICATIONS=" + String.valueOf(mApplicationInfo.size()));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_APP_INFORMATION"))
        {
            List<String> strDataList = new ArrayList<String>();

            int appNumber = 0;
            PackageInfo packageInfo;

            for (ApplicationInfo appInfo : mApplicationInfo)
            {
                strDataList.add("LABEL_" + appNumber + "=" + mPackageManager.getApplicationLabel(appInfo));
                strDataList.add("ENABLED_" + appNumber + "=" + String.valueOf(appInfo.enabled));
                strDataList.add("PACKAGE_NAME_" + appNumber + "=" + appInfo.packageName);
                strDataList.add("PROCESS_NAME_" + appNumber + "=" + appInfo.processName);
                strDataList.add("PUBLIC_SOURCE_DIR_" + appNumber + "=" + appInfo.publicSourceDir);
                strDataList.add("SOURCE_DIR_" + appNumber + "=" + appInfo.sourceDir);

                try
                {
                    packageInfo = mPackageManager.getPackageInfo(appInfo.packageName, 0);
                    strDataList.add("VERSION_CODE_" + appNumber + "=" + String.valueOf(packageInfo.versionCode));
                    strDataList.add("VERSION_NAME_" + appNumber + "=" + packageInfo.versionName);
                }
                catch (NameNotFoundException e)
                {
                    strDataList.add("VERSION_CODE_" + appNumber + "=UNKNOWN");
                    strDataList.add("VERSION_NAME_" + appNumber + "=UNKNOWN");
                }

                appNumber++;
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
        strHelpList.add("This function returns the different SW Versions");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_NUMBER_OF_APPS - Returns number of installed applications");
        strHelpList.add("  ");
        strHelpList.add("GET_APP_INFORMATION - Returns installed application information");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Application Info" + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Installed Apps: " + String.valueOf(mApplicationInfo.size()) + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_FAIL);

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
        contentRecord("testresult.txt", "Application Info" + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Installed Apps: " + String.valueOf(mApplicationInfo.size()) + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_PASS);

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

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();

        testResultName.add("NUMBER_OF_APPLICATIONS");
        testResultValues.add(String.valueOf(mApplicationInfo.size()));

        logTestResults(TAG, TEST_PASS, testResultName, testResultValues);
    }
}
