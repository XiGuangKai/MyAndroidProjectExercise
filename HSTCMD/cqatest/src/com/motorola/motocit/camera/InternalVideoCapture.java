/*
 * Copyright (c) 2014 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.FloatMath;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class InternalVideoCapture extends Test_Base implements
        MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnInfoListener,
        OnCompletionListener
{

    boolean isSupport = false;
    private InternalVideoCapturePreview internalViewfinderPreview;
    private Camera internalCamera = null;
    int numberOfCameras;
    int cameraCurrentlyLocked;
    // The first rear facing camera
    int defaultCameraId;
    int mOrientation;
    Button startCapture;
    Button startPlayback;
    TextView timeUI;
    private MediaPlayer mMediaRlayer = null;
    private MediaRecorder mMediaRecorder = null;
    private boolean mMediaRecorderRecording = false;
    private boolean mVideoPlaying = false;
    private boolean mVideoPlayingFinished = false;
    private boolean mPreviewing = false;

    private ZoomListenter zoomListener = null;

    private long mStartTime;
    private String mVideoPath;
    private File mVideoFile;
    private final String VIDEO_FILENAME = "CQATest_InternalVideoCapture.mp4";
    private final long MAX_FILESIZE_IN_BYTE = 10 * 60 * 500 * 1024; // 293M.
    private final long MIN_FILESIZE_IN_BYTE = 500 * 1024 * 10;
    private final Handler mHandler = new MainHandler();
    private static final int UPDATE_TIME = 1;
    private final int MAX_DURATION_IN_MS = 10 * 60 * 1000;

    // Following messages are defined for MTK products ONLY
    private final int MEDIA_RECORDER_INFO_FPS_ADJUSTED_MTK = 897;
    private final int MEDIA_RECORDER_INFO_BITRATE_ADJUSTED_MTK = 898;
    private final int MEDIA_RECORDER_INFO_WRITE_SLOW_MTK = 899;
    private final int MEDIA_RECORDER_INFO_START_TIMER_MTK = 1998;
    private final int MEDIA_RECORDER_INFO_CAMERA_RELEASE = 1999;

    // Define MEDIA_INFO_VIDEO_RENDERING_START in case API level is 16
    private final int MEDIA_INFO_VIDEO_RENDERING_START = 3;

    private Camera.Size mCameraSize = null;
    private SurfaceHolder mSurfaceHolder = null;
    private SurfaceView mSurfaceView = null;
    private boolean mSurfaceChange = false;
    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private boolean isPermissionAllowed = false;
    private boolean isPermissionAllowedForCamera = false;
    private boolean isPermissionAllowedForAudio = false;
    private String[] permissions = { "android.permission.CAMERA", "android.permission.RECORD_AUDIO" };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_Internal_VideoCapture";
        super.onCreate(savedInstanceState);
        /*
         * // Hide the window title.
         * requestWindowFeature(Window.FEATURE_NO_TITLE);
         * Window window = getWindow(); // keep klocwork happy
         * if (null != window)
         * {
         * window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
         * }
         */
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

        dbgLog(TAG, "mDegrees: " + mDegrees + " mResult: " + mResult, 'd');
        return mResult;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (1001 == requestCode)
        {
            for (int i = 0; i < permissions.length; i++)
            {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                {
                    if (permissions[i] == Manifest.permission.CAMERA)
                    {
                        isPermissionAllowedForCamera = true;
                        dbgLog(TAG, "isPermissionAllowedForCamera=true", 'i');
                    }

                    if (permissions[i] == Manifest.permission.RECORD_AUDIO)
                    {
                        isPermissionAllowedForAudio = true;
                        dbgLog(TAG, "isPermissionAllowedForAudio=true", 'i');
                    }
                }
                else
                {
                    dbgLog(TAG, "deny permission", 'i');
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
            isPermissionAllowedForCamera = true;
            isPermissionAllowedForAudio = true;
        }
        else
        {
            // check permissions on M release
            ArrayList<String> requestPermissions = new ArrayList();

            if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED)
            {
                isPermissionAllowedForCamera = false;
                requestPermissions.add(permissions[0]);
            }
            else
            {
                isPermissionAllowedForCamera = true;
            }

            if (checkSelfPermission(permissions[1]) != PackageManager.PERMISSION_GRANTED)
            {
                isPermissionAllowedForAudio = false;
                requestPermissions.add(permissions[1]);
            }
            else
            {
                isPermissionAllowedForAudio = true;
            }

            if (!requestPermissions.isEmpty())
            {
                // Permission has not been granted and must be requested.
                String[] params = requestPermissions.toArray(new String[requestPermissions.size()]);
                dbgLog(TAG, "requesting permissions", 'i');
                requestPermissions(params, 1001);
                return;
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if ((isPermissionAllowedForCamera && isPermissionAllowedForAudio) || isPermissionAllowed)
        {
            startInternalCamera();
        }
        else
        {
            dbgLog(TAG, "no permission granted to run camera test", 'e');
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        dbgLog(TAG, "onPause", 'v');
        if (isPermissionAllowed)
        {
            if (mMediaRecorderRecording == true)
            {
                stopVideoRecording();
            }

            if (mVideoPlaying == true)
            {
                stopVideoPlayback();
            }

            // Because the Camera object is a shared resource, it's very
            // important to release it when the activity is paused.
            if (internalCamera != null)
            {
                internalViewfinderPreview.setCamera(null);
                internalCamera.release();
                internalCamera = null;
            }

            // delete video file
            if (mVideoFile.exists())
            {
                dbgLog(TAG, "delete video file.", 'd');
                mVideoFile.delete();

                // IKSWL-32739 scan media file to sync with file system
                try
                {
                    dbgLog(TAG, "force scan media file", 'i');
                    MediaScannerConnection.scanFile(getApplicationContext(), new String[] { mVideoPath }, null, null);
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "exception occurred", 'e');
                    dbgLog(TAG, e.toString(), 'e');
                }

                return;
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

    public class ZoomListenter implements OnTouchListener
    {

        private int mode = 0;
        float oldDist;

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            switch (event.getAction() & MotionEvent.ACTION_MASK)
            {
                case MotionEvent.ACTION_DOWN:
                    mode = 1;
                    break;
                case MotionEvent.ACTION_UP:
                    mode = 0;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    mode -= 1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist = spacing(event);
                    mode += 1;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode >= 2)
                    {
                        float newDist = spacing(event);
                        if (newDist > oldDist + 30)
                        {
                            zoom(true);
                            oldDist = newDist;
                        }
                        if (newDist < oldDist - 30)
                        {
                            zoom(false);
                            oldDist = newDist;
                        }
                    }
                    break;
            }
            return true;
        }

        private void zoom(boolean zoomIn)
        {
            Camera.Parameters params;
            params = internalCamera.getParameters();
            boolean isSmoothZoomSupported = params.isSmoothZoomSupported();
            int zoom = params.getZoom();
            int zoomMax = params.getMaxZoom();
            if (zoomIn && (zoom < zoomMax))
            {
                zoom += 2;
                dbgLog(TAG, "ZoomIn, setting to " + zoom, 'i');

            }
            else if (!zoomIn)
            {
                zoom -= 2;
                dbgLog(TAG, "ZoomOut, setting to " + zoom, 'i');
            }
            else
            {
                dbgLog(TAG, "zoom exceed the max value", 'e');
            }

            if (isSmoothZoomSupported && (zoom <= params.getMaxZoom()) && (zoom >= 0))
            {
                internalCamera.startSmoothZoom(zoom);
                internalCamera.setZoomChangeListener(new Camera.OnZoomChangeListener() {

                    @Override
                    public void onZoomChange(int zoomValue, boolean stopped, Camera camera)
                    {
                        if (stopped)
                        {
                            camera.stopSmoothZoom();
                        }
                    }
                });
            }
        }

        private float spacing(MotionEvent event)
        {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return FloatMath.sqrt(x * x + y * y);
        }
    }

    private void startInternalCamera()
    {
        dbgLog(TAG, "starting internal camera", 'i');
        // Create main layout
        RelativeLayout llmain = new RelativeLayout(this);

        // Create a RelativeLayout container that will hold a SurfaceView,
        // and set it as the content of our activity.
        internalViewfinderPreview = new InternalVideoCapturePreview(this);
        internalViewfinderPreview.setId(10000);

        RelativeLayout.LayoutParams lpSurface = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lpSurface.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        llmain.addView(internalViewfinderPreview, lpSurface);

        setContentView(llmain);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        if (mGestureListener != null)
        {
            internalViewfinderPreview.setOnTouchListener(mGestureListener);
        }

        // Dynamically create capture button
        startCapture = new Button(this);
        RelativeLayout.LayoutParams lpCapture = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCapture.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        startCapture.setId(10001);
        startCapture.setText("Start Capture");
        startCapture.getBackground().setAlpha(100);
        startCapture.setGravity(Gravity.CENTER_VERTICAL);
        llmain.addView(startCapture, lpCapture);

        // Dynamically create playback button
        startPlayback = new Button(this);
        RelativeLayout.LayoutParams lpPlayback = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPlayback.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        startPlayback.setId(10002);
        startPlayback.setText("Start Playback");
        startPlayback.getBackground().setAlpha(100);
        startPlayback.setGravity(Gravity.CENTER_VERTICAL);
        llmain.addView(startPlayback, lpPlayback);

        // Dynamically create timer text view
        timeUI = new TextView(this);
        RelativeLayout.LayoutParams lpTimeUI = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTimeUI.addRule(RelativeLayout.CENTER_HORIZONTAL);
        timeUI.setId(10003);
        timeUI.setTextColor(Color.parseColor("#7FFFD4"));
        timeUI.setGravity(Gravity.CENTER_VERTICAL);
        timeUI.setTextSize(30);
        llmain.addView(timeUI, lpTimeUI);

        // Implement zoom function
        zoomListener = new ZoomListenter();

        internalViewfinderPreview.setOnTouchListener(zoomListener);

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
            Toast.makeText(InternalVideoCapture.this, "The device does NOT support Internal Camera", Toast.LENGTH_SHORT).show();
            InternalVideoCapture.this.finish();
        }

        mVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
        dbgLog(TAG, "output path: " + mVideoPath, 'd');
        mVideoFile = new File(mVideoPath + VIDEO_FILENAME);

        startCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                // avoid misoperation.
                if (mVideoPlaying)
                {
                    Toast.makeText(InternalVideoCapture.this, "Playing video... Pls stop playback firstly before capture.", Toast.LENGTH_LONG).show();
                    return;
                }

                // set playback status to false
                mVideoPlayingFinished = false;

                if (!mMediaRecorderRecording)
                {
                    startVideoRecording();
                    if (mMediaRecorderRecording == true)
                    {
                        startCapture.setText("stop capture");
                        startCapture.setBackgroundColor(Color.RED);
                        startCapture.getBackground().setAlpha(100);
                    }
                }
                else
                {
                    stopVideoRecording();
                }
            }
        });

        startPlayback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                // avoid misoperation.
                if (mMediaRecorderRecording)
                {
                    Toast.makeText(InternalVideoCapture.this, "Recording video... Pls playback after recording complete.", Toast.LENGTH_LONG).show();
                    return;
                }

                // set playback status to false
                mVideoPlayingFinished = false;

                if (!mVideoPlaying)
                {
                    startVideoPlayback();
                    if (mVideoPlaying == true)
                    {
                        startPlayback.setText("stop playback");
                        startPlayback.setBackgroundColor(Color.RED);
                        startPlayback.getBackground().setAlpha(100);
                    }
                }
                else
                {
                    stopVideoPlayback();
                }
            }
        });

        // Open the default i.e. the first rear facing camera.
        internalCamera = openWrapper(defaultCameraId);
        cameraCurrentlyLocked = defaultCameraId;
        internalViewfinderPreview.setCamera(internalCamera);

        mSurfaceHolder = internalViewfinderPreview.mHolder;
        mSurfaceView = internalViewfinderPreview.mSurfaceView;
        mSurfaceWidth = mSurfaceView.getWidth();
        mSurfaceHeight = mSurfaceView.getHeight();

        sendStartActivityPassed();
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
        finally
        {
            dbgLog(TAG, "Error opening camera by wrapper, using camera open instead", 'e');
            camera = Camera.open(cameraId);
        }
        return camera;
    }

    // -----------------------------------------------------------------------------------------------
    private void startVideoRecording()
    {

        dbgLog(TAG, "startVideoRecording", 'v');
        if (mSurfaceHolder != null)
        {
            // Check if sdcard is mounted.
            if (!(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)))
            {
                dbgLog(TAG, "No sdcard mounted, exit recording.", 'v');
                Toast.makeText(InternalVideoCapture.this, "No sdcard. Exit recording.", Toast.LENGTH_LONG).show();
                return;
            }

            // Check if storage is enough to store video.
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
            long avaliableStorage = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
            if (avaliableStorage < MIN_FILESIZE_IN_BYTE)
            {
                dbgLog(TAG, "storage is not enough, exit recording.", 'v');
                Toast.makeText(InternalVideoCapture.this, "No enough storage. Exit recording.", Toast.LENGTH_LONG).show();
                return;
            }

            if (mVideoFile.exists())
            {
                mVideoFile.delete();
            }

            mMediaRecorderRecording = true;

            if (mMediaRecorder == null)
            {
                mMediaRecorder = new MediaRecorder();
            }
            else
            {
                mMediaRecorder.reset();
            }

            if (internalCamera == null)
            {
                internalCamera = openWrapper(defaultCameraId);
                cameraCurrentlyLocked = defaultCameraId;
                internalViewfinderPreview.setCamera(internalCamera);
            }

            Camera.Parameters params = internalCamera.getParameters();
            int frameRate = params.getPreviewFrameRate();

            if (mPreviewing == true)
            {
                internalCamera.stopPreview();
                mPreviewing = false;
            }

            internalCamera.unlock();
            mMediaRecorder.setCamera(internalCamera);
            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

            if (Build.VERSION.SDK_INT >= 8)
            {
                CamcorderProfile mCamcorderProfile = CamcorderProfile.get(defaultCameraId, CamcorderProfile.QUALITY_HIGH);
                if (mCamcorderProfile == null)
                {
                    dbgLog(TAG, "Failed to get Camcorder Profile, exit recording.", 'v');
                    Toast.makeText(InternalVideoCapture.this, "Camcorder Profile error. Exit recording.", Toast.LENGTH_LONG).show();
                    return;
                }

                mMediaRecorder.setProfile(mCamcorderProfile);
                internalViewfinderPreview.setPlaybackSize(mCamcorderProfile.videoFrameWidth, mCamcorderProfile.videoFrameHeight);
            }
            else
            {
                mCameraSize = params.getPreferredPreviewSizeForVideo();
                // mCameraSize =
                // internalViewfinderPreview.getOptimalPreviewSize(sizes, w, h);
                if (mCameraSize == null)
                {
                    dbgLog(TAG, "Failed to get preferred preview size, exit recording.", 'v');
                    Toast.makeText(InternalVideoCapture.this, "Preview size error. Exit recording.", Toast.LENGTH_LONG).show();
                    return;
                }

                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setVideoSize(mCameraSize.width, mCameraSize.height);
                internalViewfinderPreview.setPlaybackSize(mCameraSize.width, mCameraSize.height);
                mMediaRecorder.setVideoFrameRate(frameRate);
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            }

            mMediaRecorder.setMaxDuration(MAX_DURATION_IN_MS);
            mMediaRecorder.setMaxFileSize(MAX_FILESIZE_IN_BYTE);
            mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());
            mMediaRecorder.setOrientationHint(180);
            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.setOnInfoListener(this);

            try
            {
                mMediaRecorder.prepare();
            }
            catch (IOException e)
            {
                dbgLog(TAG, "prepare failed for " + mVideoFile, 'e');
                stopVideoRecording();
                throw new RuntimeException(e);
            }

            try
            {
                mMediaRecorder.start(); // Recording is now started
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "Could not start media recorder. ", 'e');
                stopVideoRecording();
                return;
            }

            updateRecordingOrPlayingTime();
        }
    }

    private void stopVideoRecording()
    {
        dbgLog(TAG, "stopVideoRecording", 'v');

        if (mMediaRecorderRecording)
        {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            try
            {
                mMediaRecorder.stop();
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "stop fail: " + e.getMessage(), 'e');
            }
            mMediaRecorderRecording = false;
            startCapture.setText("start capture");
            startCapture.setBackgroundColor(Color.WHITE);
            startCapture.getBackground().setAlpha(100);
        }
        releaseMediaRecorder();
    }

    private void releaseMediaRecorder()
    {
        dbgLog(TAG, "Releasing media recorder.", 'v');
        if (mMediaRecorder != null)
        {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }

        if (internalCamera != null)
        {
            internalCamera.lock();
        }
    }

    private void closeCamera()
    {
        dbgLog(TAG, "close Camera", 'v');
        if (internalCamera == null)
        {
            dbgLog(TAG, "already stopped.", 'v');
            return;
        }

        if (mPreviewing == true)
        {
            internalCamera.stopPreview();
            mPreviewing = false;
        }

        internalCamera.release();
        internalCamera = null;
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra)
    {
        dbgLog(TAG, "MediaRecorder onError = " + what, 'v');
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN)
        {
            // may have run out of space on the sdcard.
            stopVideoRecording();
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra)
    {
        dbgLog(TAG, "MediaRecorder OnInfo.", 'v');
        boolean stopRecord = true;
        switch (what)
        {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
            {
                dbgLog(TAG, "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED.", 'v');
                Toast.makeText(InternalVideoCapture.this, "Reached max recording time.", Toast.LENGTH_LONG).show();
                break;
            }

            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
            {
                dbgLog(TAG, "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED", 'v');
                Toast.makeText(InternalVideoCapture.this, "Reached max file size.", Toast.LENGTH_LONG).show();
                break;
            }
            // When receiving following MTK message, ignore it.
            case MEDIA_RECORDER_INFO_FPS_ADJUSTED_MTK:
            case MEDIA_RECORDER_INFO_BITRATE_ADJUSTED_MTK:
            case MEDIA_RECORDER_INFO_WRITE_SLOW_MTK:
            case MEDIA_RECORDER_INFO_START_TIMER_MTK:
            case MEDIA_RECORDER_INFO_CAMERA_RELEASE:
            {
                dbgLog(TAG, "MTK Specific message received, ignore it. MediaRecorder onInfo=" + what, 'v');
                stopRecord = false;
                break;
            }

            default:
            {
                dbgLog(TAG, "Unknown message received. MediaRecorder onInfo=" + what, 'v');
                break;
            }
        }

        if (stopRecord)
        {
            if (mMediaRecorderRecording)
            {
                stopVideoRecording();
            }
        }
    }

    // --------------------------------------------------------------------------------------------------
    private void startVideoPlayback()
    {

        dbgLog(TAG, "startVideoPlayback", 'v');
        closeCamera();

        if (!mVideoFile.exists())
        {
            dbgLog(TAG, "video file does not exist.", 'v');
            Toast.makeText(InternalVideoCapture.this, "video file does not exist.", Toast.LENGTH_LONG).show();
            stopVideoPlayback();
            return;
        }

        // Stop zoom function when playback
        internalViewfinderPreview.setOnTouchListener(null);

        mVideoPlaying = true;
        if (mMediaRlayer == null)
        {
            mMediaRlayer = new MediaPlayer();
        }
        else
        {
            mMediaRlayer.reset();
        }

        mMediaRlayer.setOnCompletionListener(this);
        mMediaRlayer.setOnErrorListener(this);
        mMediaRlayer.setOnInfoListener(this);
        internalViewfinderPreview.setPlayback();

        try
        {
            mMediaRlayer.setDataSource(mVideoPath + VIDEO_FILENAME);
            dbgLog(TAG, "set play source", 'v');
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

        mMediaRlayer.setDisplay(mSurfaceHolder);
        mMediaRlayer.setScreenOnWhilePlaying(true);

        try
        {
            mMediaRlayer.prepare();
        }
        catch (IOException e)
        {
            dbgLog(TAG, "prepare failed for " + mVideoFile, 'e');
            stopVideoPlayback();
            throw new RuntimeException(e);
        }

        try
        {
            mMediaRlayer.start(); // playing is now started
        }
        catch (RuntimeException e)
        {
            dbgLog(TAG, "Could not play video. ", 'e');
            stopVideoPlayback();
            return;
        }

        updateRecordingOrPlayingTime();
    }

    private void stopVideoPlayback()
    {
        dbgLog(TAG, "stopVideoPlayback", 'v');

        if (mVideoPlaying)
        {
            mMediaRlayer.setOnErrorListener(null);
            mMediaRlayer.setOnInfoListener(null);
            try
            {
                mMediaRlayer.stop();
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "stop fail: " + e.getMessage(), 'e');
            }
            mVideoPlaying = false;
            startPlayback.setText("start playback");
            startPlayback.setBackgroundColor(Color.WHITE);
            startPlayback.getBackground().setAlpha(100);
        }
        releaseMediaPlayer();

        if (internalViewfinderPreview == null)
        {
            dbgLog(TAG, "internalViewfinderPreview=null", 'i');
        }
        else
        {
            dbgLog(TAG, "internalViewfinderPreview!=null", 'i');
        }

        if (internalCamera == null)
        {
            dbgLog(TAG, "internalCamera=null", 'i');
            internalCamera = openWrapper(defaultCameraId);
            cameraCurrentlyLocked = defaultCameraId;
            internalViewfinderPreview.setCamera(internalCamera);

            Camera.Parameters parameters = internalCamera.getParameters();
            parameters.setPreviewSize(internalViewfinderPreview.mPreviewSize.width, internalViewfinderPreview.mPreviewSize.height);
            internalCamera.setParameters(parameters);
        }

        try
        {
            internalCamera.setPreviewDisplay(mSurfaceHolder);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        internalCamera.startPreview();

        if (zoomListener == null)
        {
            zoomListener = new ZoomListenter();
        }

        internalViewfinderPreview.setOnTouchListener(zoomListener);
    }

    private void releaseMediaPlayer()
    {
        dbgLog(TAG, "Releasing media player.", 'v');
        if (mMediaRlayer != null)
        {
            mMediaRlayer.reset();
            mMediaRlayer.release();
            mMediaRlayer = null;
        }
    }

    @Override
    public boolean onInfo(MediaPlayer player, int whatInfo, int extra)
    {
        dbgLog(TAG, " Mediaplay onInfo", 'v');
        boolean stopPlayback = true;
        switch (whatInfo)
        {
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                dbgLog(TAG, "MEDIA_INFO_BAD_INTERLEAVING", 'v');
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                dbgLog(TAG, "MEDIA_INFO_METADATA_UPDATE", 'v');
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                dbgLog(TAG, "Ignore info code of MEDIA_INFO_VIDEO_TRACK_LAGGING, continue playing", 'v');
                stopPlayback = false;
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                dbgLog(TAG, "MEDIA_INFO_NOT_SEEKABLE", 'v');
                break;

            case MEDIA_INFO_VIDEO_RENDERING_START:
                dbgLog(TAG, "Ignore info code of MEDIA_INFO_VIDEO_RENDERING_START, continue playing", 'v');
                stopPlayback = false;
                break;

            default:
                dbgLog(TAG, "Unknown MediaPlayer message received. MediaPlayer onInfo=" + whatInfo, 'v');
                break;
        }

        if (stopPlayback)
        {
            if (mVideoPlaying)
            {
                stopVideoPlayback();
            }
        }

        return true;
    }

    @Override
    public boolean onError(MediaPlayer player, int whatError, int extra)
    {
        dbgLog(TAG, " Mediaplay onError = " + whatError, 'v');
        switch (whatError)
        {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                dbgLog(TAG, "MEDIA_ERROR_SERVER_DIED", 'v');
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                dbgLog(TAG, "MEDIA_ERROR_UNKNOWN", 'v');
                break;
            default:
                break;
        }

        if (mVideoPlaying)
        {
            stopVideoPlayback();
        }

        return true;
    }

    @Override
    public void onCompletion(MediaPlayer player)
    {
        dbgLog(TAG, "onCompletion called", 'v');
        mVideoPlayingFinished = true;
        Toast.makeText(InternalVideoCapture.this, "playing video complete.", Toast.LENGTH_LONG).show();
        stopVideoPlayback();
        return;
    }

    // ------------------------------------------------------------------------------------------

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
                    dbgLog(TAG, "Unhandled message: " + msg.what, 'v');
                    break;
            }
        }
    }

    private void updateRecordingOrPlayingTime()
    {
        dbgLog(TAG, "updateRecordingOrPlayingTime called", 'v');
        mStartTime = SystemClock.uptimeMillis();
        timeUI.setText("");
        timeUI.setVisibility(View.VISIBLE);
        updateRunningTime();
    }

    private void updateRunningTime()
    {
        dbgLog(TAG, "updateRunningTime called", 'v');

        if (!mMediaRecorderRecording && !mVideoPlaying)
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

        timeUI.setText(text);
        mHandler.sendEmptyMessageDelayed(UPDATE_TIME, next_update_delay);
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

            contentRecord("testresult.txt", "Camera - Internal Video Capture:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Internal Video Capture:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        if (strRxCmd.equalsIgnoreCase("START_VIDEO_RECORD"))
        {
            mVideoPlayingFinished = false;
            List<String> strReturnDataList = new ArrayList<String>();
            if (mMediaRecorderRecording)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_RECORDING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mVideoPlaying)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_PLAYING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                UpdateUiButton updateUi = new UpdateUiButton(startCapture, true);
                runOnUiThread(updateUi);

                // Generate an exception to send data back to CommServer
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=START_RECORD_PASS");
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_VIDEO_RECORD"))
        {
            mVideoPlayingFinished = false;
            List<String> strReturnDataList = new ArrayList<String>();

            if (mVideoPlaying)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_PLAYING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mMediaRecorderRecording)
            {
                // Only perform stop when recording is on-going
                UpdateUiButton updateUi = new UpdateUiButton(startCapture, true);
                runOnUiThread(updateUi);
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=STOP_RECORD_PASS");
            }
            else
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_RECORDING_STOPPED_ALREADY");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_VIDEO_PLAYBACK"))
        {
            mVideoPlayingFinished = false;
            List<String> strReturnDataList = new ArrayList<String>();
            if (!mVideoFile.exists())
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_FILE_NOT_EXIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            if (mMediaRecorderRecording)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_RECORDING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mVideoPlaying)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_PLAYING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                UpdateUiButton updateUi = new UpdateUiButton(startPlayback, true);
                runOnUiThread(updateUi);

                // Generate an exception to send data back to CommServer
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=START_PLAYBACK_PASS");
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_VIDEO_PLAYBACK"))
        {
            mVideoPlayingFinished = false;
            List<String> strReturnDataList = new ArrayList<String>();

            if (mMediaRecorderRecording)
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_RECORDING_IN_PROCESS");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mVideoPlaying)
            {
                // Only perform stop when play back is on-going
                UpdateUiButton updateUi = new UpdateUiButton(startPlayback, true);
                runOnUiThread(updateUi);
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=STOP_PLAYBACK_PASS");
            }
            else
            {
                strReturnDataList.add("VIDEO_CAPTURE_RESULT=ERROR_PLAYING_STOPPED_ALREADY");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("QUERY_VIDEO_PLAYBACK_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (mVideoPlaying)
            {
                strDataList.add("VIDEO_CAPTURE_RESULT=PLAYBACK_STATUS_PLAYING");
            }
            else if (mVideoPlayingFinished)
            {
                strDataList.add("VIDEO_CAPTURE_RESULT=PLAYBACK_STATUS_COMPLETED");
            }
            else
            {
                strDataList.add("VIDEO_CAPTURE_RESULT=PLAYBACK_STATUS_UNKNOWN");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

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

        strHelpList.add("Camera_Internal_Video_Capture");
        strHelpList.add("");
        strHelpList.add("This function will start the internal camera and capture video");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  START_VIDEO_RECORD     - Start to record video");
        strHelpList.add("  STOP_VIDEO_RECORD      - Stop video recording");
        strHelpList.add("  START_VIDEO_PLAYBACK   - Start playing video");
        strHelpList.add("  STOP_VIDEO_PLAYBACK    - Stop playing video");
        strHelpList.add("  QUERY_VIDEO_PLAYBACK_STATUS    - Check playback status");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Camera - Internal Video Capture:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - Internal Video Capture:  PASS" + "\r\n\r\n", MODE_APPEND);

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
class InternalVideoCapturePreview extends ViewGroup implements SurfaceHolder.Callback
{
    private final String TAG = "CQATest:InternalVideoCapturePreview";

    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    int mPlaybackWidth;
    int mPlaybackHeight;
    boolean mPreview;
    boolean mPreviewChanged;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;
    int mDisplayOrientation;
    int mSurfaceWidth;
    int mSurfaceHeight;
    private final Lock mCameraLock = new ReentrantLock();

    InternalVideoCapturePreview(Context context){
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

        mSurfaceWidth = mSurfaceView.getWidth();
        mSurfaceHeight = mSurfaceView.getHeight();

        mPreview = true;
        mPreviewChanged = false;
    }

    public void setCamera(Camera camera)
    {
        mCamera = camera;
        if (mCamera != null)
        {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            if (!mPreview)
            {
                mPreview = true;
                if (checkSizeChanged())
                    mPreviewChanged = true;
            }
            else
                mPreviewChanged = false;
            requestLayout();
        }
    }

    public void setPlayback()
    {
        if (mPreview)
        {
            mPreview = false;
            if (checkSizeChanged())
            {
                mPreviewChanged = true;
                requestLayout();
            }
        }
        else
            mPreviewChanged = false;
    }

    public void setPlaybackSize(int width, int height)
    {
        mPlaybackWidth = width;
        mPlaybackHeight = height;
    }

    private boolean checkSizeChanged()
    {
        if (mPreviewSize == null)
            return true;

        if (mPlaybackWidth != mPreviewSize.width || mPlaybackHeight != mPreviewSize.height)
            return true;

        return false;
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
        if ((changed || mPreviewChanged) && (getChildCount() > 0))
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

            if (mPreview && mPreviewSize != null)
            {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;

                dbgLog(TAG, "OnLayout Preview Width: " + previewWidth + " Height: " + previewHeight, 'e');

            }
            else if (!mPreview && mPlaybackWidth != 0 && mPlaybackHeight != 0)
            {
                previewWidth = mPlaybackWidth;
                previewHeight = mPlaybackHeight;

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
            mCamera.release();
        }
    }

    public Size getOptimalPreviewSize(List<Size> sizes, int w, int h)
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
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        try
        {
            mCameraLock.lock();
            if (mCamera != null)
            {
                // Now that the size is known, set up the camera parameters and
                // begin the preview.
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
