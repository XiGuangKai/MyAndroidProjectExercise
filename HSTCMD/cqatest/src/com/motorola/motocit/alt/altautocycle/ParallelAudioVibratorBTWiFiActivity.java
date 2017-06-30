/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;

import android.content.Intent;

public class ParallelAudioVibratorBTWiFiActivity extends ALTBaseActivity
{
    @Override
    void init()
    {
        this.TAG = "ParallelAudioVibratorBTWiFiActivity";
    }

    @Override
    void start()
    {
        checkProcess();
        TestUtils.dbgLog(TAG, "Start testing Audio Vibrator BT and WLAN", 'i');
        this.startService(new Intent(this, ParallelAudioBTWiFiService.class));
        this.startService(new Intent(this, ParallelVibratorELPannelService.class));
        showToast("Audio/Vibrator/BT/WiFi test");
        TestUtils.dbgLog(TAG, "call finish", 'i');
        this.finish();
    }

    @Override
    void release()
    {}
}
