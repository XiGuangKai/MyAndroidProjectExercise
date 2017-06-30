/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.mmc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.ServiceManager;
import android.os.StatFs;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.os.storage.DiskInfo;
import android.os.UserManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class MMC extends Test_Base
{
    private String sdCardPath = null;
    private String eMMCPath = null;

    private static Method storageVolumeDescription = null;
    private static Method methodGetVolumeList = null;
    private static Method methodGetVolumesInfoListM = null;
    private static Method methodGetDisksListM = null;

    private static Method methodGetUserHandle = null;

    private StorageManager mStorageManager = null;

    private UserManager mUserManager = null;

    private Button confirmButton;

    private boolean isPermissionAllowed = false;

    boolean isSdCardMountedM = false;
    boolean isSdCardM = false;

    int user_id = -1;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "MMC";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.mmc);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    private void configureStorageVolumeMethods()
    {
        // Jelly Bean changed the StorageVolume method for getDescription
        if (storageVolumeDescription == null)
        {
            try
            {
                storageVolumeDescription = StorageVolume.class.getMethod("getDescription");
            }
            catch (Exception ex)
            {
                // method does not exist
                storageVolumeDescription = null;
            }
        }

        if (storageVolumeDescription == null)
        {
            try
            {
                storageVolumeDescription = StorageVolume.class.getMethod("getDescription", new Class[] { Context.class });
            }
            catch (Exception ex)
            {
                // method does not exist
                storageVolumeDescription = null;
            }
        }
    }

    /*
     * Return the mount path of external removalable storage like sdcard, return
     * null if no any.
     */
    public String getExternalStorageMountPath()
    {
        String path = null;

        try
        {
            Parcelable[] storageVolumeList = null;

            try
            {
                methodGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");

            }
            catch (Throwable e)
            {
                methodGetVolumeList = null;
                e.printStackTrace();
            }

            if (methodGetVolumeList != null)
            {
                try
                {
                    storageVolumeList = (Parcelable[]) methodGetVolumeList.invoke(mStorageManager);
                }
                catch (Exception e)
                {}
            }
            else
            {
                dbgLog(TAG, "methodGetVolumeList=null", 'i');
            }

            /* Search in storage volume list to find sdcard path */
            for (int i = 0; i < storageVolumeList.length; i++)
            {
                StorageVolume storageVolume = (StorageVolume) storageVolumeList[i];

                dbgLog(TAG, "external_storage_path=" + storageVolume.getPath(), 'i');

                if ((storageVolume.isRemovable() == true) && (storageVolume.getPath().contains("sdcard") == true))
                {
                    path = storageVolume.getPath();
                }
            }
        }
        catch (Exception e)
        {}

        return path;
    }

    /* Return the mount path of internal eMMC storage, return null if no any. */
    private String getInternalStorageMountPath()
    {
        return System.getenv("EXTERNAL_STORAGE");
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
        dbgLog(TAG, "onResume", 'i');

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1001);
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            TextView mmcTextView = (TextView) findViewById(com.motorola.motocit.R.id.mmc);
            confirmButton = (Button) findViewById(com.motorola.motocit.R.id.mmcbutton_1);

            mmcTextView.setVisibility(View.INVISIBLE);

            mStorageManager = (StorageManager) this.getSystemService(Context.STORAGE_SERVICE);

            mUserManager = (UserManager) this.getSystemService(Context.USER_SERVICE);

            configureStorageVolumeMethods();

            if (Build.VERSION.SDK_INT >= 23)
            {
                user_id = getUserId();
            }

            sdCardPath = getExternalStorageMountPath();
            eMMCPath = getInternalStorageMountPath();
            if (sdCardPath != null)
            {
                dbgLog(TAG, "SDCARD mount path: " + sdCardPath, 'd');
            }
            if (eMMCPath != null)
            {
                dbgLog(TAG, "Internal eMMC mount Path: " + eMMCPath, 'd');
            }

            confirmButton.setOnClickListener(new View.OnClickListener()
            {
                TextView mmcTextView = (TextView) findViewById(com.motorola.motocit.R.id.mmc);

                @Override
                public void onClick(View v)
                {
                    mmcTextView.setVisibility(View.VISIBLE);

                    if (Build.VERSION.SDK_INT < 23)
                    {
                        if (sdCardPath != null)
                        {
                            if (searchSDcard(sdCardPath))
                            {
                                mmcTextView.setTextColor(Color.GREEN);
                                mmcTextView.setText("MMC Card: Mounted");
                            }
                            else
                            {
                                mmcTextView.setTextColor(Color.RED);
                                mmcTextView.setText("MMC Card: Unmounted");
                            }
                        }
                    }
                    else
                    {
                        checkSdCardM();
                        if (isSdCardMountedM)
                        {
                            mmcTextView.setTextColor(Color.GREEN);
                            mmcTextView.setText("SD Card: Mounted");
                        }
                        else
                        {
                            mmcTextView.setTextColor(Color.RED);
                            mmcTextView.setText("SD Card: Unmounted");
                        }
                    }
                }
            });

            File extDir1 = Environment.getExternalStorageDirectory();
            String aPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            dbgLog(TAG, "getExternalStoragePublicDirectory: " + android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    'd');

            dbgLog(TAG, "External storage dir from Environment: " + extDir1, 'd');
            dbgLog(TAG, "External storage absolute path: " + aPath, 'd');
            dbgLog(TAG, "Storage State: " + android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED), 'd');

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run MMC test");
        }
    }

    public int getUserId()
    {
        int userid = -1;
        try
        {
            methodGetUserHandle = mUserManager.getClass().getMethod("getUserHandle");

        }
        catch (Throwable e)
        {
            methodGetUserHandle = null;
            e.printStackTrace();
        }

        if (methodGetUserHandle != null)
        {
            try
            {
                userid = (Integer) methodGetUserHandle.invoke(mUserManager);
                dbgLog(TAG, "userid=" + userid, 'i');
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }

        }
        else
        {
            dbgLog(TAG, "methodGetUserHandle=null", 'e');
        }

        return userid;
    }

    private void checkSdCardM()
    {
        List<VolumeInfo> storageVolumeInfoListM = null;
        List<DiskInfo> diskInfoM = null;
        isSdCardMountedM = false;

        try
        {
            methodGetVolumesInfoListM = mStorageManager.getClass().getMethod("getVolumes");

        }
        catch (Throwable e)
        {
            methodGetVolumesInfoListM = null;
            e.printStackTrace();
        }

        try
        {
            methodGetDisksListM = mStorageManager.getClass().getMethod("getDisks");

        }
        catch (Throwable e)
        {
            methodGetDisksListM = null;
            e.printStackTrace();
        }

        if (methodGetDisksListM != null)
        {
            try
            {
                diskInfoM = (List<DiskInfo>) methodGetDisksListM.invoke(mStorageManager);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (diskInfoM != null)
            {

                for (DiskInfo disInfo : diskInfoM)
                {
                    dbgLog(TAG, "isSd=" + disInfo.isSd(), 'i');

                    if (disInfo.isSd())
                    {
                        isSdCardM = true;
                    }
                }

            }

        }
        else
        {
            dbgLog(TAG, "methodGetDisksListM=null", 'e');
        }

        if (methodGetVolumesInfoListM != null)
        {
            try
            {
                storageVolumeInfoListM = (List<VolumeInfo>) methodGetVolumesInfoListM.invoke(mStorageManager);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (storageVolumeInfoListM != null)
            {
                int i = 0;
                for (VolumeInfo vol : storageVolumeInfoListM)
                {
                    dbgLog(TAG, i + "_" + "getPathForUser=" + vol.getPathForUser(user_id), 'i');
                    dbgLog(TAG, i + "_" + "getState=" + vol.getState(), 'i');
                    dbgLog(TAG, i + "_" + "getType=" + vol.getType(), 'i');
                    dbgLog(TAG, i + "_" + "isMountedReadable=" + vol.isMountedReadable(), 'i');
                    dbgLog(TAG, i + "_" + "isMountedWritable=" + vol.isMountedWritable(), 'i');

                    i++;

                    if (isSdCardM)
                    {
                        if ((vol.getState() == 2) && (vol.getType() == 0))
                        {
                            dbgLog(TAG, "SD card mounted", 'i');
                            isSdCardMountedM = true;
                        }
                    }
                }
            }

        }
        else
        {
            dbgLog(TAG, "storageVolumeInfoListM=null", 'e');
        }
    }

    private boolean checkWritableM(String writePath)
    {
        List<VolumeInfo> storageVolumeInfoListM = null;
        boolean writable = false;

        try
        {
            methodGetVolumesInfoListM = mStorageManager.getClass().getMethod("getVolumes");

        }
        catch (Throwable e)
        {
            methodGetVolumesInfoListM = null;
            e.printStackTrace();
        }

        if (methodGetVolumesInfoListM != null)
        {
            try
            {
                storageVolumeInfoListM = (List<VolumeInfo>) methodGetVolumesInfoListM.invoke(mStorageManager);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            if (storageVolumeInfoListM != null)
            {
                for (VolumeInfo vol : storageVolumeInfoListM)
                {
                    if (vol.getPathForUser(user_id) != null)
                    {
                        if (writePath.equalsIgnoreCase(vol.getPathForUser(user_id).toString()))
                        {
                            if (vol.isMountedWritable())
                            {
                                dbgLog(TAG, "path:" + vol.getPathForUser(user_id) + "is writable", 'i');
                                writable = true;
                            }
                        }
                    }
                }
            }
        }
        else
        {
            dbgLog(TAG, "storageVolumeInfoListM=null", 'e');
        }

        return writable;
    }

    @Override
    protected void onStop()
    {
        super.onStop();
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

            contentRecord("testresult.txt", "MMC Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "MMC Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

    private String getStorageVolumeDescription(StorageVolume storageVolume)
    {
        String description = null;

        try
        {
            description = (storageVolumeDescription.invoke(storageVolume, getApplicationContext())).toString();
        }
        catch (Exception e)
        {
            description = null;
        }

        if (description == null)
        {
            try
            {
                description = storageVolumeDescription.invoke(storageVolume).toString();
            }
            catch (Exception e)
            {
                description = null;
            }
        }

        return description;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_STORAGE_VOLUME_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();

            Parcelable[] storageVolumeList = null;

            try
            {
                methodGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");

            }
            catch (Throwable e)
            {
                methodGetVolumeList = null;
                e.printStackTrace();
            }

            if (methodGetVolumeList != null)
            {
                try
                {
                    storageVolumeList = (Parcelable[]) methodGetVolumeList.invoke(mStorageManager);
                }
                catch (Exception e)
                {
                    strDataList.add("GET_VOLUME_LIST_FAILED: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 1)
                {
                    strDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }

                int numberOfStorageVolumes = 0;

                for (int i = 0; i < storageVolumeList.length; i++)
                {
                    StorageVolume storageVolume = (StorageVolume) storageVolumeList[i];
                    boolean sendData = false;

                    if (strRxCmdDataList.get(0).equalsIgnoreCase("INTERNAL"))
                    {
                        if (storageVolume.isRemovable() == false)
                        {
                            numberOfStorageVolumes++;
                            sendData = true;
                        }
                    }
                    else if (strRxCmdDataList.get(0).equalsIgnoreCase("REMOVABLE"))
                    {
                        if ((storageVolume.isRemovable() == true) && (storageVolume.getPath().contains("usbdisk") == false)
                                && (storageVolume.getPath().contains("usbotg") == false))
                        {
                            numberOfStorageVolumes++;
                            sendData = true;
                        }
                    }
                    else if (strRxCmdDataList.get(0).equalsIgnoreCase("ALL"))
                    {
                        numberOfStorageVolumes++;
                        sendData = true;
                    }
                    else
                    {
                        strDataList.add("UNKNOWN_MEMORY_TYPE: " + strRxCmdDataList.get(0));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                    }

                    if (sendData == true)
                    {
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_ALLOW_MASS_STORAGE=" + storageVolume.allowMassStorage()));
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_MAX_FILE_SIZE=" + storageVolume.getMaxFileSize()));

                        String svDescription = getStorageVolumeDescription(storageVolume);

                        if (svDescription == null)
                        {
                            strDataList.add("STORAGE VOLUME DESCRIPTION FUNCTION NOT AVAILABLE IN THIS VERSION OF ANDROID");
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                        }

                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_DESCRIPTION=" + svDescription));

                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_MTP_RESERVE_SPACE=" + storageVolume.getMtpReserveSpace()));
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_PATH=" + storageVolume.getPath()));
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_STORAGE_ID=" + storageVolume.getStorageId()));
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_EMULATED=" + storageVolume.isEmulated()));
                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_REMOVABLE=" + storageVolume.isRemovable()));

                        // see if mounted
                        String storageState = "";
                        if (Build.VERSION.SDK_INT < 23)
                        {
                            storageState = TestUtils.getExternalStorageState(storageVolume.getPath());
                        }
                        else
                        {
                            dbgLog(TAG, "storage volume get path=" + storageVolume.getPath(), 'i');
                            dbgLog(TAG, "storage volume get state=" + storageVolume.getState(), 'i');
                            storageState = storageVolume.getState();
                        }

                        strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_MOUNTED=" + storageState));

                        if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState)
                                || Environment.MEDIA_CHECKING.equals(storageState))
                        {
                            StatFs stat = new StatFs(storageVolume.getPath());
                            long bytesAvailable = (long) stat.getBlockSize() * (long) stat.getBlockCount();
                            strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_SIZE=" + bytesAvailable));
                        }
                        else
                        {
                            strDataList.add(String.format("STORAGE_VOLUME_" + numberOfStorageVolumes + "_SIZE=NOT_AVAILABLE"));
                        }
                    }
                }

                strDataList.add(String.format("NUMBER_OF_STORAGE_VOLUMES=" + numberOfStorageVolumes));

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
            }
            else
            {
                strDataList.add("MEMORY_TYPE_NOT_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("EXECUTE_WRITE_TEST"))
        {
            String volumeWritePath = null;

            List<String> strDataList = new ArrayList<String>();

            Parcelable[] storageVolumeList = null;

            try
            {
                methodGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");

            }
            catch (Throwable e)
            {
                methodGetVolumeList = null;
                e.printStackTrace();
            }

            if (methodGetVolumeList != null)
            {
                try
                {
                    storageVolumeList = (Parcelable[]) methodGetVolumeList.invoke(mStorageManager);
                }
                catch (Exception e)
                {
                    strDataList.add("GET_VOLUME_LIST_FAILED: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 1)
                {
                    strDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }

                int numberOfStorageVolumes = 0;

                for (int i = 0; i < storageVolumeList.length; i++)
                {
                    StorageVolume storageVolume = (StorageVolume) storageVolumeList[i];

                    if (strRxCmdDataList.get(0).equalsIgnoreCase("INTERNAL"))
                    {
                        if (storageVolume.isRemovable() == false)
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                            dbgLog(TAG, "volumeWritePath=" + volumeWritePath, 'i');
                        }
                    }
                    else if (strRxCmdDataList.get(0).equalsIgnoreCase("REMOVABLE"))
                    {
                        if ((storageVolume.isRemovable() == true) && (storageVolume.getPath().contains("usbdisk") == false)
                                && (storageVolume.getPath().contains("usbotg") == false))
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                            dbgLog(TAG, "volumeWritePath=" + volumeWritePath, 'i');
                        }
                    }
                    else
                    {
                        strDataList.add("UNKNOWN_MEMORY_TYPE: " + strRxCmdDataList.get(0));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                    }
                }

                if (numberOfStorageVolumes > 1)
                {
                    strDataList.add("EXECUTE_WRITE_TEST_CURRENTLY_SUPPORTS_ONE_STORAGE_DEVICE: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }
            else
            {
                strDataList.add("MEMORY_TYPE_NOT_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
            }

            if (volumeWritePath != null)
            {
                // first see if we have write access
                boolean mExternalStorageWriteable = false;

                if (Build.VERSION.SDK_INT < 23)
                {
                    String storageState = TestUtils.getExternalStorageState(volumeWritePath);

                    if (Environment.MEDIA_MOUNTED.equals(storageState))
                    {
                        mExternalStorageWriteable = true;
                    }
                    else
                    {
                        mExternalStorageWriteable = false;
                    }
                }
                else
                {
                    user_id = getUserId();
                    if (checkWritableM(volumeWritePath))
                    {
                        mExternalStorageWriteable = true;
                    }
                    else
                    {
                        mExternalStorageWriteable = false;
                    }
                }

                // If not writable then return fail
                if (mExternalStorageWriteable == false)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("MMC card at %s is not writable", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                boolean writeReadPassed = false;
                // Create a file and then read it back. Make sure the data is
                // the
                // same
                File tempFile = null;
                try
                {
                    String randomString = UUID.randomUUID().toString();

                    File dir = new File(volumeWritePath);
                    tempFile = File.createTempFile("mmc_wr_test", ".tmp", dir);

                    // write file
                    Writer output = new BufferedWriter(new FileWriter(tempFile));
                    output.write(randomString);
                    output.flush();
                    output.close();

                    // now read back
                    BufferedReader input = new BufferedReader(new FileReader(tempFile));

                    String tempData;
                    String readData = "";
                    StringBuffer res = new StringBuffer();
                    while ((tempData = input.readLine()) != null)
                    {
                        res.append(tempData);
                    }
                    input.close();

                    readData = res.toString();

                    if (randomString.contentEquals(readData) == false)
                    {
                        writeReadPassed = false;
                    }
                    else
                    {
                        writeReadPassed = true;
                    }
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "exception:" + e.toString(), 'i');
                    writeReadPassed = false;
                }
                finally
                {
                    // make klockwork happy
                    if ((tempFile != null) && tempFile.exists())
                    {
                        tempFile.delete();
                    }

                }

                if (writeReadPassed == false)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed write/read test on MMC card mounted at %s", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Volume Write Path is NULL", volumeWritePath));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("UNMOUNT_VOLUME"))
        {
            String volumeWritePath = null;

            List<String> strDataList = new ArrayList<String>();

            IMountService mountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));

            Parcelable[] storageVolumeList = null;

            try
            {
                methodGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");

            }
            catch (Throwable e)
            {
                methodGetVolumeList = null;
                e.printStackTrace();
            }

            if (methodGetVolumeList != null)
            {
                try
                {
                    storageVolumeList = (Parcelable[]) methodGetVolumeList.invoke(mStorageManager);
                }
                catch (Exception e)
                {
                    strDataList.add("GET_VOLUME_LIST_FAILED: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 1)
                {
                    strDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }

                int numberOfStorageVolumes = 0;

                for (int i = 0; i < storageVolumeList.length; i++)
                {
                    StorageVolume storageVolume = (StorageVolume) storageVolumeList[i];

                    if (strRxCmdDataList.get(0).equalsIgnoreCase("INTERNAL"))
                    {
                        if (storageVolume.isRemovable() == false)
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                        }
                    }
                    else if (strRxCmdDataList.get(0).equalsIgnoreCase("REMOVABLE"))
                    {
                        if ((storageVolume.isRemovable() == true) && (storageVolume.getPath().contains("usbdisk") == false)
                                && (storageVolume.getPath().contains("usbotg") == false))
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                        }
                    }
                    else
                    {
                        strDataList.add("UNKNOWN_MEMORY_TYPE: " + strRxCmdDataList.get(0));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                    }
                }

                if (numberOfStorageVolumes > 1)
                {
                    strDataList.add("UNMOUNT_MEMORY_CURRENTLY_SUPPORTS_ONE_STORAGE_DEVICE: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }
            else
            {
                strDataList.add("MEMORY_TYPE_NOT_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
            }

            if (volumeWritePath != null)
            {
                // first see if we have write access
                boolean mExternalStorageMounted = false;

                if (Build.VERSION.SDK_INT < 23)
                {
                    String storageState = TestUtils.getExternalStorageState(volumeWritePath);

                    if (Environment.MEDIA_MOUNTED.equals(storageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState) || Environment.MEDIA_CHECKING.equals(storageState))
                    {
                        mExternalStorageMounted = true;
                    }
                    else
                    {
                        mExternalStorageMounted = false;
                    }
                }
                else
                {
                    if (checkWritableM(volumeWritePath))
                    {
                        mExternalStorageMounted = true;
                    }
                    else
                    {
                        mExternalStorageMounted = false;
                    }
                }

                // If not writable then return fail
                if (mExternalStorageMounted == false)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Memory at %s is not mounted", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    mountService.unmountVolume(volumeWritePath, true, false);
                }
                catch (Exception e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Memory at %s unmount failure", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Volume Write Path is NULL", volumeWritePath));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("MOUNT_VOLUME"))
        {
            String volumeWritePath = null;

            List<String> strDataList = new ArrayList<String>();

            IMountService mountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));

            Parcelable[] storageVolumeList = null;

            try
            {
                methodGetVolumeList = mStorageManager.getClass().getMethod("getVolumeList");

            }
            catch (Throwable e)
            {
                methodGetVolumeList = null;
                e.printStackTrace();
            }

            if (methodGetVolumeList != null)
            {
                try
                {
                    storageVolumeList = (Parcelable[]) methodGetVolumeList.invoke(mStorageManager);
                }
                catch (Exception e)
                {
                    strDataList.add("GET_VOLUME_LIST_FAILED: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 1)
                {
                    strDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }

                int numberOfStorageVolumes = 0;

                for (int i = 0; i < storageVolumeList.length; i++)
                {
                    StorageVolume storageVolume = (StorageVolume) storageVolumeList[i];

                    if (strRxCmdDataList.get(0).equalsIgnoreCase("INTERNAL"))
                    {
                        if (storageVolume.isRemovable() == false)
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                        }
                    }
                    else if (strRxCmdDataList.get(0).equalsIgnoreCase("REMOVABLE"))
                    {
                        if ((storageVolume.isRemovable() == true) && (storageVolume.getPath().contains("usbdisk") == false)
                                && (storageVolume.getPath().contains("usbotg") == false))
                        {
                            numberOfStorageVolumes++;
                            volumeWritePath = storageVolume.getPath();
                        }
                    }
                    else
                    {
                        strDataList.add("UNKNOWN_MEMORY_TYPE: " + strRxCmdDataList.get(0));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                    }
                }

                if (numberOfStorageVolumes > 1)
                {
                    strDataList.add("UNMOUNT_MEMORY_CURRENTLY_SUPPORTS_ONE_STORAGE_DEVICE: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                }
            }
            else
            {
                strDataList.add("MEMORY_TYPE_NOT_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
            }

            if (volumeWritePath != null)
            {
                // first see if we have write access
                boolean mExternalStorageUnMounted = false;
                dbgLog(TAG, "volumeWritePath=" + volumeWritePath, 'i');

                if (Build.VERSION.SDK_INT < 23)
                {
                    String storageState = TestUtils.getExternalStorageState(volumeWritePath);

                    if (Environment.MEDIA_UNMOUNTED.equals(storageState))
                    {
                        mExternalStorageUnMounted = true;
                    }
                    else
                    {
                        mExternalStorageUnMounted = false;
                    }
                }
                else
                {
                    if (checkWritableM(volumeWritePath))
                    {
                        mExternalStorageUnMounted = false;
                    }
                    else
                    {
                        mExternalStorageUnMounted = true;
                    }
                }

                // If not writable then return fail
                if (mExternalStorageUnMounted == false)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Memory at %s is mounted", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    mountService.mountVolume(volumeWritePath);
                }
                catch (Exception e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Memory at %s mount failure", volumeWritePath));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Volume Write Path is NULL", volumeWritePath));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will verify the MMC mount state");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_STORAGE_VOLUME_STATUS - Returns status of selected storage volume");
        strHelpList.add("  ");
        strHelpList.add("EXECUTE_WRITE_TEST - Test if data can be written and read back from selected storage volume");
        strHelpList.add("  ");
        strHelpList.add("UNMOUNT_VOLUME - Unmount selected storage volume");
        strHelpList.add("  ");
        strHelpList.add("MOUNT_VOLUME - Unmount selected storage volume");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "MMC Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "MMC Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
