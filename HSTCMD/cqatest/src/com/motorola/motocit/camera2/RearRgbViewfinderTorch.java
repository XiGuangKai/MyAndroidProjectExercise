/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.camera2;

import android.os.Bundle;

public class RearRgbViewfinderTorch extends Camera2Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "CAMERA2_RearRgbViewfinderTorch";
        super.onCreate(savedInstanceState);
        configureTargetCamera(TARGET_CAMERA_REAR_RGB, LED_STATE_TORCH);
        mHelpMessage = new String[]{TAG,
                "This function will start the rear RGB camera and launch the viewfinder with the LED on in torch mode",
                "TORCH_ON - Turns on the torch",
                "TORCH_OFF - Turns on the torch"};
    }
}
