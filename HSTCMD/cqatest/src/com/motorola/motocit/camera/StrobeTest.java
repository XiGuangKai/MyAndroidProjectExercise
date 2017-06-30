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
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class StrobeTest extends Test_Base
{
    private SurfaceView preview = null;
    private SurfaceHolder previewHolder = null;
    private boolean inPreview = false;
    private boolean strobeFired = false;
    // the flag to make sure do strobe one time when touching screen
    private boolean isReadyToStrobe = true;
    private Camera camera = null;
    boolean activityStartedFromCommServer = false;

    private int numberOfCameras;
    private int defaultCameraId;
    private boolean isSupport = false;
    private int mOrientation;
    private final Lock mCameraLock = new ReentrantLock();

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_Strobe";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.strobe, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        dbgLog(TAG, "onConfigurationChanged called", 'i');

        // ignore orientation/keyboard change
        super.onConfigurationChanged(newConfig);

        // onConfigurationChanged can be called after onResume sometimes
        if (isExpectedOrientation())
        {
            sendStartActivityPassed();
        }
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
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            // setup preview area on layout
            preview = (SurfaceView) findViewById(com.motorola.motocit.R.id.strobe_viewfinder);
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
                Toast.makeText(StrobeTest.this, "The device does NOT support External Camera", Toast.LENGTH_SHORT).show();
                StrobeTest.this.finish();
            }

            strobeFired = false;

            // Check if commServer started this activity
            // if so then don't automatically fire strobe
            if (wasActivityStartedByCommServer())
            {
                activityStartedFromCommServer = true;
                dbgLog(TAG, "activity originated from commserver", 'd');
                // Clear msg since tap to fire is disabled if started from
                // commServer
                TextView StrobeMsgText = (TextView) findViewById(com.motorola.motocit.R.id.strobe_tap_screen_text);
                StrobeMsgText.setVisibility(View.INVISIBLE);
            }
            else
            {
                activityStartedFromCommServer = false;
                dbgLog(TAG, "activity originated UI", 'd');
            }

            camera = openWrapper(defaultCameraId);
            if (camera != null)
            {
                preview.setVisibility(View.INVISIBLE);
                preview.setVisibility(View.VISIBLE);

                Camera.Parameters parameters = camera.getParameters();
                parameters.setJpegQuality(100);
                parameters.set("flash-mode", "on");
                dbgLog(TAG, "setting camera parameters", 'i');
                camera.setParameters(parameters);

                if (isExpectedOrientation())
                {
                    sendStartActivityPassed();
                }

                camera.startPreview();
            }
            else
            {
                sendStartActivityFailed("Camera Failed to Open");
            }
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
        if (camera == null)
        {
            dbgLog(TAG, "already stopped.", 'v');
            return;
        }

        if (inPreview)
        {
            camera.stopPreview();
            inPreview = false;
        }

        camera.release();
        camera = null;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            if (camera != null)
            {
                // turn off preview if it was started
                if (inPreview)
                {
                    camera.stopPreview();
                }

                camera.release();
                camera = null;
                inPreview = false;
            }
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

    protected void strobeLED()
    {
        strobeFired = false;
        isReadyToStrobe = false;

        try
        {
            Thread.sleep(600, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        try
        {
            mCameraLock.lock();
            camera.autoFocus(autoFocusCallback);
        }
        finally
        {
            mCameraLock.unlock();
        }
    }

    AutoFocusCallback autoFocusCallback = new AutoFocusCallback()
    {
        @Override
        public void onAutoFocus(boolean arg0, Camera arg1)
        {

            try
            {
                mCameraLock.lock();
                camera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
            finally
            {
                mCameraLock.unlock();
            }
        }
    };

    ShutterCallback shutterCallback = new ShutterCallback()
    {
        @Override
        public void onShutter()
        {
            dbgLog(TAG, "onShutter'd", 'd');
        }
    };

    /** Handles data for raw picture */
    PictureCallback rawCallback = new PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            dbgLog(TAG, "onPictureTaken - raw", 'd');
            strobeFired = true;
            isReadyToStrobe = false;
        }
    };

    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            dbgLog(TAG, "onJpegTaken - raw", 'd');
            strobeFired = true;
            isReadyToStrobe = true;

            camera.stopPreview();
            camera.startPreview();
        }
    };

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h)
    {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;

        if (sizes == null)
            return null;

        Size optimalSize = null;

        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Find size
        for (Size size : sizes)
        {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
            {
                continue;
            }
            if (Math.abs(size.height - targetHeight) < minDiff)
            {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null)
        {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes)
            {
                if (Math.abs(size.height - targetHeight) < minDiff)
                {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            try
            {
                camera.setPreviewDisplay(previewHolder);
            }
            catch (Throwable t)
            {
                dbgLog("surfaceCallback", "Exception in setPreviewDisplay()", t, 'e');
                Toast.makeText(StrobeTest.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            try
            {
                mCameraLock.lock();
                if (camera != null)
                {
                    Camera.Parameters parameters = camera.getParameters();

                    List<Size> sizes = parameters.getSupportedPreviewSizes();
                    Size optimalSize = getOptimalPreviewSize(sizes, width, height);

                    double ratio = (double) width / height;
                    dbgLog(TAG, "surfaceChanged -> surface width:" + width + " height:" + height + " w/h_surface:" + ratio, 'i');
                    if (optimalSize != null)
                    {
                        dbgLog(TAG, "startPreview", 'v');
                        double ratio_preview = (double) optimalSize.width / optimalSize.height;
                        dbgLog(TAG, "optimalSize: width:" + optimalSize.width + "  " + "height:" + optimalSize.height + " ratio_preview:" + ratio_preview, 'd');
                        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                        camera.setParameters(parameters);
                        camera.startPreview();
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
    // if screen is touched, take another pic
    public boolean onTouchEvent(MotionEvent event)
    {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP)
        {
            dbgLog(TAG, "onTouchEvent, strobe LED", 'i');
            // Do not allow touch events to trigger if started from commServer

            if (isReadyToStrobe && !activityStartedFromCommServer)
            {
                if (camera != null)
                {
                    strobeLED();
                }
            }

        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("STROBE_FIRE"))
        {
            strobeLED();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        if (strRxCmd.equalsIgnoreCase("GET_STROBE_FIRE"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("STROBE_FIRED=" + strobeFired));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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

        strHelpList.add("StrobeTest");
        strHelpList.add("");
        strHelpList.add("This function will start the camera and fire strobe");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("STROBE_FIRE - Fire Camera Strobe LED.");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Camera - StrobeTest:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - StrobeTest:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - StrobeTest:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - StrobeTest:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
