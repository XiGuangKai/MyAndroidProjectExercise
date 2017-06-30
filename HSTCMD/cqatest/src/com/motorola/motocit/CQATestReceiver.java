/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;



public class CQATestReceiver extends BroadcastReceiver
{

    public CQATestReceiver()
    {
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        PackageManager pm = context.getPackageManager();
        ComponentName comp = new ComponentName(context, AppMainActivity.class);

        // make klocwork happy
        String action = intent.getAction();
        if (null == action)
        {
            return;
        }

        // Specifically check for "android.provider.Telephony.SECRET_CODE" due
        // to location change in Android 4.2
        if ((intent != null) && (action.equals("android.provider.Telephony.SECRET_CODE")))
        {
            if ((pm != null) && (comp != null))
            {
                pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }

            // Setup CQA Settings
            TestUtils.setCQASettingsFromConfig();

            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(context, AppMainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

            // Start CommServer without reboot device on ODM products. For
            // Service usage.
            if (TestUtils.isOdmDevice() && TestUtils.getAutoStartCommServer().toUpperCase(Locale.US).contains("YES"))
            {
                if (TestUtils.isCommServerRunning(context) == false)
                {
                    TestUtils.dbgLog("CQATest:CQATestReceiver", "Starting CommServer manually", 'i');
                    Intent startCommServer = new Intent();
                    startCommServer.setClass(context, CommServer.class);
                    context.startService(startCommServer);
                }
            }
        }
    }
}
