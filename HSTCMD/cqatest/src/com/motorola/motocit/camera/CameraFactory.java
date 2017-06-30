/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.ShutterCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerBinaryPacket;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class CameraFactory extends Test_Base
{

    private SurfaceView mPreview = null;
    private SurfaceHolder mPreviewHolder = null;
    private Camera mCamera = null;
    private boolean inPreview = false;
    private boolean pictureTaken = false;

    private boolean doImageAnalysis = false;

    private boolean mSendImage = false;
    private boolean mSaveLocal = false;

    private byte[] mImageData;

    private static long CAMERA_PREVIEW_TIMEOUT_MSECS = 10000;
    private static long CAMERA_TAKE_PICTURE_TIMEOUT_MSECS = 10000;

    // Track the image storage path being used
    private String mPath = null;

    private int numberOfCameras;

    private Camera.Parameters parameters;

    // Track the camera being used
    private int cameraFrontId;
    private int cameraBackId;
    private int cameraUsedId;

    // image under test
    private Bitmap imageUnderTest;

    private final Lock mCameraLock = new ReentrantLock();

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Camera_Factory";
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
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            // set activity content to a view
            View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.viewfinder_layout, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, false);
            if (mGestureListener != null)
            {
                thisView.setOnTouchListener(mGestureListener);
            }

            // find view for mPreview
            mPreview = (SurfaceView) findViewById(com.motorola.motocit.R.id.camera_viewfinder);

            // get access to SurfaceHolder providing access and control over
            // view
            // surface
            mPreviewHolder = mPreview.getHolder();

            // add SurfaceHolder.Callback
            mPreviewHolder.addCallback(surfaceCallback);

            // This is deprecated. Value is set automatically when needed
            mPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            // Find the total number of cameras available in hardware
            numberOfCameras = Camera.getNumberOfCameras();

            // Set to camera to use
            cameraUsedId = -1; // set to -1 so we do not automatically start
            // viewfinder.

            // Find the ID of the default camera
            CameraInfo cameraInfo = new CameraInfo();
            for (int i = 0; i < numberOfCameras; i++)
            {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
                {
                    cameraFrontId = i;
                }
                else if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK)
                {
                    cameraBackId = i;
                }
            }

            // open the camera that we are going to use
            if ((cameraUsedId == Camera.CameraInfo.CAMERA_FACING_BACK) || (cameraUsedId == Camera.CameraInfo.CAMERA_FACING_FRONT))
            {
                // Acquire Camera and Open Camera Preview
                try
                {
                    openCamera();
                }
                catch (CmdFailException e)
                {
                    // return message list
                    String retMessage;
                    retMessage = "onResume failed at openCamera strCmd " + e.strCmd;
                    sendStartActivityFailed(retMessage);
                }
            }
            // retrieve current resource instance
            // retrieve current resource configuration orientation
            if (isExpectedOrientation())
            {
                sendStartActivityPassed();
            }
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            // Stop Preview and Release Camera
            releaseCamera();
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
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will control either camera");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("SET_FACTORY_CAMERA_PARAMETERS - set factory specific camera parameters");
        strHelpList.add("  ");
        strHelpList.add("GET_FACTORY_CAMERA_PARAMETERS - get factory specific camera parameters");
        strHelpList.add("  ");
        strHelpList.add("SET_CAMERA_LEDCAL_VIEWFINDER_MODE - set camera to LED calibration viewfinder mode");
        strHelpList.add("  ");
        strHelpList.add("SET_CAMERA_PARAMETERS - set camera parameter");
        strHelpList.add("  ");
        strHelpList.add("GET_CAMERA_PARAMETERS - get camera parameter");
        strHelpList.add("  ");
        strHelpList.add("OPEN_CAMERA - open either camera CAMERA_FACING_BACK or CAMERA_FACING_FRONT");
        strHelpList.add("  ");
        strHelpList.add("RELEASE_CAMERA - disconnects and releases current Camera object resources");
        strHelpList.add("  ");
        strHelpList.add("IMAGE_PATH_NAME - image path and filename without extension");
        strHelpList.add("  ");
        strHelpList.add("TAKE_PICTURE - triggers an asynchronous image capture");
        strHelpList.add("  ");
        strHelpList.add("GET_ALL_STD_CAMERA_PARAMETERS - returns all camera parameters");
        strHelpList.add("  ");
        strHelpList.add("GET_NUMBER_CAMERA - returns number of camera on phone");
        strHelpList.add("  ");
        strHelpList.add("SET_VIEWFINDER_VISIBILITY - OFF or ON");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
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
        else if (strRxCmd.equalsIgnoreCase("GET_ALL_STD_CAMERA_PARAMETERS"))
        {
            if (inPreview)
            {
                try
                {
                    mCameraLock.lock();
                    parameters = mCamera.getParameters();
                    printCameraParameters(parameters);
                }
                finally
                {
                    mCameraLock.unlock();
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add(String.format("%s help printed", TAG));
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                // create a string array list for error messages
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' command '%s' failed due to no OPEN_CAMERA executed first", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

        }

        else if (strRxCmd.equalsIgnoreCase("GET_NUMBER_CAMERA"))
        {

            // list for returned data
            List<String> strDataList = new ArrayList<String>();

            // add data to return data list
            strDataList.add("NUMBER_OF_CAMERA=" + numberOfCameras);

            // send return data list to comm server
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("OPEN_CAMERA"))
        {

            // create a string array list for error messages
            List<String> strErrMsgList = new ArrayList<String>();

            // check to see received data
            if (strRxCmdDataList.size() > 0)
            {

                // if main camera
                if (strRxCmdDataList.get(0).equalsIgnoreCase("CAMERA_FACING_BACK"))
                {
                    cameraUsedId = cameraBackId;
                }

                // or front camera
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("CAMERA_FACING_FRONT"))
                {
                    cameraUsedId = cameraFrontId;
                }

                // fail if do not know which camera
                else
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to
                    // CommServer
                    strErrMsgList.add(String.format("Activity '%s' do not understand which camera with data '%s'", TAG, strRxCmdDataList.get(0)));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                openCamera();

                // wait for camera preview to start
                long startTime = System.currentTimeMillis();
                while (!inPreview)
                {
                    dbgLog(TAG, "Waiting for CAMERA_FACTORY PREVIEW to start", 'i');
                    if ((System.currentTimeMillis() - startTime) > CAMERA_PREVIEW_TIMEOUT_MSECS)
                    {
                        dbgLog(TAG, "Failed to start camera preview", 'e');
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
                if (inPreview)
                {
                    // Generate an exception to send PASS result to CommServer
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    strErrMsgList.add(String.format("Activity '%s' failed to start camera preview in '%d' msec", TAG, CAMERA_PREVIEW_TIMEOUT_MSECS));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer

                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("RELEASE_CAMERA"))
        {

            releaseCamera();

            // remove reference to image data on closing camera.
            mImageData = null;

            // explicitly reclaim memory
            System.gc();

            // Generate an exception to send PASS result to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("IMAGE_PATH_NAME"))
        {

            // check for received data
            if (strRxCmdDataList.size() > 0)
            {

                // set the image path and name for image to capture
                mPath = strRxCmdDataList.get(0);
            }
            else
            {
                mPath = null;
            }

            // Generate an exception to send PASS result to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TAKE_PICTURE"))
        {
            // initialize BinaryWaitForAck variable to no wait (false)
            BinaryWaitForAck = false;

            // tell pc that phone can handle BINARY_ACK message
            // list for returned data
            List<String> strDataList = new ArrayList<String>();

            // add data to return data list
            strDataList.add("PHONE_ACCEPTS_BINARY_ACK");

            // send return data list to comm server
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // setup string list for results from analysis
            List<String> analysisResult = new ArrayList<String>();

            // clear flag to track when picture has been taken
            pictureTaken = false;
            // clear inPreview flag since taking picture stops preview
            inPreview = false;
            // clear flag to track send image
            mSendImage = false;
            // clear flag to track save image locally on device
            mSaveLocal = false;
            // clear flag to track image analysis
            doImageAnalysis = false;
            // list of image analysis needed to be done
            List<String> listImageAnalysis = new ArrayList<String>();
            // Camera to auto focus and then take a picture
            // if no auto focus, then the camera will take picture immediately

            // check for received data
            if (strRxCmdDataList.size() > 0)
            {
                for (String strValue : strRxCmdDataList)
                {
                    if (strValue.toUpperCase().contains("SEND_FILE"))
                    {
                        mSendImage = true;
                    }
                    if (strValue.toUpperCase().contains("IMAGE_ANALYSIS_OPTIONS"))
                    {
                        doImageAnalysis = true;
                        // split IMAGE_ANALYSIS_OPTIONS data into splitResult
                        // array
                        String splitResult[] = splitKeyValuePair(strValue);
                        // go through requested analysis and check if it's in
                        // analysis options list

                        if (!ImageAnalysis.checkImageAnalysisOption(splitResult[1]))
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' failed - ImageAnalysisOptions '%s' not valid", TAG, splitResult[1]));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                        String[] analysis = splitResult[1].split(",");

                        for (String analysisCode : analysis)
                        {
                            listImageAnalysis.add(analysisCode);
                        }
                    }
                    if (strValue.toUpperCase().contains("PC_SENDS_BINARY_ACK"))
                    {
                        BinaryWaitForAck = true;
                    }
                }
            }
            // check to see if we have a file name
            if (mPath != null)
            {
                mSaveLocal = true;
            }

            try
            {
                mCameraLock.lock();
                mCamera.autoFocus(autoFocusCallback);
            }
            finally
            {
                mCameraLock.unlock();
            }

            // Need to wait for the camera to take picture before proceeding
            // wait for camera preview to start
            long startTime = System.currentTimeMillis();
            while (!pictureTaken || !inPreview)
            {
                dbgLog(TAG, "Waiting for CAMERA_FACTORY to take picture", 'i');
                if ((System.currentTimeMillis() - startTime) > CAMERA_TAKE_PICTURE_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Failed to take camera picture", 'e');
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
            if (pictureTaken)
            {
                if (doImageAnalysis)
                {
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
                if (mSendImage)
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

                // Generate an exception to send PASS result to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to take camera picture in '%d' msec", TAG, CAMERA_TAKE_PICTURE_TIMEOUT_MSECS));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

        }
        else if (strRxCmd.equalsIgnoreCase("SET_CAMERA_PARAMETERS"))
        {

            // need to receive data list
            if (strRxCmdDataList.size() > 0)
            {
                // parameter key string
                String key = " ";

                // parameter value string
                String value = " ";

                // get current Parameters
                try
                {
                    mCameraLock.lock();
                    parameters = mCamera.getParameters();
                }
                finally
                {
                    mCameraLock.unlock();
                }

                // put all parameters into a delimited string
                String paramsDelimited = parameters.flatten();

                // loop through received data list for camera parameter key and
                // value pairs
                for (String keyValuePair : strRxCmdDataList)
                {

                    // split received data into splitResult array
                    String splitResult[] = splitKeyValuePair(keyValuePair);

                    // camera parameter key
                    key = splitResult[0];
                    // camera parameter key value
                    value = splitResult[1];

                    // check to see that key is part of camera parameters
                    if (paramsDelimited.contains(key))
                    {

                        // set camera parameters
                        parameters.set(key, value);
                    }
                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' contains data '%s' not in camera parameters for command '%s'", TAG, key,
                                strRxCmd));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                try
                {
                    mCameraLock.lock();

                    // stop preview in case we change size
                    mCamera.stopPreview();

                    // set new parameters
                    mCamera.setParameters(parameters);

                    // start preview after parameter change
                    mCamera.startPreview();

                }
                catch (RuntimeException e)
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to
                    // CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' command '%s' does not understand parameter '%s'='%s'", TAG, strRxCmd, key, value));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                finally
                {
                    mCameraLock.unlock();
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
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
        else if (strRxCmd.equalsIgnoreCase("GET_CAMERA_PARAMETERS"))
        {

            // need to receive data list
            if (strRxCmdDataList.size() > 0)
            {

                // list for returned data
                List<String> strDataList = new ArrayList<String>();

                // string for returned valued
                String retValue;

                // get current Parameters
                try
                {
                    mCameraLock.lock();
                    parameters = mCamera.getParameters();
                }
                finally
                {
                    mCameraLock.unlock();
                }

                // put all parameters into a delimited string
                String paramsDelimited = parameters.flatten();

                // loop through received data list from camera parameter
                for (String keyName : strRxCmdDataList)
                {

                    // Check to see that keyName is one of the camera parameters
                    if (paramsDelimited.contains(keyName))
                    {

                        // get parameter value
                        retValue = parameters.get(keyName);

                        // add parameter key and returned value to return data
                        // list
                        strDataList.add(keyName + "=" + retValue);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(keyName + " Is Not In Camera Parameters");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                // return data list
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // Generate an exception to send PASS result to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
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

        else if (strRxCmd.equalsIgnoreCase("SET_FACTORY_CAMERA_PARAMETERS"))
        {

            // need to receive data list
            if (strRxCmdDataList.size() > 0)
            {

                // loop through received data list for factory parameters pairs
                for (String keyValuePair : strRxCmdDataList)
                {

                    // split received data into splitResult array
                    String splitResult[] = splitKeyValuePair(keyValuePair);

                    // key is the factory-cmd value
                    String key = splitResult[0];
                    // value is the factory-val value
                    String value = splitResult[1];

                    try
                    {
                        mCameraLock.lock();

                        // get current Parameters
                        parameters = mCamera.getParameters();

                        // set the factory-cmd into parameter list
                        parameters.set("factory-cmd", key);

                        // set the factory-val into parameter list
                        parameters.set("factory-val", value);

                        // set Parameter list
                        mCamera.setParameters(parameters);
                    }
                    catch (RuntimeException e)
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' command '%s' does not understand parameter '%s'='%s'", TAG, strRxCmd, key,
                                value));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                    finally
                    {
                        mCameraLock.unlock();
                    }
                }
                // list for pass result
                List<String> strReturnDataList = new ArrayList<String>();
                // Generate an exception to send Pass result to CommServer
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
        else if (strRxCmd.equalsIgnoreCase("GET_FACTORY_CAMERA_PARAMETERS"))
        {

            // need to receive data list
            if (strRxCmdDataList.size() > 0)
            {

                // list for returned data
                List<String> strDataList = new ArrayList<String>();

                // value returned for returned parameters
                String retValue;

                // loop through received data list for factory parameters
                for (String keyValue : strRxCmdDataList)
                {

                    try
                    {
                        mCameraLock.lock();

                        // there are no = to split received data list
                        // get current Parameters
                        parameters = mCamera.getParameters();

                        // set parameters with new parameter
                        parameters.set("factory-cmd", keyValue);

                        // set Parameter list
                        mCamera.setParameters(parameters);
                    }
                    catch (RuntimeException e)
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' command '%s' does not understand parameter '%s'", TAG, strRxCmd, keyValue));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                    finally
                    {
                        mCameraLock.unlock();
                    }

                    try
                    {
                        mCameraLock.lock();
                        // get current Parameters with the factory-ret parameter
                        parameters = mCamera.getParameters();

                        // get factory-ret parameter
                        retValue = parameters.get("factory-ret");

                        // add keyValue with returned value to return data list
                        strDataList.add(keyValue + "=" + retValue);
                    }
                    finally
                    {
                        mCameraLock.unlock();
                    }
                }
                // return data list
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // list for pass result
                List<String> strReturnDataList = new ArrayList<String>();
                // Generate an exception to send Pass result to CommServer
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
        else if (strRxCmd.equalsIgnoreCase("SET_VIEWFINDER_VISIBILITY"))
        {
            if (strRxCmdDataList.size() > 0)
            {

                // loop through received data list for factory parameters pairs
                for (String strValue : strRxCmdDataList)
                {

                    if (strValue.equalsIgnoreCase("OFF"))
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {

                                mPreview.setVisibility(View.GONE);
                            }
                        });
                    }

                    else if (strValue.equalsIgnoreCase("ON"))
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {

                                mPreview.setVisibility(View.VISIBLE);
                            }
                        });
                    }

                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' does not match", TAG, strRxCmd, strValue));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }

                // list for pass result
                List<String> strReturnDataList = new ArrayList<String>();
                // Generate an exception to send Pass result to CommServer
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
        else if (strRxCmd.equalsIgnoreCase("SET_CAMERA_LEDCAL_VIEWFINDER_MODE"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                final ViewGroup.LayoutParams params = mPreview.getLayoutParams();

                // loop through received data list for factory parameters pairs
                for (String strValue : strRxCmdDataList)
                {

                    if (strValue.equalsIgnoreCase("OFF"))
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                setBacklightBrightness(0);
                                params.width = 1;
                                params.height = 1;
                                mPreview.setLayoutParams(params);
                            }
                        });
                    }
                    else if (strValue.equalsIgnoreCase("ON"))
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                setBacklightBrightness(100);
                                params.width = -1; // MATCH_PARENT
                                params.height = -1; // MATCH_PARENT
                                mPreview.setLayoutParams(params);
                            }
                        });
                    }
                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' does not match", TAG, strRxCmd, strValue));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }
                // list for pass result
                List<String> strReturnDataList = new ArrayList<String>();
                // Generate an exception to send Pass result to CommServer
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
        else if (strRxCmd.equalsIgnoreCase("IMAGE_ANALYSIS"))
        {
            String fileName = "";

            if (strRxCmdDataList.size() > 0)
            {
                // loop through received data list for factory parameters pairs
                for (String strValue : strRxCmdDataList)
                {
                    if (strValue.toUpperCase().contains("FILENAME"))
                    {
                        // split received data into splitResult array
                        String splitResult[] = splitKeyValuePair(strValue);

                        // value is the factory-val value
                        fileName = splitResult[1];

                        File imageFile = new File(fileName);
                        if (!imageFile.exists())
                        {
                            // Generate an exception to send FAIL result and
                            // mesg back to
                            // CommServer
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' fileName does not exist", TAG, strRxCmd,
                                    strValue));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                        imageUnderTest = LoadBitMap.loadImageFromJpeg(fileName);
                    }

                    else if (strValue.toUpperCase().contains("IMAGE_ANALYSIS_OPTIONS"))
                    {
                        // split received data into splitResult array
                        String splitResult[] = splitKeyValuePair(strValue);

                        if (!ImageAnalysis.checkImageAnalysisOption(splitResult[1]))
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' failed - ImageAnalysisOption '%s' is not valid", TAG, splitResult[1]));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                        // image analysis options for different data results
                        String[] analysisCodes = splitResult[1].split(",");

                        if (fileName.isEmpty())
                        {
                            // Generate an exception to send FAIL result and
                            // mesg back to
                            // CommServer
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' does not have next element as filename",
                                    TAG, strRxCmd, strValue));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                        // setup string list for results from analysis
                        List<String> analysisResult = new ArrayList<String>();

                        // execute analysis
                        if (imageUnderTest != null)
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
                            // Generate an exception to send FAIL result and
                            // mesg back to
                            // CommServer
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' contains command '%s' did not load bitmap image", TAG, strRxCmd));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                        // return data list
                        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, analysisResult);
                        sendInfoPacketToCommServer(infoPacket);

                    }

                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to
                        // CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' contains command '%s' data '%s' does not match", TAG, strRxCmd, strValue));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }

                // list for pass result
                List<String> strReturnDataList = new ArrayList<String>();
                // Generate an exception to send Pass result to CommServer
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
                {}
            };
        };
        setLight.start();
    }

    private void releaseCamera()
    {

        try
        {
            mCameraLock.lock();

            if (inPreview)
            {
                // stop preview
                mCamera.stopPreview();
                // set flag
                inPreview = false;
            }

            if (mCamera != null)
            {
                // release camera
                mCamera.release();
                mCamera = null;
            }
        }
        finally
        {
            mCameraLock.unlock();
        }
    }

    private void openCamera() throws CmdFailException
    {
        // Release this camera current camera

        releaseCamera();

        // return list with possible error message. If empty, no error
        List<String> strErrMsgList = new ArrayList<String>();
        // Acquire the next camera and request Preview to reconfigure
        // parameters

        try
        {
            mCameraLock.lock();
            mCamera = openWrapper(cameraUsedId);
        }
        catch (RuntimeException e)
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            strErrMsgList.add(String.format("Activity '%s' Camera.open Exception on '%s' for camera '%d'", TAG, strRxCmd, cameraUsedId));
            dbgLog(TAG, strErrMsgList.get(0), 'i');

            // return strErrMsgList;
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
        finally
        {
            mCameraLock.unlock();
        }

        try
        {
            mCameraLock.lock();
            mCamera.setPreviewDisplay(mPreviewHolder);
            setCameraDisplayOrientation(this, cameraUsedId, mCamera);
        }
        catch (IOException e)
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer

            strErrMsgList.add(String.format("Activity '%s' Camera.setPreviewDisplay Exception on '%s' for camera '%d'", TAG, strRxCmd, cameraUsedId));
            dbgLog(TAG, strErrMsgList.get(0), 'i');

            // return strErrMsgList;
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
        finally
        {
            mCameraLock.unlock();
        }

        try
        {
            mCameraLock.lock();

            // Start the Preview
            mCamera.startPreview();
            mCamera.setOneShotPreviewCallback(previewCallback);
        }
        finally
        {
            mCameraLock.unlock();
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

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, android.hardware.Camera camera)
    {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();

        android.hardware.Camera.getCameraInfo(cameraId, info);

        int mRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

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

        int mResult;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
        {
            mResult = (info.orientation + mDegrees) % 360;
            mResult = (360 - mResult) % 360;
        }
        else
        {
            // back-facing
            mResult = ((info.orientation - mDegrees) + 360) % 360;
        }

        camera.setDisplayOrientation(mResult);
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

    private void printCameraParameters(Camera.Parameters mParam)
    {
        List<String> strHelpList = new ArrayList<String>();
        String paramsDelimited;

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will list current active camera parameters");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        paramsDelimited = mParam.flatten();
        String item;
        while (paramsDelimited.contains(";"))
        {
            item = paramsDelimited.substring(0, paramsDelimited.indexOf(";"));
            paramsDelimited = paramsDelimited.substring(paramsDelimited.indexOf(";") + 1);
            strHelpList.add(item);
        }

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters)
    {
        Camera.Size result = null;

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        if (sizes != null)
        {

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
        }

        return (result);
    }

    AutoFocusCallback autoFocusCallback = new AutoFocusCallback()
    {
        @Override
        public void onAutoFocus(boolean arg0, Camera arg1)
        {

            try
            {
                mCameraLock.lock();
                mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
            finally
            {
                mCameraLock.unlock();
            }
        }
    };

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback()
    {

        @Override
        public void surfaceDestroyed(SurfaceHolder holder)
        {

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder)
        {

            try
            {
                mCameraLock.lock();
                if (mCamera != null)
                {
                    try
                    {
                        mCamera.setPreviewDisplay(mPreviewHolder);
                    }
                    catch (IOException e)
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Activity '%s' Camera.setPreviewDisplay Exception on '%s' for camera '%d'", TAG, strRxCmd,
                                cameraUsedId));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');

                    }

                }
            }
            finally
            {
                mCameraLock.unlock();
            }

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
        {
            try
            {
                mCameraLock.lock();
                if (mCamera != null)
                {
                    parameters = mCamera.getParameters();
                    Camera.Size size = getBestPreviewSize(width, height, parameters);

                    if (size != null)
                    {
                        parameters.setPreviewSize(size.width, size.height);
                        try
                        {
                            mCamera.stopPreview();
                            mCamera.setParameters(parameters);
                        }
                        catch (RuntimeException e)
                        {
                            // Generate an exception to send FAIL result and
                            // mesg back to
                            // CommServer
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format(
                                    "Activity '%s' tried surfaceChanged failed for setPreviewSize on command '%s' with width '%s' and height '%s'",
                                    TAG, strRxCmd, size.width, size.height));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                        }

                        try
                        {
                            mCamera.setPreviewDisplay(mPreviewHolder);
                        }
                        catch (IOException e)
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("Activity '%s' Camera.setPreviewDisplay Exception on '%s' for camera '%d'", TAG,
                                    strRxCmd, cameraUsedId));
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                        }

                        mCamera.startPreview();
                        mCamera.setOneShotPreviewCallback(previewCallback);
                        // wait for camera preview to start
                        long startTime = System.currentTimeMillis();
                        while (!inPreview)
                        {
                            dbgLog(TAG, "Waiting for CAMERA_FACTORY PREVIEW to start", 'i');
                            if ((System.currentTimeMillis() - startTime) > CAMERA_PREVIEW_TIMEOUT_MSECS)
                            {
                                dbgLog(TAG, "Failed to start camera preview", 'e');
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
                }
            }
            finally
            {
                mCameraLock.unlock();
            }

        }
    };

    private final PreviewCallback previewCallback = new Camera.PreviewCallback()
    {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera)
        {
            inPreview = true;
        }
    };
    ShutterCallback shutterCallback = new ShutterCallback()
    {
        @Override
        public void onShutter()
        {
            dbgLog(TAG, "onShutter 'd", 'd');
        }
    };

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

            mImageData = data;

            if (mSaveLocal)
            {

                FileOutputStream outStream = null;

                try
                {
                    // create that contains path and filename
                    String imagePath = santizePath(mPath);

                    // get directory name where file will be stored (returns
                    // null if failed)
                    File directory = new File(imagePath).getParentFile();

                    if (directory == null)
                    {
                        throw new FileNotFoundException("Path to file was not determined");
                    }

                    // check to see if directory does not exists and directory
                    // was not created
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
                        }
                    }

                    outStream = new FileOutputStream(imagePath);
                    outStream.write(data);

                    dbgLog(TAG, "onPictureTaken - wrote bytes: " + data.length, 'd');

                    FileDescriptor outFileFD = outStream.getFD();
                    outStream.flush();
                    outFileFD.sync();
                }

                catch (FileNotFoundException e)
                {
                    e.printStackTrace();
                }
                catch (SyncFailedException e)
                {
                    e.printStackTrace();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
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

                        e.printStackTrace();
                    }
                }
            }
            pictureTaken = true;
            try
            {
                mCameraLock.lock();
                camera.startPreview();
                mCamera.setOneShotPreviewCallback(previewCallback);
            }
            finally
            {
                mCameraLock.unlock();
            }

        }
    };

}
