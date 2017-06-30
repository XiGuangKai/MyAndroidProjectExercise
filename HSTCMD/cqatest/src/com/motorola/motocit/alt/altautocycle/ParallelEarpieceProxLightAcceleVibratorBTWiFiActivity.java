/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;

import android.content.Intent;

public class ParallelEarpieceProxLightAcceleVibratorBTWiFiActivity extends
        ALTBaseActivity
{
    @Override
    void init()
    {
        this.TAG = "ParallelEarpieceProxLightAcceleVibratorBTWiFiActivity";
    }

    @Override
    void start()
    {
        checkProcess();
        TestUtils.dbgLog(TAG, "Starting Earpiece, Prox, Light, Accel, Vibrator, BT and WLAN test", 'i');
        this.startService(new Intent(this, ParallelEarpieceProxLightAcceleBTWiFiService.class));
        this.startService(new Intent(this, ParallelVibratorELPannelService.class));
        showToast("Earpiece/Prox/Light/Accele/Vibrator/BT/WiFi test");
        TestUtils.dbgLog(TAG, "call finish", 'i');
        this.finish();
    }

    @Override
    void release()
    {}
}
