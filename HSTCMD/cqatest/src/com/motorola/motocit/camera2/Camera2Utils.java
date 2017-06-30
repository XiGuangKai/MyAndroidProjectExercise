/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.camera2;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

public class Camera2Utils {
    public static final String TAG = "cqa_camera2";
    public static final boolean DBG = false;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static Size[] mSizes;
    private static int mSensorOrientation;
    private static boolean mFlashSupported = false;
    private static boolean mStrobeRequest = false;
    private static boolean mIsTorchOn = false;

    public static void getCameraCharacteristics(CameraManager cm, String id) {
        CameraCharacteristics c = null;
        try {
            c = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSizes = map.getOutputSizes(ImageFormat.JPEG);
            mSensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mFlashSupported = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static Size[] getCameraPreviewResolutions() {
        return mSizes;
    }

    public static Size getCameraMaxResoultions() {
        return mSizes[0];
    }

    public static int getCameraOrientation() {
        return mSensorOrientation;
    }

    public static boolean getTakePictureRequest() {
        return mStrobeRequest? true : false;
    }

    public static void setCameraTorchStatus(boolean on) {
        mIsTorchOn = on;
    }

    public static void setCameraStrobeRequest(boolean request) {
        mStrobeRequest = request;
    }

    public static boolean getCameraStrobeRequest() {
        return mStrobeRequest;
    }

    public static void setCamera_AE_Trigger(CaptureRequest.Builder mb) {
        if (mFlashSupported && mStrobeRequest) {
            mb.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        }
    }

    public static void setCameraTorchOn(CaptureRequest.Builder mb){
        if(mIsTorchOn) {
            mb.set(CaptureRequest.FLASH_MODE, CameraCharacteristics.FLASH_MODE_TORCH);
        }
    }

    public static Size getPreviewOptimizedSize(int width, int height) {
        /* todo: select the preview resolution dynamically */
        Size optimizedSize;
        if(width <  height) {
            optimizedSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        } else {
            optimizedSize = new Size(MAX_PREVIEW_HEIGHT, MAX_PREVIEW_WIDTH);
        }
        return optimizedSize;
    }

    public static void clearVariables() {
        mFlashSupported = false;
        mStrobeRequest = false;
        mIsTorchOn = false;
    }
}
