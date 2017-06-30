/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

public class Test_ModelAssemblyMode extends Test_Base
{
    private static List<ResolveInfo> ResolveInfoList = new ArrayList<ResolveInfo>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Model_Assembly_Mode";
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String path = intent.getStringExtra("com.motorola.motocit.Path");
        File FileToCheck = null;

        if (path == null)
        {
            path = "";
            this.setTitle("CQATest version: ");
        }
        else
        {
            this.setTitle("CQA Test/" + path);
        }

        isCQAPatResolveInfoListCleared = false;

        // Delete test results file if it exists when the Model Assembly Button
        // is pushed
        String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        FileToCheck = new File(sdcardPath + "/testresult.txt");
        if (FileToCheck.exists())
        {
            FileToCheck.delete();
        }

        //Set Sequence File in use
        String filePassedIn = "cqatest_cfg_model_assembly";
        TestUtils.setSequenceFileInUse(filePassedIn);
        testResultInit();
        // Setup Display Settings
        TestUtils.setCQASettingsFromConfig();

        // If list is empty assuming that use wants to start a new sequence
        // session.
        if (ResolveInfoList.isEmpty())
        {
            getSeqModeList();
        }

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // The activity has become visible (it is now "resumed").
        dbgLog(TAG, "onResume called", 'i');

        // In case the test was stopped by accident. Restart the new test
        if (ResolveInfoList.isEmpty() && isCQAPatResolveInfoListCleared)
        {
            getSeqModeList();
        }

        // Sequence mode is implemented by having the modelAssemblyMode
        // activity launch an activity and which should cause the
        // modelAssemblyMode activity to enter the paused state. When
        // the launched activity calls finish() the modelAssemblyMode
        // activity should be resumed and the OnResume() called which
        // will then launch the next activity.
        if (ResolveInfoList.size() > 0)
        {
            ResolveInfo info = ResolveInfoList.remove(0);
            dbgLog(TAG, "onResume started " + info.activityInfo.name, 'i');
            startActivity(activityIntent(info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
        }
        else
        {
            dbgLog(TAG, "onResume called and ResolveInfoList is empty.. calling finish()", 'i');
            finish();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        dbgLog(TAG, "onDestroy", 'd');

        if (ResolveInfoList.isEmpty() == false)
        {
            ResolveInfoList.clear();
            isCQAPatResolveInfoListCleared = true;
        }
    }

    protected void getSeqModeList()
    {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory("com.motorola.motocit.SequentialMode.ITEM");

        PackageManager pm = getPackageManager();
        List<ResolveInfo> tempList = pm.queryIntentActivities(mainIntent, 0);

        if (tempList.size() == 0)
        {
            dbgLog(TAG, "No SequentialMode items found", 'e');

            // generate toast
            Context context = getApplicationContext();
            CharSequence text = "No SequentialMode items found!";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            finish();
        }

        File file_Model_Assembaly_cfg = new File("/system/etc/motorola/12m/" + TestUtils.getSequenceFileInUse());
        File file_Model_Assembaly_cfg_sdcard = new File("/mnt/sdcard/CQATest/" + TestUtils.getSequenceFileInUse());

        if (file_Model_Assembaly_cfg.exists())
        {
            for (ResolveInfo info : tempList)
            {
                if (!TestUtils.searchStringInFile(file_Model_Assembaly_cfg, info.activityInfo.name))
                {
                    dbgLog(TAG, "ResolveInfoList Add " + info.activityInfo.name, 'i');

                    ResolveInfoList.add(info);
                }
            }
        }
        else if (file_Model_Assembaly_cfg_sdcard.exists())
        {
            for (ResolveInfo info : tempList)
            {
                if (!TestUtils.searchStringInFile(file_Model_Assembaly_cfg_sdcard, info.activityInfo.name))
                {
                    dbgLog(TAG, "ResolveInfoList Add " + info.activityInfo.name, 'i');

                    ResolveInfoList.add(info);
                }
            }
        }

        // Get optional sequential test activities
        mainIntent.removeCategory("com.motorola.motocit.SequentialMode.ITEM");
        mainIntent.addCategory("com.motorola.motocit.SequentialMode.Optional");

        List<ResolveInfo> optionalList = pm.queryIntentActivities(mainIntent, 0);

        if (optionalList.size() == 0)
        {
            dbgLog(TAG, "No optional sequential test items found", 'i');
        }
        else
        {
            if (file_Model_Assembaly_cfg.exists())
            {
                for (ResolveInfo info : optionalList)
                {
                    if (TestUtils.searchStringInFile(file_Model_Assembaly_cfg, "optional:" + info.activityInfo.name))
                    {
                        dbgLog(TAG, "ResolveInfoList Add " + info.activityInfo.name, 'i');

                        ResolveInfoList.add(info);
                    }
                }
            }
            else if (file_Model_Assembaly_cfg_sdcard.exists())
            {
                for (ResolveInfo info : optionalList)
                {
                    if (TestUtils.searchStringInFile(file_Model_Assembaly_cfg_sdcard, "optional:" + info.activityInfo.name))
                    {
                        dbgLog(TAG, "ResolveInfoList Add " + info.activityInfo.name, 'i');

                        ResolveInfoList.add(info);
                    }
                }
            }
        }

        if (ResolveInfoList.size() == 0)
        {
            dbgLog(TAG, "No SequentialMode items found", 'e');

            // generate toast
            Context context = getApplicationContext();
            CharSequence text = "No SequentialMode items found!";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            finish();
        }

        // reverse the list to maintain original implementation order
        Collections.reverse(ResolveInfoList);

        return;
    }

    protected Intent activityIntent(String pkg, String componentName)
    {
        Intent result = new Intent();
        result.setClassName(pkg, componentName);
        return result;
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS"))
        {

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

        strHelpList.add("Test Model Assembly Mode");
        strHelpList.add("");
        strHelpList.add("This activity brings up the CQA Test Model Assembly Mode");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return false;
    }

    @Override
    public boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    public boolean onSwipeUp()
    {
        return false;
    }

    @Override
    public boolean onSwipeDown()
    {
        return false;
    }
}
