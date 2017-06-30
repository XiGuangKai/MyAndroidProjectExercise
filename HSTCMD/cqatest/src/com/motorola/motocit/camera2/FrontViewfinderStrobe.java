/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.camera2;

import android.os.Bundle;

public class FrontViewfinderStrobe extends Camera2Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "CAMERA2_FrontViewfinderStrobe";
        super.onCreate(savedInstanceState);
        configureTargetCamera(TARGET_CAMERA_FRONT, LED_STATE_FLASH);
        mHelpMessage = new String[]{TAG,
                "This function will start the internal camera and launch the viewfinder",
                "STROBE_FIRE - Fire Camera Strobe LED.",
                " ",
                "STROBE_FIRE - Fire Camera Strobe LED."};
    }

}
