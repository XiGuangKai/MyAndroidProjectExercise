/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera2;

import android.os.Bundle;

public class RearRgbVideoCapture extends VideoCaptureBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fragment = CameraActivityFragment.newInstance(CameraActivityFragment.TARGET_CAMERA_REAR_RGB);
        TAG = "Camera2_RearRgbVideoCapture";
        super.onCreate(savedInstanceState);
        mHelpMessage = new String[]{TAG,
                "This function will start rear RGB camera video capture"};
    }
}