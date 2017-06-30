/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;

public class AlarmVibratorELPannelReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        context.startService(new Intent(context, ParallelVibratorELPannelService.class));
    }
}
