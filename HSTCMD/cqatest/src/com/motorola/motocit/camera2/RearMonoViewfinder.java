/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.camera2;

import android.os.Bundle;

public class RearMonoViewfinder extends Camera2Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "CAMERA2_RearMonoViewfinder";
        super.onCreate(savedInstanceState);
        configureTargetCamera(TARGET_CAMERA_REAR_MONO, LED_STATE_NONE);
        mHelpMessage = new String[]{TAG,
                "This function will start the Rear Mono camera and launch the viewfinder"};
    }
}

