/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
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
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Camera2Activity extends Test_Base {
    public final int LED_STATE_NONE = 0;
    public final int LED_STATE_TORCH = 1;
    public final int LED_STATE_FLASH = 2;
    public final int TARGET_CAMERA_REAR_RGB = 0;
    public final int TARGET_CAMERA_FRONT = 1;
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
    private String mCameraId;
    private static int mTargetCamera = 0;
    private String[] mCameraIdlist;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImagerReader;
    private HandlerThread mBgThread;
    private Handler mCameraBgHandler;
    private CaptureRequest mPreviewRequest;
    private int mState;
    private CameraSound mCameraSound;
    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera2_basic);
        mContext = this;
        mTextureView = (TextureView) findViewById(R.id.texture);
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (Camera2Utils.getTakePictureRequest()) {
                        if (mState == CAMERA_STATE_PREVIEW) {
                            startLockFocus();
                        }
                    }
                }
                return true;
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < 23) {
            isPermissionAllowed = true;
        } else {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
            } else {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed) {
            manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            getdCameraId(mTargetCamera);
            startBackgroundThread();
            mCameraSound = new CameraSound();
            if (mTextureView.isAvailable()) {
                openCamera();
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
            sendStartActivityPassed();
        } else {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }

    }

    @Override
    protected void onPause() {
        if (isPermissionAllowed) {
            release();
        }
        super.onPause();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS")) {

        } else if (strRxCmd.equalsIgnoreCase("help")) {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        } else {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp() {
        List<String> strHelpList = new ArrayList<String>();
        strHelpList.add(mHelpMessage[0]);
        strHelpList.add("");
        strHelpList.add(mHelpMessage[1]);
        strHelpList.add("");
        strHelpList.addAll(getBaseHelp());
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        for(int i = 2; i < mHelpMessage.length; i++) {
            strHelpList.add(mHelpMessage[i]);
        }
        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS")) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            contentRecord("testresult.txt", "Camera - " + TAG + ":  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);
            try {
                Thread.sleep(1000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            contentRecord("testresult.txt", "Camera - " + TAG + ":  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);
            try {
                Thread.sleep(1000, 0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (modeCheck("Seq")) {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();
                return false;
            } else {
                systemExitWrapper(0);
            }
        }

        return true;
    }
    @Override
    protected boolean onSwipeRight() {
        return true;
    }

    @Override
    protected boolean onSwipeLeft() {
        return true;
    }

    @Override
    protected boolean onSwipeDown() {
        return true;
    }

    @Override
    protected boolean onSwipeUp() {
        return true;
    }

    protected void configureTargetCamera(int targetCamera, int ledStatus) {
        mTargetCamera = targetCamera;
        setCameraLedStatus(ledStatus);
    }

    private void release() {
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

    /*
    1. get camera ID by cameraManager
    2. create a CameraDevice.StateCallback
    3. Open camera, in StateCallback, it will get the object of camera device
    4. check the characteristics by getCameraCharacteristics
    5. create a capture session with a output surface (appropriate size)
           createCaptureSession(List, CaptureSession.StateCallback, Handler)
    6. Take a picture with CaptureRequest
     */
    private HashMap mMap = new HashMap();
    private boolean getdCameraId(int targetCamera) {
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
            showToast(R.string.msg_no_camera);
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
        mImagerReader = ImageReader.newInstance(Camera2Utils.getCameraMaxResoultions().getWidth(),
                Camera2Utils.getCameraMaxResoultions().getHeight(), ImageFormat.JPEG, 2);
        mImagerReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraBgHandler);
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
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImagerReader) {
            mImagerReader.close();
            mImagerReader = null;
        }
        mCameraId = "";
        Camera2Utils.clearVariables();
    }

    private synchronized void  captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            mState = CAMERA_STATE_PICTURE_TAKEN;
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImagerReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    unlockFocus();
                    mCameraSound.playClick(0);
                    showToast(R.string.tapScreenToFireStobe);
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
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        assert texture != null;
        calculatePreviewSize();
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);
        try {
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Camera2Utils.setCameraTorchOn(mPreviewRequestBuilder);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImagerReader.getSurface()), mSessionStateCallback, mCameraBgHandler);
            mState = CAMERA_STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void calculatePreviewSize() {
        Point displaySize = new Point();
        this.getWindowManager().getDefaultDisplay().getSize(displaySize);
        mPreviewSize = Camera2Utils.getPreviewOptimizedSize(displaySize.x, displaySize.y);
        if (Camera2Utils.DBG)
            Log.d(TAG, "W x H :" + mPreviewSize.getWidth() + " x " + mPreviewSize.getHeight());

    }

    private final CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            Log.d(TAG, "Got Camera Device");
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
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
                if (null == mCameraDevice) {
                    return;
                }
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        mCaptureCallback, mCameraBgHandler);
                if (Camera2Utils.getCameraStrobeRequest()) {
                    showToast(R.string.tapScreenToFireStobe);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            showToast(R.string.msg_fail_open_camera);
        }
    };

    private void showToast(final int text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* TextureView */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
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
            Image mImage = reader.acquireNextImage();
            if (null != mImage) {
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                mImage.close();
            }
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
