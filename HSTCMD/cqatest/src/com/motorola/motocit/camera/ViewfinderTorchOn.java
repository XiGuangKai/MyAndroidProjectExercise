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
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class ViewfinderTorchOn extends Test_Base
{

    private SurfaceView preview = null;
    private SurfaceHolder previewHolder = null;
    private Camera camera = null;
    private boolean inPreview = false;
    private boolean torchOn = true;

    private int numberOfCameras;
    private int defaultCameraId;
    private boolean isSupport = false;
    private int mOrientation;

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_ViewfinderTorchOn";
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

        if (!isPermissionAllowed)
        {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
        else
        {
            startCamera();
        }
    }

    private void startCamera()
    {
        dbgLog(TAG, "starting camera", 'i');

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.viewfinder_camera_torch, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        preview = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera_viewfinder);
        previewHolder = preview.getHolder();
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
            Toast.makeText(ViewfinderTorchOn.this, "The device does NOT support External Camera", Toast.LENGTH_SHORT).show();
            ViewfinderTorchOn.this.finish();
        }

        camera = openWrapper(defaultCameraId);

        if (camera != null)
        {
            preview.setVisibility(View.INVISIBLE);
            preview.setVisibility(View.VISIBLE);

            if (isExpectedOrientation())
            {
                sendStartActivityPassed();
            }

            // CR - IKMMINTG-11011
            Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);
            camera.startPreview();

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("Camera Failed to Open");
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

    @Override
    public void onPause()
    {
        super.onPause();
        if (isPermissionAllowed)
        {
            if (inPreview)
            {
                if (camera != null)
                {
                    camera.stopPreview();
                }
            }

            if (camera != null)
            {
                camera.release();
                camera = null;
            }
            inPreview = false;
        }
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

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h)
    {
        double targetRatio = (double) w / h;

        if (sizes == null)
            return null;

        Size optimalSize = null;

        Size tempSize = sizes.get(0); // get the first preview size

        double tempRatio = (double) tempSize.width / tempSize.height;

        double minDiff = Math.abs(tempRatio - targetRatio);

        optimalSize = tempSize;

        for (Size size : sizes)
        {
            double minDiffTemp = Math.abs((double) size.width / size.height - targetRatio);
            dbgLog(TAG, "minDiff=" + minDiff, 'i');
            dbgLog(TAG, "minDiffTemp=" + minDiffTemp, 'i');
            if (minDiffTemp < minDiff)
            {
                minDiff = minDiffTemp;
                optimalSize = size;
                dbgLog(TAG, "optimalSize width=" + optimalSize.width + " height=" + optimalSize.height + " ration=" + (double) optimalSize.width / optimalSize.height, 'i');
            }
        }

        double ratio = (double) optimalSize.width / optimalSize.height;
        optimalSize.width = (int) (ratio * optimalSize.height);

        return optimalSize;
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            try
            {
                if (camera != null)
                {
                    camera.setPreviewDisplay(previewHolder);
                }
            }
            catch (Throwable t)
            {
                dbgLog("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t, 'e');
                Toast.makeText(ViewfinderTorchOn.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            if (camera != null)
            {
                Camera.Parameters parameters = camera.getParameters();
                if (parameters != null)
                {
                    double ratio = (double) width / height;
                    dbgLog(TAG, "surfaceChanged -> surface width:" + width + " height:" + height + " w/h_surface:" + ratio, 'i');
                    List<Size> sizes = parameters.getSupportedPreviewSizes();
                    Size optimalSize = getOptimalPreviewSize(sizes, width, height);

                    if (optimalSize != null)
                    {
                        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                        double ratio_preview = (double) optimalSize.width / optimalSize.height;
                        dbgLog(TAG, "setPreviewSize width=" + optimalSize.width + " height=" + optimalSize.height + " ratio_preview:" + ratio_preview, 'i');
                        camera.setParameters(parameters);
                        if (!setupFlashMode())
                        {
                            dbgLog(TAG, "Not support torch on device", 'e');
                            Toast.makeText(ViewfinderTorchOn.this, "Not support torch on device", Toast.LENGTH_LONG).show();
                            ViewfinderTorchOn.this.finish();
                        }
                    }
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {
            // no-op
        }
    };

    protected boolean setupFlashMode()
    {
        boolean result = false;
        Camera.Parameters parameters = camera.getParameters();

        // Get current state of flash
        String FlashMode = parameters.getFlashMode();

        // FlashMode will be null if flash is not supported
        if (FlashMode != null)
        {
            result = true;
            if (torchOn)
            {
                // only set FLASH_MODE_TORCH if it is set to something different
                if (!FlashMode.equals(Parameters.FLASH_MODE_TORCH))
                {
                    parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                }
            }
            // only set FLASH_MODE_OFF if it is set to something different
            else if (!FlashMode.equals(Parameters.FLASH_MODE_OFF))
            {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            }

            // set continuous auto focus
            parameters.set("focus-mode", "continuous-picture");
            camera.setParameters(parameters);
            camera.startPreview();
            inPreview = true;
        }

        return result;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        boolean result = false;

        if (strRxCmd.equalsIgnoreCase("TORCH_ON"))
        {
            torchOn = true;
            result = setupFlashMode();

            if (result)
            {
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s': Is Flash Supported?", TAG));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("TORCH_OFF"))
        {
            torchOn = false;
            result = setupFlashMode();

            if (result)
            {
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s': Is Flash Supported?", TAG));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
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
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add("Camera_ViewfinderTorchOn");
        strHelpList.add("");
        strHelpList.add("This function will start the camera and launch the viewfinder with the LED on in torch mode");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("TORCH_ON - Turns on the torch");
        strHelpList.add("TORCH_OFF - Turns off the torch");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Camera - Viewfinder Torch ON:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - Viewfinder Torch ON:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Viewfinder Torch ON:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Viewfinder Torch ON:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

}
