/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
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

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaScannerConnection;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class VideoCapture extends Test_Base implements SurfaceHolder.Callback,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, OnCompletionListener
{

    private static final int UPDATE_TIME = 1;
    private final int MAX_DURATION_IN_MS = 10 * 60 * 1000; // 10min should be
                                                           // enough for video
                                                           // capture.
    private final long MAX_FILESIZE_IN_BYTE = 10 * 60 * 500 * 1024; // 293M.
    /*
     * Recording about 10s on Edison. Video size is related to resolution.
     * About 500k per second in width:768 height:576 on edison
     */
    private final long MIN_FILESIZE_IN_BYTE = 500 * 1024 * 10;
    /*
     * video file will be stored to path: /mnt/sdcard.
     */
    private final String VIDEO_FILENAME = "CQATest_videocapture.mp4";
    private final Handler mHandler = new MainHandler();
    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private Camera mCameraDevice = null;
    private Camera.Size mCameraSize = null;
    private Camera.Parameters mParameters;
    private MediaPlayer mMediaRlayer = null;
    private MediaRecorder mMediaRecorder = null;
    private boolean mMediaRecorderRecording = false;
    private boolean mVideoPlaying = false;
    private boolean mVideoPlayingFinished = false;
    private boolean mPreviewing = false;
    private boolean mSurfaceChange = false;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private long mStartTime;
    private TextView mShowTimeView;
    private Button mButtonRecord;
    private Button mButtonPlayback;
    private String mVideoPath;
    private File mVideoFile;

    // Following messages are defined for MTK products ONLY
    private final int MEDIA_RECORDER_INFO_FPS_ADJUSTED_MTK = 897;
    private final int MEDIA_RECORDER_INFO_BITRATE_ADJUSTED_MTK = 898;
    private final int MEDIA_RECORDER_INFO_WRITE_SLOW_MTK = 899;
    private final int MEDIA_RECORDER_INFO_START_TIMER_MTK = 1998;
    private final int MEDIA_RECORDER_INFO_CAMERA_RELEASE = 1999;

    // Define MEDIA_INFO_VIDEO_RENDERING_START in case API level is 16
    private final int MEDIA_INFO_VIDEO_RENDERING_START = 3;

    private int numberOfCameras;
    private int defaultCameraId;
    private boolean isSupport = false;
    private int mOrientation;

    private boolean isPermissionAllowed = false;
    private boolean isPermissionAllowedForCamera = false;
    private boolean isPermissionAllowedForAudio = false;
    private String[] permissions = { "android.permission.CAMERA", "android.permission.RECORD_AUDIO" };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_External_VideoCapture";
        super.onCreate(savedInstanceState);

        dbgLog(TAG, "onCreate()", 'v');
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

            if (isPermissionAllowedForCamera && isPermissionAllowedForAudio)
            {
                dbgLog(TAG, "onRequestPermissionsResult, going to start camera", 'i');
                startCamera();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    @Override
    public void onResume()
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
            startCamera();
        }
        else
        {
            dbgLog(TAG, "no permission granted to run camera test", 'e');
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
    }

    private void startCamera()
    {
        dbgLog(TAG, "starting camera", 'i');
/*
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow(); // keep klocwork happy
        if (null != window)
        {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // setContentView(com.motorola.motocit.R.layout.video_capture_layout);
*/
        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.video_capture_layout, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        this.setTitle("CQA Test/Camera/Video Capture");
        mSurfaceView = (SurfaceView) findViewById(com.motorola.motocit.R.id.videocapture_surfaceview);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mButtonRecord = (Button) findViewById(com.motorola.motocit.R.id.videocapturebutton_capture);
        mButtonPlayback = (Button) findViewById(com.motorola.motocit.R.id.videocapturebutton_playback);
        mShowTimeView = (TextView) findViewById(com.motorola.motocit.R.id.videocapturetextview_time);

        mVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
        dbgLog(TAG, "output path: " + mVideoPath, 'd');
        mVideoFile = new File(mVideoPath + VIDEO_FILENAME);

        mButtonRecord.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // avoid misoperation.
                if (mVideoPlaying)
                {
                    Toast.makeText(VideoCapture.this, "Playing video... Pls stop playback firstly before capture.", Toast.LENGTH_LONG).show();
                    return;
                }

                // set playback status to false
                mVideoPlayingFinished = false;

                if (!mMediaRecorderRecording)
                {
                    startVideoRecording();
                    if (mMediaRecorderRecording == true)
                    {
                        mButtonRecord.setText("stop capture");
                        mButtonRecord.setBackgroundColor(Color.RED);
                    }
                }
                else
                {
                    stopVideoRecording();
                }
            }
        });

        mButtonPlayback.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // avoid misoperation.
                if (mMediaRecorderRecording)
                {
                    Toast.makeText(VideoCapture.this, "Recording video... Pls playback after recording complete.", Toast.LENGTH_LONG).show();
                    return;
                }

                // set playback status to false
                mVideoPlayingFinished = false;

                if (!mVideoPlaying)
                {
                    startVideoPlayback();
                    if (mVideoPlaying == true)
                    {
                        mButtonPlayback.setText("stop playback");
                        mButtonPlayback.setBackgroundColor(Color.RED);
                    }
                }
                else
                {
                    stopVideoPlayback();
                }
            }
        });

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
            Toast.makeText(VideoCapture.this, "The device does NOT support External Camera", Toast.LENGTH_SHORT).show();
            VideoCapture.this.finish();
        }

        sendStartActivityPassed();
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

            closeCamera();

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

    // -----------------------------------------------------------------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        dbgLog(TAG, "surfaceCreated", 'v');
        mSurfaceHolder = holder;
        mCameraDevice = openWrapper(defaultCameraId);
        if (mCameraDevice != null)
        {
            mParameters = mCameraDevice.getParameters();
            mCameraDevice.setParameters(mParameters);
        }
        else
        {
            dbgLog(TAG, "surfaceCreated: failed to open camera.", 'v');
            Toast.makeText(getApplicationContext(), "Camera not available!", Toast.LENGTH_LONG).show();
            return;
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

    private void setPreviewDisplay(SurfaceHolder holder)
    {
        dbgLog(TAG, "setPreviewDisplay", 'v');
        try
        {
            mCameraDevice.setPreviewDisplay(holder);
        }
        catch (Throwable ex)
        {
            closeCamera();
            throw new RuntimeException("setPreviewDisplay failed", ex);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        dbgLog(TAG, "surfaceChanged", 'v');
        mSurfaceHolder = holder;
        mSurfaceWidth = mSurfaceView.getWidth();
        mSurfaceHeight = mSurfaceView.getHeight();

        if (mCameraDevice == null)
        {
            mCameraDevice = openWrapper(defaultCameraId);
            if (mCameraDevice == null)
            {
                dbgLog(TAG, "surfaceChanged: failed to open camera.", 'v');
                Toast.makeText(getApplicationContext(), "Camera not available!", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (mPreviewing == true)
        {
            mCameraDevice.stopPreview();
            mPreviewing = false;
        }

        setPreviewDisplay(mSurfaceHolder);
        mParameters = mCameraDevice.getParameters();

        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = getOptimalPreviewSize(sizes, width, height);
        List<String> focusModes = mParameters.getSupportedFocusModes();
        dbgLog(TAG, "focus modes=" + focusModes, 'i');

        double ratio = (double) width / height;
        dbgLog(TAG, "surfaceChanged -> surface width:" + width + " height:" + height + " w/h_surface:" + ratio, 'i');
        if (optimalSize != null)
        {
            dbgLog(TAG, "startPreview", 'v');
            double ratio_preview = (double) optimalSize.width / optimalSize.height;
            dbgLog(TAG, "optimalSize: width:" + optimalSize.width + "  " + "height:" + optimalSize.height + " ratio_preview:" + ratio_preview, 'd');
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            {
                dbgLog(TAG, "set focus-mode to continuous-video", 'i');
                mParameters.set("focus-mode", "continuous-video");
            }
            mCameraDevice.setParameters(mParameters);
            mCameraDevice.startPreview();
            mPreviewing = true;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        dbgLog(TAG, "surfaceDestroyed", 'v');
        mSurfaceHolder = null;
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
                Toast.makeText(VideoCapture.this, "No sdcard. Exit recording.", Toast.LENGTH_LONG).show();
                return;
            }

            // Check if storage is enough to store video.
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
            long avaliableStorage = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
            if (avaliableStorage < MIN_FILESIZE_IN_BYTE)
            {
                dbgLog(TAG, "storage is not enough, exit recording.", 'v');
                Toast.makeText(VideoCapture.this, "No enough storage. Exit recording.", Toast.LENGTH_LONG).show();
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

            Camera.Parameters params = mCameraDevice.getParameters();
            int frameRate = params.getPreviewFrameRate();

            if (mPreviewing == true)
            {
                mCameraDevice.stopPreview();
                mPreviewing = false;
            }

            mCameraDevice.unlock();
            mMediaRecorder.setCamera(mCameraDevice);
            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

            if (Build.VERSION.SDK_INT >= 8)
            {
                CamcorderProfile mCamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                if (mCamcorderProfile == null)
                {
                    dbgLog(TAG, "Failed to get Camcorder Profile, exit recording.", 'v');
                    Toast.makeText(VideoCapture.this, "Camcorder Profile error. Exit recording.", Toast.LENGTH_LONG).show();
                    return;
                }

                mMediaRecorder.setProfile(mCamcorderProfile);
            }
            else
            {
                mCameraSize = params.getPreferredPreviewSizeForVideo();
                if (mCameraSize == null)
                {
                    dbgLog(TAG, "Failed to get preferred preview size, exit recording.", 'v');
                    Toast.makeText(VideoCapture.this, "Preview size error. Exit recording.", Toast.LENGTH_LONG).show();
                    return;
                }

                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setVideoSize(mCameraSize.width, mCameraSize.height);
                mMediaRecorder.setVideoFrameRate(frameRate);
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            }

            mMediaRecorder.setMaxDuration(MAX_DURATION_IN_MS);
            mMediaRecorder.setMaxFileSize(MAX_FILESIZE_IN_BYTE);
            mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());
            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.setOnInfoListener(this);

            try
            {
                mMediaRecorder.prepare();
                mMediaRecorder.start(); // Recording is now started
                mButtonPlayback.setEnabled(true);
            }
            catch (IllegalStateException ex)
            {
                dbgLog(TAG, "prepare failed:" + ex.getMessage(), 'e');
                stopVideoRecording();
            }
            catch (IOException e)
            {
                dbgLog(TAG, "prepare failed for " + mVideoFile, 'e');
                stopVideoRecording();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "prepare failed,Exception:"+e.getMessage(), 'e');
                stopVideoRecording();
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
            catch (IllegalStateException ex)
            {
                dbgLog(TAG, "recorder stop fail,IllegalStateException:" + ex.getMessage(), 'e');
                recorderExceptionHandle();
                return;
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "recorder stop fail,RuntimeException: " + e.getMessage(), 'e');
                recorderExceptionHandle();
                return;
            }
            catch (Exception e)
            {
                dbgLog(TAG, "recorder stop fail,Exception: " + e.getMessage(), 'e');
            }
            mMediaRecorderRecording = false;
            mButtonRecord.setText("start capture");
            mButtonRecord.setBackgroundColor(Color.WHITE);
        }
        releaseMediaRecorder();
    }

    private void recorderExceptionHandle()
    {
            Toast.makeText(this, "Video recorder failed!", Toast.LENGTH_LONG).show();
            mButtonPlayback.setEnabled(false);
            mMediaRecorderRecording = false;
            mButtonRecord.setText("start capture");
            mButtonRecord.setBackgroundColor(Color.WHITE);
            releaseMediaRecorder();
    }

    private void playBackExceptionHandle()
    {
                Toast.makeText(this, "Video play stop failed", Toast.LENGTH_LONG).show();
                mVideoPlaying = false;
                mButtonPlayback.setText("start playback");
                mButtonPlayback.setBackgroundColor(Color.WHITE);
                releaseMediaPlayer();
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

        if (mCameraDevice != null)
        {
            mCameraDevice.lock();
        }
    }

    private void closeCamera()
    {
        dbgLog(TAG, "close Camera", 'v');
        if (mCameraDevice == null)
        {
            dbgLog(TAG, "already stopped.", 'v');
            return;
        }

        if (mPreviewing == true)
        {
            mCameraDevice.stopPreview();
            mPreviewing = false;
        }

        mCameraDevice.release();
        mCameraDevice = null;
    }

    // from MediaRecorder.OnErrorListener
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
                Toast.makeText(VideoCapture.this, "Reached max recording time.", Toast.LENGTH_LONG).show();
                break;
            }

            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
            {
                dbgLog(TAG, "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED", 'v');
                Toast.makeText(VideoCapture.this, "Reached max file size.", Toast.LENGTH_LONG).show();
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
            dbgLog(TAG, "play file is not exist.", 'v');
            Toast.makeText(VideoCapture.this, "play file is not exist.", Toast.LENGTH_LONG).show();
            stopVideoPlayback();
            return;
        }

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
        }
        catch (IllegalStateException e)
        {
            dbgLog(TAG, "prepare failed, exception:"+e.getMessage(), 'e');
        }

        try
        {
            mMediaRlayer.start(); // playing is now started
        }
        catch (IllegalStateException e)
        {
            dbgLog(TAG, "mediaRlayer start failed, exception:"+e.getMessage(), 'e');
            stopVideoPlayback();
            return;
        }
        catch (RuntimeException e)
        {
            dbgLog(TAG, "Could not play video. ", 'e');
            stopVideoPlayback();
            return;
        }
        catch (Exception e)
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
            catch (IllegalStateException e)
            {
                dbgLog(TAG, "play stop fail, IllegalStateException: " + e.getMessage(), 'e');
                playBackExceptionHandle();
                return;
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "play stop fail, RuntimeException: " + e.getMessage(), 'e');
                playBackExceptionHandle();
                return;
            }
            catch (Exception e)
            {
                dbgLog(TAG, "play stop fail, Exception: " + e.getMessage(), 'e');
            }
            mVideoPlaying = false;
            mButtonPlayback.setText("start playback");
            mButtonPlayback.setBackgroundColor(Color.WHITE);
        }
        releaseMediaPlayer();

        // Add this just for triggering surfaceChanged to restart camera
        // preview.
        if (mSurfaceChange == false)
        {
            mSurfaceChange = true;
            mSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(mSurfaceWidth - 1, mSurfaceHeight - 1));
        }
        else
        {
            mSurfaceChange = false;
            mSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(mSurfaceWidth + 1, mSurfaceHeight + 1));
        }
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
        Toast.makeText(VideoCapture.this, "playing video complete.", Toast.LENGTH_LONG).show();
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
        mShowTimeView.setText("");
        mShowTimeView.setVisibility(View.VISIBLE);
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

        mShowTimeView.setText(text);
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

            contentRecord("testresult.txt", "Camera - Video Capture:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Video Capture:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
                UpdateUiButton updateUi = new UpdateUiButton(mButtonRecord, true);
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
                UpdateUiButton updateUi = new UpdateUiButton(mButtonRecord, true);
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
                UpdateUiButton updateUi = new UpdateUiButton(mButtonPlayback, true);
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
                UpdateUiButton updateUi = new UpdateUiButton(mButtonPlayback, true);
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

        strHelpList.add("Video Capture");
        strHelpList.add("");
        strHelpList.add("This activity brings up the Video Capture test");
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
        contentRecord("testresult.txt", "Camera - Video Capture:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - Video Capture:  PASS" + "\r\n\r\n", MODE_APPEND);

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
