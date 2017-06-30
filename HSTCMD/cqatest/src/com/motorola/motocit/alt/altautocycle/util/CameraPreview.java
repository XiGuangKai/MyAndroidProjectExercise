/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements
        SurfaceHolder.Callback
{
    private SurfaceHolder mHolder;
    private Camera mCamera;
    public boolean pictureFlag;
    public int backCameraId;
    public int frontCameraId;
    public int defaultCameraId;
    private int numberOfCameras;
    private boolean mTorchOn;

    public CameraPreview(Context context){
        super(context);

        mTorchOn = false;

        numberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++)
        {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK)
                backCameraId = i;
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
                frontCameraId = i;
        }
        defaultCameraId = backCameraId;
        try
        {
            this.pictureFlag = false;
            this.mHolder = this.getHolder();
            this.mHolder.addCallback(this);
            this.mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        catch (Exception e)
        {}
    }

    public void release()
    {
        if (this.mCamera != null)
        {
            try
            {
                this.pictureFlag = false;
                this.mCamera.stopPreview();
                this.mCamera.release();
            }
            catch (Exception e)
            {}
        }
    }

    public void switchCamera(int CameraId)
    {
        if (mCamera != null)
        {
            pictureFlag = false;
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        defaultCameraId = CameraId;
        mCamera = Camera.open(CameraId);
        setCamera();
    }

    public void setCamera()
    {
        if (mCamera != null)
        {
            Camera.Parameters parameters = this.mCamera.getParameters();
            parameters.setPictureFormat(PixelFormat.JPEG);
            String flashMode = parameters.getFlashMode();
            if (flashMode != null)
            {
                if (defaultCameraId == backCameraId)
                {
                    if (mTorchOn)
                    {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                        mTorchOn = false;
                    }
                    else
                    {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mTorchOn = true;
                    }
                }
                else if (defaultCameraId == frontCameraId)
                {
                    if (!flashMode.equals(Camera.Parameters.FLASH_MODE_TORCH))
                    {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    }
                }
            }
            this.mCamera.setParameters(parameters);
            this.mCamera.startPreview();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            if (this.mCamera != null)
                this.mCamera.setPreviewDisplay(holder);
        }
        catch (Exception e)
        {}
    }

    public void takePicture()
    {
        if (this.mCamera != null)
        {
            try
            {
                this.pictureFlag = true;
                this.mCamera.takePicture(null, null, null);
            }
            catch (Exception e)
            {}
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height)
    {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {};
}
