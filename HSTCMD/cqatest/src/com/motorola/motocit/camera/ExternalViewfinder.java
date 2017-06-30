/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class ExternalViewfinder extends Test_Base
{
    private SurfaceView externalViewfinderPreview = null;
    private SurfaceHolder previewHolder = null;
    private Camera externalCamera = null;
    private boolean isPreview = false;
    Camera.Parameters parameters;
    int currentZoomLevel = 0;
    int maxZoomLevel = 0;

    private int numberOfCameras;
    private int defaultCameraId;
    private boolean isSupport = false;
    private int mOrientation;

    private final Lock mCameraLock = new ReentrantLock();

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_External_Viewfinder";
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (1001 == requestCode)
        {
            if (grantResults.length > 0)
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    isPermissionAllowed = true;
                }
                else
                {
                    isPermissionAllowed = false;
                    finish();
                }
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.CAMERA }, 1001);
                return;
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.viewfinder_layout, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
            if (mGestureListener != null)
            {
                thisView.setOnTouchListener(mGestureListener);
            }

            externalViewfinderPreview = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera_viewfinder);
            previewHolder = externalViewfinderPreview.getHolder();
            previewHolder.addCallback(surfaceCallback);
            previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            // Find the total number of cameras available
            numberOfCameras = Camera.getNumberOfCameras();

            // Find the ID of the default camera
            CameraInfo cameraInfo = new CameraInfo();
            for (int i = 0; i < numberOfCameras; i++)
            {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK)
                {
                    defaultCameraId = i;
                    isSupport = true;
                    mOrientation = getDisplayOrientation(cameraInfo);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
                }
            }

            if (!isSupport)
            {
                Toast.makeText(ExternalViewfinder.this, "The device does NOT support External Camera", Toast.LENGTH_SHORT).show();
                ExternalViewfinder.this.finish();
            }

            externalCamera = openWrapper(defaultCameraId);
            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }

    }

    /**
     * The definition for method openLegacy:
     *
     * Camera openLegacy(int cameraId, int halVersion)
     *
     * @param cameraId
     *            The hardware camera to access, between 0 and
     *            #getNumberOfCameras()}-1
     * @param halVersion
     *            The HAL API version this camera device to be opened as
     *
     *            final int CAMERA_HAL_API_VERSION_1_0 = 0x100;
     */

    private Camera openWrapper(int cameraId)
    {
        Class[] paramInt = new Class[2];
        paramInt[0] = Integer.TYPE;
        paramInt[1] = Integer.TYPE;
        Camera camera = null;

        try
        {
            Class cls = Class.forName("android.hardware.Camera");
            Method method = cls.getDeclaredMethod("openLegacy", paramInt);
            camera = (Camera) method.invoke(null, cameraId, 0x100);
            dbgLog(TAG, "Opening legacy camera", 'i');
        }
        catch (ClassNotFoundException e)
        {}
        catch (NoSuchMethodException e)
        {}
        catch (IllegalAccessException e)
        {}
        catch (InvocationTargetException e)
        {}

        if (camera == null)
        {
            dbgLog(TAG, "Error opening camera by wrapper, using camera open instead", 'e');
            camera = Camera.open(cameraId);
        }

        return camera;
    }

    private void closeCamera()
    {
        dbgLog(TAG, "close Camera", 'v');
        if (externalCamera == null)
        {
            dbgLog(TAG, "already stopped.", 'v');
            return;
        }

        if (isPreview)
        {
            externalCamera.stopPreview();
            isPreview = false;
        }

        externalCamera.release();
        externalCamera = null;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            if (isPreview)
            {
                externalCamera.stopPreview();
            }

            externalCamera.release();
            externalCamera = null;
            isPreview = false;
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        try
        {
            dbgLog(TAG, "onStop called, closing camera", 'i');
            closeCamera();
        }
        catch (Throwable ex)
        {
            throw new RuntimeException("close camera failed onStop", ex);
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

    }

    public int getDisplayOrientation(CameraInfo cameraInfo)
    {
        Display mDisplay = this.getWindowManager().getDefaultDisplay();
        int mRotation = mDisplay.getRotation();
        int mResult;
        int mDegrees = 0;

        switch (mRotation)
        {
            case Surface.ROTATION_0:
                mDegrees = 0;
                break;
            case Surface.ROTATION_90:
                mDegrees = 90;
                break;
            case Surface.ROTATION_180:
                mDegrees = 180;
                break;
            case Surface.ROTATION_270:
                mDegrees = 270;
                break;
        }

        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
        {
            mResult = (cameraInfo.orientation + mDegrees) % 360;
            // compensate the mirror
            mResult = (360 - mResult) % 360;
        }
        else
        {
            // back-facing
            mResult = ((cameraInfo.orientation - mDegrees) + 360) % 360;
        }

        dbgLog(TAG, "mDegrees:" + mDegrees + " mResult:" + mResult + " orientation:" + cameraInfo.orientation, 'd');
        return mResult;
    }

    private void setZoomFunction()
    {
        ZoomControls zoomControls = (ZoomControls) findViewById(com.motorola.motocit.R.id.camera_zoom_controls);
        dbgLog(TAG, "isSmoothZoomSupported=" + parameters.isSmoothZoomSupported(), 'i');

        if (parameters.isSmoothZoomSupported())
        {
            // most phones
            maxZoomLevel = parameters.getMaxZoom();

            zoomControls.setIsZoomInEnabled(true);
            zoomControls.setIsZoomOutEnabled(true);
            zoomControls.setLongClickable(true);
            zoomControls.setZoomSpeed(10);
            zoomControls.setOnZoomInClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (currentZoomLevel < maxZoomLevel)
                    {
                        currentZoomLevel++;
                        externalCamera.startSmoothZoom(currentZoomLevel);

                    }
                }
            });

            zoomControls.setOnZoomOutClickListener(new OnClickListener() {
                @Override
                public void onClick(View v)
                {
                    if (currentZoomLevel > 0)
                    {
                        currentZoomLevel--;
                        externalCamera.startSmoothZoom(currentZoomLevel);
                    }
                }
            });
        }
        else
        {
            // no zoom on phone
            zoomControls.setVisibility(View.GONE);
            Toast.makeText(ExternalViewfinder.this, "smooth zoom not supported", Toast.LENGTH_LONG).show();
        }
    }

    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters)
    {
        Camera.Size result = null;

        // keep klocwork happy
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if (sizes == null)
        {
            return (result);
        }

        for (Camera.Size size : sizes)
        {
            if ((size.width <= width) && (size.height <= height))
            {
                if (result == null)
                {
                    result = size;
                }
                else
                {
                    int resultDelta = ((width - result.width) + height) - result.height;
                    int newDelta = ((width - size.width) + height) - size.height;

                    if (newDelta < resultDelta)
                    {
                        result = size;
                    }
                }
            }
        }

        return (result);
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            try
            {
                externalCamera.setPreviewDisplay(previewHolder);
            }
            catch (Throwable t)
            {
                dbgLog("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t, 'e');
                Toast.makeText(ExternalViewfinder.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            try
            {
                mCameraLock.lock();

                if (externalCamera != null)
                {
                    parameters = externalCamera.getParameters();
                    setZoomFunction();
                    Camera.Size size = getBestPreviewSize(width, height, parameters);

                    if (size != null)
                    {
                        parameters.setPreviewSize(size.width, size.height);
                        parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
                        // set continuous auto focus
                        parameters.set("focus-mode", "continuous-picture");
                        externalCamera.setParameters(parameters);
                        externalCamera.startPreview();
                        isPreview = true;
                    }
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception: " + e.toString(), 'e');
            }
            finally
            {
                mCameraLock.unlock();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {
            // no-op
        }
    };

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS"))
        {

        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Camera - External Viewfinder:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Camera - External Viewfinder:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add("Camera_External_Viewfinder");
        strHelpList.add("");
        strHelpList.add("This function will start the external camera and launch the viewfinder");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Camera - External Viewfinder:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Camera - External Viewfinder:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeUp()
    {
        return true;
    }

    @Override
    public boolean onSwipeDown()
    {
        if (modeCheck("Seq"))
        {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

            return false;
        }
        else
        {
            systemExitWrapper(0);
        }
        return true;
    }
}
