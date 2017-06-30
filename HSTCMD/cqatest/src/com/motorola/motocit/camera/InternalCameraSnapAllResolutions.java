/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class InternalCameraSnapAllResolutions extends Test_Base
{
    private boolean inPreview = false;
    private boolean inPictureTaken = false;
    private boolean isPictureTaken = false;
    private boolean resolutionSelected = false;
    private boolean isSupport = false;

    private String path = null;
    private List<Camera.Size> supportedPictureSizes;
    private int pictureWidth;
    private int pictureHeight;
    private int mRotateDegree;
    private int mOrientation;
    private int numberOfCameras;
    private int defaultCameraId;
    private Camera internalCamera = null;
    private Camera.Parameters parameters;
    private SurfaceView preview = null;
    private SurfaceHolder previewHolder = null;

    private Handler mHandler = new Handler();
    private OrientationEvent mOrientationEventListener = null;

    private final Lock mCameraLock = new ReentrantLock();
    private static long CAMERA_TAKE_PICTURE_TIMEOUT_MSECS = 10000;

    private ListView mListView;
    private TextView mSnapInfo;
    private ImageView mImageView;
    private ArrayList<HashMap<String, String>> mResolutionList = new ArrayList<HashMap<String, String>>();

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_InternalCameraSnapAllResolutions";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.snapallresolutions_layout);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
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

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        enableSystemUiHider(false);
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
            mImageView = (ImageView) findViewById(com.motorola.motocit.R.id.image_snapallresolutions);
            preview = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera_snapallresolutions);

            mListView = (ListView) findViewById(com.motorola.motocit.R.id.list_resolution);
            mSnapInfo = (TextView) findViewById(com.motorola.motocit.R.id.snap_info);
            mSnapInfo.setVisibility(View.VISIBLE);
            mSnapInfo.setTextColor(Color.YELLOW);
            mSnapInfo.setText("Select one resolution and tap it to take picture");

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
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
                {
                    defaultCameraId = i;
                    isSupport = true;
                    mOrientation = getDisplayOrientation(cameraInfo);
                }
            }

            if (!isSupport)
            {
                Toast.makeText(InternalCameraSnapAllResolutions.this, "The device does NOT support Internal Camera", Toast.LENGTH_SHORT).show();
                InternalCameraSnapAllResolutions.this.finish();
            }

            // Open the default i.e. the first rear facing camera.
            internalCamera = openWrapper(defaultCameraId);

            if (null == internalCamera)
            {
                sendStartActivityFailed("Could not open internal camera");
            }
            else
            {
                parameters = internalCamera.getParameters();
                supportedPictureSizes = internalCamera.getParameters().getSupportedPictureSizes();

                if (supportedPictureSizes != null)
                {
                    mResolutionList.clear();
                    for (int i = 0; i < supportedPictureSizes.size(); i++)
                    {
                        HashMap<String, String> map = new HashMap<String, String>();
                        map.put("Resolution", (i + 1) + "   width: " + supportedPictureSizes.get(i).width +
                                "   height: " + supportedPictureSizes.get(i).height);
                        mResolutionList.add(map);
                    }

                    SimpleAdapter mlistAdapter = new SimpleAdapter(InternalCameraSnapAllResolutions.this,
                            mResolutionList,
                            com.motorola.motocit.R.layout.cameraresolutionlist_item,
                            new String[] { "Resolution" },
                            new int[] { com.motorola.motocit.R.id.ResolutionItemInfo });
                    mListView.setAdapter(mlistAdapter);
                    mListView.setOnItemClickListener(new OnItemClickListenerImpl());
                }

                sendStartActivityPassed();

                mOrientationEventListener = new OrientationEvent(InternalCameraSnapAllResolutions.this);
                if (mOrientationEventListener.canDetectOrientation())
                {
                    mOrientationEventListener.enable();
                }
                else
                {
                    dbgLog(TAG, "Can't Detect Orientation!", 'd');
                }
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

    private class OnItemClickListenerImpl implements OnItemClickListener
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            ListView lv = (ListView) parent;
            HashMap<String, String> map = (HashMap<String, String>) lv.getItemAtPosition(position);
            String resolution = map.get("Resolution");
            dbgLog(TAG, "selected resolution: " + resolution + " position: " + position, 'i');

            if (internalCamera != null)
            {
                resolutionSelected = true;
                pictureWidth = supportedPictureSizes.get(position).width;
                pictureHeight = supportedPictureSizes.get(position).height;

                mHandler.post(startSurfacePreview);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        dbgLog(TAG, "onTouchEvent:", 'i');

        if ((inPreview == true) && (inPictureTaken == false))
        {
            inPictureTaken = true;
            mHandler.post(takePictureCallback);
        }

        return super.onTouchEvent(event);
    }

    private class OrientationEvent extends OrientationEventListener
    {
        public OrientationEvent(Context context)
        {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation)
        {
            mRotateDegree = 0;
            if (orientation != ORIENTATION_UNKNOWN)
            {
                mRotateDegree = roundOrientation(orientation);
            }
        }

        private int roundOrientation(int orientation)
        {
            int retVal = (((orientation + 45) / 90) * 90) % 360;

            // Process for picture post rotation
            if (retVal == 0)
            {
                retVal = (retVal + 270) % 360;
            }
            else if (retVal == 180)
            {
                retVal = (retVal + 90) % 360;
            }
            else if (retVal == 270)
            {
                retVal = (retVal + 180) % 360;
            }

            return retVal;
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            if (inPreview)
            {
                internalCamera.stopPreview();
                inPreview = false;
            }

            if (internalCamera != null)
            {
                internalCamera.release();
                internalCamera = null;
            }

            if (mOrientationEventListener != null)
            {
                mOrientationEventListener.disable();
                mOrientationEventListener = null;
            }

            // delete image files
            String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/data/local/12m";
            File dir = new File(filePath);
            if (dir.exists())
            {
                File[] filesList = dir.listFiles();
                if (filesList != null)
                {
                    for (File file : filesList)
                    {
                        String filename = file.getName();
                        if (filename.contains("image_internal_"))
                        {
                            file.delete();
                            dbgLog(TAG, "onPause: delete image - " + filename, 'd');
                        }
                    }
                }
            }
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

    private final Runnable startSurfacePreview = new Runnable()
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "startSurfacePreview:", 'i');

            mSnapInfo.setVisibility(View.INVISIBLE);
            preview.setVisibility(View.VISIBLE);
            mImageView.setVisibility(View.INVISIBLE);
            mListView.setVisibility(View.INVISIBLE);

            parameters = internalCamera.getParameters();
            Camera.Size size = getBestPreviewSize(preview.getWidth(), preview.getHeight(), parameters);

            if (size != null)
            {
                parameters.setPreviewSize(size.width, size.height);
                internalCamera.setParameters(parameters);
                internalCamera.startPreview();
                inPreview = true;
            }
        }
    };

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {
        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {
            try
            {
                if (internalCamera != null)
                {
                    internalCamera.setDisplayOrientation(mOrientation);
                    internalCamera.setPreviewDisplay(previewHolder);
                }
            }
            catch (Throwable t)
            {
                dbgLog("PreviewDemo-surfaceCallback", "Exception in setPreviewDisplay()", t, 'e');
                Toast.makeText(InternalCameraSnapAllResolutions.this, t.getMessage(), Toast.LENGTH_LONG).show();
            }

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            dbgLog(TAG, "surfaceChanged", 'd');

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {

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
        }
    };

    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            FileOutputStream outStream = null;
            try
            {
                if (data != null)
                {
                    path = String.format("/system/etc/motorola/12m/image_internal_%d_%d.jpg", pictureWidth, pictureHeight);

                    String imagePath = santizePath(path);
                    File directory = new File(imagePath).getParentFile();

                    // keep klocwork happy
                    if (null == directory)
                    {
                        throw new IOException("Could create directory file object");
                    }

                    if (!directory.exists() && !directory.mkdirs())
                    {
                        throw new IOException("Path to file could not be created.");
                    }

                    File imageFile = new File(path);
                    if (imageFile.exists())
                    {
                        imageFile.delete();
                    }

                    outStream = new FileOutputStream(imagePath);

                    // Display image
                    dbgLog(TAG, "onPictureTaken: display image - " + imagePath, 'd');

                    Bitmap bitmapOrg = LoadBitMap.loadImageFromByte(data);
                    int width = bitmapOrg.getWidth();
                    int height = bitmapOrg.getHeight();
                    Matrix matrix = new Matrix();

                    dbgLog(TAG, "mRotateDegree: " + mRotateDegree, 'd');
                    matrix.postRotate(mOrientation + 180);
                    Bitmap resizedBitmap = Bitmap.createBitmap(bitmapOrg, 0, 0, width, height, matrix, true);
                    BitmapDrawable bitmap = new BitmapDrawable(resizedBitmap);
                    dbgLog(TAG, "onPictureTaken: display image - " + imagePath, 'd');

                    BufferedOutputStream buffOutStream = new BufferedOutputStream(outStream);
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, buffOutStream);

                    buffOutStream.close();
                    buffOutStream = null;

                    mImageView.setImageDrawable(bitmap);
                    mImageView.setVisibility(View.VISIBLE);

                    // Need to request layout since layout won't be updated by
                    // setImageDrawable when
                    // resolution of new image is the same as that of old image.
                    mImageView.requestLayout();
                    mImageView.invalidate();
                }
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally
            {}

            // keep klocwork happy
            if (null != outStream)
            {
                try
                {
                    outStream.close();
                }
                catch (IOException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            inPreview = false;
            inPictureTaken = false;
            isPictureTaken = true;
            preview.setVisibility(View.INVISIBLE);
            internalCamera.stopPreview();
        }
    };

    private final Runnable takePictureCallback = new Runnable()
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "takePictureCallback", 'd');
            parameters.setPictureSize(pictureWidth, pictureHeight);
            parameters.setJpegQuality(100);
            internalCamera.setParameters(parameters);
            internalCamera.autoFocus(autoFocusCallback);
        }
    };

    AutoFocusCallback autoFocusCallback = new AutoFocusCallback()
    {
        @Override
        public void onAutoFocus(boolean arg0, Camera arg1)
        {
            internalCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    };

    private String santizePath(String path)
    {
        if (!path.startsWith("/"))
        {
            path = "/" + path;
        }
        if (!path.contains("."))
        {
            path += ".jpg";
        }

        dbgLog(TAG, "out_put_path: " + Environment.getExternalStorageDirectory().getAbsolutePath() + path, 'd');
        return Environment.getExternalStorageDirectory().getAbsolutePath() + path;
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

            contentRecord("testresult.txt", "Camera - Internal Camera Snap All Resolutions:  PASS" + "\n\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Camera - Internal Camera Snap All Resolutions:  FAILED" + "\n\n", MODE_APPEND);

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
            if (resolutionSelected == true)
            {
                resolutionSelected = false;
                preview.setVisibility(View.INVISIBLE);
                mImageView.setVisibility(View.INVISIBLE);
                mListView.setVisibility(View.VISIBLE);
                mSnapInfo.setVisibility(View.VISIBLE);

                if (inPreview)
                {
                    internalCamera.stopPreview();
                }
            }
            else if (modeCheck("Seq"))
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
        if (strRxCmd.equalsIgnoreCase("GET_SUPPORTED_RESOLUTIONS"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (supportedPictureSizes != null)
            {
                for (int i = 0; i < supportedPictureSizes.size(); i++)
                {
                    strDataList.add((i + 1) + "   width: " + supportedPictureSizes.get(i).width +
                            "   height: " + supportedPictureSizes.get(i).height);
                }
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_VIEWFINDER"))
        {
            // if no size data, use default size.
            dbgLog(TAG, "START_VIEWFINDER", 'i');
            if ((internalCamera != null) && (supportedPictureSizes != null))
            {
                // set default resolution
                int i = supportedPictureSizes.size() - 1;

                if (supportedPictureSizes.get(0).width > supportedPictureSizes.get(i).width)
                {
                    i = 0;
                }

                pictureWidth = supportedPictureSizes.get(i).width;
                pictureHeight = supportedPictureSizes.get(i).height;

                resolutionSelected = true;
                List<String> strDataList = new ArrayList<String>();

                if (strRxCmdDataList.size() != 0)
                {
                    if (strRxCmdDataList.size() != 2)
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("missing parameter.");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                    else
                    {
                        for (String keyValuePair : strRxCmdDataList)
                        {
                            String splitResult[] = splitKeyValuePair(keyValuePair);
                            String key = splitResult[0];
                            String value = splitResult[1];

                            if (key.equalsIgnoreCase("RESOLUTION_WIDTH"))
                            {
                                pictureWidth = Integer.parseInt(value);
                            }
                            else if (key.equalsIgnoreCase("RESOLUTION_HEIGHT"))
                            {
                                pictureHeight = Integer.parseInt(value);
                            }
                        }

                        for (i = 0; i < supportedPictureSizes.size(); i++)
                        {
                            if ((pictureWidth == supportedPictureSizes.get(i).width) &&
                                    (pictureHeight == supportedPictureSizes.get(i).height))
                            {
                                break;
                            }
                        }

                        if (i >= supportedPictureSizes.size())
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add("invalid resolution");
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                    }
                }

                // start preview
                mHandler.post(startSurfacePreview);

                dbgLog(TAG, "picture resolution: width= " + pictureWidth + "  height= " + pictureHeight, 'i');
                strDataList.add("Picture resolution: width= " + pictureWidth + "  height= " + pictureHeight);

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("camera is null");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("TAKE_PICTURE"))
        {
            isPictureTaken = false;

            if (inPreview == true)
            {
                parameters.setPictureSize(pictureWidth, pictureHeight);
                parameters.setJpegQuality(100);
                internalCamera.setParameters(parameters);

                try
                {
                    mCameraLock.lock();
                    internalCamera.autoFocus(autoFocusCallback);;
                }
                finally
                {
                    mCameraLock.unlock();
                }

                // Need to wait for the camera to take picture before proceeding
                long startTime = System.currentTimeMillis();
                while (!isPictureTaken)
                {
                    dbgLog(TAG, "Waiting for taking picture", 'i');
                    if ((System.currentTimeMillis() - startTime) > CAMERA_TAKE_PICTURE_TIMEOUT_MSECS)
                    {
                        dbgLog(TAG, "Failed to take picture", 'e');
                        break;
                    }
                    try
                    {
                        Thread.sleep(50);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Start viewfinder at first");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (!isPictureTaken)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Failed to take picture");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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

        strHelpList.add("Camera_InternalCameraSnapAllResolutions");
        strHelpList.add("");
        strHelpList.add("This function will take picture for selected resolution");
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
        contentRecord("testresult.txt", "Camera - Internal Camera Snap All Resolutions:  FAILED" + "\n\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Camera - Internal Camera Snap All Resolutions:  PASS" + "\n\n", MODE_APPEND);

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
        if (resolutionSelected == true)
        {
            resolutionSelected = false;
            preview.setVisibility(View.INVISIBLE);
            mImageView.setVisibility(View.INVISIBLE);
            mListView.setVisibility(View.VISIBLE);
            mSnapInfo.setVisibility(View.VISIBLE);

            if (inPreview)
            {
                internalCamera.stopPreview();
            }
        }
        else if (modeCheck("Seq"))
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
