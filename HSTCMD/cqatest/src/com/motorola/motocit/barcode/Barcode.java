/*
 * Copyright (c) 2014-2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.barcode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.TelephonyProperties;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Barcode extends Test_Base
{

    TelephonyManager telephonyManager;

    private TextView barcode_textview;
    private TextView imei_meid_textview;
    private TextView imei_meid_2_textview;
    private TextView model_textview;
    private TextView iccid_sim_textview;
    private TextView iccid_sim2_textview;

    private String mBarcode;
    private String mIMEI_MEID;
    private String mIMEI_MEID_2;
    private String mModel;
    private String mIccidSim;
    private String mIccidSim2;

    private ImageView barcodeImg;
    private ImageView imeiImg;
    private ImageView imei2Img;
    private ImageView modelImg;
    private ImageView iccidSimImg;
    private ImageView iccidSim2Img;

    private Bitmap barcode_bmpImg = null;
    private Bitmap imei_bmpImg = null;
    private Bitmap imei2_bmpImg = null;
    private Bitmap model_bmpImg = null;
    private Bitmap iccid_sim_bmpImg = null;
    private Bitmap iccid_sim2_bmpImg = null;

    private static Method sMethodGetDefault;
    private static Method sMethodGetImeiMeid = null;
    private static Method sMethodGetIccidSim = null;
    private Class<?> MSimClass = null;
    private static Object sObject;

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Barcode";
        super.onCreate(savedInstanceState);
        setContentView(com.motorola.motocit.R.layout.barcode_layout);
        barcode_textview = (TextView) findViewById(com.motorola.motocit.R.id.barcode_textview);
        barcode_textview.setMovementMethod(ScrollingMovementMethod.getInstance());

        imei_meid_textview = (TextView) findViewById(com.motorola.motocit.R.id.imei_textview);
        imei_meid_2_textview = (TextView) findViewById(com.motorola.motocit.R.id.imei2_textview);
        model_textview = (TextView) findViewById(com.motorola.motocit.R.id.model_textview);
        iccid_sim_textview = (TextView) findViewById(com.motorola.motocit.R.id.iccid_sim_textview);
        iccid_sim2_textview = (TextView) findViewById(com.motorola.motocit.R.id.iccid_sim2_textview);

        barcodeImg = (ImageView) findViewById(com.motorola.motocit.R.id.barcode_img);
        imeiImg = (ImageView) findViewById(com.motorola.motocit.R.id.imei_img);
        imei2Img = (ImageView) findViewById(com.motorola.motocit.R.id.imei2_img);
        modelImg = (ImageView) findViewById(com.motorola.motocit.R.id.model_img);
        iccidSimImg = (ImageView) findViewById(com.motorola.motocit.R.id.iccidSim_img);
        iccidSim2Img = (ImageView) findViewById(com.motorola.motocit.R.id.iccidSim2_img);

        mBarcode = SystemProperties.get("ro.serialno", "unknown");
        mModel = Build.MODEL;
    }

    private boolean isDualSim() {

        try {
            String dualSim = SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, "unknown");
            dbgLog(TAG, "isDualSim-Property name=" + TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG, 'i');
            dbgLog(TAG, "isDualSim-Property value=" + dualSim, 'i');
            if (dualSim.equals("dsds") || dualSim.equals("dsda")) {
                return true;
            }
        }
        catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean preImeiCheckDigit(String imeiDigit){
        boolean lengthcheck = false;
        // Check if IMEI has all 14 digits
        if((imeiDigit!= null) && (imeiDigit.length() == 14)){
           lengthcheck = true;
        }
        return lengthcheck;
    }

    private String calculateImeiCheckDigit(String imei14Digits) {

        int [] imeiArray = new int[imei14Digits.length()];

        for (int i=0; i<imei14Digits.length(); i++) {
            imeiArray[i] = Character.getNumericValue(imei14Digits.charAt(i));
        }

        // Start Luhn algorithm
        int sum = 0;

        for (int i=0; i<imei14Digits.length(); i++) {
            // step 1: double the digit every two positions
            if ((i % 2) == 0) {
                sum += imeiArray[i];
            }
            else {
                int digitDouble = imeiArray[i] * 2;

                // step 2: sum the digits
                if (digitDouble >= 10) {
                    sum += digitDouble % 10; // get second digit of digitDouble
                    sum += digitDouble / 10; // get first digit of digitDouble
                }
                else {
                    sum += digitDouble;
                }
            }
        }

        // step 3: find the number that makes the sum divisible by 10
        int checkDigit = (10 - (sum  % 10)) % 10;

        return imei14Digits + Integer.toString(checkDigit);

    }

    protected void getData()
    {
        dbgLog(TAG, "getData-Android SDK=" + android.os.Build.VERSION.SDK_INT, 'i');

        if (android.os.Build.VERSION.SDK_INT >= 22) {
            // Read out first IMEI
            mIMEI_MEID = telephonyManager.getDeviceId();

            if(preImeiCheckDigit(mIMEI_MEID)) { // Add 15th verification digit if it's missing.
                mIMEI_MEID = calculateImeiCheckDigit(mIMEI_MEID);
            }

            dbgLog(TAG, "IMEI/MEID #1=" + mIMEI_MEID, 'i');

            // Check if it's DS device and read out IMEI/MEID if is.
            if (isDualSim()) {
                mIMEI_MEID_2 = telephonyManager.getDeviceId(1);

                if (preImeiCheckDigit(mIMEI_MEID_2)) { // Add 15th verification digit if it's missing.
                    mIMEI_MEID_2 = calculateImeiCheckDigit(mIMEI_MEID_2);
                }

                dbgLog(TAG, "IMEI/MEID #2=" + mIMEI_MEID_2, 'i');
            }

            SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
            List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
            if(null != subscriptions) {
                if(subscriptions.size() > 0){
                    mIccidSim = subscriptions.get(0).getIccId();
                }
                if(subscriptions.size() > 1 && isDualSim()) {
                    mIccidSim2 = subscriptions.get(1).getIccId();
                }
            }
        }
        else {
            if (sMethodGetImeiMeid == null)
            {
                try
                {
                    sMethodGetImeiMeid = MSimClass.getDeclaredMethod("getDeviceId", Integer.TYPE);
                    MSimClass = Class.forName("android.telephony.MSimTelephonyManager");
                    sMethodGetDefault = MSimClass.getDeclaredMethod("getDefault");
                    sObject = sMethodGetDefault.invoke(null);
                }
                catch (Exception ex)
                {
                    // method does not exist
                    sMethodGetImeiMeid = null;
                }
            }

            if (sMethodGetImeiMeid != null)
            {
                try
                {
                    mIMEI_MEID = (sMethodGetImeiMeid.invoke(sObject, 0)).toString();

                    if (preImeiCheckDigit(mIMEI_MEID)) { // Add 15th verification digit if it's missing.
                        mIMEI_MEID = calculateImeiCheckDigit(mIMEI_MEID);
                    }

                    dbgLog(TAG, "IMEI/MEID #1=" + mIMEI_MEID, 'i');

                    if (isDualSim()) {
                        mIMEI_MEID_2 = (sMethodGetImeiMeid.invoke(sObject, 1)).toString();

                        if (preImeiCheckDigit(mIMEI_MEID_2)) { // Add 15th verification digit if it's missing.
                            mIMEI_MEID_2 = calculateImeiCheckDigit(mIMEI_MEID_2);
                        }

                        dbgLog(TAG, "IMEI/MEID #2=" + mIMEI_MEID_2, 'i');
                    }                
                }
                catch (Exception e) {}
            }
            else if(TextUtils.isEmpty(mIMEI_MEID))
            {
                dbgLog(TAG, "IMEI/MEID #1 still empty. Reading it out.", 'i');
                mIMEI_MEID = telephonyManager.getDeviceId();

                if (preImeiCheckDigit(mIMEI_MEID)) { // Add 15th verification digit if it's missing.
                    mIMEI_MEID = calculateImeiCheckDigit(mIMEI_MEID);
                }

                dbgLog(TAG, "IMEI/MEID #1=" + mIMEI_MEID, 'i');
            }
        }

        if (sMethodGetIccidSim == null)
        {
            try
            {
                sMethodGetIccidSim = MSimClass.getDeclaredMethod("getSimSerialNumber", Integer.TYPE);
            }
            catch (Exception ex)
            {
                // method does not exist
                sMethodGetIccidSim = null;
            }
        }

        if (sMethodGetIccidSim != null)
        {
            try
            {
                mIccidSim = (sMethodGetIccidSim.invoke(sObject, 0)).toString();
                dbgLog(TAG, "ICCID-SIM #1=" + mIccidSim, 'i');

                if(isDualSim()) {
                    mIccidSim2 = (sMethodGetIccidSim.invoke(sObject, 1)).toString();
                    dbgLog(TAG, "ICCID-SIM #2=" + mIccidSim2, 'i');
                }
            }
            catch (Exception e) {}
        }
        else if (TextUtils.isEmpty(mIccidSim))
        {
            dbgLog(TAG, "ICCID-SIM #1= still empty. Reading it out.", 'i');
            mIccidSim = telephonyManager.getSimSerialNumber();
            dbgLog(TAG, "ICCID-SIM #1=" + mIccidSim, 'i');
        }

    }

    protected void printData()
    {
        dbgLog(TAG, "printData", 'i');

        barcode_textview.setText("Barcode: " + mBarcode);
        model_textview.setText("Model: " + mModel);
        imei_meid_textview.setText("");
        imei_meid_2_textview.setText("");
        iccid_sim_textview.setText("");
        iccid_sim2_textview.setText("");

        if (TextUtils.isEmpty(mIMEI_MEID) == false)
        {
            imei_meid_textview.setText("IMEI_MEID: " + mIMEI_MEID);
        }

        if (TextUtils.isEmpty(mIMEI_MEID_2) == false)
        {
            imei_meid_2_textview.setText("IMEI_MEID_2: " + mIMEI_MEID_2);
        }

        if (TextUtils.isEmpty(mIccidSim) == false)
        {
            iccid_sim_textview.setText("SIM ICCID: " + mIccidSim);
        }

        if (TextUtils.isEmpty(mIccidSim2) == false)
        {
            iccid_sim2_textview.setText("SIM ICCID 2: " + mIccidSim2);
        }
    }

    protected void generateImg()
    {
        // Generate img for barcode
        dbgLog(TAG, "start to generate barcode img", 'i');
        if ((TextUtils.isEmpty(mBarcode) == false) && (mBarcode.contentEquals("unknown") == false))
        {
            barcode_bmpImg = TestUtils.GenerateImage(mBarcode, "1d");

            if (barcode_bmpImg != null)
            {
                barcodeImg.setImageBitmap(barcode_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mBarcode is empty", 'e');
        }

        // Generate img for model
        dbgLog(TAG, "start to generate model img", 'i');
        if (TextUtils.isEmpty(mModel) == false)
        {
            model_bmpImg = TestUtils.GenerateImage(mModel, "1d");

            if (model_bmpImg != null)
            {
                modelImg.setImageBitmap(model_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mModel is empty", 'e');
        }

        // Generate img for imei 1 (meid if it is cdma device)
        dbgLog(TAG, "start to generate imei 1 img", 'i');
        if (TextUtils.isEmpty(mIMEI_MEID) == false)
        {
            imei_bmpImg = TestUtils.GenerateImage(mIMEI_MEID, "1d");

            if (imei_bmpImg != null)
            {
                imeiImg.setImageBitmap(imei_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mIMEI_MEID is empty", 'e');
        }

        // Generate img for sim 1 ICCID
        dbgLog(TAG, "start to generate sim 1 ICCID img", 'i');
        if (TextUtils.isEmpty(mIccidSim) == false)
        {
            iccid_sim_bmpImg = TestUtils.GenerateImage(mIccidSim, "1d");

            if (iccid_sim_bmpImg != null)
            {
                iccidSimImg.setImageBitmap(iccid_sim_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mIccidSim is empty", 'e');
        }

        // Generate img for imei 2 (meid if it is cdma device)
        dbgLog(TAG, "start to generate imei 2 img", 'i');
        if (TextUtils.isEmpty(mIMEI_MEID_2) == false)
        {
            imei2_bmpImg = TestUtils.GenerateImage(mIMEI_MEID_2, "1d");

            if (imei2_bmpImg != null)
            {
                imei2Img.setImageBitmap(imei2_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mIMEI_MEID_2 is empty", 'e');
        }

        // Generate img for sim2 ICCID
        dbgLog(TAG, "start to generate sim 2 ICCID img", 'i');
        if (TextUtils.isEmpty(mIccidSim2) == false)
        {
            iccid_sim2_bmpImg = TestUtils.GenerateImage(mIccidSim2, "1d");

            if (iccid_sim2_bmpImg != null)
            {
                iccidSim2Img.setImageBitmap(iccid_sim2_bmpImg);
            }
        }
        else
        {
            dbgLog(TAG, "mIccidSim2 is empty", 'e');
        }
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
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, 1001);
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            getData();
            printData();
            generateImg();

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Sim test");
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        // If get here then unrecognised tell cmd
        // Generate an exception to send FAIL result and mesg back to CommServer
        List<String> strErrMsgList = new ArrayList<String>();
        strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
        dbgLog(TAG, strErrMsgList.get(0), 'i');
        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function generate barcode img for IMEI, IMEI2, MEID, Barcode, Model and Sim ICCID");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.clear();
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if (wasActivityStartedByCommServer() == true)
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Barcode Img Generate Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Barcode Img Generate Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Barcode Img Generate Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Barcode Img Generate Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
