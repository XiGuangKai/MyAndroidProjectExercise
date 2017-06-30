/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.android.internal.telephony.TelephonyIntents;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.os.Build;

public class SecretCodeBroadcastReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (TelephonyIntents.SECRET_CODE_ACTION.equals(intent.getAction()))
        {
            if ("eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE))
            {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClass(context, AltMainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }
}