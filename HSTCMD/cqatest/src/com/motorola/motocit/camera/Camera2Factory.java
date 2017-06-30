/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.hardware.camera2.utils.TypeReference;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Pair;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ZoomControls;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerBinaryPacket;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class Camera2Factory extends Test_Base
{

    private SurfaceView mPreview = null;
    private SurfaceHolder mPreviewHolder = null;
    private Surface mPreviewSurface = null;
    private SurfaceView mPreviewHider = null;
    private CameraManager mCameraManager = null;

    private HandlerThread mCameraHandlerThread = null;
    private Handler mCameraHandler = null;

    private CameraCaptureSession mCaptureSession;
    private final Object mCaptureSessionLockObject = new Object();

    private CaptureRequest.Builder mPreviewCaptureRequestBuilder = null;
    private CaptureRequest.Builder mImageCaptureRequestBuilder = null;

    private ImageReader mImageReader;
    private ImageReader mPreviewImageReader;
    private String mTakePicturePreviewSurfaceType = "";
    private String mTakePictureCaptureSurfaceType = "";

    private final Object mImageDataLockObject = new Object();
    private byte[] mImageData;

    // Track the image storage path being used
    private String mPath = null;

    private int mNumberOfCameras = 0;
    private String[] mCameraIdList;
    private static ConcurrentHashMap<String, Boolean> mCameraIdAvailability = new ConcurrentHashMap<String, Boolean>();
    private static ConcurrentHashMap<String, Boolean> mCameraIdTorchAvailability = new ConcurrentHashMap<String, Boolean>();
    private static ConcurrentHashMap<String, Boolean> mCameraIdTorchEnabled = new ConcurrentHashMap<String, Boolean>();

    // Track the camera being used
    private String mCameraBackId;
    private String mCameraExternalId;
    private String mCameraFrontId;
    private final Object mCameraIdInUseLockObject = new Object();
    private CameraDevice mCameraDeviceInUse;
    private CameraCharacteristics mCameraInUseCharacteristics = null;

    private boolean mIsPermissionAllowed = false;

    private static final int IMAGE_CAPTURE_STATE_PREVIEW = 0;
    private static final int IMAGE_CAPTURE_STATE_AF_LOCK_WAIT = 1;
    private static final int IMAGE_CAPTURE_STATE_AE_LOCK_WAIT = 2;
    private static final int IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT = 3;
    private static final int IMAGE_CAPTURE_STATE_TAKE_PICTURE = 4;

    private int mImageCaptureState = IMAGE_CAPTURE_STATE_PREVIEW;

    private List<String> mTotalCaptureResultList = null;
    private boolean mLogCaptureResults = false;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera2_Factory";
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
                    mIsPermissionAllowed = true;
                }
                else
                {
                    mIsPermissionAllowed = false;
                    finish();
                }
            }
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            mIsPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
            }
            else
            {
                mIsPermissionAllowed = true;
            }
        }

        if (mIsPermissionAllowed)
        {
            if (mCameraHandlerThread == null || mCameraHandler == null)
            {
                startCameraHandlerThread();
            }

            if (mCameraManager == null)
            {
                mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
                mCameraManager.registerAvailabilityCallback(mCameraManagerAvailabilityCallback, mCameraHandler);
                mCameraManager.registerTorchCallback(mCameraManagerTorchCallback, mCameraHandler);
            }

            //Get how many cameras and assign front, back, and external IDs
            try
            {
                mCameraIdList = mCameraManager.getCameraIdList();
                mNumberOfCameras = mCameraIdList.length;
                mCameraBackId = "";
                mCameraExternalId = "";
                mCameraFrontId = "";

                for (int cameraIndex = 0; cameraIndex < mNumberOfCameras; cameraIndex++)
                {
                    CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdList[cameraIndex]);

                    Integer lensFacing = (Integer) cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);

                    if(lensFacing != null )
                    {
                        switch(lensFacing.intValue())
                        {
                            case CameraMetadata.LENS_FACING_BACK:
                            {
                                if (mCameraBackId.length() == 0)
                                {
                                    mCameraBackId = mCameraIdList[cameraIndex];
                                }
                                break;
                            }
                            case CameraMetadata.LENS_FACING_EXTERNAL:
                            {
                                if (mCameraExternalId.length() == 0)
                                {
                                    mCameraExternalId = mCameraIdList[cameraIndex];
                                }
                                break;
                            }
                            case CameraMetadata.LENS_FACING_FRONT:
                            {
                                if (mCameraFrontId.length() == 0)
                                {
                                    mCameraFrontId = mCameraIdList[cameraIndex];
                                }
                                break;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unable to access camera manager", 'e');
                mNumberOfCameras = 0;
            }

            dbgLog(TAG, "Number of cameras found: " + mNumberOfCameras, 'i');
        }

    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (mIsPermissionAllowed)
        {
            // set activity content to a view
            View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.viewfinder_layout, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);

            if (mGestureListener != null)
            {
                thisView.setOnTouchListener(mGestureListener);
            }

            // find view for mPreview
            if(mPreview == null)
            {
                mPreview = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera_viewfinder);
                mPreviewHolder = mPreview.getHolder();
                mPreviewHolder.addCallback(surfaceCallback);
                mPreviewSurface = mPreviewHolder.getSurface();
            }

            // find view for mPreviewHider
            if(mPreviewHider == null)
            {
                mPreviewHider = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera2_viewfinder_hider);
            }

            // find view for zoomControls and hide them
            ZoomControls zoomControls = (ZoomControls) findViewById(com.motorola.motocit.R.id.camera_zoom_controls);
            zoomControls.hide();

            sendStartActivityPassed();

            if (mCameraHandlerThread == null || mCameraHandler == null)
            {
                startCameraHandlerThread();
            }
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
    }

    @Override
    public void onRestart()
    {
        super.onRestart();
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        mPreview = null;
        mPreviewHolder = null;
        mPreviewSurface = null;

        mPreviewHider = null;
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (mIsPermissionAllowed)
        {
            if(isFinishing())
            {
                // Stop Preview and Release Camera
                mCameraManager.unregisterAvailabilityCallback(mCameraManagerAvailabilityCallback);
                mCameraManager.unregisterTorchCallback(mCameraManagerTorchCallback);
                stopCameraHandlerThread();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        dbgLog(TAG, "onConfigurationChanged called", 'i');

        // ignore orientation/keyboard change
        super.onConfigurationChanged(newConfig);
    }

    private void startCameraHandlerThread()
    {
        mCameraHandlerThread = new HandlerThread("CameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
    }

    private void stopCameraHandlerThread()
    {
        mCameraHandlerThread.quitSafely();
        try
        {
            mCameraHandlerThread.join();
            mCameraHandlerThread = null;
            mCameraHandler = null;
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Failed to stop camera handler thread", 'e');
        }
    }

    private final CameraManager.AvailabilityCallback mCameraManagerAvailabilityCallback = new CameraManager.AvailabilityCallback()
    {
        @Override
        public void onCameraAvailable(String cameraId)
        {
            dbgLog(TAG, "Camera ID " + cameraId + " is available.", 'i');
            mCameraIdAvailability.put(cameraId, true);
        }

        @Override
        public void onCameraUnavailable (String cameraId)
        {
            dbgLog(TAG, "Camera ID " + cameraId + " is unavailable.", 'i');
            mCameraIdAvailability.put(cameraId, false);
        }
    };

    private final CameraManager.TorchCallback mCameraManagerTorchCallback = new CameraManager.TorchCallback()
    {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled)
        {
            dbgLog(TAG, "Camera ID " + cameraId + " Torch is mode is " + enabled, 'i');
            mCameraIdTorchEnabled.put(cameraId, enabled);
            dbgLog(TAG, "Camera ID " + cameraId + " Torch is available.", 'i');
            mCameraIdTorchAvailability.put(cameraId, true);
        }

        @Override
        public void onTorchModeUnavailable(String cameraId)
        {
            dbgLog(TAG, "Camera ID " + cameraId + " Torch is unavailable.", 'i');
            mCameraIdTorchAvailability.put(cameraId, false);
        }
    };

    private final CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onClosed(CameraDevice cameraDevice)
        {
            dbgLog(TAG, "CameraDevice.StateCallback onClosed Called", 'i');
            mCameraDeviceInUse = null;
            mPreviewCaptureRequestBuilder = null;
            mImageCaptureRequestBuilder = null;
            if(mImageReader != null)
            {
                mImageReader.close();
                mImageReader = null;
            }
            if(mPreviewImageReader != null)
            {
                mPreviewImageReader.close();
                mPreviewImageReader = null;
            }
            mTotalCaptureResultList = null;
            mCameraInUseCharacteristics= null;
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice)
        {
            dbgLog(TAG, "CameraDevice.StateCallback onDisconnected Called", 'i');
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error)
        {
            dbgLog(TAG, "CameraDevice.StateCallback onError Called", 'i');

            switch (error)
            {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: ERROR_CAMERA_DEVICE", 'i');
                    break;
                }
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: ERROR_CAMERA_DISABLED", 'i');
                    break;
                }
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: ERROR_CAMERA_IN_USE", 'i');
                    break;
                }
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: ERROR_CAMERA_SERVICE", 'i');
                    break;
                }
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: ERROR_MAX_CAMERAS_IN_USE", 'i');
                    break;
                }
                default:
                {
                    dbgLog(TAG, "CameraDevice.StateCallback Error: UNKNOWN  Error Number=" + error, 'i');
                }
            }
        }

        @Override
        public void onOpened(CameraDevice cameraDevice)
        {
            synchronized (mCameraIdInUseLockObject)
            {
                dbgLog(TAG, "CameraDevice.StateCallback onOpened Called", 'i');
                mCameraDeviceInUse = cameraDevice;
                mCameraIdInUseLockObject.notifyAll();
            }
        }
    };

    private CameraCaptureSession.CaptureCallback mImageCaptureCallback = new CameraCaptureSession.CaptureCallback()
    {
        private void handleCurrentImageCaptureState(CaptureResult captureResult)
        {
            try
            {
                switch (mImageCaptureState)
                {
                    case IMAGE_CAPTURE_STATE_PREVIEW:
                    {
                        dbgLog(TAG, "Handling Current Image Capture State: IMAGE_CAPTURE_STATE_PREVIEW.", 'i');
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                        break;
                    }
                    case IMAGE_CAPTURE_STATE_AF_LOCK_WAIT:
                    {
                        dbgLog(TAG, "Handling Current Image Capture State: IMAGE_CAPTURE_STATE_AF_LOCK_WAIT.", 'i');
                        Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                        Integer captureRequestAFMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);

                        if(afState != null)
                        {
                            dbgLog(TAG, "AF State = " + afState.toString(), 'i');
                        }
                        else
                        {
                            dbgLog(TAG, "AF State = NULL", 'i');
                        }


                        if (afState != null && afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED)
                        {
                            dbgLog(TAG, "Starting AE PRECAPTURE TRIGGER.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_AE_LOCK_WAIT);
                        }
                        else if (captureRequestAFMode != null && captureRequestAFMode == CameraMetadata.CONTROL_AF_MODE_OFF)
                        {
                            dbgLog(TAG, "AF MODE STATE SWITCHED! Starting AE PRECAPTURE TRIGGER.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_AE_LOCK_WAIT);
                        }
                        else
                        {
                            //Auto Focus not yet locked, try again with IDLE trigger
                            dbgLog(TAG, "Waiting for AF Lock.", 'i');
                            mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                        }

                        break;
                    }
                    case IMAGE_CAPTURE_STATE_AE_LOCK_WAIT:
                    {
                        dbgLog(TAG, "Handling Current Image Capture State: IMAGE_CAPTURE_STATE_AE_LOCK_WAIT.", 'i');
                        Integer aeState = captureResult.get(CaptureResult.CONTROL_AE_STATE);
                        Integer captureRequestAEMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE);

                        if(aeState != null)
                        {
                            dbgLog(TAG, "AE State = " + aeState.toString(), 'i');
                        }
                        else
                        {
                            dbgLog(TAG, "AE State = NULL", 'i');
                        }

                        if (aeState != null && (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED || aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED))
                        {
                            //Turn on Flash mode if AE says so
                            if(aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED)
                            {
                                mImageCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                            }
                            else
                            {
                                mImageCaptureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                            }
                            dbgLog(TAG, "Starting AWB LOCK WAIT.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT);
                        }
                        else if (captureRequestAEMode != null && captureRequestAEMode == CameraMetadata.CONTROL_AE_MODE_OFF)
                        {
                            dbgLog(TAG, "AE MODE STATE SWITCHED! Starting AWB LOCK WAIT.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT);
                        }
                        else
                        {
                            //Auto Exposure not yet converged, try again with IDLE trigger
                            dbgLog(TAG, "Waiting for AE Lock.", 'i');
                            mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                        }

                        break;
                    }
                    case IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT:
                    {
                        dbgLog(TAG, "Handling Current Image Capture State: IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT.", 'i');
                        Integer awbState = captureResult.get(CaptureResult.CONTROL_AWB_STATE);
                        Integer captureRequestAWBMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE);

                        if(awbState != null)
                        {
                            dbgLog(TAG, "AWB State = " + awbState.toString(), 'i');
                        }
                        else
                        {
                            dbgLog(TAG, "AWB State = NULL", 'i');
                        }

                        if (awbState != null && awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED)
                        {
                            dbgLog(TAG, "Starting TAKE PICTURE.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_TAKE_PICTURE);
                        }
                        else if (captureRequestAWBMode != null && captureRequestAWBMode == CameraMetadata.CONTROL_AWB_MODE_OFF)
                        {
                            dbgLog(TAG, "AWB MODE STATE SWITCHED! Starting TAKE PICTURE.", 'i');
                            handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_TAKE_PICTURE);
                        }
                        else
                        {
                            //Auto White Balance not yet converged, try again.
                            dbgLog(TAG, "Waiting for AWB Lock.", 'i');
                            mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                        }

                        break;
                    }
                    case IMAGE_CAPTURE_STATE_TAKE_PICTURE:
                    {
                        dbgLog(TAG, "Handling Current Image Capture State: IMAGE_CAPTURE_STATE_TAKE_PICTURE.", 'i');
                        break;
                    }
                }
            }
            catch(Exception e)
            {
                dbgLog(TAG, "Exception while handling new camera state: " + e.toString(), 'e');
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureCompleted called.", 'i');

            mTotalCaptureResultList = totalCaptureResultToStringList(result);

            if (mLogCaptureResults)
            {
                dbgLog(TAG, "Printing TotalCaptureResult from onCaptureCompleted", 'i');

                for (String captureResult : mTotalCaptureResultList)
                {
                    dbgLog(TAG, "\t" + captureResult, 'i');
                }
            }

            handleCurrentImageCaptureState(result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureFailed called.", 'i');
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureProgressed called.", 'i');
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureSequenceAborted called.", 'i');
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureSequenceCompleted called.", 'i');
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber)
        {
            dbgLog(TAG, "mImageCaptureCallback onCaptureStarted called.", 'i');
        }
    };

    private void handleNextCameraCaptureState(int nextCaptureState)
    {
        try
        {
            Integer captureRequestAFMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
            Integer captureRequestAEMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE);
            Integer captureRequestAWBMode = mPreviewCaptureRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE);

            switch (nextCaptureState)
            {
                case IMAGE_CAPTURE_STATE_AF_LOCK_WAIT:
                {
                    //Perform Auto Focus first
                    if (captureRequestAFMode != null && captureRequestAFMode != CameraMetadata.CONTROL_AF_MODE_OFF)
                    {
                        dbgLog(TAG, "Handling New Image Capture State: IMAGE_CAPTURE_STATE_AF_LOCK_WAIT.", 'i');

                        mImageCaptureState = IMAGE_CAPTURE_STATE_AF_LOCK_WAIT;

                        //Cancel any previous AF triggers
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), null, mCameraHandler);

                        //Start Auto Focus Trigger
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);

                        //Reset trigger to IDLE in capture request builder
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                        break;
                    }
                    dbgLog(TAG, "AF MODE is null or OFF, go to state IMAGE_CAPTURE_STATE_AE_LOCK_WAIT.", 'i');
                }
                case IMAGE_CAPTURE_STATE_AE_LOCK_WAIT:
                {
                    //Perform Auto Exposure second
                    if (captureRequestAEMode != null && captureRequestAEMode != CameraMetadata.CONTROL_AE_MODE_OFF)
                    {
                        dbgLog(TAG, "Handling New Image Capture State: IMAGE_CAPTURE_STATE_AE_LOCK_WAIT.", 'i');

                        mImageCaptureState = IMAGE_CAPTURE_STATE_AE_LOCK_WAIT;

                        //Cancel any previous AE triggers or locks
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), null, mCameraHandler);

                        //Start Auto Exposure Trigger
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);

                        //Reset trigger to IDLE in capture request builder
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                        break;
                    }
                    dbgLog(TAG, "AE MODE is null or OFF, go to state IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT.", 'i');
                }
                case IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT:
                {
                    //Perform Auto White Balance Third

                    //Lock Auto Exposure if available
                    Boolean aeLockAvailable = false;
                    try
                    {
                        aeLockAvailable = (Boolean) mCameraInUseCharacteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);

                        if (aeLockAvailable != null && aeLockAvailable)
                        {
                            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                            mImageCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                        }
                    }
                    catch(Exception e)
                    {
                    }

                    if (captureRequestAWBMode != null && captureRequestAWBMode != CameraMetadata.CONTROL_AWB_MODE_OFF)
                    {
                        dbgLog(TAG, "Handling New Image Capture State: IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT.", 'i');
                        mImageCaptureState = IMAGE_CAPTURE_STATE_AWB_LOCK_WAIT;
                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                        break;
                    }
                    dbgLog(TAG, "AWB MODE is null or OFF, go to state IMAGE_CAPTURE_STATE_TAKE_PICTURE.", 'i');
                }
                case IMAGE_CAPTURE_STATE_TAKE_PICTURE:
                {
                    //Finally take image

                    //Lock Auto Exposure and Auto White Balance if available
                    Boolean aeLockAvailable = false;
                    Boolean awbLockAvailable = false;

                    try
                    {
                        aeLockAvailable = (Boolean) mCameraInUseCharacteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);

                        if (aeLockAvailable != null && aeLockAvailable)
                        {
                            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                            mImageCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                        }
                    }
                    catch(Exception e)
                    {
                        aeLockAvailable = false;
                    }

                    try
                    {
                        awbLockAvailable = (Boolean) mCameraInUseCharacteristics.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE);

                        if (awbLockAvailable != null && awbLockAvailable)
                        {
                            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                            mImageCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                        }
                    }
                    catch(Exception e)
                    {
                        awbLockAvailable = false;
                    }

                    dbgLog(TAG, "Handling New Image Capture State: IMAGE_CAPTURE_STATE_TAKE_PICTURE.", 'i');

                    mImageCaptureState = IMAGE_CAPTURE_STATE_TAKE_PICTURE;
                    mCaptureSession.capture(mImageCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);

                    //Reset AE and AWB locks to false after starting capture
                    if (aeLockAvailable)
                    {
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                        mImageCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                    }

                    if (awbLockAvailable)
                    {
                        mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                        mImageCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                    }

                    break;
                }
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Exception while handling next camera state: " + e.toString(), 'e');
        }
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(ImageReader imageReader)
        {
            dbgLog(TAG, "mOnImageAvailableListener onImageAvailable called.", 'i');

            if(mImageCaptureState == IMAGE_CAPTURE_STATE_TAKE_PICTURE)
            {
                synchronized (mImageDataLockObject)
                {
                    Image image = null;
                    try
                    {
                        dbgLog(TAG, "mOnImageAvailableListener acquire latest image.", 'i');
                        image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        mImageData = new byte[buffer.remaining()];
                        buffer.get(mImageData);
                        mImageDataLockObject.notifyAll();
                    }
                    catch (Exception e)
                    {
                        dbgLog(TAG, "mOnImageAvailableListener failed to get image.", 'i');
                    }
                    finally
                    {
                        if (image != null)
                        {
                            image.close();
                        }
                    }
                }
            }
            else
            {
                try
                {
                    imageReader.acquireLatestImage().close();
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "mOnImageAvailableListener failed to close image.", 'i');
                }
            }
        }

    };

    private void setBacklightBrightness(final float brightness)
    {
        Thread setLight = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ContentResolver resolver = getContentResolver();
                            Settings.System.putInt(resolver, "screen_brightness_mode", 0);

                            Window window = getWindow();
                            if (null != window)
                            {
                                WindowManager.LayoutParams lp = window.getAttributes();
                                lp.screenBrightness = (float) (1.0f * brightness * 0.01);
                                window.setAttributes(lp);
                            }
                        }
                    });
                }
                finally
                {
                }
            }

            ;
        };
        setLight.start();
    }

    private String santizePath(String path)
    {
        // make sure filename starts with /
        if (!path.startsWith("/"))
        {
            path = "/" + path;
        }

        // since we are doing jpeg files, make sure filename ends with jpg
        if (!path.contains("."))
        {
            path += ".jpg";
        }

        // return the full path and desired filename
        dbgLog(TAG, "out_put_path: " + Environment.getExternalStorageDirectory().getAbsolutePath() + path, 'd');
        return Environment.getExternalStorageDirectory().getAbsolutePath() + path;
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {
            dbgLog(TAG, "surfaceCallback surfaceDestroyed called.", 'i');
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            dbgLog(TAG, "surfaceCallback surfaceCreated called.", 'i');
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            dbgLog(TAG, "surfaceCallback surfaceChanged called.", 'i');
            dbgLog(TAG, "surfaceCallback surfaceChanged format=" + format, 'i');
            dbgLog(TAG, "surfaceCallback surfaceChanged width=" + width, 'i');
            dbgLog(TAG, "surfaceCallback surfaceChanged height=" + height, 'i');
        }
    };

    private class HidePreviewWindow implements Runnable
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "Running HidePreviewWindow", 'i');
            if(mPreviewHider != null)
            {
                mPreviewHider.setVisibility(View.VISIBLE);
            }
        }
    }

    private class ShowPreviewWindow implements Runnable
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "Running ShowPreviewWindow", 'i');
            if(mPreviewHider != null)
            {
                mPreviewHider.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean onSwipeRight()
    {
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
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
        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("HELP"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_NUMBER_CAMERAS"))
        {
            // list for returned data
            List<String> strDataList = new ArrayList<String>();

            // add data to return data list
            strDataList.add("NUMBER_OF_CAMERAS=" + mNumberOfCameras);

            for (int cameraIndex = 0; cameraIndex < mNumberOfCameras; cameraIndex++)
            {
                strDataList.add("CAMERA_" + cameraIndex + "_ID=" + mCameraIdList[cameraIndex]);
            }

            // send return data list to comm server
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CAMERA_CHARACTERISTICS"))
        {
            // list for returned data
            List<String> strDataList = new ArrayList<String>();

            // add data to return data list
            strDataList.add("NUMBER_OF_CAMERAS=" + mNumberOfCameras);

            for (int cameraIndex = 0; cameraIndex < mNumberOfCameras; cameraIndex++)
            {
                strDataList.add("CAMERA_" + cameraIndex + "_ID=" + mCameraIdList[cameraIndex]);

                try
                {
                    CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdList[cameraIndex]);
                    List<CameraCharacteristics.Key<?>> cameraCharacteristicsKeys = cameraCharacteristics.getKeys();

                    strDataList.add("CAMERA_" + cameraIndex + "_NUMBER_OF_KEYS=" + cameraCharacteristicsKeys.size());

                    for (CameraCharacteristics.Key<?> cameraKey : cameraCharacteristicsKeys)
                    {
                        if (cameraKey == CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
                                {
                                    returnStringValue = returnStringValue + "50HZ,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ)
                                {
                                    returnStringValue = returnStringValue + "60HZ,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                                {
                                    returnStringValue = returnStringValue + "AUTO,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_AE_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_MODE_ON)
                                {
                                    returnStringValue = returnStringValue + "ON,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                                {
                                    returnStringValue = returnStringValue + "ON_ALWAYS_FLASH,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
                                {
                                    returnStringValue = returnStringValue + "ON_AUTO_FLASH,";
                                }
                                else if (value == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
                                {
                                    returnStringValue = returnStringValue + "ON_AUTO_FLASH_REDEYE,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_AVAILABLE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                        {
                            String returnStringValue = "";
                            Range<Integer>[] rangeIntegerArray = (Range<Integer>[]) cameraCharacteristics.get(cameraKey);
                            for (Range<Integer> value: rangeIntegerArray)
                            {
                                returnStringValue = returnStringValue + value.toString() + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                        {
                            Range<Integer> rangeInteger = (Range<Integer>) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_COMPENSATION_RANGE=" + rangeInteger.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                        {
                            Rational value = (Rational) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_COMPENSATION_STEP=" + value.doubleValue());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)
                        {
                            Boolean value = (Boolean) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AE_LOCK_AVAILABLE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_AF_MODE_AUTO)
                                {
                                    returnStringValue = returnStringValue + "AUTO,";
                                }
                                else if (value == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                {
                                    returnStringValue = returnStringValue + "CONTINUOUS_PICTURE,";
                                }
                                else if (value == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                {
                                    returnStringValue = returnStringValue + "CONTINUOUS_VIDEO,";
                                }
                                else if (value == CameraMetadata.CONTROL_AF_MODE_EDOF)
                                {
                                    returnStringValue = returnStringValue + "EDOF,";
                                }
                                else if (value == CameraMetadata.CONTROL_AF_MODE_MACRO)
                                {
                                    returnStringValue = returnStringValue + "MACRO,";
                                }
                                else if (value == CameraMetadata.CONTROL_AF_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AF_AVAILABLE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_EFFECT_MODE_AQUA)
                                {
                                    returnStringValue = returnStringValue + "AQUA,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)
                                {
                                    returnStringValue = returnStringValue + "BLACKBOARD,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_MONO)
                                {
                                    returnStringValue = returnStringValue + "MONO,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)
                                {
                                    returnStringValue = returnStringValue + "NEGATIVE,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)
                                {
                                    returnStringValue = returnStringValue + "POSTERIZE,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                                {
                                    returnStringValue = returnStringValue + "SEPIA,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)
                                {
                                    returnStringValue = returnStringValue + "SOLARIZE,";
                                }
                                else if (value == CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD)
                                {
                                    returnStringValue = returnStringValue + "WHITEBOARD,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AVAILABLE_EFFECTS=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AVAILABLE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_MODE_AUTO)
                                {
                                    returnStringValue = returnStringValue + "AUTO,";
                                }
                                else if (value == CameraMetadata.CONTROL_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.CONTROL_MODE_OFF_KEEP_STATE)
                                {
                                    returnStringValue = returnStringValue + "OFF_KEEP_STATE,";
                                }
                                else if (value == CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                                {
                                    returnStringValue = returnStringValue + "USE_SCENE_MODE,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AVAILABLE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_SCENE_MODE_ACTION)
                                {
                                    returnStringValue = returnStringValue + "ACTION,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_BARCODE)
                                {
                                    returnStringValue = returnStringValue + "BARCODE,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_BEACH)
                                {
                                    returnStringValue = returnStringValue + "BEACH,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT)
                                {
                                    returnStringValue = returnStringValue + "CANDLELIGHT,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
                                {
                                    returnStringValue = returnStringValue + "DISABLED,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
                                {
                                    returnStringValue = returnStringValue + "FACE_PRIORITY,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS)
                                {
                                    returnStringValue = returnStringValue + "FIREWORKS,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_HDR)
                                {
                                    returnStringValue = returnStringValue + "HDR,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO)
                                {
                                    returnStringValue = returnStringValue + "HIGH_SPEED_VIDEO,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE)
                                {
                                    returnStringValue = returnStringValue + "LANDSCAPE,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_NIGHT)
                                {
                                    returnStringValue = returnStringValue + "NIGHT,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT)
                                {
                                    returnStringValue = returnStringValue + "NIGHT_PORTRAIT,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_PARTY)
                                {
                                    returnStringValue = returnStringValue + "PARTY,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT)
                                {
                                    returnStringValue = returnStringValue + "PORTRAIT,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_SNOW)
                                {
                                    returnStringValue = returnStringValue + "SNOW,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_SPORTS)
                                {
                                    returnStringValue = returnStringValue + "SPORTS,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO)
                                {
                                    returnStringValue = returnStringValue + "STEADYPHOTO,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_SUNSET)
                                {
                                    returnStringValue = returnStringValue + "SUNSET,";
                                }
                                else if (value == CameraMetadata.CONTROL_SCENE_MODE_THEATRE)
                                {
                                    returnStringValue = returnStringValue + "THEATRE,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AVAILABLE_SCENE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                                {
                                    returnStringValue = returnStringValue + "ON,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.CONTROL_AWB_MODE_AUTO)
                                {
                                    returnStringValue = returnStringValue + "AUTO,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                                {
                                    returnStringValue = returnStringValue + "CLOUDY_DAYLIGHT,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                                {
                                    returnStringValue = returnStringValue + "DAYLIGHT,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
                                {
                                    returnStringValue = returnStringValue + "FLUORESCENT,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
                                {
                                    returnStringValue = returnStringValue + "INCANDESCENT,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_SHADE)
                                {
                                    returnStringValue = returnStringValue + "SHADE,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_TWILIGHT)
                                {
                                    returnStringValue = returnStringValue + "TWILIGHT,";
                                }
                                else if (value == CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT)
                                {
                                    returnStringValue = returnStringValue + "WARM_FLUORESCENT,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AWB_AVAILABLE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE)
                        {
                            Boolean value = (Boolean) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_AWB_LOCK_AVAILABLE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_MAX_REGIONS_AE)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_MAX_REGIONS_AE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_MAX_REGIONS_AF=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.CONTROL_MAX_REGIONS_AWB)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_CONTROL_MAX_REGIONS_AWB=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.DEPTH_DEPTH_IS_EXCLUSIVE)
                        {
                            Boolean value = (Boolean) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_DEPTH_DEPTH_IS_EXCLUSIVE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.EDGE_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.EDGE_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.EDGE_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG)
                                {
                                    returnStringValue = returnStringValue + "ZERO_SHUTTER_LAG,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_EDGE_AVAILABLE_EDGE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        {
                            Boolean value = (Boolean) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_FLASH_INFO_AVAILABLE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.HOT_PIXEL_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.HOT_PIXEL_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)
                            {
                                returnStringValue = "FULL";
                            }
                            else if (value.intValue() == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
                            {
                                returnStringValue = "LEGACY";
                            }
                            else if (value.intValue() == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED)
                            {
                                returnStringValue = "LIMITED";
                            }
                            else if (value.intValue() == 3)
                            {
                                returnStringValue = "3";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_INFO_SUPPORTED_HARDWARE_LEVEL=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES)
                        {
                            String returnStringValue = "";
                            Size[] sizeArray = (Size[]) cameraCharacteristics.get(cameraKey);
                            for (Size value : sizeArray)
                            {
                                returnStringValue = value.toString() + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_JPEG_AVAILABLE_THUMBNAIL_SIZES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_FACING)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.LENS_FACING_BACK)
                            {
                                returnStringValue = "BACK";
                            }
                            else if (value.intValue() == CameraMetadata.LENS_FACING_EXTERNAL)
                            {
                                returnStringValue = "EXTERNAL";
                            }
                            else if (value.intValue() == CameraMetadata.LENS_FACING_FRONT)
                            {
                                returnStringValue = "FRONT";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_FACING=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_AVAILABLE_APERTURES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_AVAILABLE_FILTER_DENSITIES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_AVAILABLE_FOCAL_LENGTHS=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
                                {
                                    returnStringValue = returnStringValue + "ON,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE)
                            {
                                returnStringValue = "APPROXIMATE";
                            }
                            else if (value.intValue() == CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED)
                            {
                                returnStringValue = "CALIBRATED";
                            }
                            else if (value.intValue() == CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED)
                            {
                                returnStringValue = "UNCALIBRATED";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_FOCUS_DISTANCE_CALIBRATION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
                        {
                            Float value = (Float) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_HYPERFOCAL_DISTANCE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                        {
                            Float value = (Float) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INFO_MINIMUM_FOCUS_DISTANCE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_INTRINSIC_CALIBRATION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_POSE_ROTATION)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_POSE_ROTATION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_POSE_TRANSLATION)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_POSE_TRANSLATION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.LENS_RADIAL_DISTORTION)
                        {
                            String returnStringValue = "";
                            float[] floatArray = (float[]) cameraCharacteristics.get(cameraKey);
                            for (float value : floatArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_LENS_RADIAL_DISTORTION=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)
                                {
                                    returnStringValue = returnStringValue + "MINIMAL,";
                                }
                                else if (value == CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
                                {
                                    returnStringValue = returnStringValue + "ZERO_SHUTTER_LAG,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REPROCESS_MAX_CAPTURE_STALL=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
                                {
                                    returnStringValue = returnStringValue + "BACKWARD_COMPATIBLE,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)
                                {
                                    returnStringValue = returnStringValue + "BURST_CAPTURE,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)
                                {
                                    returnStringValue = returnStringValue + "CONSTRAINED_HIGH_SPEED_VIDEO,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
                                {
                                    returnStringValue = returnStringValue + "DEPTH_OUTPUT,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING)
                                {
                                    returnStringValue = returnStringValue + "MANUAL_POST_PROCESSING,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
                                {
                                    returnStringValue = returnStringValue + "MANUAL_SENSOR,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)
                                {
                                    returnStringValue = returnStringValue + "PRIVATE_REPROCESSING,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                                {
                                    returnStringValue = returnStringValue + "RAW,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)
                                {
                                    returnStringValue = returnStringValue + "READ_SENSOR_SETTINGS,";
                                }
                                else if (value == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)
                                {
                                    returnStringValue = returnStringValue + "YUV_REPROCESSING,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_AVAILABLE_CAPABILITIES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_MAX_NUM_INPUT_STREAMS=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_MAX_NUM_OUTPUT_PROC=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_MAX_NUM_OUTPUT_PROC_STALLING=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_MAX_NUM_OUTPUT_RAW=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_PARTIAL_RESULT_COUNT=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH)
                        {
                            Byte value = (Byte) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_REQUEST_PIPELINE_MAX_DEPTH=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                        {
                            Float value = (Float) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SCALER_CROPPING_TYPE)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.SCALER_CROPPING_TYPE_CENTER_ONLY)
                            {
                                returnStringValue = "CENTER_ONLY";
                            }
                            else if (value.intValue() == CameraMetadata.SCALER_CROPPING_TYPE_FREEFORM)
                            {
                                returnStringValue = "FREEFORM";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_SCALER_CROPPING_TYPE=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        {
                            String returnStringValue = "";
                            StreamConfigurationMap streamConfigurationMap = (StreamConfigurationMap) cameraCharacteristics.get(cameraKey);

                            Range<Integer>[] highSpeedVideoFpsRangesArray = streamConfigurationMap.getHighSpeedVideoFpsRanges();
                            if(highSpeedVideoFpsRangesArray != null && highSpeedVideoFpsRangesArray.length > 0)
                            {
                                returnStringValue = "";
                                for (Range<Integer> highSpeedVideoFpsRanges : highSpeedVideoFpsRangesArray)
                                {
                                    returnStringValue = returnStringValue + highSpeedVideoFpsRanges.toString() + ",";
                                }
                                returnStringValue = returnStringValue.replaceAll(",$", "");
                                strDataList.add("CAMERA_" + cameraIndex + "_SCALER_STREAM_CONFIGURATION_MAP_HIGH_SPEED_VIDEO_FPS_RANGES=" + returnStringValue);
                            }

                            Size[] highSpeedVideoSizesArray = streamConfigurationMap.getHighSpeedVideoSizes();
                            if(highSpeedVideoSizesArray != null && highSpeedVideoSizesArray.length > 0)
                            {
                                returnStringValue = "";
                                for (Size highSpeedVideoSizes : highSpeedVideoSizesArray)
                                {
                                    returnStringValue = returnStringValue + highSpeedVideoSizes.toString() + ",";
                                }
                                returnStringValue = returnStringValue.replaceAll(",$", "");
                                strDataList.add("CAMERA_" + cameraIndex + "_SCALER_STREAM_CONFIGURATION_MAP_HIGH_SPEED_VIDEO_SIZES=" + returnStringValue);
                            }

                            final int[] outputFormatsArray = streamConfigurationMap.getOutputFormats();
                            returnStringValue = "";
                            if(outputFormatsArray != null && outputFormatsArray.length > 0)
                            {
                                for (int outputFormat : outputFormatsArray)
                                {
                                    String strOutputFormat = "";
                                    if (outputFormat == ImageFormat.DEPTH16)
                                    {
                                        strOutputFormat = "DEPTH16";
                                    }
                                    else if (outputFormat == ImageFormat.DEPTH_POINT_CLOUD)
                                    {
                                        strOutputFormat = "DEPTH_POINT_CLOUD";
                                    }
                                    else if (outputFormat == ImageFormat.FLEX_RGBA_8888)
                                    {
                                        strOutputFormat = "FLEX_RGBA_8888";
                                    }
                                    else if (outputFormat == ImageFormat.FLEX_RGB_888)
                                    {
                                        strOutputFormat = "FLEX_RGB_888";
                                    }
                                    else if (outputFormat == ImageFormat.JPEG)
                                    {
                                        strOutputFormat = "JPEG";
                                    }
                                    else if (outputFormat == ImageFormat.NV16)
                                    {
                                        strOutputFormat = "NV16";
                                    }
                                    else if (outputFormat == ImageFormat.NV21)
                                    {
                                        strOutputFormat = "NV21";
                                    }
                                    else if (outputFormat == ImageFormat.PRIVATE)
                                    {
                                        strOutputFormat = "PRIVATE";
                                    }
                                    else if (outputFormat == ImageFormat.RAW10)
                                    {
                                        strOutputFormat = "RAW10";
                                    }
                                    else if (outputFormat == ImageFormat.RAW12)
                                    {
                                        strOutputFormat = "RAW12";
                                    }
                                    else if (outputFormat == ImageFormat.RAW_SENSOR)
                                    {
                                        strOutputFormat = "RAW_SENSOR";
                                    }
                                    else if (outputFormat == ImageFormat.RGB_565)
                                    {
                                        strOutputFormat = "IMAGE_FORMAT_RGB_565";
                                    }
                                    else if (outputFormat == ImageFormat.UNKNOWN)
                                    {
                                        strOutputFormat = "UNKNOWN";
                                    }
                                    else if (outputFormat == ImageFormat.YUV_420_888)
                                    {
                                        strOutputFormat = "YUV_420_888";
                                    }
                                    else if (outputFormat == ImageFormat.YUV_422_888)
                                    {
                                        strOutputFormat = "YUV_422_888";
                                    }
                                    else if (outputFormat == ImageFormat.YUV_444_888)
                                    {
                                        strOutputFormat = "YUV_444_888";
                                    }
                                    else if (outputFormat == ImageFormat.YUY2)
                                    {
                                        strOutputFormat = "YUY2";
                                    }
                                    else if (outputFormat == ImageFormat.YV12)
                                    {
                                        strOutputFormat = "YV12";
                                    }
                                    else if (outputFormat == PixelFormat.A_8)
                                    {
                                        strOutputFormat = "A_8";
                                    }
                                    else if (outputFormat == PixelFormat.LA_88)
                                    {
                                        strOutputFormat = "LA_88";
                                    }
                                    else if (outputFormat == PixelFormat.L_8)
                                    {
                                        strOutputFormat = "L_8";
                                    }
                                    else if (outputFormat == PixelFormat.OPAQUE)
                                    {
                                        strOutputFormat = "OPAQUE";
                                    }
                                    else if (outputFormat == PixelFormat.RGBA_4444)
                                    {
                                        strOutputFormat = "RGBA_4444";
                                    }
                                    else if (outputFormat == PixelFormat.RGBA_5551)
                                    {
                                        strOutputFormat = "RGBA_5551";
                                    }
                                    else if (outputFormat == PixelFormat.RGBA_8888)
                                    {
                                        strOutputFormat = "RGBA_8888";
                                    }
                                    else if (outputFormat == PixelFormat.RGBX_8888)
                                    {
                                        strOutputFormat = "RGBX_8888";
                                    }
                                    else if (outputFormat == PixelFormat.RGB_332)
                                    {
                                        strOutputFormat = "RGB_332";
                                    }
                                    else if (outputFormat == PixelFormat.RGB_565)
                                    {
                                        strOutputFormat = "PIXEL_FORMAT_RGB_565";
                                    }
                                    else if (outputFormat == PixelFormat.RGB_888)
                                    {
                                        strOutputFormat = "RGB_888";
                                    }
                                    else if (outputFormat == PixelFormat.TRANSLUCENT)
                                    {
                                        strOutputFormat = "TRANSLUCENT";
                                    }
                                    else if (outputFormat == PixelFormat.TRANSPARENT)
                                    {
                                        strOutputFormat = "TRANSPARENT";
                                    }
                                    else if (outputFormat == PixelFormat.UNKNOWN)
                                    {
                                        strOutputFormat = "UNKNOWN";
                                    }
                                    else
                                    {
                                        strOutputFormat = "UNKNOWN";
                                    }

                                    returnStringValue = returnStringValue + strOutputFormat + ",";

                                    Size[] highResolutionOutputSizesArray = streamConfigurationMap.getHighResolutionOutputSizes(outputFormat);
                                    if(highResolutionOutputSizesArray != null && highResolutionOutputSizesArray.length > 0)
                                    {
                                        String highResolutionOutputSizesReturnStringValue = "";
                                        for (Size highResolutionOutputSizes : highResolutionOutputSizesArray)
                                        {
                                            highResolutionOutputSizesReturnStringValue = highResolutionOutputSizesReturnStringValue + highResolutionOutputSizes.toString() + ",";
                                        }
                                        highResolutionOutputSizesReturnStringValue = highResolutionOutputSizesReturnStringValue.replaceAll(",$", "");
                                        strDataList.add("CAMERA_" + cameraIndex + "_SCALER_STREAM_CONFIGURATION_MAP_HIGH_RESOLUTION_OUTPUT_SIZES_" + strOutputFormat + "=" + highResolutionOutputSizesReturnStringValue);
                                    }

                                    Size[] outputSizesArray = streamConfigurationMap.getOutputSizes(outputFormat);
                                    if(outputSizesArray != null && outputSizesArray.length > 0)
                                    {
                                        String outputSizesReturnStringValue = "";
                                        for (Size outputSizes : outputSizesArray)
                                        {
                                            outputSizesReturnStringValue = outputSizesReturnStringValue + outputSizes.toString() + ",";
                                        }
                                        outputSizesReturnStringValue = outputSizesReturnStringValue.replaceAll(",$", "");
                                        strDataList.add("CAMERA_" + cameraIndex + "_SCALER_STREAM_CONFIGURATION_MAP_OUTPUT_SIZES_" + strOutputFormat + "=" + outputSizesReturnStringValue);
                                    }
                                }

                                returnStringValue = returnStringValue.replaceAll(",$", "");
                                strDataList.add("CAMERA_" + cameraIndex + "_SCALER_STREAM_CONFIGURATION_MAP_OUTPUT_FORMATS=" + returnStringValue);
                            }

                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS)
                                {
                                    returnStringValue = returnStringValue + "COLOR_BARS,";
                                }
                                else if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY)
                                {
                                    returnStringValue = returnStringValue + "COLOR_BARS_FADE_TO_GRAY,";
                                }
                                else if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_CUSTOM1)
                                {
                                    returnStringValue = returnStringValue + "CUSTOM1,";
                                }
                                else if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_PN9)
                                {
                                    returnStringValue = returnStringValue + "PN9,";
                                }
                                else if (value == CameraMetadata.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR)
                                {
                                    returnStringValue = returnStringValue + "SOLID_COLOR,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_AVAILABLE_TEST_PATTERN_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
                        {
                            BlackLevelPattern value = (BlackLevelPattern) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_BLACK_LEVEL_PATTERN=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_CALIBRATION_TRANSFORM1=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_CALIBRATION_TRANSFORM2=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_COLOR_TRANSFORM1=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_COLOR_TRANSFORM2=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_FORWARD_MATRIX1)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_FORWARD_MATRIX1=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_FORWARD_MATRIX2)
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_FORWARD_MATRIX2=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        {
                            Rect value = (Rect) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_ACTIVE_ARRAY_SIZE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR)
                            {
                                returnStringValue = "BGGR";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG)
                            {
                                returnStringValue = "GBRG";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG)
                            {
                                returnStringValue = "GRBG";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB)
                            {
                                returnStringValue = "RGB";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB)
                            {
                                returnStringValue = "RGGB";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                        {
                            Range<Long> value = (Range<Long>) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_EXPOSURE_TIME_RANGE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED)
                        {
                            Boolean value = (Boolean) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_LENS_SHADING_APPLIED=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION)
                        {
                            Long value = (Long) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_MAX_FRAME_DURATION=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        {
                            SizeF value = (SizeF) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_PHYSICAL_SIZE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                        {
                            Size value = (Size) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_PIXEL_ARRAY_SIZE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
                        {
                            Rect value = (Rect) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                        {
                            Range<Integer> value = (Range<Integer>) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_SENSITIVITY_RANGE=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME)
                            {
                                returnStringValue = "REALTIME";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN)
                            {
                                returnStringValue = "UNKNOWN";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_TIMESTAMP_SOURCE=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_INFO_WHITE_LEVEL=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_MAX_ANALOG_SENSITIVITY=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_ORIENTATION)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_ORIENTATION=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)
                        {
                            String returnStringValue = "";
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER)
                            {
                                returnStringValue = "CLOUDY_WEATHER";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT)
                            {
                                returnStringValue = "COOL_WHITE_FLUORESCENT";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D50)
                            {
                                returnStringValue = "D50";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D55)
                            {
                                returnStringValue = "D55";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D65)
                            {
                                returnStringValue = "D65";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D75)
                            {
                                returnStringValue = "D75";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT)
                            {
                                returnStringValue = "DAYLIGHT";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT)
                            {
                                returnStringValue = "DAYLIGHT_FLUORESCENT";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT)
                            {
                                returnStringValue = "DAY_WHITE_FLUORESCENT";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER)
                            {
                                returnStringValue = "FINE_WEATHER";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FLASH)
                            {
                                returnStringValue = "FLASH";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT)
                            {
                                returnStringValue = "FLUORESCENT";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN)
                            {
                                returnStringValue = "ISO_STUDIO_TUNGSTEN";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_SHADE)
                            {
                                returnStringValue = "SHADE";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A)
                            {
                                returnStringValue = "STANDARD_A";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B)
                            {
                                returnStringValue = "STANDARD_B";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C)
                            {
                                returnStringValue = "STANDARD_C";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN)
                            {
                                returnStringValue = "TUNGSTEN";
                            }
                            else if (value.intValue() == CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT)
                            {
                                returnStringValue = "WHITE_FLUORESCENT";
                            }
                            else
                            {
                                returnStringValue = "UNKNOWN";
                            }

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_REFERENCE_ILLUMINANT1=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)
                        {
                            Byte value = (Byte) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SENSOR_REFERENCE_ILLUMINANT2=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SHADING_AVAILABLE_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.SHADING_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.SHADING_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.SHADING_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_SHADING_AVAILABLE_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                                {
                                    returnStringValue = returnStringValue + "FULL,";
                                }
                                else if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                                {
                                    returnStringValue = returnStringValue + "SIMPLE,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES)
                        {
                            String returnStringValue = "";
                            boolean[] booleanArray = (boolean[]) cameraCharacteristics.get(cameraKey);
                            for (boolean value : booleanArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF)
                                {
                                    returnStringValue = returnStringValue + "OFF,";
                                }
                                else if (value == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
                                {
                                    returnStringValue = returnStringValue + "ON,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_STATISTICS_INFO_MAX_FACE_COUNT=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.SYNC_MAX_LATENCY)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_SYNC_MAX_LATENCY=" + value.toString());
                        }
                        else if (cameraKey == CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
                        {
                            String returnStringValue = "";
                            int[] intArray = (int[]) cameraCharacteristics.get(cameraKey);
                            for (int value : intArray)
                            {
                                if (value == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                                {
                                    returnStringValue = returnStringValue + "CONTRAST_CURVE,";
                                }
                                else if (value == CameraMetadata.TONEMAP_MODE_FAST)
                                {
                                    returnStringValue = returnStringValue + "FAST,";
                                }
                                else if (value == CameraMetadata.TONEMAP_MODE_GAMMA_VALUE)
                                {
                                    returnStringValue = returnStringValue + "GAMMA_VALUE,";
                                }
                                else if (value == CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
                                {
                                    returnStringValue = returnStringValue + "HIGH_QUALITY,";
                                }
                                else if (value == CameraMetadata.TONEMAP_MODE_PRESET_CURVE)
                                {
                                    returnStringValue = returnStringValue + "PRESET_CURVE,";
                                }
                                else
                                {
                                    returnStringValue = returnStringValue + "UNKNOWN,";
                                }
                            }

                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add("CAMERA_" + cameraIndex + "_TONEMAP_AVAILABLE_TONE_MAP_MODES=" + returnStringValue);
                        }
                        else if (cameraKey == CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)
                        {
                            Integer value = (Integer) cameraCharacteristics.get(cameraKey);

                            strDataList.add("CAMERA_" + cameraIndex + "_TONEMAP_MAX_CURVE_POINTS=" + value.toString());
                        }
                        else
                        {
                            if (cameraKey != null)
                            {
                                Object cameraCharacteristicsValue = null;

                                cameraCharacteristicsValue = cameraCharacteristics.get(cameraKey);

                                Class cameraCharacteristicsClass = cameraCharacteristicsValue.getClass();

                                if (cameraCharacteristicsClass.equals(Byte.class))
                                {
                                    Byte value = (Byte) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(byte[].class))
                                {
                                    String returnStringValue = "";
                                    byte[] valueArray = (byte[]) cameraCharacteristicsValue;

                                    for (byte value : valueArray)
                                    {
                                        returnStringValue += String.format("%02x", value);
                                    }

                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(Boolean.class))
                                {
                                    Boolean value = (Boolean) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(Boolean[].class))
                                {
                                    String returnStringValue = "";
                                    Boolean[] valueArray = (Boolean[]) cameraCharacteristicsValue;
                                    for (Boolean value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value.toString() + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(Integer.class))
                                {
                                    Integer value = (Integer) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(int[].class))
                                {
                                    String returnStringValue = "";
                                    int[] valueArray = (int[]) cameraCharacteristicsValue;
                                    for (int value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(Double.class))
                                {
                                    Double value = (Double) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(double[].class))
                                {
                                    String returnStringValue = "";
                                    double[] valueArray = (double[]) cameraCharacteristicsValue;
                                    for (double value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(RggbChannelVector.class))
                                {
                                    RggbChannelVector value = (RggbChannelVector) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(ColorSpaceTransform.class))
                                {
                                    ColorSpaceTransform value = (ColorSpaceTransform) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(MeteringRectangle[].class))
                                {
                                    String returnStringValue = "";
                                    MeteringRectangle[] valueArray = (MeteringRectangle[]) cameraCharacteristicsValue;
                                    for (MeteringRectangle value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value.toString() + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(Size.class))
                                {
                                    Size value = (Size) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(Size[].class))
                                {
                                    String returnStringValue = "";
                                    Size[] valueArray = (Size[]) cameraCharacteristicsValue;
                                    for (Size value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value.toString() + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(SizeF.class))
                                {
                                    SizeF value = (SizeF) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(Float.class))
                                {
                                    Float value = (Float) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(float[].class))
                                {
                                    String returnStringValue = "";
                                    float[] valueArray = (float[]) cameraCharacteristicsValue;
                                    for (float value : valueArray)
                                    {
                                        returnStringValue = returnStringValue + value + ",";
                                    }
                                    returnStringValue = returnStringValue.replaceAll(",$", "");
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + returnStringValue);
                                }
                                else if (cameraCharacteristicsClass.equals(Rect.class))
                                {
                                    Rect value = (Rect) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(Rect.class))
                                {
                                    Rect value = (Rect) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(Rational.class))
                                {
                                    Rational value = (Rational) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else if (cameraCharacteristicsClass.equals(TonemapCurve.class))
                                {
                                    TonemapCurve value = (TonemapCurve) cameraCharacteristicsValue;
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + value.toString());
                                }
                                else
                                {
                                    strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=" + cameraCharacteristicsValue.toString());
                                }
                            }
                            else
                            {
                                strDataList.add("CAMERA_" + cameraIndex + "_" + cameraKey.getName() + "=NULL");
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Exception accessing camera characteristics" + e.toString(), 'e');

                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to access camera characteristics", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }

            // send return data list to comm server
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("OPEN_CAMERA"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            String cameraID = null;
            Long timeout = 1000L;

            if(mCameraDeviceInUse != null)
            {
                dbgLog(TAG, "Closing current camera in use.", 'i');
                mCameraDeviceInUse.close();
            }

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("CAMERA_ID"))
                    {
                        if(value.equalsIgnoreCase("CAMERA_FACING_BACK") || value.equalsIgnoreCase("LENS_FACING_BACK"))
                        {
                            cameraID = mCameraBackId;
                        }
                        else if(value.equalsIgnoreCase("CAMERA_FACING_EXTERNAL") || value.equalsIgnoreCase("LENS_FACING_EXTERNAL"))
                        {
                            cameraID = mCameraExternalId;
                        }
                        else if(value.equalsIgnoreCase("CAMERA_FACING_FRONT") || value.equalsIgnoreCase("LENS_FACING_FRONT"))
                        {
                            cameraID = mCameraFrontId;
                        }
                        else if(value.matches("^-?\\d+$"))
                        {
                            //Specify camera index directly
                            cameraID = value;
                        }
                        else
                        {
                            strReturnDataList.add("INVALID VALUE FOR CAMERA_ID: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if(key.equalsIgnoreCase("TIMEOUT"))
                    {
                        timeout = Long.parseLong(value);
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(cameraID == null || Arrays.asList(mCameraIdList).contains(cameraID) == false)
            {
                strReturnDataList.add("Invalid CAMERA_ID.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            try
            {
                dbgLog(TAG, "Opening Camera ID: " + cameraID, 'i');
                mCameraManager.openCamera(cameraID, mCameraStateCallback, mCameraHandler);

                mCameraInUseCharacteristics= mCameraManager.getCameraCharacteristics(cameraID);

                //Use Synchronized to ensure mCameraDeviceInUse is set before continuing on
                synchronized (mCameraIdInUseLockObject)
                {
                    try
                    {
                        dbgLog(TAG, "Waiting for mCameraDeviceInUse to be set", 'i');
                        mCameraIdInUseLockObject.wait(timeout);
                        dbgLog(TAG, "mCameraDeviceInUse has been set", 'i');
                    }
                    catch (Exception e)
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Failed to Open Camera", TAG, strRxCmd));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception opening camera: " + e.toString(), 'e');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to Open Camera", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(mCameraDeviceInUse == null)
            {
                strReturnDataList.add("mCameraDeviceInUse is null. Failed to open camera.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLOSE_CAMERA"))
        {
            if(mCameraDeviceInUse != null)
            {
                dbgLog(TAG, "Closing current camera in use.", 'i');
                mCameraDeviceInUse.close();
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_VIEWFINDER"))
        {
            mLogCaptureResults = false;

            List<String> strReturnDataList = new ArrayList<String>();

            // check for received data
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("LOG_CAPTURE_RESULTS"))
                    {
                        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            mLogCaptureResults = true;
                        }
                        else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                        {
                            mLogCaptureResults = false;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN LOG_CAPTURE_RESULTS: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }

            if (mPreviewSurface == null)
            {
                strReturnDataList.add("Camera2_Factory activity must be in Foreground for START_VIEWFINDER to function.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if(mCameraDeviceInUse == null)
            {
                strReturnDataList.add("mCameraDeviceInUse is NULL. OPEN_CAMERA must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if(mPreviewCaptureRequestBuilder == null)
            {
                strReturnDataList.add("mPreviewCaptureRequestBuilder is NULL. SET_CAPTURE_REQUEST_SETTINGS must be performed first using CAPTURE_REQUEST_TYPE=PREVIEW_WINDOW.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if(mCaptureSession != null)
            {
                //Use Synchronized to ensure mCameraDeviceInUse is set before continuing on
                synchronized (mCaptureSessionLockObject)
                {
                    try
                    {
                        dbgLog(TAG, "Waiting for mCaptureSession to close", 'i');
                        mCaptureSession.stopRepeating();
                        mCaptureSession.close();
                        mCaptureSessionLockObject.wait(1000L);
                        dbgLog(TAG, "mCaptureSession has been closed", 'i');
                    }
                    catch (Exception e)
                    {

                    }
                }
            }

            mImageCaptureState= IMAGE_CAPTURE_STATE_PREVIEW;

            try
            {
                dbgLog(TAG, "Creating capture session.", 'i');
                mCameraDeviceInUse.createCaptureSession(Arrays.asList(mPreviewSurface),
                        new CameraCaptureSession.StateCallback()
                        {
                            @Override
                            public void onActive(CameraCaptureSession session)
                            {
                                dbgLog(TAG, "Camera session onActive called.", 'i');
                            }

                            @Override
                            public void onClosed(CameraCaptureSession session)
                            {
                                synchronized (mCaptureSessionLockObject)
                                {
                                    dbgLog(TAG, "Camera session onClosed called.", 'i');
                                    mCaptureSession = null;
                                    mCaptureSessionLockObject.notifyAll();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                            {
                                dbgLog(TAG, "Camera session onConfigureFailed called.", 'i');
                            }

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession)
                            {
                                dbgLog(TAG, "Camera session onConfigured called.", 'i');

                                // The camera is already closed
                                if (null == mCameraDeviceInUse)
                                {
                                    return;
                                }

                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                                try
                                {
                                    dbgLog(TAG, "Camera session setRepeatingRequest.", 'i');
                                    mPreviewCaptureRequestBuilder.addTarget(mPreviewSurface);
                                    if(mLogCaptureResults)
                                    {
                                        mCaptureSession.capture(mPreviewCaptureRequestBuilder.build(), mImageCaptureCallback, mCameraHandler);
                                    }
                                    else
                                    {
                                        mCaptureSession.setRepeatingRequest(mPreviewCaptureRequestBuilder.build(), null, mCameraHandler);
                                    }
                                }
                                catch (CameraAccessException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onReady(CameraCaptureSession session)
                            {
                                dbgLog(TAG, "Camera session onReady called.", 'i');
                            }

                            @Override
                            public void onSurfacePrepared(CameraCaptureSession session, Surface surface)
                            {
                                dbgLog(TAG, "Camera session onSurfacePrepared called.", 'i');
                            }
                        }, mCameraHandler
                );
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception starting viewfinder: " + e.toString(), 'e');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to start viewfinder", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_VIEWFINDER"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if(mCaptureSession == null)
            {
                strReturnDataList.add("mCaptureSession is NULL. START_VIEWFINDER must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            synchronized (mCaptureSessionLockObject)
            {
                try
                {
                    dbgLog(TAG, "Waiting for mCaptureSession to close", 'i');
                    mCaptureSession.stopRepeating();
                    mCaptureSession.close();
                    mCaptureSessionLockObject.wait(1000L);
                    dbgLog(TAG, "mCaptureSession has been closed", 'i');
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Exception stoping viewfinder: " + e.toString(), 'e');

                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to stop viewfinder", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TAKE_PICTURE"))
        {
            long takePictureTimeout = 10000;
            boolean sendImage = false;
            boolean saveLocal = false;
            boolean doImageAnalysis = false;
            mLogCaptureResults = false;
            mImageCaptureState = IMAGE_CAPTURE_STATE_PREVIEW;

            // initialize BinaryWaitForAck variable to no wait (false)
            BinaryWaitForAck = false;

            //Delete any previous image before taking a new picture
            mImageData = null;

            // tell pc that phone can handle BINARY_ACK message
            // list for returned data
            List<String> strDataList = new ArrayList<String>();

            // add data to return data list
            strDataList.add("PHONE_ACCEPTS_BINARY_ACK");

            // send return data list to comm server
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            List<String> strReturnDataList = new ArrayList<String>();

            // list of image analysis needed to be done
            List<String> listImageAnalysis = new ArrayList<String>();
            // Camera to auto focus and then take a picture
            // if no auto focus, then the camera will take picture immediately

            // check for received data
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    if (keyValuePair.toUpperCase().contains("PC_SENDS_BINARY_ACK"))
                    {
                        BinaryWaitForAck = true;
                    }
                    else
                    {
                        String splitResult[] = splitKeyValuePair(keyValuePair);
                        String key = splitResult[0];
                        String value = null;

                        if (splitResult.length > 1)
                        {
                            value = splitResult[1];
                        }

                        if (key.equalsIgnoreCase("SEND_FILE"))
                        {
                            if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                            {
                                sendImage = true;
                            }
                            else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                            {
                                sendImage = false;
                            }
                            else
                            {
                                strReturnDataList.add("UNKNOWN SEND_FILE: " + value);
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                            }
                        }
                        else if (key.equalsIgnoreCase("IMAGE_ANALYSIS_OPTIONS"))
                        {
                            doImageAnalysis = true;

                            if (!ImageAnalysis.checkImageAnalysisOption(value))
                            {
                                List<String> strErrMsgList = new ArrayList<String>();
                                strErrMsgList.add(String.format("Activity '%s' failed - ImageAnalysisOptions '%s' not valid", TAG, splitResult[1]));
                                dbgLog(TAG, strErrMsgList.get(0), 'i');
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                            }

                            String[] analysis = value.split(",");

                            for (String analysisCode : analysis)
                            {
                                listImageAnalysis.add(analysisCode);
                            }
                        }
                        else if (key.equalsIgnoreCase("TIMEOUT"))
                        {
                            takePictureTimeout = Long.parseLong(value);
                        }
                        else if (key.equalsIgnoreCase("LOG_CAPTURE_RESULTS"))
                        {
                            if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                            {
                                mLogCaptureResults = true;
                            }
                            else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                            {
                                mLogCaptureResults = false;
                            }
                            else
                            {
                                strReturnDataList.add("UNKNOWN LOG_CAPTURE_RESULTS: " + value);
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                            }
                        }
                        else if (key.equalsIgnoreCase("PREVIEW_TYPE"))
                        {
                            mTakePicturePreviewSurfaceType = value;
                        }
                        else if (key.equalsIgnoreCase("CAPTURE_SURFACE_TYPE"))
                        {
                            mTakePictureCaptureSurfaceType = value;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                }
            }
            // check to see if we have a file name
            if (mPath != null)
            {
                saveLocal = true;
            }

            if (mCameraDeviceInUse == null)
            {
                strReturnDataList.add("mCameraDeviceInUse is NULL. OPEN_CAMERA must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (mImageCaptureRequestBuilder == null)
            {
                strReturnDataList.add("mImageCaptureRequestBuilder is NULL. SET_CAPTURE_REQUEST_SETTINGS must be performed first using CAPTURE_REQUEST_TYPE=IMAGE_CAPTURE.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (mPreviewCaptureRequestBuilder == null)
            {
                strReturnDataList.add("mPreviewCaptureRequestBuilder is NULL. SET_CAPTURE_REQUEST_SETTINGS must be performed first using CAPTURE_REQUEST_TYPE=PREVIEW.");
                strReturnDataList.add("mPreviewCaptureRequestBuilder is used for Auto Focus, Auto Exposure, and Auto White Balance.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (mImageReader == null)
            {
                strReturnDataList.add("mImageReader is NULL. SET_IMAGE_READER_SETTINGS must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (mCaptureSession != null)
            {
                //Use Synchronized to ensure mCameraDeviceInUse is set before continuing on
                synchronized (mCaptureSessionLockObject)
                {
                    try
                    {
                        dbgLog(TAG, "Waiting for mCaptureSession to close", 'i');
                        mCaptureSession.stopRepeating();
                        mCaptureSession.close();
                        mCaptureSessionLockObject.wait(1000L);
                        dbgLog(TAG, "mCaptureSession has been closed", 'i');
                    }
                    catch (Exception e)
                    {

                    }
                }
            }

            List<Surface> surfaceList = new ArrayList<Surface>();

            boolean displaySurfaceAdded = false;
            boolean captureImageSurfaceAdded = false;

            switch (mTakePicturePreviewSurfaceType.toUpperCase())
            {
                case "PREVIEW_IMAGE":
                    surfaceList.add(mPreviewImageReader.getSurface());
                    break;
                case "DISPLAY":
                    surfaceList.add(mPreviewSurface);
                    displaySurfaceAdded = true;
                    break;
                case "CAPTURE_IMAGE":
                default:
                    surfaceList.add(mImageReader.getSurface());
                    captureImageSurfaceAdded = true;
                    break;
            }

            switch (mTakePictureCaptureSurfaceType.toUpperCase())
            {
                case "DISPLAY":
                    if(!displaySurfaceAdded)
                    {
                        surfaceList.add(mPreviewSurface);
                    }
                    break;
                case "CAPTURE_IMAGE":
                default:
                    if(!captureImageSurfaceAdded)
                    {
                        surfaceList.add(mImageReader.getSurface());
                    }
                    break;
            }



            try
            {
                dbgLog(TAG, "Creating capture session.", 'i');
                mCameraDeviceInUse.createCaptureSession(surfaceList,
                        new CameraCaptureSession.StateCallback()
                        {
                            @Override
                            public void onActive(CameraCaptureSession session)
                            {
                                dbgLog(TAG, "Camera session onActive called.", 'i');
                            }

                            @Override
                            public void onClosed(CameraCaptureSession session)
                            {
                                synchronized (mCaptureSessionLockObject)
                                {
                                    dbgLog(TAG, "Camera session onClosed called.", 'i');
                                    mCaptureSession = null;
                                    mCaptureSessionLockObject.notifyAll();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                            {
                                dbgLog(TAG, "Camera session onConfigureFailed called.", 'i');
                            }

                            @Override
                            public void onConfigured(CameraCaptureSession cameraCaptureSession)
                            {
                                dbgLog(TAG, "Camera session onConfigured called.", 'i');
                                // The camera is already closed
                                if (null == mCameraDeviceInUse)
                                {
                                    return;
                                }

                                mCaptureSession = cameraCaptureSession;

                                //Setup Targets
                                switch (mTakePicturePreviewSurfaceType.toUpperCase())
                                {
                                    case "PREVIEW_IMAGE":
                                        mPreviewCaptureRequestBuilder.addTarget(mPreviewImageReader.getSurface());
                                        break;
                                    case "DISPLAY":
                                        mPreviewCaptureRequestBuilder.addTarget(mPreviewSurface);
                                        break;
                                    case "CAPTURE_IMAGE":
                                    default:
                                        //Default to "CAPTURE_IMAGE" for backwards compatibility
                                        mPreviewCaptureRequestBuilder.addTarget(mImageReader.getSurface());
                                        break;
                                }

                                switch (mTakePictureCaptureSurfaceType.toUpperCase())
                                {
                                    case "DISPLAY":
                                        mImageCaptureRequestBuilder.addTarget(mPreviewSurface);
                                        break;
                                    case "CAPTURE_IMAGE":
                                    default:
                                        mImageCaptureRequestBuilder.addTarget(mImageReader.getSurface());
                                        break;
                                }

                                //Start with IMAGE_CAPTURE_STATE_AF_LOCK_WAIT state
                                handleNextCameraCaptureState(IMAGE_CAPTURE_STATE_AF_LOCK_WAIT);
                            }

                            @Override
                            public void onReady(CameraCaptureSession session)
                            {
                                dbgLog(TAG, "Camera session onReady called.", 'i');
                            }

                            @Override
                            public void onSurfacePrepared(CameraCaptureSession session, Surface surface)
                            {
                                dbgLog(TAG, "Camera session onSurfacePrepared called.", 'i');
                            }
                        }, mCameraHandler
                );
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception taking picture: " + e.toString(), 'e');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to take picture", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            synchronized (mImageDataLockObject)
            {
                try
                {
                    dbgLog(TAG, "Waiting for image capture to complete", 'i');
                    mImageDataLockObject.wait(takePictureTimeout);
                    dbgLog(TAG, "Image Capture has been completed", 'i');
                }
                catch (Exception e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to Take Picture in " + takePictureTimeout + " milliseconds", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }

            //Close capture session after capture passed or failed
            if (mCaptureSession != null)
            {
                //Use Synchronized to ensure mCameraDeviceInUse is set before continuing on
                synchronized (mCaptureSessionLockObject)
                {
                    try
                    {
                        dbgLog(TAG, "Waiting for mCaptureSession to close", 'i');
                        mCaptureSession.stopRepeating();
                        mCaptureSession.close();
                        mCaptureSessionLockObject.wait(1000L);
                        dbgLog(TAG, "mCaptureSession has been closed", 'i');
                    }
                    catch (Exception e)
                    {

                    }
                }
            }

            if(mImageData == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to Take Picture in " + takePictureTimeout + " milliseconds", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (saveLocal)
            {
                FileOutputStream outStream = null;

                try
                {
                    // create that contains path and filename
                    String imagePath = santizePath(mPath);

                    // get directory name where file will be stored (returns null if failed)
                    File directory = new File(imagePath).getParentFile();

                    if (directory == null)
                    {
                        throw new FileNotFoundException("Path to file was not determined");
                    }

                    // check to see if directory does not exists and directory was not created
                    if (!directory.exists() && !directory.mkdirs())
                    {
                        throw new IOException("Path to file could not be created.");
                    }

                    // create new handle to desired path and file
                    File imageFile = new File(imagePath);

                    // check to see if file already exists
                    if (imageFile.exists())
                    {
                        // if file exist, delete current file
                        boolean delStatus = imageFile.delete();
                        if (!delStatus)
                        {
                            dbgLog(TAG, "onPictureTaken cannot delete file: " + imagePath, 'd');

                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' failed to delete existing file.", TAG));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                    }

                    outStream = new FileOutputStream(imagePath);

                    outStream.write(mImageData);

                    dbgLog(TAG, "onImageAvailable - wrote bytes: " + mImageData.length, 'd');

                    FileDescriptor outFileFD = outStream.getFD();
                    outStream.flush();
                    outFileFD.sync();
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Exception saving picture: " + e.toString(), 'e');

                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' failed to save image.", TAG));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                finally
                {
                    try
                    {
                        if (outStream != null)
                        {

                            outStream.close();
                        }
                    }
                    catch (IOException e)
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' failed to close outStream.", TAG));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
            }

            if (doImageAnalysis)
            {
                Bitmap imageUnderTest = null;

                // setup string list for results from analysis
                List<String> analysisResult = new ArrayList<String>();

                // load bitmap from jpeg data image
                imageUnderTest = LoadBitMap.loadImageFromByte(mImageData);

                if (imageUnderTest == null)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' failed to convert camera picture to bitmap", TAG));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                else
                {
                    for (String analysisCode : listImageAnalysis)
                    {
                        // avgLum is for backwards capability
                        if (analysisCode.equalsIgnoreCase("avgLum") || analysisCode.equalsIgnoreCase("CalcImageLuminance"))
                        {
                            analysisResult.addAll(ImageAnalysis.calcImageLuminance(imageUnderTest));
                        }
                        // add more analysis calls when available here
                    }
                }

                // return data list
                CommServerDataPacket infoPacket2 = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, analysisResult);
                sendInfoPacketToCommServer(infoPacket2);
            }
            if (sendImage)
            {
                // list for data size information
                strDataList.clear();

                // get data size
                int dataSize = mImageData.length;
                strDataList.add(String.format("FILE_SIZE=%d", dataSize));

                // send binary data to commserver
                CommServerBinaryPacket binaryPacket = new CommServerBinaryPacket(nRxSeqTag, strRxCmd, TAG, strDataList, mImageData);
                sendBinaryPacketToCommServer(binaryPacket);
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_TOTAL_CAPTURE_RESULTS"))
        {
            List<String> strDataList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();

            if(mTotalCaptureResultList != null)
            {
                strDataList.addAll(mTotalCaptureResultList);
            }
            else
            {
                strReturnDataList.add("mTotalCaptureResultList is NULL. TAKE_PICTURE must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }


            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send PASS result to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_IMAGE_PATH"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            // check for received data
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("IMAGE_PATH"))
                    {
                        if (value.equalsIgnoreCase("NULL"))
                        {
                            mPath = null;
                        }
                        else
                        {
                            mPath = value;
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }

            // Generate an exception to send PASS result to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_IMAGE_PATH"))
        {
            List<String> strDataList = new ArrayList<String>();

            if(mPath == null)
            {
                strDataList.add("IMAGE_PATH=NULL");
            }
            else
            {
                strDataList.add("IMAGE_PATH=" + mPath);
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send PASS result to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_CAPTURE_REQUEST_SETTINGS"))
        {
            String captureRequestType = "";
            CaptureRequest.Builder captureRequestBuilder = null;
            List<String> strReturnDataList = new ArrayList<String>();

            if(mCameraDeviceInUse == null)
            {
                strReturnDataList.add("mCameraDeviceInUse is NULL. OPEN_CAMERA must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("CAPTURE_REQUEST_TYPE"))
                    {
                        captureRequestType = value.toUpperCase();
                    }
                }

                if(!captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW") && !captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                {
                    strReturnDataList.add("CAPTURE_REQUEST_TYPE MUST BE SET TO EITHER PREVIEW_WINDOW or IMAGE_CAPTURE.");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                try
                {
                    if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                    {
                        mPreviewCaptureRequestBuilder = createCaptureRequestBuilder(mPreviewCaptureRequestBuilder, strRxCmdDataList);
                    }
                    else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                    {
                        mImageCaptureRequestBuilder = createCaptureRequestBuilder(mImageCaptureRequestBuilder, strRxCmdDataList);
                    }
                }
                catch (Exception e)
                {
                    throw e;
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_CAPTURE_REQUEST_FACTORY_SETTING"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            String captureRequestType = "";
            String captureRequestKey = "";
            String captureRequestValue = "";
            String captureRequestClass = "";

            if (strRxCmdDataList.size() == 4)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("CAPTURE_REQUEST_TYPE"))
                    {
                        captureRequestType = value.toUpperCase();
                    }
                    else if (key.equalsIgnoreCase("CAPTURE_REQUEST_KEY"))
                    {
                        captureRequestKey = value;
                    }
                    else if (key.equalsIgnoreCase("CAPTURE_REQUEST_VALUE"))
                    {
                        captureRequestValue = value;
                    }
                    else if (key.equalsIgnoreCase("CAPTURE_REQUEST_CLASS"))
                    {
                        captureRequestClass = value;
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                if (!captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW") && !captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                {
                    strReturnDataList.add("CAPTURE_REQUEST_TYPE MUST BE SET TO EITHER PREVIEW_WINDOW or IMAGE_CAPTURE.");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                {
                    if(mPreviewCaptureRequestBuilder == null)
                    {
                        strReturnDataList.add("PREVIEW CAPTURE REQUEST IS NULL. OPEN_CAMERA and SET_CAPTURE_REQUEST_SETTINGS must be performed first.");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
                else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                {
                    if(mImageCaptureRequestBuilder == null)
                    {
                        strReturnDataList.add("IMAGE_CAPTURE CAPTURE REQUEST IS NULL. OPEN_CAMERA and SET_CAPTURE_REQUEST_SETTINGS must be performed first.");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                try
                {
                    Constructor constructorClass = CaptureRequest.Key.class.getDeclaredConstructor(String.class, Class.class);
                    Constructor constructorTypeReference = CaptureRequest.Key.class.getDeclaredConstructor(String.class, TypeReference.class);

                    if(captureRequestClass.equalsIgnoreCase("BYTE"))
                    {
                        CaptureRequest.Key<Byte> key = (CaptureRequest.Key<Byte>) constructorClass.newInstance(captureRequestKey, byte.class);
                        byte setValue = Byte.parseByte(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("BYTE_ARRAY"))
                    {
                        CaptureRequest.Key<byte[]> key = (CaptureRequest.Key<byte[]>) constructorClass.newInstance(captureRequestKey, byte[].class);
                        byte[] setValue = hexStringToByteArray(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                           mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                           mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("BOOLEAN"))
                    {
                        CaptureRequest.Key<Boolean> key = (CaptureRequest.Key<Boolean>) constructorClass.newInstance(captureRequestKey, boolean.class);

                        boolean setValue = false;

                        if (captureRequestValue.equalsIgnoreCase("TRUE") || captureRequestValue.equalsIgnoreCase("YES"))
                        {
                            setValue = true;
                        }
                        else if (captureRequestValue.equalsIgnoreCase("FALSE") || captureRequestValue.equalsIgnoreCase("NO"))
                        {
                            setValue = false;;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN BOOLEAN VALUE: " + captureRequestValue);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("INT"))
                    {
                        CaptureRequest.Key<Integer> key = (CaptureRequest.Key<Integer>) constructorClass.newInstance(captureRequestKey, int.class);
                        int setValue = Integer.parseInt(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("INT_ARRAY"))
                    {
                        CaptureRequest.Key<int[]> key = (CaptureRequest.Key<int[]>) constructorClass.newInstance(captureRequestKey, int[].class);

                        String[] intArrayValues = captureRequestValue.split(",");

                        List<Integer> intArrayValueList = new ArrayList<Integer>();

                        for(String intArrayValueString : intArrayValues)
                        {
                            intArrayValueList.add(Integer.parseInt(intArrayValueString));
                        }

                        Integer[] integerArray = (Integer[]) intArrayValueList.toArray(new Integer[intArrayValueList.size()]);

                        int[] setValue = new int[integerArray.length];
                        int index = 0;
                        for(Integer integerValue : integerArray)
                        {
                            setValue[index++] = integerValue.intValue();
                        }

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("DOUBLE"))
                    {
                        CaptureRequest.Key<Double> key = (CaptureRequest.Key<Double>) constructorClass.newInstance(captureRequestKey, double.class);
                        double setValue = Double.parseDouble(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("DOUBLE_ARRAY"))
                    {
                        CaptureRequest.Key<double[]> key = (CaptureRequest.Key<double[]>) constructorClass.newInstance(captureRequestKey, double[].class);

                        String[] doubleArrayValues = captureRequestValue.split(",");

                        List<Double> doubleArrayValueList = new ArrayList<Double>();

                        for(String doubleArrayValueString : doubleArrayValues)
                        {
                            doubleArrayValueList.add(Double.parseDouble(doubleArrayValueString));
                        }

                        Double[] doubleArray = (Double[]) doubleArrayValueList.toArray(new Double[doubleArrayValueList.size()]);

                        double[] setValue = new double[doubleArray.length];
                        int index = 0;
                        for(Double doubleValue : doubleArray)
                        {
                            setValue[index++] = doubleValue.intValue();
                        }

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("RGGBCHANNELVECTOR"))
                    {
                        CaptureRequest.Key<RggbChannelVector> key = (CaptureRequest.Key<RggbChannelVector>) constructorClass.newInstance(captureRequestKey, RggbChannelVector.class);

                        RggbChannelVector setValue = convertStringToRggbChannelVector(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("COLORSPACETRANSFORM"))
                    {
                        CaptureRequest.Key<ColorSpaceTransform> key = (CaptureRequest.Key<ColorSpaceTransform>) constructorClass.newInstance(captureRequestKey, ColorSpaceTransform.class);

                        ColorSpaceTransform setValue = convertStringToColorSpaceTransform(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("METERINGRECTANGLE_ARRAY"))
                    {
                        CaptureRequest.Key<MeteringRectangle[]> key = (CaptureRequest.Key<MeteringRectangle[]>) constructorClass.newInstance(captureRequestKey, MeteringRectangle[].class);

                        String[] meteringRectangleValues = captureRequestValue.split("\\),");

                        List<MeteringRectangle> meteringRectangleList = new ArrayList<MeteringRectangle>();

                        for(String meteringRectangleValue : meteringRectangleValues)
                        {
                            meteringRectangleList.add(convertStringToMeteringRectangle(meteringRectangleValue));
                        }

                        MeteringRectangle[] setValue = (MeteringRectangle[]) meteringRectangleList.toArray(new MeteringRectangle[meteringRectangleList.size()]);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("RANGE_INTEGER"))
                    {
                        CaptureRequest.Key<Range<Integer>> key = (CaptureRequest.Key<Range<Integer>>) constructorTypeReference.newInstance(captureRequestKey, new TypeReference<Range<Integer>>() {{ }});

                        Range<Integer> setValue = convertStringToRange(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("SIZE"))
                    {
                        CaptureRequest.Key<Size> key = (CaptureRequest.Key<Size>) constructorClass.newInstance(captureRequestKey, Size.class);
                        Size setValue = Size.parseSize(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("FLOAT"))
                    {
                        CaptureRequest.Key<Float> key = (CaptureRequest.Key<Float>) constructorClass.newInstance(captureRequestKey, float.class);
                        float setValue = Float.parseFloat(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("RECT"))
                    {
                        CaptureRequest.Key<Rect> key = (CaptureRequest.Key<Rect>) constructorClass.newInstance(captureRequestKey, Rect.class);
                        Rect setValue = convertStringToRect(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("LONG"))
                    {
                        CaptureRequest.Key<Long> key = (CaptureRequest.Key<Long>) constructorClass.newInstance(captureRequestKey, long.class);
                        long setValue = Long.parseLong(captureRequestValue);

                        if (captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else if(captureRequestClass.equalsIgnoreCase("TONEMAPCURVE"))
                    {
                        CaptureRequest.Key<TonemapCurve> key = (CaptureRequest.Key<TonemapCurve>) constructorClass.newInstance(captureRequestKey, TonemapCurve.class);
                        TonemapCurve setValue = convertStringToTonemapCurve(captureRequestValue);

                        if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
                        {
                            mPreviewCaptureRequestBuilder.set(key, setValue);
                        }
                        else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
                        {
                            mImageCaptureRequestBuilder.set(key, setValue);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN CAPTURE_REQUEST_CLASS.");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Exception sending factory command: " + e.toString(), 'e');

                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to send factory command", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else
            {
                strReturnDataList.add("Incorrect number of tokens. Must have CAPTURE_REQUEST_TYPE, CAPTURE_REQUEST_KEY, CAPTURE_REQUEST_VALUE, and CAPTURE_REQUEST_CLASS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CAPTURE_REQUEST_SETTINGS"))
        {
            String captureRequestType = "";
            List<String> strDataList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();
            CaptureRequest captureRequest = null;

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("CAPTURE_REQUEST_TYPE"))
                    {
                        captureRequestType = value.toUpperCase();
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }

            if (!captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW") && !captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
            {
                strReturnDataList.add("CAPTURE_REQUEST_TYPE MUST BE SET TO EITHER PREVIEW_WINDOW or IMAGE_CAPTURE.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if(captureRequestType.equalsIgnoreCase("PREVIEW_WINDOW"))
            {
                if(mPreviewCaptureRequestBuilder != null)
                {
                    captureRequest = mPreviewCaptureRequestBuilder.build();
                }
                else
                {
                    strReturnDataList.add("PREVIEW CAPTURE REQUEST IS NULL. OPEN_CAMERA and SET_CAPTURE_REQUEST_SETTINGS must be performed first.");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }
            else if (captureRequestType.equalsIgnoreCase("IMAGE_CAPTURE"))
            {
                if(mImageCaptureRequestBuilder != null)
                {
                    captureRequest = mImageCaptureRequestBuilder.build();
                }
                else
                {
                    strReturnDataList.add("IMAGE_CAPTURE CAPTURE REQUEST IS NULL. OPEN_CAMERA and SET_CAPTURE_REQUEST_SETTINGS must be performed first.");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }

            List<CaptureRequest.Key<?>> captureRequestKeys = captureRequest.getKeys();

            for (CaptureRequest.Key<?> captureRequestKey : captureRequestKeys)
            {
                Object captureRequestValue = null;

                captureRequestValue = captureRequest.get(captureRequestKey);

                if (captureRequestKey == CaptureRequest.BLACK_LEVEL_LOCK)
                {
                    Boolean value = (Boolean) captureRequestValue;
                    strDataList.add("BLACK_LEVEL_LOCK=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("COLOR_CORRECTION_ABERRATION_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.COLOR_CORRECTION_GAINS)
                {
                    RggbChannelVector value = (RggbChannelVector) captureRequestValue;
                    strDataList.add("COLOR_CORRECTION_GAINS=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.COLOR_CORRECTION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    {
                        returnStringValue = "TRANSFORM_MATRIX";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("COLOR_CORRECTION_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.COLOR_CORRECTION_TRANSFORM)
                {
                    ColorSpaceTransform value = (ColorSpaceTransform) captureRequestValue;
                    strDataList.add("COLOR_CORRECTION_TRANSFORM=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_ANTIBANDING_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
                    {
                        returnStringValue = "50HZ";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ)
                    {
                        returnStringValue = "60HZ";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AE_ANTIBANDING_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)
                {
                    Integer value = (Integer) captureRequestValue;
                    strDataList.add("CONTROL_AE_EXPOSURE_COMPENSATION=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_LOCK)
                {
                    Boolean value = (Boolean) captureRequestValue;
                    strDataList.add("CONTROL_AE_LOCK=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    {
                        returnStringValue = "ON_ALWAYS_FLASH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    {
                        returnStringValue = "ON_AUTO_FLASH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
                    {
                        returnStringValue = "ON_AUTO_FLASH_REDEYE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AE_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
                    {
                        returnStringValue = "CANCEL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
                    {
                        returnStringValue = "IDLE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    {
                        returnStringValue = "START";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AE_PRECAPTURE_TRIGGER=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureRequestValue;
                    for(MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add("CONTROL_AE_REGIONS=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
                {
                    Range<Integer> value = (Range<Integer>) captureRequestValue;
                    strDataList.add("CONTROL_AE_TARGET_FPS_RANGE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AF_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    {
                        returnStringValue = "CONTINUOUS_PICTURE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    {
                        returnStringValue = "CONTINUOUS_VIDEO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_EDOF)
                    {
                        returnStringValue = "EDOF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_MACRO)
                    {
                        returnStringValue = "MACRO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AF_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AF_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureRequestValue;
                    for(MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add("CONTROL_AF_REGIONS=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AF_TRIGGER)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                    {
                        returnStringValue = "CANCEL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                    {
                        returnStringValue = "IDLE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_START)
                    {
                        returnStringValue = "START";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AF_TRIGGER=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AWB_LOCK)
                {
                    Boolean value = (Boolean) captureRequestValue;
                    strDataList.add("CONTROL_AWB_LOCK=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AWB_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                    {
                        returnStringValue = "CLOUDY_DAYLIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                    {
                        returnStringValue = "DAYLIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
                    {
                        returnStringValue = "FLUORESCENT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
                    {
                        returnStringValue = "INCANDESCENT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_SHADE)
                    {
                        returnStringValue = "SHADE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_TWILIGHT)
                    {
                        returnStringValue = "TWILIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT)
                    {
                        returnStringValue = "WARM_FLUORESCENT";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_AWB_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_AWB_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureRequestValue;
                    for(MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add("CONTROL_AWB_REGIONS=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_CAPTURE_INTENT)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_CUSTOM)
                    {
                        returnStringValue = "CUSTOM";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL)
                    {
                        returnStringValue = "MANUAL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW)
                    {
                        returnStringValue = "PREVIEW";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                    {
                        returnStringValue = "STILL_CAPTURE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD)
                    {
                        returnStringValue = "VIDEO_RECORD";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT)
                    {
                        returnStringValue = "VIDEO_SNAPSHOT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_CAPTURE_INTENT=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_EFFECT_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_AQUA)
                    {
                        returnStringValue = "AQUA";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)
                    {
                        returnStringValue = "BLACKBOARD";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_MONO)
                    {
                        returnStringValue = "MONO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)
                    {
                        returnStringValue = "NEGATIVE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)
                    {
                        returnStringValue = "POSTERIZE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                    {
                        returnStringValue = "SEPIA";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)
                    {
                        returnStringValue = "SOLARIZE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD)
                    {
                        returnStringValue = "WHITEBOARD";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_EFFECT_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_OFF_KEEP_STATE)
                    {
                        returnStringValue = "OFF_KEEP_STATE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                    {
                        returnStringValue = "USE_SCENE_MODE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_SCENE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_ACTION)
                    {
                        returnStringValue = "ACTION";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_BARCODE)
                    {
                        returnStringValue = "BARCODE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_BEACH)
                    {
                        returnStringValue = "BEACH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT)
                    {
                        returnStringValue = "CANDLELIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
                    {
                        returnStringValue = "DISABLED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
                    {
                        returnStringValue = "FACE_PRIORITY";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS)
                    {
                        returnStringValue = "FIREWORKS";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_HDR)
                    {
                        returnStringValue = "HDR";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO)
                    {
                        returnStringValue = "HIGH_SPEED_VIDEO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE)
                    {
                        returnStringValue = "LANDSCAPE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_NIGHT)
                    {
                        returnStringValue = "NIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT)
                    {
                        returnStringValue = "NIGHT_PORTRAIT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_PARTY)
                    {
                        returnStringValue = "PARTY";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT)
                    {
                        returnStringValue = "PORTRAIT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SNOW)
                    {
                        returnStringValue = "SNOW";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SPORTS)
                    {
                        returnStringValue = "SPORTS";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO)
                    {
                        returnStringValue = "STEADYPHOTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SUNSET)
                    {
                        returnStringValue = "SUNSET";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_THEATRE)
                    {
                        returnStringValue = "THEATRE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_SCENE_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("CONTROL_VIDEO_STABILIZATION_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.EDGE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.EDGE_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add("EDGE_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.FLASH_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.FLASH_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_MODE_SINGLE)
                    {
                        returnStringValue = "SINGLE";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_MODE_TORCH)
                    {
                        returnStringValue = "TORCH";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("FLASH_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.HOT_PIXEL_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("HOT_PIXEL_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.JPEG_GPS_LOCATION)
                {
                    Location value = (Location) captureRequestValue;

                    strDataList.add("JPEG_GPS_LOCATION=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.JPEG_ORIENTATION)
                {
                    Integer value = (Integer) captureRequestValue;

                    strDataList.add("JPEG_ORIENTATION=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.JPEG_QUALITY)
                {
                    Byte value = (Byte) captureRequestValue;

                    strDataList.add("JPEG_QUALITY=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.JPEG_THUMBNAIL_QUALITY)
                {
                    Byte value = (Byte) captureRequestValue;

                    strDataList.add("JPEG_THUMBNAIL_QUALITY=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.JPEG_THUMBNAIL_SIZE)
                {
                    Size value = (Size) captureRequestValue;

                    strDataList.add("JPEG_THUMBNAIL_SIZE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.LENS_APERTURE)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("LENS_APERTURE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.LENS_FILTER_DENSITY)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("LENS_FILTER_DENSITY=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.LENS_FOCAL_LENGTH)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("LENS_FOCAL_LENGTH=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.LENS_FOCUS_DISTANCE)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("LENS_FOCUS_DISTANCE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("LENS_OPTICAL_STABILIZATION_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.NOISE_REDUCTION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)
                    {
                        returnStringValue = "MINIMAL";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("NOISE_REDUCTION_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("REPROCESS_EFFECTIVE_EXPOSURE_FACTOR=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.SCALER_CROP_REGION)
                {
                    Rect value = (Rect) captureRequestValue;

                    strDataList.add("SCALER_CROP_REGION=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.SENSOR_EXPOSURE_TIME)
                {
                    Long value = (Long) captureRequestValue;

                    strDataList.add("SENSOR_EXPOSURE_TIME=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.SENSOR_FRAME_DURATION)
                {
                    Long value = (Long) captureRequestValue;

                    strDataList.add("SENSOR_FRAME_DURATION=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.SENSOR_SENSITIVITY)
                {
                    Integer value = (Integer) captureRequestValue;

                    strDataList.add("SENSOR_SENSITIVITY=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.SENSOR_TEST_PATTERN_DATA)
                {
                    String returnStringValue = "";
                    int[] valueArray = (int[]) captureRequestValue;
                    for(Integer value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add("SENSOR_TEST_PATTERN_DATA=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.SENSOR_TEST_PATTERN_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS)
                    {
                        returnStringValue = "COLOR_BARS";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY)
                    {
                        returnStringValue = "COLOR_BARS_FADE_TO_GRAY";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_CUSTOM1)
                    {
                        returnStringValue = "CUSTOM1";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_PN9)
                    {
                        returnStringValue = "PN9";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR)
                    {
                        returnStringValue = "SOLID_COLOR";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("SENSOR_TEST_PATTERN_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.SHADING_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.SHADING_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.SHADING_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.SHADING_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("SHADING_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.STATISTICS_FACE_DETECT_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                    {
                        returnStringValue = "FULL";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    {
                        returnStringValue = "SIMPLE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("STATISTICS_FACE_DETECT_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE)
                {
                    Boolean value = (Boolean) captureRequestValue;

                    strDataList.add("STATISTICS_HOT_PIXEL_MAP_MODE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("STATISTICS_LENS_SHADING_MAP_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.TONEMAP_CURVE)
                {
                    TonemapCurve value = (TonemapCurve) captureRequestValue;

                    strDataList.add("TONEMAP_CURVE=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.TONEMAP_GAMMA)
                {
                    Float value = (Float) captureRequestValue;

                    strDataList.add("TONEMAP_GAMMA=" + value.toString());
                }
                else if (captureRequestKey == CaptureRequest.TONEMAP_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                    {
                        returnStringValue = "CONTRAST_CURVE";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_GAMMA_VALUE)
                    {
                        returnStringValue = "GAMMA_VALUE";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_PRESET_CURVE)
                    {
                        returnStringValue = "PRESET_CURVE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("TONEMAP_MODE=" + returnStringValue);
                }
                else if (captureRequestKey == CaptureRequest.TONEMAP_PRESET_CURVE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureRequestValue;

                    if (value.intValue() == CameraMetadata.TONEMAP_PRESET_CURVE_REC709)
                    {
                        returnStringValue = "REC709";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_PRESET_CURVE_SRGB)
                    {
                        returnStringValue = "SRGB";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add("TONEMAP_PRESET_CURVE=" + returnStringValue);
                }
                else
                {
                    if (captureRequestValue != null)
                    {
                        Class captureRequestClass = captureRequestValue.getClass();

                        if (captureRequestClass.equals(Byte.class))
                        {
                            Byte value = (Byte) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(byte[].class))
                        {
                            String returnStringValue = "";
                            byte[] valueArray = (byte[]) captureRequestValue;

                            for (byte value : valueArray)
                            {
                                returnStringValue += String.format("%02x", value);
                            }

                            strDataList.add(captureRequestKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureRequestClass.equals(Boolean.class))
                        {
                            Boolean value = (Boolean) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(Integer.class))
                        {
                            Integer value = (Integer) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(int[].class))
                        {
                            String returnStringValue = "";
                            int[] valueArray = (int[]) captureRequestValue;
                            for (int value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(captureRequestKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureRequestClass.equals(Double.class))
                        {
                            Double value = (Double) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(double[].class))
                        {
                            String returnStringValue = "";
                            double[] valueArray = (double[]) captureRequestValue;
                            for (double value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(captureRequestKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureRequestClass.equals(RggbChannelVector.class))
                        {
                            RggbChannelVector value = (RggbChannelVector) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(ColorSpaceTransform.class))
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(MeteringRectangle[].class))
                        {
                            String returnStringValue = "";
                            MeteringRectangle[] valueArray = (MeteringRectangle[]) captureRequestValue;
                            for (MeteringRectangle value : valueArray)
                            {
                                returnStringValue = returnStringValue + value.toString() + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(captureRequestKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureRequestClass.equals(Size.class))
                        {
                            Size value = (Size) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(Float.class))
                        {
                            Float value = (Float) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(float[].class))
                        {
                            String returnStringValue = "";
                            float[] valueArray = (float[]) captureRequestValue;
                            for (float value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(captureRequestKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureRequestClass.equals(Rect.class))
                        {
                            Rect value = (Rect) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(Long.class))
                        {
                            Long value = (Long) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else if (captureRequestClass.equals(TonemapCurve.class))
                        {
                            TonemapCurve value = (TonemapCurve) captureRequestValue;
                            strDataList.add(captureRequestKey.getName() + "=" + value.toString());
                        }
                        else
                        {
                            strDataList.add(captureRequestKey.getName() + "=UNKNOWN_CLASS_TYPE:" + captureRequestClass.getName());
                        }
                    }
                    else
                    {
                        strDataList.add(captureRequestKey.getName() + "=NULL");
                    }
                }
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_IMAGE_READER_SETTINGS"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            String imageReaderType = "";

            int imageReaderWidth = 0;
            int imageReaderHeight = 0;
            int imageReaderFormat = 0;

            if (strRxCmdDataList.size() >= 3)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("IMAGE_READER_TYPE"))
                    {
                        imageReaderType = value;
                    }
                    else if (key.equalsIgnoreCase("WIDTH"))
                    {
                        imageReaderWidth = Integer.parseInt(value);
                    }
                    else if (key.equalsIgnoreCase("HEIGHT"))
                    {
                        imageReaderHeight = Integer.parseInt(value);
                    }
                    else if (key.equalsIgnoreCase("FORMAT"))
                    {
                        if (value.equalsIgnoreCase("DEPTH16"))
                        {
                            imageReaderFormat = ImageFormat.DEPTH16;
                        }
                        else if (value.equalsIgnoreCase("DEPTH_POINT_CLOUD"))
                        {
                            imageReaderFormat = ImageFormat.DEPTH_POINT_CLOUD;
                        }
                        else if (value.equalsIgnoreCase("FLEX_RGBA_8888"))
                        {
                            imageReaderFormat = ImageFormat.FLEX_RGBA_8888;
                        }
                        else if (value.equalsIgnoreCase("FLEX_RGB_888"))
                        {
                            imageReaderFormat = ImageFormat.FLEX_RGB_888;
                        }
                        else if (value.equalsIgnoreCase("JPEG"))
                        {
                            imageReaderFormat = ImageFormat.JPEG;
                        }
                        else if (value.equalsIgnoreCase("NV16"))
                        {
                            imageReaderFormat = ImageFormat.NV16;
                        }
                        else if (value.equalsIgnoreCase("NV21"))
                        {
                            imageReaderFormat = ImageFormat.NV21;
                        }
                        else if (value.equalsIgnoreCase("PRIVATE"))
                        {
                            imageReaderFormat = ImageFormat.PRIVATE;
                        }
                        else if (value.equalsIgnoreCase("RAW10"))
                        {
                            imageReaderFormat = ImageFormat.RAW10;
                        }
                        else if (value.equalsIgnoreCase("RAW12"))
                        {
                            imageReaderFormat = ImageFormat.RAW12;
                        }
                        else if (value.equalsIgnoreCase("RAW_SENSOR"))
                        {
                            imageReaderFormat = ImageFormat.RAW_SENSOR;
                        }
                        else if (value.equalsIgnoreCase("IMAGE_FORMAT_RGB_565"))
                        {
                            imageReaderFormat = ImageFormat.RGB_565;
                        }
                        else if (value.equalsIgnoreCase("YUV_420_888"))
                        {
                            imageReaderFormat = ImageFormat.YUV_420_888;
                        }
                        else if (value.equalsIgnoreCase("YUV_422_888"))
                        {
                            imageReaderFormat = ImageFormat.YUV_422_888;
                        }
                        else if (value.equalsIgnoreCase("YUV_444_888"))
                        {
                            imageReaderFormat = ImageFormat.YUV_444_888;
                        }
                        else if (value.equalsIgnoreCase("YUY2"))
                        {
                            imageReaderFormat = ImageFormat.YUY2;
                        }
                        else if (value.equalsIgnoreCase("YV12"))
                        {
                            imageReaderFormat = ImageFormat.YV12;
                        }
                        else if (value.equalsIgnoreCase("A_8"))
                        {
                            imageReaderFormat = PixelFormat.A_8;
                        }
                        else if (value.equalsIgnoreCase("LA_88"))
                        {
                            imageReaderFormat = PixelFormat.LA_88;
                        }
                        else if (value.equalsIgnoreCase("L_8"))
                        {
                            imageReaderFormat= PixelFormat.L_8;
                        }
                        else if (value.equalsIgnoreCase("OPAQUE"))
                        {
                            imageReaderFormat = PixelFormat.OPAQUE;
                        }
                        else if (value.equalsIgnoreCase("RGBA_4444"))
                        {
                            imageReaderFormat = PixelFormat.RGBA_4444;
                        }
                        else if (value.equalsIgnoreCase("RGBA_5551"))
                        {
                            imageReaderFormat = PixelFormat.RGBA_5551;
                        }
                        else if (value.equalsIgnoreCase("RGBA_8888"))
                        {
                            imageReaderFormat = PixelFormat.RGBA_8888;
                        }
                        else if (value.equalsIgnoreCase("RGBX_8888"))
                        {
                            imageReaderFormat = PixelFormat.RGBX_8888;
                        }
                        else if (value.equalsIgnoreCase("RGB_332"))
                        {
                            imageReaderFormat = PixelFormat.RGB_332;
                        }
                        else if (value.equalsIgnoreCase("PIXEL_FORMAT_RGB_565"))
                        {
                            imageReaderFormat = PixelFormat.RGB_565;
                        }
                        else if (value.equalsIgnoreCase("RGB_888"))
                        {
                            imageReaderFormat = PixelFormat.RGB_888;
                        }
                        else if (value.equalsIgnoreCase("TRANSLUCENT"))
                        {
                            imageReaderFormat = PixelFormat.TRANSLUCENT;
                        }
                        else if (value.equalsIgnoreCase("TRANSPARENT"))
                        {
                            imageReaderFormat = PixelFormat.TRANSPARENT;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN VALUE FOR FORMAT: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
            else
            {
                strReturnDataList.add("Incorrect number of tokens. Must have WIDTH, HEIGHT, and FORMAT");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if(imageReaderType.equalsIgnoreCase("PREVIEW"))
            {
                mPreviewImageReader = ImageReader.newInstance(imageReaderWidth, imageReaderHeight, imageReaderFormat, 2);
                mPreviewImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
            }
            else
            {
                //Default to IMAGE_CAPTURE for backwards compatibility
                mImageReader = ImageReader.newInstance(imageReaderWidth, imageReaderHeight, imageReaderFormat, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_IMAGE_READER_SETTINGS"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            List<String> strDataList = new ArrayList<String>();

            if(mImageReader == null && mPreviewImageReader == null)
            {
                strReturnDataList.add("IMAGE READER IS NULL. SET_IMAGE_READER_SETTINGS must be performed first.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            for(int imageReaderCounter = 0; imageReaderCounter < 2; imageReaderCounter++)
            {
                String strImageFormat = "";
                int imageFormat =  PixelFormat.UNKNOWN;

                if (imageReaderCounter == 0 && mImageReader != null)
                {
                    strDataList.add("IMAGE_CAPTURE_WIDTH=" + mImageReader.getWidth());
                    strDataList.add("IMAGE_CAPTURE_HEIGHT=" + mImageReader.getHeight());

                    imageFormat = mImageReader.getImageFormat();
                }
                else if (imageReaderCounter == 1 && mPreviewImageReader != null)
                {
                    strDataList.add("PREVIEW_WIDTH=" + mPreviewImageReader.getWidth());
                    strDataList.add("PREVIEW_HEIGHT=" + mPreviewImageReader.getHeight());

                    imageFormat = mPreviewImageReader.getImageFormat();
                }

                if (imageFormat == ImageFormat.DEPTH16)
                {
                    strImageFormat = "DEPTH16";
                }
                else if (imageFormat == ImageFormat.DEPTH_POINT_CLOUD)
                {
                    strImageFormat = "DEPTH_POINT_CLOUD";
                }
                else if (imageFormat == ImageFormat.FLEX_RGBA_8888)
                {
                    strImageFormat = "FLEX_RGBA_8888";
                }
                else if (imageFormat == ImageFormat.FLEX_RGB_888)
                {
                    strImageFormat = "FLEX_RGB_888";
                }
                else if (imageFormat == ImageFormat.JPEG)
                {
                    strImageFormat = "JPEG";
                }
                else if (imageFormat == ImageFormat.NV16)
                {
                    strImageFormat = "NV16";
                }
                else if (imageFormat == ImageFormat.NV21)
                {
                    strImageFormat = "NV21";
                }
                else if (imageFormat == ImageFormat.PRIVATE)
                {
                    strImageFormat = "PRIVATE";
                }
                else if (imageFormat == ImageFormat.RAW10)
                {
                    strImageFormat = "RAW10";
                }
                else if (imageFormat == ImageFormat.RAW12)
                {
                    strImageFormat = "RAW12";
                }
                else if (imageFormat == ImageFormat.RAW_SENSOR)
                {
                    strImageFormat = "RAW_SENSOR";
                }
                else if (imageFormat == ImageFormat.RGB_565)
                {
                    strImageFormat = "IMAGE_FORMAT_RGB_565";
                }
                else if (imageFormat == ImageFormat.UNKNOWN)
                {
                    strImageFormat = "UNKNOWN";
                }
                else if (imageFormat == ImageFormat.YUV_420_888)
                {
                    strImageFormat = "YUV_420_888";
                }
                else if (imageFormat == ImageFormat.YUV_422_888)
                {
                    strImageFormat = "YUV_422_888";
                }
                else if (imageFormat == ImageFormat.YUV_444_888)
                {
                    strImageFormat = "YUV_444_888";
                }
                else if (imageFormat == ImageFormat.YUY2)
                {
                    strImageFormat = "YUY2";
                }
                else if (imageFormat == ImageFormat.YV12)
                {
                    strImageFormat = "YV12";
                }
                else if (imageFormat == PixelFormat.A_8)
                {
                    strImageFormat = "A_8";
                }
                else if (imageFormat == PixelFormat.LA_88)
                {
                    strImageFormat = "LA_88";
                }
                else if (imageFormat == PixelFormat.L_8)
                {
                    strImageFormat = "L_8";
                }
                else if (imageFormat == PixelFormat.OPAQUE)
                {
                    strImageFormat = "OPAQUE";
                }
                else if (imageFormat == PixelFormat.RGBA_4444)
                {
                    strImageFormat = "RGBA_4444";
                }
                else if (imageFormat == PixelFormat.RGBA_5551)
                {
                    strImageFormat = "RGBA_5551";
                }
                else if (imageFormat == PixelFormat.RGBA_8888)
                {
                    strImageFormat = "RGBA_8888";
                }
                else if (imageFormat == PixelFormat.RGBX_8888)
                {
                    strImageFormat = "RGBX_8888";
                }
                else if (imageFormat == PixelFormat.RGB_332)
                {
                    strImageFormat = "RGB_332";
                }
                else if (imageFormat == PixelFormat.RGB_565)
                {
                    strImageFormat = "PIXEL_FORMAT_RGB_565";
                }
                else if (imageFormat == PixelFormat.RGB_888)
                {
                    strImageFormat = "RGB_888";
                }
                else if (imageFormat == PixelFormat.TRANSLUCENT)
                {
                    strImageFormat = "TRANSLUCENT";
                }
                else if (imageFormat == PixelFormat.TRANSPARENT)
                {
                    strImageFormat = "TRANSPARENT";
                }
                else if (imageFormat == PixelFormat.UNKNOWN)
                {
                    strImageFormat = "UNKNOWN";
                }
                else
                {
                    strImageFormat = "UNKNOWN";
                }


                if (imageReaderCounter == 0 && mImageReader != null)
                {
                    strDataList.add("IMAGE_CAPTURE_FORMAT=" + strImageFormat);
                }
                else if (imageReaderCounter == 1 && mPreviewImageReader != null)
                {
                    strDataList.add("PREVIEW_FORMAT=" + strImageFormat);
                }
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_BRIGHTNESS_LEVEL"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            float brightness = 0;

            if (strRxCmdDataList.size() == 1)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("BRIGHTNESS"))
                    {
                        brightness = Float.parseFloat(value);
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
            else
            {
                strReturnDataList.add("Incorrect number of tokens. Must have BRIGHTNESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            final float passBrightness = brightness;

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    setBacklightBrightness(passBrightness);
                }
            });


            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("HIDE_PREVIEW"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            runOnUiThread(new HidePreviewWindow());

            // Generate an exception to send Pass result to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SHOW_PREVIEW"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            runOnUiThread(new ShowPreviewWindow());

            // Generate an exception to send Pass result to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("IMAGE_ANALYSIS"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            String fileName = "";
            Bitmap imageUnderTest = null;
            String[] analysisCodes = null;

            if (strRxCmdDataList.size() == 2)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.toUpperCase().contains("FILENAME"))
                    {
                        // value is the factory-val value
                        fileName = value;

                        File imageFile = new File(fileName);
                        if (!imageFile.exists())
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' fileName does not exist", TAG, key, value));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                        imageUnderTest = LoadBitMap.loadImageFromJpeg(fileName);
                    }
                    else if (key.toUpperCase().contains("IMAGE_ANALYSIS_OPTIONS"))
                    {
                        if (!ImageAnalysis.checkImageAnalysisOption(value))
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' failed - ImageAnalysisOption '%s' is not valid", TAG, splitResult[1]));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                        // image analysis options for different data results
                        analysisCodes = value.split(",");
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // setup string list for results from analysis
                List<String> analysisResult = new ArrayList<String>();

                // execute analysis
                if (imageUnderTest != null && analysisCodes != null)
                {
                    for (String analysisCode : analysisCodes)
                    {
                        // avgLum is for backwards capability
                        if (analysisCode.equalsIgnoreCase("avgLum") || analysisCode.equalsIgnoreCase("CalcImageLuminance"))
                        {
                            analysisResult.addAll(ImageAnalysis.calcImageLuminance(imageUnderTest));
                        }
                        // add more analysis calls when available here
                    }
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains command '%s' did not load bitmap image", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // return data list
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, analysisResult);
                sendInfoPacketToCommServer(infoPacket);


                // Generate an exception to send Pass result to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Incorrect number of tokens. Must have FILENAME and IMAGE_ANALYSIS_OPTIONS"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_TORCH_MODE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            String cameraID = null;
            boolean torchMode = false;

            if (strRxCmdDataList.size() == 2)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("CAMERA_ID"))
                    {
                        if(value.equalsIgnoreCase("CAMERA_FACING_BACK") || value.equalsIgnoreCase("LENS_FACING_BACK"))
                        {
                            cameraID = mCameraBackId;
                        }
                        else if(value.equalsIgnoreCase("CAMERA_FACING_EXTERNAL") || value.equalsIgnoreCase("LENS_FACING_EXTERNAL"))
                        {
                            cameraID = mCameraExternalId;
                        }
                        else if(value.equalsIgnoreCase("CAMERA_FACING_FRONT") || value.equalsIgnoreCase("LENS_FACING_FRONT"))
                        {
                            cameraID = mCameraFrontId;
                        }
                        else if(value.matches("^-?\\d+$"))
                        {
                            //Specify camera index directly
                            cameraID = value;
                        }
                        else
                        {
                            strReturnDataList.add("INVALID VALUE FOR CAMERA_ID: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("TORCH_MODE"))
                    {
                        if(value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("ON"))
                        {
                            torchMode = true;
                        }
                        else if(value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("OFF"))
                        {
                            torchMode = false;
                        }
                        else
                        {
                            strReturnDataList.add("INVALID VALUE FOR CAMERA_ID: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains incorrec data for command '%s'. Must set CAMERA_ID and TORCH_MODE", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(cameraID == null || Arrays.asList(mCameraIdList).contains(cameraID) == false)
            {
                strReturnDataList.add("Invalid CAMERA_ID.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            try
            {
                if(!mCameraIdTorchAvailability.get(cameraID))
                {
                    strReturnDataList.add("Torch not available for Camera ID: " + cameraID);
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                dbgLog(TAG, "Setting Torch: Camera ID=" + cameraID + " Torch Mode=" + torchMode, 'i');
                mCameraManager.setTorchMode(cameraID, torchMode);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Exception setting torch camera: " + e.toString(), 'e');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to Set Torch", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will control either camera using the Android Camera2 API");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_NUMBER_CAMERAS - Returns number of cameras and camera ID's");
        strHelpList.add("  NUMBER_OF_CAMERAS - Number of cameras found");
        strHelpList.add("  CAMERA_X_ID - Camera ID for camera index X");
        strHelpList.add("  ");
        strHelpList.add("GET_CAMERA_CHARACTERISTICS - Returns all characteristics for each camera");
        strHelpList.add("  NUMBER_OF_CAMERAS - Number of cameras found");
        strHelpList.add("  CAMERA_X_ID - Camera ID for camera index X");
        strHelpList.add("  CAMERA_X_COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES - Supported abberation modes for Camera X.");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  CAMERA_X_CONTROL_AE_AVAILABLE_ANTIBANDING_MODES - Supported Auto Exposure antibanding modes for Camera X.");
        strHelpList.add("    50HZ, 60HZ, AUTO, or OFF");
        strHelpList.add("  CAMERA_X_CONTROL_AE_AVAILABLE_MODES - Supported Auto Exposure modes for Camera X.");
        strHelpList.add("    OFF, ON, ON_ALWAYS_FLASH, ON_AUTO_FLASH, or ON_AUTO_FLASH_REDEYE");
        strHelpList.add("  CAMERA_X_CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES - Supported ranges for AE Frames Per Second for Camera X.");
        strHelpList.add("  CAMERA_X_CONTROL_AE_COMPENSATION_RANGE - Supported range for AE Compensation for Camera X.");
        strHelpList.add("  CAMERA_X_CONTROL_AE_COMPENSATION_STEP - Supported step range for AE Compensation for Camera X.");
        strHelpList.add("  CAMERA_X_CONTROL_AE_LOCK_AVAILABLE - Is AE Lock available for Camera X.");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CAMERA_X_CONTROL_AF_AVAILABLE_MODES - Supported Auto Focus modes for Camera X.");
        strHelpList.add("    AUTO, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO, EDOF, MACRO, or OFF");
        strHelpList.add("  CAMERA_X_CONTROL_AVAILABLE_EFFECTS - Supported effects for Camera X.");
        strHelpList.add("    AQUA, BLACKBOARD, MONO, NEGATIVE, OFF, POSTERIZE, SEPIA, SOLARIZE, or WHITEBOARD");
        strHelpList.add("  CAMERA_X_CONTROL_AVAILABLE_MODES - Supported modes for Camera X.");
        strHelpList.add("    AUTO, OFF, OFF_KEEP_STATE, or USE_SCENE_MODE");
        strHelpList.add("  CAMERA_X_CONTROL_AVAILABLE_SCENE_MODES - Supported scene modes for Camera X.");
        strHelpList.add("    ACTION, BARCODE, BEACH, CANDLELIGHT, DISABLED, FACE_PRIORITY, FIREWORKS, HDR, HIGH_SPEED_VIDEO, ");
        strHelpList.add("    LANDSCAPE, NIGHT, NIGHT_PORTRAIT, PARTY, PORTRAIT, SNOW, SPORTS, STEADYPHOTO, SUNSET, or THEATRE");
        strHelpList.add("  CAMERA_X_CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES - Supported video stablization for Camera X.");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  CAMERA_X_CONTROL_AWB_AVAILABLE_MODES - Supported Auto White Balance for Camera X.");
        strHelpList.add("    AUTO, CLOUDY_DAYLIGHT, DAYLIGHT, FLUORESCENT, INCANDESCENT, OFF, SHADE, TWILIGHT, or WARM_FLUORESCENT");
        strHelpList.add("  CAMERA_X_CONTROL_AWB_LOCK_AVAILABLE - Is AWB Lock available for Camera X.");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CAMERA_X_CONTROL_MAX_REGIONS_AE - Max AE regions for Camera X.");
        strHelpList.add("  CAMERA_X_CONTROL_MAX_REGIONS_AF - Max AF regions for Camera X.");
        strHelpList.add("  CAMERA_X_CONTROL_MAX_REGIONS_AWB - Max AWB regions for Camera X.");
        strHelpList.add("  CAMERA_X_DEPTH_DEPTH_IS_EXCLUSIVE - Is Depth Depth Exclusive for Camera X.");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CAMERA_X_EDGE_AVAILABLE_EDGE_MODES - Edge modes available for Camera X.");
        strHelpList.add("    FAST, HIGH_QUALITY, OFF, or ZERO_SHUTTER_LAG");
        strHelpList.add("  CAMERA_X_FLASH_INFO_AVAILABLE - Is flash info available for Camera X.");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CAMERA_X_HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES - Hot pixel modes available for Camera X.");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  CAMERA_X_INFO_SUPPORTED_HARDWARE_LEVEL - Supported HW level for Camera X.");
        strHelpList.add("    FULL, LEGACY, or LIMITED");
        strHelpList.add("  CAMERA_X_JPEG_AVAILABLE_THUMBNAIL_SIZES - JPEG Thumbnail sizes available for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_FACING - Lens facing for Camera X.");
        strHelpList.add("    BACK, EXTERNAL, or FRONT");
        strHelpList.add("  CAMERA_X_LENS_INFO_AVAILABLE_APERTURES - Available lens apertures for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_INFO_AVAILABLE_FOCAL_LENGTHS - Available lens focal lengths for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION - Available lens stabilization for Camera X.");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  CAMERA_X_LENS_INFO_FOCUS_DISTANCE_CALIBRATION - Available lens focus distance calibration for Camera X.");
        strHelpList.add("    APPROXIMATE, CALIBRATED or UNCALIBRATED");
        strHelpList.add("  CAMERA_X_LENS_INFO_HYPERFOCAL_DISTANCE - Available lens hyperfocal distance for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_INFO_MINIMUM_FOCUS_DISTANCE - Minimum lens focus distance for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_INTRINSIC_CALIBRATION - Lens intrinsic calibration values for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_POSE_ROTATION - Lens pose rotation for Camera X.");
        strHelpList.add("  CAMERA_X_LENS_RADIAL_DISTORTION - Lens radial distortion for Camera X.");
        strHelpList.add("  CAMERA_X_NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES - Noise reduction modes available for Camera X.");
        strHelpList.add("    FAST, HIGH_QUALITY, MINIMAL, OFF or ZERO_SHUTTER_LAG");
        strHelpList.add("  CAMERA_X_REPROCESS_MAX_CAPTURE_STALL- Reprocess max capture stall for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_AVAILABLE_CAPABILITIES - Available capabilities for Camera X.");
        strHelpList.add("    BACKWARD_COMPATIBLE, BURST_CAPTURE, CONSTRAINED_HIGH_SPEED_VIDEO, DEPTH_OUTPUT, MANUAL_POST_PROCESSING,");
        strHelpList.add("    MANUAL_SENSOR, PRIVATE_REPROCESSING, RAW, READ_SENSOR_SETTINGS, or YUV_REPROCESSING");
        strHelpList.add("  CAMERA_X_REQUEST_MAX_NUM_INPUT_STREAMS - Max input streams for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_MAX_NUM_OUTPUT_PROC - Max output processes for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_MAX_NUM_OUTPUT_PROC_STALLING - Max output processes including stalling for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_MAX_NUM_OUTPUT_RAW - Max raw output processes for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_PARTIAL_RESULT_COUNT - Number of partial results that may be delivered for Camera X.");
        strHelpList.add("  CAMERA_X_REQUEST_PIPELINE_MAX_DEPTH - Maximum pipeline stages for Camera X.");
        strHelpList.add("  CAMERA_X_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM - Maximum digital zoom for Camera X.");
        strHelpList.add("  CAMERA_X_SCALER_CROPPING_TYPE - Available scaler cropping types for Camera X.");
        strHelpList.add("    CENTER_ONLY or FREEFORM");
        strHelpList.add("  CAMERA_X_SCALER_STREAM_CONFIGURATION_MAP_HIGH_SPEED_VIDEO_FPS_RANGES - Available high speed video FPS ranges for Camera X.");
        strHelpList.add("  CAMERA_X_SCALER_STREAM_CONFIGURATION_MAP_HIGH_SPEED_VIDEO_SIZES - Available high speed video sizes for Camera X.");
        strHelpList.add("  CAMERA_X_SCALER_STREAM_CONFIGURATION_MAP_HIGH_RESOLUTION_OUTPUT_SIZES_Y - Available high resolution output sizes for Camera X and output format Y.");
        strHelpList.add("  CAMERA_X_SCALER_STREAM_CONFIGURATION_MAP_OUTPUT_SIZES_Y - Available output sizes for Camera X and output format Y.");
        strHelpList.add("  CAMERA_X_SCALER_STREAM_CONFIGURATION_MAP_OUTPUT_FORMATS - Available output formats for Camera X.");
        strHelpList.add("    DEPTH16, DEPTH_POINT_CLOUD, FLEX_RGBA_8888, FLEX_RGB_888, JPEG, NV16, NV21, PRIVATE, RAW10, RAW12, RAW_SENSOR, ");
        strHelpList.add("    IMAGE_FORMAT_RGB_565, UNKNOWN, YUV_420_888, YUV_422_888, YUV_444_888, YUY2, YV12, A_8, LA_88, L_8, OPAQUE, ");
        strHelpList.add("    RGBA_4444, RGBA_5551, RGBA_8888, RGBX_8888, RGB_332, RGB_565, RGB_888, TRANSLUCENT, or TRANSPARENT");
        strHelpList.add("  CAMERA_X_SENSOR_AVAILABLE_TEST_PATTERN_MODES - Available sensor test patterns for Camera X.");
        strHelpList.add("    COLOR_BARS, COLOR_BARS_FADE_TO_GRAY, CUSTOM1, OFF, PN9, or SOLID_COLOR");
        strHelpList.add("  CAMERA_X_SENSOR_BLACK_LEVEL_PATTERN - Sensor black level pattern for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_CALIBRATION_TRANSFORM1 - Sensor calibration transform 1 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_CALIBRATION_TRANSFORM2 - Sensor calibration transform 2 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_COLOR_TRANSFORM1 - Sensor color transform 1 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_COLOR_TRANSFORM2 - Sensor color transform 2 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_FORWARD_MATRIX1 - Sensor forward matrix 1 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_FORWARD_MATRIX2 - Sensor forward matrix 2 for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_ACTIVE_ARRAY_SIZE - Sensor active array size for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT - Sensor color filter arrangement for Camera X.");
        strHelpList.add("    BGGR, GBRG, GRBG, RGB, or RGGB");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_EXPOSURE_TIME_RANGE - Sensor exposure time range for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_LENS_SHADING_APPLIED - Sensor lens shading applied for Camera X.");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_MAX_FRAME_DURATION - Sensor max frame duration for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_PHYSICAL_SIZE - Sensor physical size for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_PIXEL_ARRAY_SIZE - Sensor pixel array size for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_PHYSICAL_SIZE - Sensor physical size for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE - Sensor pre correction active array size for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_SENSITIVITY_RANGE - Sensor sensitivity range for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_TIMESTAMP_SOURCE - Sensor timestamp source for Camera X.");
        strHelpList.add("    REALTIME or UNKNOWN");
        strHelpList.add("  CAMERA_X_SENSOR_INFO_WHITE_LEVEL  -Sensor white level for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_MAX_ANALOG_SENSITIVITY - Sensor max analog sensitivity for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_ORIENTATION - Sensor orientation for Camera X.");
        strHelpList.add("  CAMERA_X_SENSOR_REFERENCE_ILLUMINANT1 - Sensor reference illuminant 1 for Camera X.");
        strHelpList.add("    CLOUDY_WEATHER, COOL_WHITE_FLUORESCENT, D50, D55, D65, D75, DAYLIGHT, DAYLIGHT_FLUORESCENT, DAY_WHITE_FLUORESCENT");
        strHelpList.add("    FINE_WEATHER, FLASH, FLUORESCENT, ISO_STUDIO_TUNGSTEN, SHADE, STANDARD_A, STANDARD_B, STANDARD_C, TUNGSTEN, or WHITE_FLUORESCENT");
        strHelpList.add("  CAMERA_X_SENSOR_REFERENCE_ILLUMINANT2 - Sensor reference illuminant 2 for Camera X.");
        strHelpList.add("  CAMERA_X_SHADING_AVAILABLE_MODES - Shading available modes for Camera X.");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  CAMERA_X_STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES - Face detect modes for Camera X.");
        strHelpList.add("    FULL, OFF, or SIMPLE");
        strHelpList.add("  CAMERA_X_STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES - Available hot pixel map mode statistics for Camera X.");
        strHelpList.add("  CAMERA_X_STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES - Available lens shading map mode statistics for Camera X.");
        strHelpList.add("    OFF, or ON");
        strHelpList.add("  CAMERA_X_STATISTICS_INFO_MAX_FACE_COUNT - Max face count statistics for Camera X.");
        strHelpList.add("  CAMERA_X_SYNC_MAX_LATENCY - Sync max latency for Camera X.");
        strHelpList.add("  CAMERA_X_TONEMAP_AVAILABLE_TONE_MAP_MODES - Available tone map modes for Camera X.");
        strHelpList.add("    CONTRAST_CURVE, FAST, GAMMA_VALUE, HIGH_QUALITY, or PRESET_CURVE");
        strHelpList.add("  CAMERA_X_TONEMAP_MAX_CURVE_POINTS - Tone map curve points Camera X.");
        strHelpList.add("  ");
        strHelpList.add("OPEN_CAMERA - Open specific camera");
        strHelpList.add("  CAMERA_ID - CAMERA_FACING_BACK, CAMERA_FACING_EXTERNAL, CAMERA_FACING_FRONT, or XX, where XX is the camera ID number.");
        strHelpList.add("  TIMEOUT - Timeout in mS for the camera to open before failing.");
        strHelpList.add("START_VIEWFINDER - Starts the camera viewfinder.");
        strHelpList.add("  LOG_CAPTURE_RESULTS - Log capture results to ADB logcat. TRUE or FALSE. This will also cause capture to be used instead of setRepeatinRequest");
        strHelpList.add("  ");
        strHelpList.add("STOP_VIEWFINDER - Stops the camera viewfinder.");
        strHelpList.add("  ");
        strHelpList.add("TAKE_PICTURE - Take picture.");
        strHelpList.add("  SEND_FILE - YES or NO. Send image over CommServer after capture.");
        strHelpList.add("  PC_SENDS_BINARY_ACK - Must be sent if SEND_FILE=YES if PC accepts binary image transfer.");
        strHelpList.add("  IMAGE_ANALYSIS_OPTIONS - avgLum or CalcImageLuminance. Perform image analysis after image is captured");
        strHelpList.add("  TIMEOUT - Timeout in mS for the image capture to complete before failing.");
        strHelpList.add("  LOG_CAPTURE_RESULTS - Log capture results to ADB logcat. TRUE or FALSE.");
        strHelpList.add("  PREVIEW_TYPE - How to display PREVIEW. PREVIEW_IMAGE, CAPTURE_IMAGE, or DISPLAY");
        strHelpList.add("  ");
        strHelpList.add("GET_TOTAL_CAPTURE_RESULTS -Get total capture results (metadata) used after an picture is taken.");
        strHelpList.add("  BLACK_LEVEL_LOCK - Black level lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  COLOR_CORRECTION_ABERRATION_MODE - Aberration mode.");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  COLOR_CORRECTION_GAINS - Color correction gains.");
        strHelpList.add("  COLOR_CORRECTION_MODE - Aberration mode.");
        strHelpList.add("    FAST, HIGH_QUALITY, or TRANSFORM_MATRIX");
        strHelpList.add("  COLOR_CORRECTION_TRANSFORM - Color correction transform.");
        strHelpList.add("  CONTROL_AE_ANTIBANDING_MODE - Auto exposure antibanding.");
        strHelpList.add("    50HZ, 60HZ, AUTO, or OFF");
        strHelpList.add("  CONTROL_AE_EXPOSURE_COMPENSATION - Auto exposure compenstation.");
        strHelpList.add("  CONTROL_AE_LOCK - Auto exposure lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CONTROL_AE_MODE - Auto exposure mode");
        strHelpList.add("    OFF, ON, ON_ALWAYS_FLASH, ON_AUTO_FLASH, or ON_AUTO_FLASH_REDEYE");
        strHelpList.add("  CONTROL_AE_PRECAPTURE_TRIGGER - Auto exposure trigger status");
        strHelpList.add("    CANCEL, IDLE, or START");
        strHelpList.add("  CONTROL_AE_REGIONS - Auto exposure region");
        strHelpList.add("  CONTROL_AE_STATE - Auto exposure state");
        strHelpList.add("    CONVERGED, FLASH_REQUIRED, INACTIVE, LOCKED, PRECAPTURE, or SEARCHING");
        strHelpList.add("  CONTROL_AE_TARGET_FPS_RANGE - Auto exposure frame per second range");
        strHelpList.add("  CONTROL_AF_MODE - Auto focus mode");
        strHelpList.add("    AUTO, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO, EDOF, MACRO, or OFF");
        strHelpList.add("  CONTROL_AF_REGIONS - Auto focus region");
        strHelpList.add("  CONTROL_AF_STATE - Auto focus state");
        strHelpList.add("    INACTIVE, PASSIVE_SCAN, PASSIVE_FOCUSED, ACTIVE_SCAN, FOCUSED_LOCKED, NOT_FOCUSED_LOCKED, or PASSIVE_UNFOCUSED");
        strHelpList.add("  CONTROL_AF_TRIGGER -Auto focus trigger status");
        strHelpList.add("    CANCEL, IDLE, or START");
        strHelpList.add("  CONTROL_AWB_LOCK - Auto white balance lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CONTROL_AWB_MODE - Auto white balance mode");
        strHelpList.add("    AUTO, CLOUDY_DAYLIGHT, DAYLIGHT, FLUORESCENT, INCANDESCENT, OFF, SHADE, TWILIGHT, or WARM_FLUORESCENT");
        strHelpList.add("  CONTROL_AWB_REGIONS - Auto white balance region");
        strHelpList.add("  CONTROL_AWB_STATE - Auto white balance state");
        strHelpList.add("    INACTIVE, SEARCHING, CONVERGED, or LOCKED");
        strHelpList.add("  CONTROL_CAPTURE_INTENT - Capture intent");
        strHelpList.add("    CUSTOM, MANUAL, PREVIEW, STILL_CAPTURE, VIDEO_RECORD, VIDEO_SNAPSHOT, or ZERO_SHUTTER_LAG");
        strHelpList.add("  CONTROL_EFFECT_MODE - Effect mode");
        strHelpList.add("    AQUA, BLACKBOARD, MONO, NEGATIVE, OFF, POSTERIZE, SEPIA, SOLARIZE, or WHITEBOARD");
        strHelpList.add("  CONTROL_MODE - Mode");
        strHelpList.add("    AUTO, OFF, OFF_KEEP_STATE, or USE_SCENE_MODE");
        strHelpList.add("  CONTROL_SCENE_MODE - Scene Mode");
        strHelpList.add("    ACTION, BARCODE, BEACH, CANDLELIGHT, DISABLED, FACE_PRIORITY, FIREWORKS, HDR, HIGH_SPEED_VIDEO, ");
        strHelpList.add("    LANDSCAPE, NIGHT, NIGHT_PORTRAIT, PARTY, PORTRAIT, SNOW, SPORTS, STEADYPHOTO, SUNSET, or THEATRE");
        strHelpList.add("  CONTROL_VIDEO_STABILIZATION_MODE - Video stabilization Mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  EDGE_MODE - Edge Mode");
        strHelpList.add("    FAST, HIGH_QUALITY, OFF, or ZERO_SHUTTER_LAG");
        strHelpList.add("  FLASH_MODE - Flash Mode");
        strHelpList.add("    OFF, SINGLE, or TORCH");
        strHelpList.add("  FLASH_STATE - Flash state");
        strHelpList.add("    UNAVAILABLE, CHARGING, READY, FIRED, or PARTIAL");
        strHelpList.add("  HOT_PIXEL_MODE - Hot pixel mode");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  JPEG_GPS_LOCATION - Jpeg GPS location");
        strHelpList.add("  JPEG_ORIENTATION - Jpeg orientation");
        strHelpList.add("  JPEG_QUALITY - Jpeg quality");
        strHelpList.add("  JPEG_THUMBNAIL_QUALITY - Jpeg thumbnail quality");
        strHelpList.add("  JPEG_THUMBNAIL_SIZE - Jpeg thumbnail size");
        strHelpList.add("  LENS_APERTURE - Lens aperture");
        strHelpList.add("  LENS_FILTER_DENSITY - Lens filter density");
        strHelpList.add("  LENS_FOCAL_LENGTH - Lens focal length");
        strHelpList.add("  LENS_FOCUS_DISTANCE - Lens focus distance");
        strHelpList.add("  LENS_FOCUS_RANGE - Lens focus range");
        strHelpList.add("  LENS_INTRINSIC_CALIBRATION - Lens intrinsic calibration");
        strHelpList.add("  LENS_OPTICAL_STABILIZATION_MODE - Lens optical stabilization mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  LENS_POSE_ROTATION - Lens pose rotation");
        strHelpList.add("  LENS_POSE_TRANSLATION - Lens pose translation");
        strHelpList.add("  LENS_RADIAL_DISTORTION - Lens radial distortion");
        strHelpList.add("  LENS_STATE - Lens state");
        strHelpList.add("    STATIONARY or MOVING");
        strHelpList.add("  NOISE_REDUCTION_MODE -Noise reduction mode");
        strHelpList.add("    FAST, HIGH_QUALITY, MINIMAL, OFF or ZERO_SHUTTER_LAG");
        strHelpList.add("  REPROCESS_EFFECTIVE_EXPOSURE_FACTOR - Reprocess effective exposure factor");
        strHelpList.add("  REQUEST_PIPELINE_DEPTH - Pipeline depth");
        strHelpList.add("  SCALER_CROP_REGION - Scaler crop region");
        strHelpList.add("  SENSOR_EXPOSURE_TIME - Sensor exposure time");
        strHelpList.add("  SENSOR_FRAME_DURATION - Sensor frame duration");
        strHelpList.add("  SENSOR_GREEN_SPLIT - Sensor green split");
        strHelpList.add("  SENSOR_NEUTRAL_COLOR_POINT - Sensor neutral color point");
        strHelpList.add("  SENSOR_NOISE_PROFILE - Sensor noise profile");
        strHelpList.add("  SENSOR_ROLLING_SHUTTER_SKEW - Sensor rolling shutter skew");
        strHelpList.add("  SENSOR_SENSITIVITY - Sensor sensitivity");
        strHelpList.add("  SENSOR_TEST_PATTERN_DATA - Sensor test pattern data");
        strHelpList.add("  SENSOR_TEST_PATTERN_MODE - Sensor test pattern mode");
        strHelpList.add("    COLOR_BARS, COLOR_BARS_FADE_TO_GRAY, CUSTOM1, OFF, PN9, or SOLID_COLOR");
        strHelpList.add("  SENSOR_TIMESTAMP - Sensor timestamp");
        strHelpList.add("  SHADING_MODE - Shading mode");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  STATISTICS_FACES - Statistics faces");
        strHelpList.add("  STATISTICS_FACE_DETECT_MODE - Statistics face detect mode");
        strHelpList.add("    FULL, OFF, or SIMPLE");
        strHelpList.add("  STATISTICS_HOT_PIXEL_MAP - Statistics hot pixel map");
        strHelpList.add("  STATISTICS_HOT_PIXEL_MAP_MODE - Statistics hot pixel map mode");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  STATISTICS_LENS_SHADING_CORRECTION_MAP - Statistics lens shading correction map");
        strHelpList.add("  STATISTICS_LENS_SHADING_MAP_MODE - Statistics lens shading map mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  STATISTICS_SCENE_FLICKER - Statistics scene flicker");
        strHelpList.add("    NONE, 50HZ, or 60HZ");
        strHelpList.add("  TONEMAP_CURVE - Tone map curve");
        strHelpList.add("  TONEMAP_GAMMA - Tone map gamma");
        strHelpList.add("  TONEMAP_MODE - Tone map mode");
        strHelpList.add("    CONTRAST_CURVE, FAST, GAMMA_VALUE, HIGH_QUALITY, or PRESET_CURVE");
        strHelpList.add("  TONEMAP_PRESET_CURVE - Tone map preset curve");
        strHelpList.add("    REC709, or SRGB");
        strHelpList.add("  ");
        strHelpList.add("CLOSE_CAMERA - Close currently opened camera");
        strHelpList.add("  ");
        strHelpList.add("SET_IMAGE_PATH - Set image save file path");
        strHelpList.add("  IMAGE_PATH - Device path of image save location. Set to NULL to not save image.");
        strHelpList.add("  ");
        strHelpList.add("GET_IMAGE_PATH - Get image save file path");
        strHelpList.add("  IMAGE_PATH - Path of image save location. If set to NULL, image will not be saved.");
        strHelpList.add("  ");
        strHelpList.add("SET_CAPTURE_REQUEST_SETTINGS - Set capture request settings");
        strHelpList.add("  CAPTURE_REQUEST_TYPE - PREVIEW_WINDOW or IMAGE_CAPTURE.");
        strHelpList.add("  TEMPLATE - Template to use when creating capture request.");
        strHelpList.add("    MANUAL, PREVIEW, RECORD, STILL_CAPTURE, VIDEO_SNAPSHOT, or ZERO_SHUTTER_LAG");
        strHelpList.add("  BLACK_LEVEL_LOCK - Black level lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  COLOR_CORRECTION_ABERRATION_MODE - Aberration mode.");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  COLOR_CORRECTION_GAINS - Color correction gains.");
        strHelpList.add("  COLOR_CORRECTION_MODE - Aberration mode.");
        strHelpList.add("    FAST, HIGH_QUALITY, or TRANSFORM_MATRIX");
        strHelpList.add("  COLOR_CORRECTION_TRANSFORM - Color correction transform.");
        strHelpList.add("  CONTROL_AE_ANTIBANDING_MODE - Auto exposure antibanding.");
        strHelpList.add("    50HZ, 60HZ, AUTO, or OFF");
        strHelpList.add("  CONTROL_AE_EXPOSURE_COMPENSATION - Auto exposure compenstation.");
        strHelpList.add("  CONTROL_AE_LOCK - Auto exposure lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CONTROL_AE_MODE - Auto exposure mode");
        strHelpList.add("    OFF, ON, ON_ALWAYS_FLASH, ON_AUTO_FLASH, or ON_AUTO_FLASH_REDEYE");
        strHelpList.add("  CONTROL_AE_PRECAPTURE_TRIGGER - Auto exposure trigger status");
        strHelpList.add("    CANCEL, IDLE, or START");
        strHelpList.add("  CONTROL_AE_REGIONS - Auto exposure region");
        strHelpList.add("  CONTROL_AE_TARGET_FPS_RANGE - Auto exposure frame per second range");
        strHelpList.add("  CONTROL_AF_MODE - Auto focus mode");
        strHelpList.add("    AUTO, CONTINUOUS_PICTURE, CONTINUOUS_VIDEO, EDOF, MACRO, or OFF");
        strHelpList.add("  CONTROL_AF_REGIONS - Auto focus region");
        strHelpList.add("  CONTROL_AF_TRIGGER -Auto focus trigger status");
        strHelpList.add("    CANCEL, IDLE, or START");
        strHelpList.add("  CONTROL_AWB_LOCK - Auto white balance lock status");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  CONTROL_AWB_MODE - Auto white balance mode");
        strHelpList.add("    AUTO, CLOUDY_DAYLIGHT, DAYLIGHT, FLUORESCENT, INCANDESCENT, OFF, SHADE, TWILIGHT, or WARM_FLUORESCENT");
        strHelpList.add("  CONTROL_AWB_REGIONS - Auto white balance region");
        strHelpList.add("  CONTROL_CAPTURE_INTENT - Capture intent");
        strHelpList.add("    CUSTOM, MANUAL, PREVIEW, STILL_CAPTURE, VIDEO_RECORD, VIDEO_SNAPSHOT, or ZERO_SHUTTER_LAG");
        strHelpList.add("  CONTROL_EFFECT_MODE - Effect mode");
        strHelpList.add("    AQUA, BLACKBOARD, MONO, NEGATIVE, OFF, POSTERIZE, SEPIA, SOLARIZE, or WHITEBOARD");
        strHelpList.add("  CONTROL_MODE - Mode");
        strHelpList.add("    AUTO, OFF, OFF_KEEP_STATE, or USE_SCENE_MODE");
        strHelpList.add("  CONTROL_SCENE_MODE - Scene Mode");
        strHelpList.add("    ACTION, BARCODE, BEACH, CANDLELIGHT, DISABLED, FACE_PRIORITY, FIREWORKS, HDR, HIGH_SPEED_VIDEO, ");
        strHelpList.add("    LANDSCAPE, NIGHT, NIGHT_PORTRAIT, PARTY, PORTRAIT, SNOW, SPORTS, STEADYPHOTO, SUNSET, or THEATRE");
        strHelpList.add("  CONTROL_VIDEO_STABILIZATION_MODE - Video stabilization Mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  EDGE_MODE - Edge Mode");
        strHelpList.add("    FAST, HIGH_QUALITY, OFF, or ZERO_SHUTTER_LAG");
        strHelpList.add("  FLASH_MODE - Flash Mode");
        strHelpList.add("    OFF, SINGLE, or TORCH");
        strHelpList.add("  HOT_PIXEL_MODE - Hot pixel mode");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  JPEG_ORIENTATION - Jpeg orientation");
        strHelpList.add("  JPEG_QUALITY - Jpeg quality");
        strHelpList.add("  JPEG_THUMBNAIL_QUALITY - Jpeg thumbnail quality");
        strHelpList.add("  JPEG_THUMBNAIL_SIZE - Jpeg thumbnail size");
        strHelpList.add("  LENS_APERTURE - Lens aperture");
        strHelpList.add("  LENS_FILTER_DENSITY - Lens filter density");
        strHelpList.add("  LENS_FOCAL_LENGTH - Lens focal length");
        strHelpList.add("  LENS_FOCUS_DISTANCE - Lens focus distance");
        strHelpList.add("  LENS_OPTICAL_STABILIZATION_MODE - Lens optical stabilization mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  NOISE_REDUCTION_MODE -Noise reduction mode");
        strHelpList.add("    FAST, HIGH_QUALITY, MINIMAL, OFF or ZERO_SHUTTER_LAG");
        strHelpList.add("  REPROCESS_EFFECTIVE_EXPOSURE_FACTOR - Reprocess effective exposure factor");
        strHelpList.add("  SCALER_CROP_REGION - Scaler crop region");
        strHelpList.add("  SENSOR_EXPOSURE_TIME - Sensor exposure time");
        strHelpList.add("  SENSOR_FRAME_DURATION - Sensor frame duration");
        strHelpList.add("  SENSOR_SENSITIVITY - Sensor sensitivity");
        strHelpList.add("  SENSOR_TEST_PATTERN_DATA - Sensor test pattern data");
        strHelpList.add("  SENSOR_TEST_PATTERN_MODE - Sensor test pattern mode");
        strHelpList.add("    COLOR_BARS, COLOR_BARS_FADE_TO_GRAY, CUSTOM1, OFF, PN9, or SOLID_COLOR");
        strHelpList.add("  SHADING_MODE - Shading mode");
        strHelpList.add("    FAST, HIGH_QUALITY, or OFF");
        strHelpList.add("  STATISTICS_FACE_DETECT_MODE - Statistics face detect mode");
        strHelpList.add("    FULL, OFF, or SIMPLE");
        strHelpList.add("  STATISTICS_HOT_PIXEL_MAP_MODE - Statistics hot pixel map mode");
        strHelpList.add("    TRUE or FALSE");
        strHelpList.add("  STATISTICS_LENS_SHADING_MAP_MODE - Statistics lens shading map mode");
        strHelpList.add("    OFF or ON");
        strHelpList.add("  TONEMAP_CURVE - Tone map curve");
        strHelpList.add("  TONEMAP_GAMMA - Tone map gamma");
        strHelpList.add("  TONEMAP_MODE - Tone map mode");
        strHelpList.add("    CONTRAST_CURVE, FAST, GAMMA_VALUE, HIGH_QUALITY, or PRESET_CURVE");
        strHelpList.add("  TONEMAP_PRESET_CURVE - Tone map preset curve");
        strHelpList.add("    REC709, or SRGB");
        strHelpList.add("  ");
        strHelpList.add("SET_CAPTURE_REQUEST_FACTORY_SETTING - Set capture request settings used for factory");
        strHelpList.add("  CAPTURE_REQUEST_TYPE - PREVIEW_WINDOW or IMAGE_CAPTURE.");
        strHelpList.add("  CAPTURE_REQUEST_KEY - Full key name to set. Example: android.control.awbMode.");
        strHelpList.add("  CAPTURE_REQUEST_VALUE - Value to set key to.");
        strHelpList.add("  CAPTURE_REQUEST_CLASS - Class type for the value to set.");
        strHelpList.add("     BYTE, BYTE_ARRAY, BOOLEAN, INT, INT_ARRAY, RGGBCHANNELVECTOR, COLORSPACETRANSFORM, METERINGRECTANGLE_ARRAY, ");
        strHelpList.add("     RANGE_INTEGER, SIZE, FLOAT, RECT, LONG, or TONEMAPCURVE");
        strHelpList.add("  ");
        strHelpList.add("GET_CAPTURE_REQUEST_SETTINGS - Get settings for capture request");
        strHelpList.add("  CAPTURE_REQUEST_TYPE - PREVIEW_WINDOW or IMAGE_CAPTURE. Returns data for all items defined in SET_CAPTURE_REQUEST_SETTINGS");
        strHelpList.add("  ");
        strHelpList.add("SET_IMAGE_READER_SETTINGS - Set settings for image reader. Width and height sets the resolution for the captured image.");
        strHelpList.add("  WIDTH - Width of image to capture.");
        strHelpList.add("  HEIGHT - Height of image to capture.");
        strHelpList.add("  FORMAT - Format of image to capture.");
        strHelpList.add("    DEPTH16, DEPTH_POINT_CLOUD, FLEX_RGBA_8888, FLEX_RGB_888, JPEG, NV16, NV21, PRIVATE, RAW10, RAW12, RAW_SENSOR, ");
        strHelpList.add("    IMAGE_FORMAT_RGB_565, UNKNOWN, YUV_420_888, YUV_422_888, YUV_444_888, YUY2, YV12, A_8, LA_88, L_8, OPAQUE, ");
        strHelpList.add("    RGBA_4444, RGBA_5551, RGBA_8888, RGBX_8888, RGB_332, RGB_565, RGB_888, TRANSLUCENT, or TRANSPARENT");
        strHelpList.add("  ");
        strHelpList.add("GET_IMAGE_READER_SETTINGS - Get settings for image reader.");
        strHelpList.add("  WIDTH - Width of image to capture.");
        strHelpList.add("  HEIGHT - Height of image to capture.");
        strHelpList.add("  FORMAT - Format of image to capture.");
        strHelpList.add("    DEPTH16, DEPTH_POINT_CLOUD, FLEX_RGBA_8888, FLEX_RGB_888, JPEG, NV16, NV21, PRIVATE, RAW10, RAW12, RAW_SENSOR, ");
        strHelpList.add("    IMAGE_FORMAT_RGB_565, UNKNOWN, YUV_420_888, YUV_422_888, YUV_444_888, YUY2, YV12, A_8, LA_88, L_8, OPAQUE, ");
        strHelpList.add("    RGBA_4444, RGBA_5551, RGBA_8888, RGBX_8888, RGB_332, RGB_565, RGB_888, TRANSLUCENT, or TRANSPARENT");
        strHelpList.add("  ");
        strHelpList.add("SET_TORCH_MODE - Enable or Disable torch without opening camera.");
        strHelpList.add("  CAMERA_ID - CAMERA_FACING_BACK, CAMERA_FACING_EXTERNAL, CAMERA_FACING_FRONT, or XX, where XX is the camera ID number.");
        strHelpList.add("  TORCH_MODE - ON or OFF.");
        strHelpList.add("  ");
        strHelpList.add("HIDE_PREVIEW - Hide preview from display.");
        strHelpList.add("  ");
        strHelpList.add("SHOW_PREVIEW - Show preview to display.");
        strHelpList.add("  ");
        strHelpList.add("SET_BRIGHTNESS_LEVEL - Set brightness level of display.");
        strHelpList.add("  BRIGHTNESS - Display brightness.");
        strHelpList.add("  ");
        strHelpList.add("IMAGE_ANALYSIS - Perform image analysis on image already on device.");
        strHelpList.add("  FILENAME - Filename of image to perform image analysis on.");
        strHelpList.add("  IMAGE_ANALYSIS_OPTIONS - avgLum or CalcImageLuminance. Perform image analysis after image is captured");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    private RggbChannelVector convertStringToRggbChannelVector(String value) throws Exception
    {
        value = value.replace(" ", "");
        String[] rggbData = value.split(",");
        RggbChannelVector rggbChannelVector = null;

        float red = 0.0f;
        float greenEven = 0.0f;
        float greenOdd = 0.0f;
        float blue = 0.0f;

        try
        {
            for (String colorValue : rggbData)
            {
                String[] splitColorValue = colorValue.split(":");

                if (splitColorValue[0].equalsIgnoreCase("R") || splitColorValue[0].equalsIgnoreCase("RED"))
                {
                    red = Float.parseFloat(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("G_EVEN") || splitColorValue[0].equalsIgnoreCase("GREEN_EVEN"))
                {
                    greenEven = Float.parseFloat(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("G_ODD") || splitColorValue[0].equalsIgnoreCase("GREEN_ODD"))
                {
                    greenOdd = Float.parseFloat(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("B") || splitColorValue[0].equalsIgnoreCase("BLUE"))
                {
                    blue = Float.parseFloat(splitColorValue[1]);
                }
            }

            rggbChannelVector = new RggbChannelVector(red, greenEven, greenOdd, blue);
        }
        catch (Exception e)
        {
            throw e;
        }

        return rggbChannelVector;
    }

    private ColorSpaceTransform convertStringToColorSpaceTransform(String value) throws Exception
    {
        value = value.replace(" ", "");
        ColorSpaceTransform colorSpaceTransform = null;
        String colorSpaceTransformData = value.replace("[", "");
        colorSpaceTransformData = colorSpaceTransformData.replace("]", "");

        String[] rationals = colorSpaceTransformData.split(",");

        int[] elements = new int[]{
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0
        };

        int elementNumber = 0;

        try
        {
            for(String rational : rationals)
            {
                String[] element = rational.split("/");
                String numerator = element[0];
                String denominator = element[1];

                elements[elementNumber++] = Integer.parseInt(numerator);
                elements[elementNumber++] = Integer.parseInt(denominator);
            }

            colorSpaceTransform = new ColorSpaceTransform(elements);
        }
        catch (Exception e)
        {
            throw e;
        }

        return colorSpaceTransform;
    }


    private MeteringRectangle convertStringToMeteringRectangle(String value) throws Exception
    {
        value = value.replace(" ", "");
        value = value.replace("(", "");
        value = value.replace(")", "");

        MeteringRectangle meteringRectangle = null;

        try
        {
            String[] meteringRectangleObjects = value.split(",");

            int x = 0;
            int y = 0;
            int width = 0;
            int height = 0;
            int meteringWeight = 0;

            for(String meteringRectangleObject : meteringRectangleObjects)
            {
                String[] meteringRectangleItem =meteringRectangleObject.split(":");
                String meteringRectangleType = meteringRectangleItem[0];
                String meteringRectangleValue = meteringRectangleItem[1];

                if (meteringRectangleType.equalsIgnoreCase("X"))
                {
                    x = Integer.parseInt(meteringRectangleValue);
                }
                else if (meteringRectangleType.equalsIgnoreCase("Y"))
                {
                    y = Integer.parseInt(meteringRectangleValue);
                }
                else if (meteringRectangleType.equalsIgnoreCase("W") || meteringRectangleType.equalsIgnoreCase("WIDTH"))
                {
                    width = Integer.parseInt(meteringRectangleValue);
                }
                else if (meteringRectangleType.equalsIgnoreCase("H") || meteringRectangleType.equalsIgnoreCase("HEIGHT"))
                {
                    height = Integer.parseInt(meteringRectangleValue);
                }
                else if (meteringRectangleType.equalsIgnoreCase("WT") || meteringRectangleType.equalsIgnoreCase("METERINGWEIGHT") || meteringRectangleType.equalsIgnoreCase("METERING_WEIGHT"))
                {
                    meteringWeight = Integer.parseInt(meteringRectangleValue);
                }
            }

            meteringRectangle = new MeteringRectangle(x, y, width, height, meteringWeight);
        }
        catch (Exception e)
        {
            throw e;
        }

        return meteringRectangle;

    }

    private Range<Integer> convertStringToRange(String value) throws Exception
    {
        value = value.replace(" ", "");
        Range<Integer> range = null;

        try
        {
            String[] rangeString = value.split(",");

            int lower = Integer.parseInt(rangeString[0]);
            int upper = Integer.parseInt(rangeString[1]);

            range = new Range(lower, upper);
        }
        catch (Exception e)
        {
            throw e;
        }

        return range;
    }


    private Rect convertStringToRect(String value) throws Exception
    {
        value = value.replace(" ", "");
        String[] rectData = value.split(",");
        Rect rect = null;

        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        try
        {
            for (String rectValue : rectData)
            {
                String[] splitColorValue = rectValue.split(":");

                if (splitColorValue[0].equalsIgnoreCase("LEFT"))
                {
                    left = Integer.parseInt(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("TOP"))
                {
                    top = Integer.parseInt(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("RIGHT"))
                {
                    right = Integer.parseInt(splitColorValue[1]);
                }
                else if (splitColorValue[0].equalsIgnoreCase("BOTTOM"))
                {
                    bottom = Integer.parseInt(splitColorValue[1]);
                }
            }

            rect = new Rect(left, top, right, bottom);
        }
        catch (Exception e)
        {
            throw e;
        }

        return rect;
    }

    private String convertRectToString(Rect rect)
    {
        String rectString = "LEFT:" + rect.left + ",TOP:" + rect.top + ",RIGHT:" + rect.right + ",BOTTOM:" + rect.bottom;

        return rectString;
    }

    private TonemapCurve convertStringToTonemapCurve(String value)
    {
        value = value.replace(" ", "");

        TonemapCurve tonemapCurve = null;

        try
        {
            String[] toneMapCurveData = value.split("],");
            float[] redCurve = null;;
            float[] greenCurve = null;
            float[] blueCurve = null;

            for(String toneMapCurve : toneMapCurveData)
            {
                toneMapCurve = toneMapCurve.replace("[", "");
                toneMapCurve = toneMapCurve.replace("]", "");
                toneMapCurve = toneMapCurve.replace("(", "");
                toneMapCurve = toneMapCurve.replace(")", "");

                String[] toneMapCurveSplit = toneMapCurve.split(":");
                String color = toneMapCurveSplit[0];
                String[] colorValues = toneMapCurveSplit[1].split(",");

                List<Float> colorValueList = new ArrayList<Float>();

                for(String colorValue : colorValues)
                {
                    colorValueList.add(Float.parseFloat(colorValue));
                }

                Float[] floatArray = (Float[]) colorValueList.toArray(new Float[colorValueList.size()]);;

                if (color.equalsIgnoreCase("R") || color.equalsIgnoreCase("RED"))
                {
                    redCurve = new float[floatArray.length];
                    int index = 0;
                    for(Float floatValue : floatArray)
                    {
                        redCurve[index++] = floatValue.floatValue();
                    }
                }
                else if (color.equalsIgnoreCase("G") || color.equalsIgnoreCase("GREEN"))
                {
                    greenCurve = new float[floatArray.length];
                    int index = 0;
                    for(Float floatValue : floatArray)
                    {
                        greenCurve[index++] = floatValue.floatValue();
                    }
                }
                else if (color.equalsIgnoreCase("B") || color.equalsIgnoreCase("BLUE"))
                {
                    blueCurve = new float[floatArray.length];
                    int index = 0;
                    for(Float floatValue : floatArray)
                    {
                        blueCurve[index++] = floatValue.floatValue();
                    }
                }
            }

            tonemapCurve = new TonemapCurve(redCurve, greenCurve, blueCurve);
        }
        catch (Exception e)
        {
            throw e;
        }

        return tonemapCurve;
    }

    private byte[] hexStringToByteArray(String hexString) throws NumberFormatException
    {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            try
            {
                // Verify Data is in Hex format
                Long.parseLong(Character.toString(hexString.charAt(i)), 16);
            }
            catch (NumberFormatException nEx)
            {
                throw nEx;
            }
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    private CaptureRequest.Builder createCaptureRequestBuilder(CaptureRequest.Builder captureRequestBuilder, List<String> strList) throws CmdFailException
    {
        List<String> strReturnDataList = new ArrayList<String>();

        for (String keyValuePair : strList)
        {
            String splitResult[] = splitKeyValuePair(keyValuePair);
            String key = splitResult[0];
            String value = splitResult[1];

            try
            {
                if (key.equalsIgnoreCase("TEMPLATE"))
                {
                    captureRequestBuilder = null;

                    if (value.equalsIgnoreCase("MANUAL"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
                    }
                    else if (value.equalsIgnoreCase("PREVIEW"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    }
                    else if (value.equalsIgnoreCase("RECORD"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    }
                    else if (value.equalsIgnoreCase("STILL_CAPTURE"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    }
                    else if (value.equalsIgnoreCase("VIDEO_SNAPSHOT"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                    }
                    else if (value.equalsIgnoreCase("ZERO_SHUTTER_LAG"))
                    {
                        captureRequestBuilder = mCameraDeviceInUse.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN TEMPLATE: " + value);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
            catch(Exception e)
            {
                strReturnDataList.add("Exception occurred creating request builder: " + e.toString());
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }

        if(captureRequestBuilder == null)
        {
            strReturnDataList.add("Capture Request Builder not created. Did you provide a TEMPLATE?");
            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        for (String keyValuePair : strRxCmdDataList)
        {
            String splitResult[] = splitKeyValuePair(keyValuePair);
            String key = splitResult[0];
            String value = splitResult[1];

            if (strRxCmdDataList.size() > 0)
            {
                try
                {
                    if (key.equalsIgnoreCase("TEMPLATE"))
                    {
                        //Do nothing since this was already handled above to first create the builder.
                    }
                    else if (key.equalsIgnoreCase("CAPTURE_REQUEST_TYPE"))
                    {
                        //Do nothing since this was already handled above.
                    }
                    else if (key.equalsIgnoreCase("BLACK_LEVEL_LOCK"))
                    {
                        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            captureRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);
                        }
                        else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, false);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN BLACK_LEVEL_LOCK: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("COLOR_CORRECTION_ABERRATION_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN COLOR_CORRECTION_ABERRATION_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("COLOR_CORRECTION_GAINS"))
                    {
                        RggbChannelVector rggbChannelVectorValue = convertStringToRggbChannelVector(value);
                        captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVectorValue);
                    }
                    else if (key.equalsIgnoreCase("COLOR_CORRECTION_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN COLOR_CORRECTION_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("COLOR_CORRECTION_TRANSFORM"))
                    {
                        ColorSpaceTransform colorSpaceTransform = convertStringToColorSpaceTransform(value);
                        captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, colorSpaceTransform);
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_ANTIBANDING_MODE"))
                    {
                        if (value.equalsIgnoreCase("50HZ"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ);
                        }
                        else if (value.equalsIgnoreCase("60HZ"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ);
                        }
                        else if (value.equalsIgnoreCase("AUTO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AE_ANTIBANDING_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_EXPOSURE_COMPENSATION"))
                    {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_LOCK"))
                    {
                        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                        }
                        else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AE_LOCK: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_MODE"))
                    {
                        if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ON"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        }
                        else if (value.equalsIgnoreCase("ON_ALWAYS_FLASH"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                        }
                        else if (value.equalsIgnoreCase("ON_AUTO_FLASH"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        }
                        else if (value.equalsIgnoreCase("ON_AUTO_FLASH_REDEYE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AE_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_PRECAPTURE_TRIGGER"))
                    {
                        if (value.equalsIgnoreCase("CANCEL"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                        }
                        else if (value.equalsIgnoreCase("IDLE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                        }
                        else if (value.equalsIgnoreCase("START"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AE_PRECAPTURE_TRIGGER: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_REGIONS"))
                    {
                        String[] meteringRectangleValues = value.split("\\),");

                        List<MeteringRectangle> meteringRectangleList = new ArrayList<MeteringRectangle>();

                        for(String meteringRectangleValue : meteringRectangleValues)
                        {
                            meteringRectangleList.add(convertStringToMeteringRectangle(meteringRectangleValue));
                        }

                        MeteringRectangle[] meteringRectangleArray = (MeteringRectangle[]) meteringRectangleList.toArray(new MeteringRectangle[meteringRectangleList.size()]);

                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRectangleArray);
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AE_TARGET_FPS_RANGE"))
                    {
                        Range<Integer> range = convertStringToRange(value);
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range);
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AF_MODE"))
                    {
                        if (value.equalsIgnoreCase("AUTO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                        }
                        else if (value.equalsIgnoreCase("CONTINUOUS_PICTURE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        }
                        else if (value.equalsIgnoreCase("CONTINUOUS_VIDEO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        }
                        else if (value.equalsIgnoreCase("EDOF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_EDOF);
                        }
                        else if (value.equalsIgnoreCase("MACRO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_MACRO);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AF_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AF_REGIONS"))
                    {
                        String[] meteringRectangleValues = value.split("\\),");

                        List<MeteringRectangle> meteringRectangleList = new ArrayList<MeteringRectangle>();

                        for(String meteringRectangleValue : meteringRectangleValues)
                        {
                            meteringRectangleList.add(convertStringToMeteringRectangle(meteringRectangleValue));
                        }

                        MeteringRectangle[] meteringRectangleArray = (MeteringRectangle[]) meteringRectangleList.toArray(new MeteringRectangle[meteringRectangleList.size()]);

                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArray);
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AF_TRIGGER"))
                    {
                        if (value.equalsIgnoreCase("CANCEL"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        }
                        else if (value.equalsIgnoreCase("IDLE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                        }
                        else if (value.equalsIgnoreCase("START"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AF_TRIGGER: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AWB_LOCK"))
                    {
                        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                        }
                        else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AWB_LOCK: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AWB_MODE"))
                    {
                        if (value.equalsIgnoreCase("AUTO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
                        }
                        else if (value.equalsIgnoreCase("CLOUDY_DAYLIGHT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT);
                        }
                        else if (value.equalsIgnoreCase("DAYLIGHT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT);
                        }
                        else if (value.equalsIgnoreCase("FLUORESCENT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT);
                        }
                        else if (value.equalsIgnoreCase("INCANDESCENT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("SHADE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_SHADE);
                        }
                        else if (value.equalsIgnoreCase("TWILIGHT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_TWILIGHT);
                        }
                        else if (value.equalsIgnoreCase("WARM_FLUORESCENT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_AWB_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_AWB_REGIONS"))
                    {
                        String[] meteringRectangleValues = value.split("\\),");

                        List<MeteringRectangle> meteringRectangleList = new ArrayList<MeteringRectangle>();

                        for(String meteringRectangleValue : meteringRectangleValues)
                        {
                            meteringRectangleList.add(convertStringToMeteringRectangle(meteringRectangleValue));
                        }

                        MeteringRectangle[] meteringRectangleArray = (MeteringRectangle[]) meteringRectangleList.toArray(new MeteringRectangle[meteringRectangleList.size()]);

                        captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, meteringRectangleArray);
                    }
                    else if (key.equalsIgnoreCase("CONTROL_CAPTURE_INTENT"))
                    {
                        if (value.equalsIgnoreCase("CUSTOM"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_CUSTOM);
                        }
                        else if (value.equalsIgnoreCase("MANUAL"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL);
                        }
                        else if (value.equalsIgnoreCase("PREVIEW"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW);
                        }
                        else if (value.equalsIgnoreCase("STILL_CAPTURE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                        }
                        else if (value.equalsIgnoreCase("VIDEO_RECORD"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
                        }
                        else if (value.equalsIgnoreCase("VIDEO_SNAPSHOT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT);
                        }
                        else if (value.equalsIgnoreCase("ZERO_SHUTTER_LAG"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_CAPTURE_INTENT: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_EFFECT_MODE"))
                    {
                        if (value.equalsIgnoreCase("AQUA"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_AQUA);
                        }
                        else if (value.equalsIgnoreCase("BLACKBOARD"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD);
                        }
                        else if (value.equalsIgnoreCase("MONO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
                        }
                        else if (value.equalsIgnoreCase("NEGATIVE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("POSTERIZE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE);
                        }
                        else if (value.equalsIgnoreCase("SEPIA"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SEPIA);
                        }
                        else if (value.equalsIgnoreCase("SOLARIZE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE);
                        }
                        else if (value.equalsIgnoreCase("WHITEBOARD"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_EFFECT_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_MODE"))
                    {
                        if (value.equalsIgnoreCase("AUTO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("OFF_KEEP_STATE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF_KEEP_STATE);
                        }
                        else if (value.equalsIgnoreCase("USE_SCENE_MODE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_SCENE_MODE"))
                    {
                        if (value.equalsIgnoreCase("ACTION"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_ACTION);
                        }
                        else if (value.equalsIgnoreCase("BARCODE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
                        }
                        else if (value.equalsIgnoreCase("BEACH"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BEACH);
                        }
                        else if (value.equalsIgnoreCase("CANDLELIGHT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT);
                        }
                        else if (value.equalsIgnoreCase("DISABLED"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_DISABLED);
                        }
                        else if (value.equalsIgnoreCase("FACE_PRIORITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
                        }
                        else if (value.equalsIgnoreCase("FIREWORKS"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS);
                        }
                        else if (value.equalsIgnoreCase("HDR"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR);
                        }
                        else if (value.equalsIgnoreCase("HIGH_SPEED_VIDEO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO);
                        }
                        else if (value.equalsIgnoreCase("LANDSCAPE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE);
                        }
                        else if (value.equalsIgnoreCase("NIGHT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
                        }
                        else if (value.equalsIgnoreCase("NIGHT_PORTRAIT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT);
                        }
                        else if (value.equalsIgnoreCase("PARTY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_PARTY);
                        }
                        else if (value.equalsIgnoreCase("PORTRAIT"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT);
                        }
                        else if (value.equalsIgnoreCase("SNOW"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_SNOW);
                        }
                        else if (value.equalsIgnoreCase("SPORTS"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_SPORTS);
                        }
                        else if (value.equalsIgnoreCase("STEADYPHOTO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO);
                        }
                        else if (value.equalsIgnoreCase("SUNSET"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_SUNSET);
                        }
                        else if (value.equalsIgnoreCase("THEATRE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_THEATRE);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_SCENE_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("CONTROL_VIDEO_STABILIZATION_MODE"))
                    {
                        if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ON"))
                        {
                            captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN CONTROL_VIDEO_STABILIZATION_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("EDGE_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ZERO_SHUTTER_LAG"))
                        {
                            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN EDGE_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("FLASH_MODE"))
                    {
                        if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("SINGLE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
                        }
                        else if (value.equalsIgnoreCase("TORCH"))
                        {
                            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN FLASH_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("HOT_PIXEL_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN HOT_PIXEL_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("JPEG_GPS_LOCATION"))
                    {
                        strReturnDataList.add("Setting JPEG_GPS_LOCATION Not currently supported");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                    else if (key.equalsIgnoreCase("JPEG_ORIENTATION"))
                    {
                        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("JPEG_QUALITY"))
                    {
                        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, Byte.parseByte(value));
                    }
                    else if (key.equalsIgnoreCase("JPEG_THUMBNAIL_QUALITY"))
                    {
                        captureRequestBuilder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, Byte.parseByte(value));
                    }
                    else if (key.equalsIgnoreCase("JPEG_THUMBNAIL_SIZE"))
                    {
                        captureRequestBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, Size.parseSize(value));
                    }
                    else if (key.equalsIgnoreCase("LENS_APERTURE"))
                    {
                        captureRequestBuilder.set(CaptureRequest.LENS_APERTURE, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("LENS_FILTER_DENSITY"))
                    {
                        captureRequestBuilder.set(CaptureRequest.LENS_FILTER_DENSITY, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("LENS_FOCAL_LENGTH"))
                    {
                        captureRequestBuilder.set(CaptureRequest.LENS_FOCAL_LENGTH, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("LENS_FOCUS_DISTANCE"))
                    {
                        captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("LENS_OPTICAL_STABILIZATION_MODE"))
                    {
                        if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ON"))
                        {
                            captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN LENS_OPTICAL_STABILIZATION_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("NOISE_REDUCTION_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("MINIMAL"))
                        {
                            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ZERO_SHUTTER_LAG"))
                        {
                            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN NOISE_REDUCTION_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("REPROCESS_EFFECTIVE_EXPOSURE_FACTOR"))
                    {
                        captureRequestBuilder.set(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("SCALER_CROP_REGION"))
                    {
                        Rect rect = convertStringToRect(value);
                        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, rect);
                    }
                    else if (key.equalsIgnoreCase("SENSOR_EXPOSURE_TIME"))
                    {
                        captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong(value));
                    }
                    else if (key.equalsIgnoreCase("SENSOR_FRAME_DURATION"))
                    {
                        captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, Long.parseLong(value));
                    }
                    else if (key.equalsIgnoreCase("SENSOR_SENSITIVITY"))
                    {
                        captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("SENSOR_TEST_PATTERN_DATA"))
                    {
                        String[] intArrayValues = value.split(",");

                        List<Integer> intArrayValueList = new ArrayList<Integer>();

                        for(String intArrayValueString : intArrayValues)
                        {
                            intArrayValueList.add(Integer.parseInt(intArrayValueString));
                        }

                        Integer[] integerArray = (Integer[]) intArrayValueList.toArray(new Integer[intArrayValueList.size()]);

                        int[] intArray = new int[integerArray.length];
                        int index = 0;
                        for(Integer integerValue : integerArray)
                        {
                            intArray[index++] = integerValue.intValue();
                        }

                        captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_DATA, intArray);
                    }
                    else if (key.equalsIgnoreCase("SENSOR_TEST_PATTERN_MODE"))
                    {
                        if (value.equalsIgnoreCase("COLOR_BARS"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS);
                        }
                        else if (value.equalsIgnoreCase("COLOR_BARS_FADE_TO_GRAY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY);
                        }
                        else if (value.equalsIgnoreCase("CUSTOM1"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_CUSTOM1);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("PN9"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_PN9);
                        }
                        else if (value.equalsIgnoreCase("SOLID_COLOR"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN SENSOR_TEST_PATTERN_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("SHADING_MODE"))
                    {
                        if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN SHADING_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("STATISTICS_FACE_DETECT_MODE"))
                    {
                        if (value.equalsIgnoreCase("FULL"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("SIMPLE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN STATISTICS_FACE_DETECT_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("STATISTICS_HOT_PIXEL_MAP_MODE"))
                    {
                        if (value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE, true);
                        }
                        else if (value.equalsIgnoreCase("FALSE") || value.equalsIgnoreCase("NO"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE, false);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN STATISTICS_HOT_PIXEL_MAP_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("STATISTICS_LENS_SHADING_MAP_MODE"))
                    {
                        if (value.equalsIgnoreCase("OFF"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
                        }
                        else if (value.equalsIgnoreCase("ON"))
                        {
                            captureRequestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN STATISTICS_LENS_SHADING_MAP_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("TONEMAP_CURVE"))
                    {
                        TonemapCurve tonemapCurve = convertStringToTonemapCurve(value);
                        captureRequestBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve);
                    }
                    else if (key.equalsIgnoreCase("TONEMAP_GAMMA"))
                    {
                        captureRequestBuilder.set(CaptureRequest.TONEMAP_GAMMA, Float.parseFloat(value));
                    }
                    else if (key.equalsIgnoreCase("TONEMAP_MODE"))
                    {
                        if (value.equalsIgnoreCase("CONTRAST_CURVE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE);
                        }
                        else if (value.equalsIgnoreCase("FAST"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_FAST);
                        }
                        else if (value.equalsIgnoreCase("GAMMA_VALUE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_GAMMA_VALUE);
                        }
                        else if (value.equalsIgnoreCase("HIGH_QUALITY"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_HIGH_QUALITY);
                        }
                        else if (value.equalsIgnoreCase("PRESET_CURVE"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_PRESET_CURVE);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN TONEMAP_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("TONEMAP_PRESET_CURVE"))
                    {
                        if (value.equalsIgnoreCase("REC709"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CameraMetadata.TONEMAP_PRESET_CURVE_REC709);
                        }
                        else if (value.equalsIgnoreCase("SRGB"))
                        {
                            captureRequestBuilder.set(CaptureRequest.TONEMAP_PRESET_CURVE, CameraMetadata.TONEMAP_PRESET_CURVE_SRGB);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN TONEMAP_PRESET_CURVE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
                catch (Exception e)
                {
                    strReturnDataList.add("Exception occurred creating request builder: " + e.toString());
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        return captureRequestBuilder;
    }

    private List<String> totalCaptureResultToStringList(TotalCaptureResult totalCaptureResult)
    {
        List<String> stringList = new ArrayList<String>();

        List<CaptureResult> partialCaptureResults = totalCaptureResult.getPartialResults();

        if(partialCaptureResults != null)
        {
            int partialResultCounter = 0;
            for (CaptureResult partialCaptureResult : partialCaptureResults)
            {
                stringList.addAll(captureResultToStringList(partialCaptureResult, "PARTIAL_CAPTURE_RESULT_" + partialResultCounter + "_"));
                partialResultCounter++;
            }
        }

        stringList.addAll(captureResultToStringList(totalCaptureResult, "TOTAL_CAPTURE_RESULT_"));

        return stringList;
    }

    private List<String> captureResultToStringList(CaptureResult captureResult, String captureResultHeading)
    {
        List<String> strDataList = new ArrayList<String>();
        String heading = "";
        if (captureResultHeading != null)
        {
            heading = captureResultHeading;
        }

        try
        {
            List<CaptureResult.Key<?>> captureResultKeys = captureResult.getKeys();

            for (CaptureResult.Key<?> captureResultKey : captureResultKeys)
            {
                Object captureResultValue = null;

                captureResultValue = captureResult.get(captureResultKey);

                if (captureResultKey == CaptureResult.BLACK_LEVEL_LOCK)
                {
                    Boolean value = (Boolean) captureResultValue;
                    strDataList.add(heading + "BLACK_LEVEL_LOCK=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.COLOR_CORRECTION_ABERRATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "COLOR_CORRECTION_ABERRATION_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.COLOR_CORRECTION_GAINS)
                {
                    RggbChannelVector value = (RggbChannelVector) captureResultValue;
                    strDataList.add(heading + "COLOR_CORRECTION_GAINS=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.COLOR_CORRECTION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    {
                        returnStringValue = "TRANSFORM_MATRIX";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "COLOR_CORRECTION_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.COLOR_CORRECTION_TRANSFORM)
                {
                    ColorSpaceTransform value = (ColorSpaceTransform) captureResultValue;
                    strDataList.add(heading + "COLOR_CORRECTION_TRANSFORM=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_ANTIBANDING_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_50HZ)
                    {
                        returnStringValue = "50HZ";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_60HZ)
                    {
                        returnStringValue = "60HZ";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AE_ANTIBANDING_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION)
                {
                    Integer value = (Integer) captureResultValue;
                    strDataList.add(heading + "CONTROL_AE_EXPOSURE_COMPENSATION=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_LOCK)
                {
                    Boolean value = (Boolean) captureResultValue;
                    strDataList.add(heading + "CONTROL_AE_LOCK=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    {
                        returnStringValue = "ON_ALWAYS_FLASH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    {
                        returnStringValue = "ON_AUTO_FLASH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
                    {
                        returnStringValue = "ON_AUTO_FLASH_REDEYE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AE_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
                    {
                        returnStringValue = "CANCEL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
                    {
                        returnStringValue = "IDLE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    {
                        returnStringValue = "START";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AE_PRECAPTURE_TRIGGER=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureResultValue;
                    for (MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "CONTROL_AE_REGIONS=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_STATE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_CONVERGED)
                    {
                        returnStringValue = "CONVERGED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED)
                    {
                        returnStringValue = "FLASH_REQUIRED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_INACTIVE)
                    {
                        returnStringValue = "INACTIVE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_LOCKED)
                    {
                        returnStringValue = "LOCKED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_PRECAPTURE)
                    {
                        returnStringValue = "PRECAPTURE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AE_STATE_SEARCHING)
                    {
                        returnStringValue = "SEARCHING";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AE_STATE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AE_TARGET_FPS_RANGE)
                {
                    Range<Integer> value = (Range<Integer>) captureResultValue;
                    strDataList.add(heading + "CONTROL_AE_TARGET_FPS_RANGE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.CONTROL_AF_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    {
                        returnStringValue = "CONTINUOUS_PICTURE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    {
                        returnStringValue = "CONTINUOUS_VIDEO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_EDOF)
                    {
                        returnStringValue = "EDOF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_MACRO)
                    {
                        returnStringValue = "MACRO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AF_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AF_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureResultValue;
                    for (MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "CONTROL_AF_REGIONS=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AF_STATE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_INACTIVE)
                    {
                        returnStringValue = "INACTIVE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN)
                    {
                        returnStringValue = "PASSIVE_SCAN";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED)
                    {
                        returnStringValue = "PASSIVE_FOCUSED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN)
                    {
                        returnStringValue = "ACTIVE_SCAN";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED)
                    {
                        returnStringValue = "FOCUSED_LOCKED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)
                    {
                        returnStringValue = "NOT_FOCUSED_LOCKED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED)
                    {
                        returnStringValue = "PASSIVE_UNFOCUSED";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AF_STATE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AF_TRIGGER)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                    {
                        returnStringValue = "CANCEL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                    {
                        returnStringValue = "IDLE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AF_TRIGGER_START)
                    {
                        returnStringValue = "START";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AF_TRIGGER=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AWB_LOCK)
                {
                    Boolean value = (Boolean) captureResultValue;
                    strDataList.add(heading + "CONTROL_AWB_LOCK=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.CONTROL_AWB_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                    {
                        returnStringValue = "CLOUDY_DAYLIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
                    {
                        returnStringValue = "DAYLIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
                    {
                        returnStringValue = "FLUORESCENT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
                    {
                        returnStringValue = "INCANDESCENT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_SHADE)
                    {
                        returnStringValue = "SHADE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_TWILIGHT)
                    {
                        returnStringValue = "TWILIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT)
                    {
                        returnStringValue = "WARM_FLUORESCENT";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AWB_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AWB_REGIONS)
                {
                    String returnStringValue = "";
                    MeteringRectangle[] valueArray = (MeteringRectangle[]) captureResultValue;
                    for (MeteringRectangle value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "CONTROL_AWB_REGIONS=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_AWB_STATE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_AWB_STATE_INACTIVE)
                    {
                        returnStringValue = "INACTIVE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_STATE_SEARCHING)
                    {
                        returnStringValue = "SEARCHING";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_STATE_CONVERGED)
                    {
                        returnStringValue = "CONVERGED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_AWB_STATE_LOCKED)
                    {
                        returnStringValue = "LOCKED";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_AWB_STATE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_CAPTURE_INTENT)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_CUSTOM)
                    {
                        returnStringValue = "CUSTOM";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_MANUAL)
                    {
                        returnStringValue = "MANUAL";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW)
                    {
                        returnStringValue = "PREVIEW";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
                    {
                        returnStringValue = "STILL_CAPTURE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD)
                    {
                        returnStringValue = "VIDEO_RECORD";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT)
                    {
                        returnStringValue = "VIDEO_SNAPSHOT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_CAPTURE_INTENT=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_EFFECT_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_AQUA)
                    {
                        returnStringValue = "AQUA";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_BLACKBOARD)
                    {
                        returnStringValue = "BLACKBOARD";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_MONO)
                    {
                        returnStringValue = "MONO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE)
                    {
                        returnStringValue = "NEGATIVE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_POSTERIZE)
                    {
                        returnStringValue = "POSTERIZE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_SEPIA)
                    {
                        returnStringValue = "SEPIA";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_SOLARIZE)
                    {
                        returnStringValue = "SOLARIZE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_EFFECT_MODE_WHITEBOARD)
                    {
                        returnStringValue = "WHITEBOARD";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_EFFECT_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_MODE_AUTO)
                    {
                        returnStringValue = "AUTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_OFF_KEEP_STATE)
                    {
                        returnStringValue = "OFF_KEEP_STATE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                    {
                        returnStringValue = "USE_SCENE_MODE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_SCENE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_ACTION)
                    {
                        returnStringValue = "ACTION";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_BARCODE)
                    {
                        returnStringValue = "BARCODE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_BEACH)
                    {
                        returnStringValue = "BEACH";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_CANDLELIGHT)
                    {
                        returnStringValue = "CANDLELIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_DISABLED)
                    {
                        returnStringValue = "DISABLED";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
                    {
                        returnStringValue = "FACE_PRIORITY";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS)
                    {
                        returnStringValue = "FIREWORKS";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_HDR)
                    {
                        returnStringValue = "HDR";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO)
                    {
                        returnStringValue = "HIGH_SPEED_VIDEO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_LANDSCAPE)
                    {
                        returnStringValue = "LANDSCAPE";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_NIGHT)
                    {
                        returnStringValue = "NIGHT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT)
                    {
                        returnStringValue = "NIGHT_PORTRAIT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_PARTY)
                    {
                        returnStringValue = "PARTY";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_PORTRAIT)
                    {
                        returnStringValue = "PORTRAIT";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SNOW)
                    {
                        returnStringValue = "SNOW";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SPORTS)
                    {
                        returnStringValue = "SPORTS";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_STEADYPHOTO)
                    {
                        returnStringValue = "STEADYPHOTO";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_SUNSET)
                    {
                        returnStringValue = "SUNSET";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_SCENE_MODE_THEATRE)
                    {
                        returnStringValue = "THEATRE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_SCENE_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "CONTROL_VIDEO_STABILIZATION_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.EDGE_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.EDGE_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.EDGE_MODE_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN";
                    }

                    strDataList.add(heading + "EDGE_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.FLASH_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.FLASH_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_MODE_SINGLE)
                    {
                        returnStringValue = "SINGLE";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_MODE_TORCH)
                    {
                        returnStringValue = "TORCH";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "FLASH_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.FLASH_STATE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.FLASH_STATE_UNAVAILABLE)
                    {
                        returnStringValue = "UNAVAILABLE";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_STATE_CHARGING)
                    {
                        returnStringValue = "CHARGING";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_STATE_READY)
                    {
                        returnStringValue = "READY";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_STATE_FIRED)
                    {
                        returnStringValue = "FIRED";
                    }
                    else if (value.intValue() == CameraMetadata.FLASH_STATE_PARTIAL)
                    {
                        returnStringValue = "PARTIAL";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "FLASH_STATE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.HOT_PIXEL_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.HOT_PIXEL_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "HOT_PIXEL_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.JPEG_GPS_LOCATION)
                {
                    Location value = (Location) captureResultValue;

                    strDataList.add(heading + "JPEG_GPS_LOCATION=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.JPEG_ORIENTATION)
                {
                    Integer value = (Integer) captureResultValue;

                    strDataList.add(heading + "JPEG_ORIENTATION=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.JPEG_QUALITY)
                {
                    Byte value = (Byte) captureResultValue;

                    strDataList.add(heading + "JPEG_QUALITY=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.JPEG_THUMBNAIL_QUALITY)
                {
                    Byte value = (Byte) captureResultValue;

                    strDataList.add(heading + "JPEG_THUMBNAIL_QUALITY=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.JPEG_THUMBNAIL_SIZE)
                {
                    Size value = (Size) captureResultValue;

                    strDataList.add(heading + "JPEG_THUMBNAIL_SIZE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_APERTURE)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "LENS_APERTURE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_FILTER_DENSITY)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "LENS_FILTER_DENSITY=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_FOCAL_LENGTH)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "LENS_FOCAL_LENGTH=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_FOCUS_DISTANCE)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "LENS_FOCUS_DISTANCE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_FOCUS_RANGE)
                {
                    Pair value = (Pair) captureResultValue;

                    strDataList.add(heading + "LENS_FOCUS_RANGE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.LENS_INTRINSIC_CALIBRATION)
                {
                    String returnStringValue = "";
                    float[] valueArray = (float[]) captureResultValue;
                    for (float value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "LENS_INTRINSIC_CALIBRATION=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "LENS_OPTICAL_STABILIZATION_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.LENS_POSE_ROTATION)
                {
                    String returnStringValue = "";
                    float[] valueArray = (float[]) captureResultValue;
                    for (float value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "LENS_POSE_ROTATION=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.LENS_POSE_TRANSLATION)
                {
                    String returnStringValue = "";
                    float[] valueArray = (float[]) captureResultValue;
                    for (float value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "LENS_POSE_TRANSLATION=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.LENS_RADIAL_DISTORTION)
                {
                    String returnStringValue = "";
                    float[] valueArray = (float[]) captureResultValue;
                    for (float value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "LENS_RADIAL_DISTORTION=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.LENS_STATE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.LENS_STATE_STATIONARY)
                    {
                        returnStringValue = "STATIONARY";
                    }
                    else if (value.intValue() == CameraMetadata.LENS_STATE_MOVING)
                    {
                        returnStringValue = "MOVING";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "LENS_STATE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.NOISE_REDUCTION_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)
                    {
                        returnStringValue = "MINIMAL";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)
                    {
                        returnStringValue = "ZERO_SHUTTER_LAG";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "NOISE_REDUCTION_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "REPROCESS_EFFECTIVE_EXPOSURE_FACTOR=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.REQUEST_PIPELINE_DEPTH)
                {
                    Byte value = (Byte) captureResultValue;

                    strDataList.add(heading + "REQUEST_PIPELINE_DEPTH=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SCALER_CROP_REGION)
                {
                    Rect value = (Rect) captureResultValue;

                    strDataList.add(heading + "SCALER_CROP_REGION=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_EXPOSURE_TIME)
                {
                    Long value = (Long) captureResultValue;

                    strDataList.add(heading + "SENSOR_EXPOSURE_TIME=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_FRAME_DURATION)
                {
                    Long value = (Long) captureResultValue;

                    strDataList.add(heading + "SENSOR_FRAME_DURATION=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_GREEN_SPLIT)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "SENSOR_GREEN_SPLIT=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)
                {
                    String returnStringValue = "";
                    Rational[] valueArray = (Rational[]) captureResultValue;
                    for (Rational value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.doubleValue() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "SENSOR_NEUTRAL_COLOR_POINT=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.SENSOR_NOISE_PROFILE)
                {
                    String returnStringValue = "";
                    Pair[] valueArray = (Pair[]) captureResultValue;
                    for (Pair value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "SENSOR_NOISE_PROFILE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)
                {
                    Long value = (Long) captureResultValue;

                    strDataList.add(heading + "SENSOR_ROLLING_SHUTTER_SKEW=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_SENSITIVITY)
                {
                    Integer value = (Integer) captureResultValue;

                    strDataList.add(heading + "SENSOR_SENSITIVITY=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SENSOR_TEST_PATTERN_DATA)
                {
                    String returnStringValue = "";
                    int[] valueArray = (int[]) captureResultValue;
                    for (int value : valueArray)
                    {
                        returnStringValue = returnStringValue + value + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "SENSOR_TEST_PATTERN_DATA=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.SENSOR_TEST_PATTERN_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS)
                    {
                        returnStringValue = "COLOR_BARS";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS_FADE_TO_GRAY)
                    {
                        returnStringValue = "COLOR_BARS_FADE_TO_GRAY";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_CUSTOM1)
                    {
                        returnStringValue = "CUSTOM1";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_PN9)
                    {
                        returnStringValue = "PN9";
                    }
                    else if (value.intValue() == CameraMetadata.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR)
                    {
                        returnStringValue = "SOLID_COLOR";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "SENSOR_TEST_PATTERN_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.SENSOR_TIMESTAMP)
                {
                    Long value = (Long) captureResultValue;

                    strDataList.add(heading + "SENSOR_TIMESTAMP=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.SHADING_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.SHADING_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.SHADING_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.SHADING_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "SHADING_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.STATISTICS_FACES)
                {
                    String returnStringValue = "";
                    Face[] valueArray = (Face[]) captureResultValue;
                    for (Face value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "STATISTICS_FACES=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.STATISTICS_FACE_DETECT_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL)
                    {
                        returnStringValue = "FULL";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    {
                        returnStringValue = "SIMPLE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "STATISTICS_FACE_DETECT_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.STATISTICS_HOT_PIXEL_MAP)
                {
                    String returnStringValue = "";
                    Point[] valueArray = (Point[]) captureResultValue;
                    for (Point value : valueArray)
                    {
                        returnStringValue = returnStringValue + value.toString() + ",";
                    }
                    returnStringValue = returnStringValue.replaceAll(",$", "");
                    strDataList.add(heading + "STATISTICS_HOT_PIXEL_MAP=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE)
                {
                    Boolean value = (Boolean) captureResultValue;

                    strDataList.add(heading + "STATISTICS_HOT_PIXEL_MAP_MODE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)
                {
                    LensShadingMap value = (LensShadingMap) captureResultValue;

                    strDataList.add(heading + "STATISTICS_LENS_SHADING_CORRECTION_MAP=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.STATISTICS_LENS_SHADING_MAP_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_OFF)
                    {
                        returnStringValue = "OFF";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
                    {
                        returnStringValue = "ON";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "STATISTICS_LENS_SHADING_MAP_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.STATISTICS_SCENE_FLICKER)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.STATISTICS_SCENE_FLICKER_NONE)
                    {
                        returnStringValue = "NONE";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_SCENE_FLICKER_50HZ)
                    {
                        returnStringValue = "50HZ";
                    }
                    else if (value.intValue() == CameraMetadata.STATISTICS_SCENE_FLICKER_60HZ)
                    {
                        returnStringValue = "60HZ";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "STATISTICS_SCENE_FLICKER=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.TONEMAP_CURVE)
                {
                    TonemapCurve value = (TonemapCurve) captureResultValue;

                    strDataList.add(heading + "TONEMAP_CURVE=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.TONEMAP_GAMMA)
                {
                    Float value = (Float) captureResultValue;

                    strDataList.add(heading + "TONEMAP_GAMMA=" + value.toString());
                }
                else if (captureResultKey == CaptureResult.TONEMAP_MODE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
                    {
                        returnStringValue = "CONTRAST_CURVE";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_FAST)
                    {
                        returnStringValue = "FAST";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_GAMMA_VALUE)
                    {
                        returnStringValue = "GAMMA_VALUE";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
                    {
                        returnStringValue = "HIGH_QUALITY";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_MODE_PRESET_CURVE)
                    {
                        returnStringValue = "PRESET_CURVE";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "TONEMAP_MODE=" + returnStringValue);
                }
                else if (captureResultKey == CaptureResult.TONEMAP_PRESET_CURVE)
                {
                    String returnStringValue = "";
                    Integer value = (Integer) captureResultValue;

                    if (value.intValue() == CameraMetadata.TONEMAP_PRESET_CURVE_REC709)
                    {
                        returnStringValue = "REC709";
                    }
                    else if (value.intValue() == CameraMetadata.TONEMAP_PRESET_CURVE_SRGB)
                    {
                        returnStringValue = "SRGB";
                    }
                    else
                    {
                        returnStringValue = "UNKNOWN,";
                    }

                    strDataList.add(heading + "TONEMAP_PRESET_CURVE=" + returnStringValue);
                }
                else
                {
                    if (captureResultValue != null)
                    {
                        Class captureResultClass = captureResultValue.getClass();

                        if (captureResultClass.equals(Byte.class))
                        {
                            Byte value = (Byte) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(byte[].class))
                        {
                            String returnStringValue = "";
                            byte[] valueArray = (byte[]) captureResultValue;
                            for (byte value : valueArray)
                            {
                                returnStringValue += String.format("%02x", value);
                            }

                            strDataList.add(heading + captureResultKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureResultClass.equals(Boolean.class))
                        {
                            Boolean value = (Boolean) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(Integer.class))
                        {
                            Integer value = (Integer) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(int[].class))
                        {
                            String returnStringValue = "";
                            int[] valueArray = (int[]) captureResultValue;
                            for (int value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(heading + captureResultKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureResultClass.equals(Double.class))
                        {
                            Double value = (Double) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(double[].class))
                        {
                            String returnStringValue = "";
                            double[] valueArray = (double[]) captureResultValue;
                            for (double value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(heading + captureResultKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureResultClass.equals(RggbChannelVector.class))
                        {
                            RggbChannelVector value = (RggbChannelVector) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(ColorSpaceTransform.class))
                        {
                            ColorSpaceTransform value = (ColorSpaceTransform) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(MeteringRectangle[].class))
                        {
                            String returnStringValue = "";
                            MeteringRectangle[] valueArray = (MeteringRectangle[]) captureResultValue;
                            for (MeteringRectangle value : valueArray)
                            {
                                returnStringValue = returnStringValue + value.toString() + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(heading + captureResultKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureResultClass.equals(Size.class))
                        {
                            Size value = (Size) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(Float.class))
                        {
                            Float value = (Float) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(float[].class))
                        {
                            String returnStringValue = "";
                            float[] valueArray = (float[]) captureResultValue;
                            for (float value : valueArray)
                            {
                                returnStringValue = returnStringValue + value + ",";
                            }
                            returnStringValue = returnStringValue.replaceAll(",$", "");
                            strDataList.add(heading + captureResultKey.getName() + "=" + returnStringValue);
                        }
                        else if (captureResultClass.equals(Rect.class))
                        {
                            Rect value = (Rect) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(Long.class))
                        {
                            Long value = (Long) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else if (captureResultClass.equals(TonemapCurve.class))
                        {
                            TonemapCurve value = (TonemapCurve) captureResultValue;
                            strDataList.add(heading + captureResultKey.getName() + "=" + value.toString());
                        }
                        else
                        {
                            strDataList.add(heading + captureResultKey.getName() + "=UNKNOWN_CLASS_TYPE:" + captureResultClass.getName());
                        }
                    }
                    else
                    {
                        strDataList.add(heading + captureResultKey.getName() + "=NULL");
                    }
                }
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Exception getting capture results: " + e.toString(), 'e');
        }

        return strDataList;
    }
}
