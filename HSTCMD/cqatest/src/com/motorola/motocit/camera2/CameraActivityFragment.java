/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *    Date           CR                  Author                                  Description
 * 2017/01/26    IKSWN-21780    Guilherme Deo - guideo91       CQATest - video capture of 3 cameras on Golden Eagle
 */
package com.motorola.motocit.camera2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.TestUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraActivityFragment extends Fragment
            implements View.OnClickListener {

    public static final int TARGET_CAMERA_REAR_RGB = 0;
    public static final int TARGET_CAMERA_FRONT = 1;
    public static final int TARGET_CAMERA_REAR_MONO = 2;

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final String TAG = CameraActivityFragment.class.getSimpleName();
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 180);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 0);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 90);
    }

    private TextureView mTextureView;
    private Button mButtonVideo;
    private Button mButtonPlayback;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    private Size mPreviewSize;
    private Size mVideoSize;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    private boolean mFrontFacing = false;
    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;
    private Surface mRecorderSurface;

    // Because screen orientation is locked in landscape,
    // getDefaultDisplay().getRotation() always returns 1 (Surface.ROTATION_90),
    // listen to OrientationEventListener for nature device orientation
    private int mDeviceOrientation = 0;
    private DeviceOrientationEvent mDeviceOrientationEvent;
    class DeviceOrientationEvent extends OrientationEventListener {

        /**
         * Creates a new DeviceOrientationEvent.
         *
         * @param context for the DeviceOrientationEvent.
         */
        public DeviceOrientationEvent(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // Round orientation to 0°, 90°, 180°, and 270°
            mDeviceOrientation = ((orientation + 45) / 90 * 90) % 360;
        }
    }

    private String mCameraId;
    protected String[] mCameraIdList;
    protected HashMap mMap = new HashMap();
    protected boolean getCameraId(int targetCamera) {
        Activity activity = getActivity();
        boolean ret = false;
        try {
            CameraManager manager = null;
            manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            mCameraIdList = manager.getCameraIdList();
            TestUtils.dbgLog(TAG, "CameraId List" + Arrays.toString(mCameraIdList), 'd');
            if (mCameraIdList != null) {
                CameraCharacteristics c;
                int i = 0;
                for (String index : mCameraIdList) {
                    c = manager.getCameraCharacteristics(index);
                    Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                    if (null != lensFacing && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
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

    private static int mTargetCamera = 0;
    public static CameraActivityFragment newInstance(int targetCamera) {
        mTargetCamera = targetCamera;
        return new CameraActivityFragment();
    }

    private final String VIDEO_FILENAME = "CQATest_videocapture.mp4";
    private File mVideoFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + VIDEO_FILENAME);

    private MediaPlayer mMediaPlayer;
    private boolean mVideoPlaying = false;
    private boolean mSurfaceChange = false;

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        TestUtils.dbgLog(TAG, "Couldn't find any suitable video size", 'd');
        return choices[choices.length - 1];
    }


    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            TestUtils.dbgLog(TAG, "Couldn't find any suitable preview size", 'e');
            return choices[0];
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(com.motorola.motocit.R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (TextureView) view.findViewById(com.motorola.motocit.R.id.texture);
        mButtonVideo = (Button) view.findViewById(com.motorola.motocit.R.id.record);
        mButtonVideo.setOnClickListener(this);
        mButtonPlayback = (Button) view.findViewById(com.motorola.motocit.R.id.playback);
        mButtonPlayback.setOnClickListener(this);
        mShowTimeView = (TextView) view.findViewById(com.motorola.motocit.R.id.videocapturetextview_time);
        mDeviceOrientationEvent = new DeviceOrientationEvent(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        getCameraId(mTargetCamera);
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        mDeviceOrientationEvent.enable();
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        if (mVideoFile.exists())
        {
            mVideoFile.delete();
        }
        super.onPause();

        mDeviceOrientationEvent.disable();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case com.motorola.motocit.R.id.record: {
                if (mIsRecordingVideo && !mVideoPlaying) {
                    stopRecordingVideo();
                } else if (!mIsRecordingVideo && !mVideoPlaying){
                    startRecordingVideo();
                }
                break;
            }
            case com.motorola.motocit.R.id.playback: {
                if(!mIsRecordingVideo && !mVideoPlaying){
                    startVideoPlayback();
                }else if(!mIsRecordingVideo && mVideoPlaying){
                    stopVideoPlayback();
                }
                break;
            }
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        TestUtils.dbgLog(TAG, "onRequestPermissionsResult", 'd');
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance("This sample needs permission for camera and audio recording.")
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance("This sample needs permission for camera and audio recording.")
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (getActivity().checkSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            TestUtils.dbgLog(TAG, "tryAcquire", 'd');
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            mFrontFacing = (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT);

            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(mCameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance("This device does not support Camera2 API.")
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            TestUtils.dbgLog(TAG, "startPreview: Something is wrong", 'd');
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
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

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity());
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int degrees = 0;
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                degrees = DEFAULT_ORIENTATIONS.get(rotation);
                // Front camera, device orientation in landscape (90° 0r 270°)
                if (mFrontFacing && (mDeviceOrientation == 90 || mDeviceOrientation == 270)) {
                    degrees = degrees + 180;
                }
                mMediaRecorder.setOrientationHint(degrees);
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                degrees = INVERSE_ORIENTATIONS.get(rotation);
                // Front camera, device orientation in landscape (90° 0r 270°)
                if (mFrontFacing && (mDeviceOrientation == 90 || mDeviceOrientation == 270)) {
                    degrees = degrees - 180;
                }
                mMediaRecorder.setOrientationHint(degrees);
                break;
            default:
                break;
        }

        mMediaRecorder.prepare();
    }

    private String getVideoFilePath(Context context) {
        return mVideoFile.getAbsolutePath();
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            if (mVideoFile.exists())
            {
                mVideoFile.delete();
            }
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            mRecorderSurface = mMediaRecorder.getSurface();
            surfaces.add(mRecorderSurface);
            mPreviewBuilder.addTarget(mRecorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            mButtonVideo.setText("stop capture");
                            mButtonVideo.setBackgroundColor(Color.RED);
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                            updateRecordingOrPlayingTime();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mButtonVideo.setText("start capture");
        mButtonVideo.setBackgroundColor(Color.WHITE);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Activity activity = getActivity();
        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + mNextVideoAbsolutePath,
                    Toast.LENGTH_SHORT).show();
            TestUtils.dbgLog(TAG, "Video saved: " + mNextVideoAbsolutePath, 'd');
        }
        mNextVideoAbsolutePath = null;
        startPreview();
    }

    private void startVideoPlayback()
    {
        TestUtils.dbgLog(TAG, "startVideoPlayback", 'd');
        closeCamera();

        if (!mVideoFile.exists())
        {
            TestUtils.dbgLog(TAG, "video file does not exist", 'd');
            stopVideoPlayback();
            return;
        }

        mVideoPlaying = true;
        if (mMediaPlayer == null)
        {
            mMediaPlayer = new MediaPlayer();
        }
        else
        {
            mMediaPlayer.reset();
        }

        try
        {
            mMediaPlayer.setDataSource(mVideoFile.getAbsolutePath());
            TestUtils.dbgLog(TAG, "set play source", 'd');
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        catch (IllegalStateException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        Surface surface = new Surface(texture);
        mMediaPlayer.setSurface(surface);
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                Activity activity = getActivity();
                Toast.makeText(activity, "Video Playback Completed", Toast.LENGTH_SHORT).show();
                stopVideoPlayback();
            }
        });

        try
        {
            mMediaPlayer.prepare();
        }
        catch (IOException e)
        {
            TestUtils.dbgLog(TAG, "preparing video file failed " + mVideoFile, 'e');
            stopVideoPlayback();
        }
        catch (IllegalStateException e)
        {
            TestUtils.dbgLog(TAG, "preparing video file failed " + mVideoFile, 'e');
        }

        try
        {
            mMediaPlayer.start(); // playing is now started
            mButtonPlayback.setText("stop playback");
            mButtonPlayback.setBackgroundColor(Color.RED);
        }
        catch (IllegalStateException e)
        {
            TestUtils.dbgLog(TAG, "mediaRlayer start failed, exception:"+e.getMessage(), 'e');
            stopVideoPlayback();
            return;
        }
        catch (RuntimeException e)
        {
            TestUtils.dbgLog(TAG, "Could not play video", 'e');
            stopVideoPlayback();
            return;
        }
        catch (Exception e)
        {
            TestUtils.dbgLog(TAG, "Could not play video", 'e');
            stopVideoPlayback();
            return;
        }

        updateRecordingOrPlayingTime();
    }

    private void stopVideoPlayback()
    {
        TestUtils.dbgLog(TAG, "stopVideoPlayback", 'd');

        if (mVideoPlaying)
        {
            mMediaPlayer.setOnErrorListener(null);
            mMediaPlayer.setOnInfoListener(null);
            try
            {
                mMediaPlayer.stop();
            }
            catch (IllegalStateException e)
            {
                TestUtils.dbgLog(TAG, "play stop fail, IllegalStateException: " + e.getMessage(), 'e');
                return;
            }
            catch (RuntimeException e)
            {
                TestUtils.dbgLog(TAG, "play stop fail, RuntimeException: " + e.getMessage(), 'e');
                return;
            }
            catch (Exception e)
            {
                TestUtils.dbgLog(TAG, "play stop fail, Exception: " + e.getMessage(), 'e');
            }
            mVideoPlaying = false;
            mButtonPlayback.setText("start playback");
            mButtonPlayback.setBackgroundColor(Color.WHITE);
        }
        releaseMediaPlayer();

        // Add this just for triggering surfaceChanged to restart camera
        // preview.
        openCamera(mTextureView.getWidth(), mTextureView.getHeight());
    }

    private void releaseMediaPlayer()
    {
        TestUtils.dbgLog(TAG, "Releasing media player", 'd');
        if (mMediaPlayer != null)
        {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public void stopRecordingOrPlayback(){
        if (mIsRecordingVideo && !mVideoPlaying){
            stopRecordingVideo();
        }else if (!mIsRecordingVideo && mVideoPlaying){
            stopVideoPlayback();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage("This sample needs permission for camera and audio recording.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }

    //Update timer
    private long mStartTime;
    private TextView mShowTimeView;
    private final Handler mHandler = new MainHandler();
    private static final int UPDATE_TIME = 1;

    private class MainHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {

                case UPDATE_TIME:
                    updateRunningTime();
                    break;

                default:
                    TestUtils.dbgLog(TAG, "Unhandled message: " + msg.what, 'd');
                    break;
            }
        }
    }

    private void updateRecordingOrPlayingTime()
    {
        TestUtils.dbgLog(TAG, "updateRecordingOrPlayingTime called", 'd');
        mStartTime = SystemClock.uptimeMillis();
        mShowTimeView.setText("");
        mShowTimeView.setVisibility(View.VISIBLE);
        updateRunningTime();
    }

    private void updateRunningTime()
    {
        TestUtils.dbgLog(TAG, "updateRunningTime called", 'd');

        if (!mIsRecordingVideo && !mVideoPlaying)
        {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mStartTime;
        long next_update_delay = 1000 - (delta % 1000);
        long seconds = delta / 1000; // round to nearest

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2)
        {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2)
        {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0)
        {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2)
            {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
        }

        mShowTimeView.setText(text);
        mHandler.sendEmptyMessageDelayed(UPDATE_TIME, next_update_delay);
    }
}
