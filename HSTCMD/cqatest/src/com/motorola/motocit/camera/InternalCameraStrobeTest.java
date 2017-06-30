/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class InternalCameraStrobeTest extends Test_Base
{

    boolean isSupport = false;
    private InternalPreview internalViewfinderPreview;
    private Camera internalCamera = null;
    int numberOfCameras;
    int cameraCurrentlyLocked;
    // The first rear facing camera
    int defaultCameraId;
    int mOrientation;
    private boolean strobeFired = false;
    private final Lock mCameraLock = new ReentrantLock();
    boolean activityStartedFromCommServer = false;
    // the flag to make sure do strobe one time when touching screen
    private boolean isReadyToStrobe = true;

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_InternalCameraStrobeTest";
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow(); // keep klocwork happy
        if (null != window)
        {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    protected void strobeLED()
    {
        strobeFired = false;
        isReadyToStrobe = false;
        internalCamera.stopPreview();
        internalCamera.startPreview();

        Camera.Parameters parameters = internalCamera.getParameters();
        parameters.set("flash-mode", "on");
        dbgLog(TAG, "setting front camera flash mode on", 'i');
        internalCamera.setParameters(parameters);

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
            internalCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
        finally
        {
            mCameraLock.unlock();
        }
    }

    ShutterCallback shutterCallback = new ShutterCallback() {
        @Override
        public void onShutter()
        {
            dbgLog(TAG, "onShutter'd", 'd');
        }
    };

    /** Handles data for raw picture */
    PictureCallback rawCallback = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            dbgLog(TAG, "onPictureTaken - raw", 'd');
            strobeFired = true;
            isReadyToStrobe = false;
        }
    };

    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback() {
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

        dbgLog(TAG, "mDegrees: " + mDegrees + " mResult: " + mResult, 'd');
        return mResult;
    }

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
                if (internalCamera != null)
                {
                    strobeLED();
                }
            }

        }

        return true;
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
    protected void onResume()
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
            // Create a RelativeLayout container that will hold a SurfaceView,
            // and set it as the content of our activity.
            internalViewfinderPreview = new InternalPreview(this);

            if (mGestureListener != null)
            {
                internalViewfinderPreview.setOnTouchListener(mGestureListener);
            }

            setContentView(internalViewfinderPreview);

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);

            // Find the total number of cameras available
            numberOfCameras = Camera.getNumberOfCameras();

            // Find the ID of the default camera
            CameraInfo cameraInfo = new CameraInfo();
            for (int i = 0; i < numberOfCameras; i++)
            {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
                {
                    defaultCameraId = i;
                    isSupport = true;
                    mOrientation = getDisplayOrientation(cameraInfo);
                    internalViewfinderPreview.setDisplayOrientation(mOrientation);
                }
            }

            if (!isSupport)
            {
                Toast.makeText(InternalCameraStrobeTest.this, "The device does NOT support Internal Camera", Toast.LENGTH_SHORT).show();
                InternalCameraStrobeTest.this.finish();
            }

            strobeFired = false;

            // Check if commServer started this activity
            // if so then don't automatically fire strobe
            if (wasActivityStartedByCommServer())
            {
                activityStartedFromCommServer = true;
                dbgLog(TAG, "activity originated from commserver", 'd');
            }
            else
            {
                activityStartedFromCommServer = false;
                dbgLog(TAG, "activity originated UI", 'd');
                Toast.makeText(InternalCameraStrobeTest.this, "Touch screen to trigger the flash", Toast.LENGTH_SHORT).show();
            }

            // Open the default i.e. the first rear facing camera.
            internalCamera = openWrapper(defaultCameraId);
            cameraCurrentlyLocked = defaultCameraId;
            internalViewfinderPreview.setCamera(internalCamera);
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
        if (internalCamera == null)
        {
            dbgLog(TAG, "already stopped.", 'v');
            return;
        }

        internalViewfinderPreview.setCamera(null);
        internalCamera.release();
        internalCamera = null;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            // Because the Camera object is a shared resource, it's very
            // important to release it when the activity is paused.
            if (internalCamera != null)
            {
                internalViewfinderPreview.setCamera(null);
                internalCamera.release();
                internalCamera = null;
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

            contentRecord("testresult.txt", "Camera - Internal Camera Strobe:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Internal Camera Strobe:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        strHelpList.add("Camera_InternalCameraStrobeTest");
        strHelpList.add("");
        strHelpList.add("This function will start the internal camera and strobe the flash");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("STROBE_FIRE - Fire Camera Strobe LED.");
        strHelpList.add("  ");
        strHelpList.add("STROBE_FIRE - Fire Camera Strobe LED.");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Camera - Internal Camera Strobe:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - Internal Camera Strobe:  PASS" + "\r\n\r\n", MODE_APPEND);

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

// ----------------------------------------------------------------------

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered
 * preview of the Camera to the surface. We need to center the SurfaceView
 * because not all devices have cameras that support preview sizes at the same
 * aspect ratio as the device's display.
 */
class InternalPreview extends ViewGroup implements SurfaceHolder.Callback
{
    private final String TAG = "CQATest:InternalCameraStrobeTestPreview";

    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;
    int mDisplayOrientation;
    private final Lock mCameraLock = new ReentrantLock();

    InternalPreview(Context context){
        super(context);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        int XOffset = 0;
        int YOffset = 0;

        XOffset = TestUtils.getDisplayYTopOffset();
        YOffset = TestUtils.getDisplayXLeftOffset();
        mSurfaceView.setX(XOffset);
        mSurfaceView.setY(YOffset);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera camera)
    {
        mCamera = camera;
        if (mCamera != null)
        {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.

        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec) - TestUtils.getDisplayYTopOffset();
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec) - TestUtils.getDisplayXLeftOffset();

        dbgLog(TAG, "SUGGESTED MINIMUM Width: " + getSuggestedMinimumWidth() + " Height: " + getSuggestedMinimumHeight(), 'e');
        dbgLog(TAG, "MEASURED SPEC Width: " + widthMeasureSpec + " Height: " + heightMeasureSpec, 'e');
        dbgLog(TAG, "MEASURED SIZE Width: " + width + " Height: " + height, 'e');

        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null)
        {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
            dbgLog(TAG, "PREVIEW SIZE Width: " + mPreviewSize.width + " Height: " + mPreviewSize.height, 'e');
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        if (changed && (getChildCount() > 0))
        {
            final View child = getChildAt(0);

            // keep klocwork happy
            if (null == child)
            {
                return;
            }

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;

            if (mPreviewSize != null)
            {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                dbgLog(TAG, "OnLayout Preview Width: " + previewWidth + " Height: " + previewHeight, 'e');

            }

            dbgLog(TAG, "OnLayout Width: " + width + " Height: " + height, 'e');

            // Center the child SurfaceView within the parent.
            if ((width * previewHeight) > (height * previewWidth))
            {
                final int scaledChildWidth = (previewWidth * height) / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);

                dbgLog(TAG, "Child Left: " + ((width - scaledChildWidth) / 2) + " Top: " + 0 + " Right: " + ((width + scaledChildWidth) / 2) + " Bottom: " + height, 'e');
            }
            else
            {
                final int scaledChildHeight = (previewHeight * width) / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
                dbgLog(TAG, "Child Left: " + 0 + " Top: " + ((height - scaledChildHeight) / 2) + " Right: " + width + " Bottom: " + ((height + scaledChildHeight) / 2), 'e');
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.

        try
        {
            if (mCamera != null)
            {
                mCamera.setDisplayOrientation(mDisplayOrientation);
                mCamera.setPreviewDisplay(holder);
            }
        }
        catch (IOException exception)
        {
            dbgLog(TAG, "IOException caused by setPreviewDisplay()", 'e');
            dbgLog(TAG, "" + exception.getMessage(), 'e');
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null)
        {
            mCamera.stopPreview();
        }
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h)
    {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null)
        {
            return null;
        }

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
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

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null)
        {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes)
            {
                if (Math.abs(size.height - targetHeight) < minDiff)
                {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                    dbgLog(TAG, "getOptimalPreviewSize: Height: " + optimalSize.height + "Width: " + optimalSize.width, 'd');
                }
            }
        }

        return optimalSize;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
    {
        try
        {
            mCameraLock.lock();
            if (mCamera != null)
            {
                // Now that the size is known, set up the camera parameters and
                // begin
                // the preview.
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

                dbgLog(TAG, "surfaceChanged: width: " + mPreviewSize.width + "height: " + mPreviewSize.height, 'd');

                requestLayout();

                mCamera.setParameters(parameters);
                mCamera.startPreview();
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

    public void setDisplayOrientation(int mOrientation)
    {
        mDisplayOrientation = mOrientation;
        dbgLog(TAG, "mDisplayOrientation: " + mDisplayOrientation, 'd');
    }

    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
