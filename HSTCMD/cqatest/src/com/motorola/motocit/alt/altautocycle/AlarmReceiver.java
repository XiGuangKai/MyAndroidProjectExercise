/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

public class AlarmReceiver extends ALTBaseReceiver
{

    @Override
    public void onReceive(Context context, Intent intent)
    {
        getLogFilePath(context);
        boolean flag = false;
        try
        {
            SharedPreferences settings = context.getSharedPreferences("altautocycle", Context.MODE_MULTI_PROCESS);
            if (settings != null)
            {
                flag = settings.getBoolean("wait_start", false);
                boolean autoStart = settings.getBoolean("start_flag", false);
                TestUtils.dbgLog("CQATest", "in AlarmReceiver, startFlag=" + autoStart, 'i');
            }
        }
        catch (Exception e)
        {}
        Intent service = null;
        if (flag)
        {
            TestUtils.dbgLog("CQATest", "wait_start true, reset alarm service", 'i');
            service = new Intent(context, com.motorola.motocit.alt.altautocycle.ResetAlarmService.class);
        }
        else
        {
            TestUtils.dbgLog("CQATest", "wait_start false, start function test service", 'i');
            service = new Intent(context, com.motorola.motocit.alt.altautocycle.FunctionTestService.class);
        }
        context.startService(service);
    }
}
