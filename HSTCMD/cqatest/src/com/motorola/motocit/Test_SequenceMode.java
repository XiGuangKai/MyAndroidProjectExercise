/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.motorola.motocit.mmc.MMC;

public class Test_SequenceMode extends Test_Base
{
    private static List<ResolveInfo> ResolveInfoList = new ArrayList<ResolveInfo>();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Test_Sequence_Mode";
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String path = intent.getStringExtra("com.motorola.motocit.Path");

        if (path == null)
        {
            path = "";
            this.setTitle("CQATest version: ");
        }
        else
        {
            this.setTitle("CQA Test/" + path);
        }


        // set flag to false
        isCQASeqResolveInfoListCleared = false;

        // If Odm device, check whether external sdcard is available for test
        // result copy at the end of test
        if (TestUtils.isOdmDevice())
        {
            MMC sdcard = new MMC();
            String sdcardPath = sdcard.getExternalStorageMountPath();

            if (sdcardPath == null)
            {
                Toast.makeText(this, "EXTERNAL SD CARD IS NOT SUPPORTED!", Toast.LENGTH_SHORT).show();

            }
            else if (searchSDcard(sdcardPath) == false)
            {
                Toast.makeText(this, "EXTERNAL SD CARD IS NOT MOUNTED!", Toast.LENGTH_SHORT).show();
            }
            else
            {
                dbgLog(TAG, "External sdcard is mounted at " + sdcardPath, 'd');
            }
        }

        TestUtils.setSequenceFileInUse(TestUtils.getfactoryOrAltConfigFileInUse());

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
        if (ResolveInfoList.isEmpty() && isCQASeqResolveInfoListCleared)
        {
            getSeqModeList();
        }

        // Sequence mode is implemented by having the sequenceMode
        // activity launch an activity and which should cause the
        // sequenceMode activity to enter the paused state. When
        // the launched activity calls finish() the sequenceMode
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
            isCQASeqResolveInfoListCleared = true;
        }
    }

    protected void getSeqModeList()
    {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        PackageManager pm = getPackageManager();

        // get test result activity at first then put it in the list.
        mainIntent.addCategory("com.motorola.motocit.SequentialMode.ITEM");
        List<ResolveInfo> tempTestResultList = pm.queryIntentActivities(mainIntent, 0);

        if (tempTestResultList.size() == 0)
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

        ResolveInfo test_result = null;

        for (ResolveInfo info : tempTestResultList)
        {
            if (!TestUtils.activityFilter(info.activityInfo.name))
            {
                if (info.activityInfo.name.contains("Test_Result"))
                {
                    // find test result activity, exit loop
                    dbgLog(TAG, "Get test result activity!", 'i');
                    test_result = info;
                    break;
                }
            }
        }

        if (test_result != null)
        {
            // add test result activity, make sure it's the last activity in
            // sequential mode
            dbgLog(TAG, "ResolveInfoList Add " + test_result.activityInfo.name, 'i');
            ResolveInfoList.add(test_result);
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
            for (ResolveInfo info : optionalList)
            {
                // Optional activity name must be configed in the config file as
                // below format:
                // "optional:<activity name>"
                // e.g optional:com.motorola.motocit.cosmetic.CosmeticTest
                if (TestUtils.activityFilter("optional:" + info.activityInfo.name))
                {
                    if (info.activityInfo.name.toLowerCase().contains("fingerprint"))
                    {
                        // Affinity 16MP hw has Fingerprint but 13MP does not
                        // so still need to check it even it's the same product
                        // config
                        if (TestUtils.hasFingerprintSensor())
                        {
                            ResolveInfoList.add(info);
                            dbgLog(TAG, "ResolveInfoList Add optional activity " + info.activityInfo.name, 'i');
                        }
                    }
                    else
                    {
                        ResolveInfoList.add(info);
                        dbgLog(TAG, "ResolveInfoList Add optional activity " + info.activityInfo.name, 'i');
                    }
                }
            }
        }

        mainIntent.removeCategory("com.motorola.motocit.SequentialMode.Optional");
        mainIntent.addCategory("com.motorola.motocit.SequentialMode.ITEM");

        List<ResolveInfo> tempList = pm.queryIntentActivities(mainIntent, 0);

        // filter list by activityFilter()
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

        for (ResolveInfo info : tempList)
        {
            if (!TestUtils.activityFilter(info.activityInfo.name))
            {
                if (info.activityInfo.name.contains("Test_Result"))
                {
                    continue;
                }
                else
                {
                    dbgLog(TAG, "ResolveInfoList Add " + info.activityInfo.name, 'i');

                    ResolveInfoList.add(info);
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

        strHelpList.add("Test Sequence Mode");
        strHelpList.add("");
        strHelpList.add("This activity brings up the CQA Test Sequence Mode");
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
