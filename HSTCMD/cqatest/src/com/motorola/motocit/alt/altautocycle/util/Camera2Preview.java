/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioManager;
import android.media.ImageReader;
import android.media.SoundPool;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import com.motorola.motocit.camera2.Camera2Utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class Camera2Preview extends TextureView {

    public final int LED_STATE_NONE = 0;
    public final int LED_STATE_TORCH = 1;
    public final int LED_STATE_FLASH = 2;
    public final int TARGET_CAMERA_FRONT = 0;
    public final int TARGET_CAMERA_REAR_RGB = 1;
    public final int TARGET_CAMERA_REAR_MONO = 2;

    private final int CAMERA_STATE_PREVIEW = 0;
    private final int CAMERA_STATE_WAITING_AF_LOCK = CAMERA_STATE_PREVIEW + 1;
    private final int CAMERA_STATE_WAITING_PRECAPTURE = CAMERA_STATE_PREVIEW + 2;
    private final int CAMERA_STATE_WAITING_NON_PRECAPTURE = CAMERA_STATE_PREVIEW + 3;
    private final int CAMERA_STATE_PICTURE_TAKEN = CAMERA_STATE_PREVIEW + 4;

    protected String[] mHelpMessage;

    private Context mContext = null;
    private CameraManager manager = null;
    private TextureView mTextureView;
    private Size mPreviewSize;
    public String mCameraId;
    public int defaultCameraId;
    private String[] mCameraIdlist;
    private CameraDevice mCamera;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;
    private HandlerThread mBgThread;
    private Handler mCameraBgHandler;
    private CaptureRequest mPreviewRequest;
    private int mState;
    private CameraSound mCameraSound;

    public boolean pictureFlag;
    public int backCameraId = TARGET_CAMERA_REAR_RGB;
    public boolean hasMonoCamera = false;
    public int backMonoCameraId = TARGET_CAMERA_REAR_MONO;
    public int frontCameraId = TARGET_CAMERA_FRONT;
    private boolean mTorchOn;

    private String TAG = "Camera2Preview";

    public Camera2Preview(Context context){
        super(context);
        mContext = context;
        mTextureView = this;
        setSurfaceTextureListener(mSurfaceTextureListener);
        try {
            CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            if (manager.getCameraIdList().length > 2) {
                hasMonoCamera = true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        mTorchOn = false;

        getCameraId(backCameraId);
        defaultCameraId = backCameraId;
        try
        {
            this.pictureFlag = false;
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
                releaseCamera2();
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
            release();
            defaultCameraId = CameraId;
            setCamera();
            getCameraId(CameraId);
            startBackgroundThread();
            mCameraSound = new CameraSound();
            openCamera();
        }
    }

    public void setCamera()
    {
        if (defaultCameraId == backCameraId)
        {
            if (mTorchOn)
            {
                setCameraLedStatus(LED_STATE_FLASH);
                mTorchOn = false;
            }
            else
            {
                setCameraLedStatus(LED_STATE_TORCH);
                mTorchOn = true;
            }
        }
        else if (defaultCameraId == frontCameraId)
        {
            setCameraLedStatus(LED_STATE_TORCH);
        }
        else if(defaultCameraId == backMonoCameraId)
        {
            setCameraLedStatus(LED_STATE_NONE);
        }
    }

    public void takePicture()
    {
        if (this.mCamera != null)
        {
            try
            {
                this.pictureFlag = true;
                if (mState == CAMERA_STATE_PREVIEW) {
                    startLockFocus();
                }
            }
            catch (Exception e)
            {}
        }
    }

    //------------------------------------------------------
    // Camera2 Stuff
    //------------------------------------------------------

    private void releaseCamera2() {
        closeCamera();
        stopBackgroundThread();
        if(mCameraSound != null) mCameraSound.release();
        if(mMap != null) mMap.clear();
    }

    private void startBackgroundThread() {
        mBgThread = new HandlerThread("CameraBackground");
        mBgThread.start();
        mCameraBgHandler = new Handler(mBgThread.getLooper());
    }

    private void stopBackgroundThread() {
        if(mBgThread != null) mBgThread.quitSafely();
        try {
            mCameraBgHandler = null;
            if(mBgThread != null) mBgThread.join();
            mBgThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private HashMap mMap = new HashMap();
    private boolean getCameraId(int targetCamera) {
        boolean ret = false;
        try {
            if (manager == null) {
                manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            }
            mCameraIdlist = manager.getCameraIdList();
            if (Camera2Utils.DBG) Log.d(TAG, "CameraId List" + Arrays.toString(mCameraIdlist));
            if (mCameraIdlist != null) {
                CameraCharacteristics c;
                int i = 0;
                for (String index : mCameraIdlist) {
                    c = manager.getCameraCharacteristics(index);
                    Integer lenFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (null != lenFacing && lenFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        mMap.put(TARGET_CAMERA_FRONT, index);
                    } else {
                        int target = i == 0 ? TARGET_CAMERA_REAR_RGB : TARGET_CAMERA_REAR_MONO;
                        mMap.put(target, index);
                        i++;
                    }
                }
                mCameraId = mMap.get(targetCamera).toString();
                ret = true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private void setCameraLedStatus(int value) {
        switch (value) {
            case LED_STATE_NONE:
                Camera2Utils.setCameraTorchStatus(false);
                Camera2Utils.setCameraStrobeRequest(false);
                break;
            case LED_STATE_TORCH:
                Camera2Utils.setCameraTorchStatus(true);
                break;
            case LED_STATE_FLASH:
                Camera2Utils.setCameraStrobeRequest(true);
                break;
            default:
                break;
        }
    }

    private void openCamera() {
        if(mCameraId.isEmpty()) {
            showToast(com.motorola.motocit.R.string.msg_no_camera);
            return;
        }

        try {
            manager.openCamera(mCameraId, mDeviceStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        Camera2Utils.getCameraCharacteristics(manager, mCameraId);
        mImageReader = ImageReader.newInstance(Camera2Utils.getCameraMaxResoultions().getWidth(),
                Camera2Utils.getCameraMaxResoultions().getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraBgHandler);
        if (Camera2Utils.DBG) {
            Size[] sizes = Camera2Utils.getCameraPreviewResolutions();
            for (int i = 0; i < sizes.length; i++) {
                Log.d(TAG, "res = " + sizes[i].getWidth() + "x" + sizes[i].getHeight());
            }
            Log.d(TAG, "TextureView: " + mTextureView.getWidth() + " x " + mTextureView.getHeight());
            Log.d(TAG, "Orientation: " + Integer.toString(Camera2Utils.getCameraOrientation()));
        }
    }

    private void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCamera) {
            mCamera.close();
            mCamera = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        mCameraId = "";
        Camera2Utils.clearVariables();
    }

    private synchronized void captureStillPicture() {
        try {
            if (null == mCamera) {
                return;
            }
            mState = CAMERA_STATE_PICTURE_TAKEN;
            final CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    unlockFocus();
                    mCameraSound.playClick(0);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session,
                                            CaptureRequest request, CaptureFailure failure) {
                    unlockFocus();
                }
            };
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, mCameraBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void preCaptureSequence() {
        try {
            Camera2Utils.setCamera_AE_Trigger(mPreviewRequestBuilder);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            mState = CAMERA_STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mCameraBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startLockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            Camera2Utils.setCamera_AE_Trigger(mPreviewRequestBuilder);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            mState = CAMERA_STATE_WAITING_AF_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mCameraBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mCameraBgHandler);
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mCameraBgHandler);
            mState = CAMERA_STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private void createCameraPreviewSession() {
        SurfaceTexture texture = getSurfaceTexture();
        assert texture != null;
        calculatePreviewSize();
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        try {
            mPreviewRequestBuilder
                    = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Camera2Utils.setCameraTorchOn(mPreviewRequestBuilder);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallback, mCameraBgHandler);
            mState = CAMERA_STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rotation = display.getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void calculatePreviewSize() {
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] choices = map.getOutputSizes(ImageFormat.JPEG);
            for (Size size : choices) {
                if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                    mPreviewSize = new Size(size.getHeight(), size.getWidth());
                    break;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            mCamera = camera;
            Log.d(TAG, "Got Camera Device");
            createCameraPreviewSession();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCamera = null;
        }
    };

    /* callback to track the progress of CaptureSession */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            processResults(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            processResults(result);
        }

        private void processResults(CaptureResult r) {
            Integer afState = r.get(CaptureResult.CONTROL_AF_STATE);
            Integer aeState = r.get(CaptureResult.CONTROL_AE_STATE);

            switch (mState) {
                case CAMERA_STATE_WAITING_AF_LOCK:
                    if (afState == null) {
                        captureStillPicture();
                    } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            captureStillPicture();
                        } else {
                            preCaptureSequence();
                        }
                    }
                    break;
                case CAMERA_STATE_WAITING_PRECAPTURE:
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = CAMERA_STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                case CAMERA_STATE_WAITING_NON_PRECAPTURE:
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = CAMERA_STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
            }

        }
    };

    private final CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            mCaptureSession = session;
            try {
                if (null == mCamera) {
                    return;
                }
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mCameraBgHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            showToast(com.motorola.motocit.R.string.msg_fail_open_camera);
        }
    };

    private void showToast(final int text) {
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
    }

    /* TextureView */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            startBackgroundThread();
            mCameraSound = new CameraSound();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged " + width + " " + height);
            Camera2Utils.getPreviewOptimizedSize(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if(Camera2Utils.DBG) Log.d(TAG, "Image Captured");
        }
    };

    class CameraSound {
        private final String[] SOUND_FILES = {
                "/system/media/audio/ui/camera_click.ogg",
                "/system/media/audio/ui/camera_focus.ogg",
                "/system/media/audio/ui/VideoRecord.ogg",
                "/system/media/audio/ui/VideoRecord.ogg",
                "/system/media/audio/ui/multishot_click.ogg"
        };
        private SoundPool mSoundPool;
        private int[] ids;
        private boolean STATE_SOUND_LOADED = false;

        public CameraSound() {
            mSoundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
            ids = new int[SOUND_FILES.length];
            for (int i = 0; i < SOUND_FILES.length; i++) {
                ids[i] = mSoundPool.load(SOUND_FILES[i], 1);
            }
            STATE_SOUND_LOADED = true;
        }

        public void playClick(int index) {
            if (STATE_SOUND_LOADED) {
                mSoundPool.play(ids[index], 1.0f, 1.0f, 0, 0, 1.0f);
            }
        }

        public void release() {
            mSoundPool.release();
            STATE_SOUND_LOADED = false;
        }

        private SoundPool.OnLoadCompleteListener mLoadCompleteListener =
                new SoundPool.OnLoadCompleteListener() {
                    public void onLoadComplete(SoundPool soundPool,
                                               int sampleId, int status) {
                        STATE_SOUND_LOADED = true;
                    }
                };
    }
}
