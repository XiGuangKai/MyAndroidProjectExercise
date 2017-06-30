/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera2;

import android.os.Bundle;

public class RearMonoVideoCapture extends VideoCaptureBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fragment = CameraActivityFragment.newInstance(CameraActivityFragment.TARGET_CAMERA_REAR_MONO);
        TAG = "Camera2_RearMonoVideoCapture";
        super.onCreate(savedInstanceState);
        mHelpMessage = new String[]{TAG,
                "This function will start rear mono camera video capture"};
    }
}