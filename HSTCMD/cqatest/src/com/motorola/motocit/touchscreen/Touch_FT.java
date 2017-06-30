/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.touchscreen;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;
import com.focaltech.tp.test.FT_Test;
import com.focaltech.tp.test.FT_Test_FT5X46;
import com.focaltech.tp.test.FT_Test_FT8716;

import android.os.SystemProperties;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Touch_FT extends Test_Base
{
    private FT_Test m_Test = null;
    private boolean m_bDevice = false;
    private final String TP_CONFIG_FILE_PATH = "/system/etc/motorola/12m/";
    private final static String TP_SYS_NODE = "/sys/devices/virtual/touchscreen/";
    private final static String GRAPHICS_SYS_NODE = "/sys/devices/virtual/graphics/fb0/panel_supplier";
    private TextView m_textResult = null;
    private String m_focalTechConfigFile;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Touch_FT";
        super.onCreate(savedInstanceState);
        this.setTitle(getString(com.motorola.motocit.R.string.tcmd_touchscreen_touch_ft));
        adjustViewDisplayArea(com.motorola.motocit.R.layout.touch_ft);
        m_textResult = (TextView)findViewById(com.motorola.motocit.R.id.text_result);
    }

    private void sendMsgToHandler(int msgType, String remark)
    {
        Message msg = Message.obtain();
        msg.what = msgType;
        Bundle bundle = new Bundle();
        bundle.putString("result", remark);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Bundle bundle = null;
            super.handleMessage(msg);
            switch (msg.what){
            case 0:
                removeMessages(0);
                bundle = msg.getData();
                String result = bundle.getString("result");
                if(result.indexOf("Passed") == 0){
                    m_textResult.setText(result);
                    m_textResult.setTextColor(Color.GREEN);
                }else{
                    m_textResult.setText(result);
                    m_textResult.setTextColor(Color.RED);
                }
                break;
            default:
                break;
            }
        }
    };

    private String getTouchVendor(String cmd){
        String  touchVendor = "";
        FileInputStream in = null;

        try{
            in = new FileInputStream(TP_SYS_NODE + cmd + "/vendor");
            int len = 0;
            byte[] data1 = new byte[100];
            try{
                len = in.read(data1);
                touchVendor = new String(data1, 0, len);
            }catch (IOException e){
                e.printStackTrace();
            }
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }finally{
            if (in != null){
                try{
                    in.close();
                    in = null;
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        dbgLog(TAG, "touchVendor="+touchVendor, 'i');
        return touchVendor;
    }

    private String getLCDVendor(){
        String  lcdVendor = "";
        FileInputStream in = null;

        try{
            in = new FileInputStream(GRAPHICS_SYS_NODE);
            int len = 0;
            byte[] data1 = new byte[100];
            try{
                len = in.read(data1);
                lcdVendor = new String(data1, 0, len);
            }catch (IOException e){
                e.printStackTrace();
            }
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }finally{
            if (in != null){
                try{
                    in.close();
                    in = null;
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        dbgLog(TAG, "lcdVendor="+lcdVendor, 'i');
        return lcdVendor;
    }

    private String getProductName(){
        return SystemProperties.get("ro.hw.device", "unknown");
    }

    private String getBarcode(){
        return SystemProperties.get("ro.serialno", "unknown");
    }

    private String createReportFileName(){
        String reportFileName = "";
        DateFormat date = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentTime = date.format(new Date());
        reportFileName = "TPTestResult_"+getBarcode()+"_"+currentTime;
        return reportFileName;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if(strRxCmd.equalsIgnoreCase("ft5436")){
            m_Test = new FT_Test_FT5X46();
        }else if(strRxCmd.equalsIgnoreCase("ft8716")){
            m_Test = new FT_Test_FT8716();
        }
        if (m_Test != null){
            List<String> strDataList = new ArrayList<String>();
            int tpTestResult = 999;
            String reportFileName = "";
            if(m_Test != null){
                m_bDevice = m_Test.initDevice();
            }else{
                strDataList.add("FT_Test " + strRxCmd + " is null!");
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
                return;
            }
            int iVID = m_Test.readReg(0xA3);
            if(iVID == 0x54){
                String touchVendor = getTouchVendor(strRxCmd.toLowerCase().trim());
                String lcdVendor = getLCDVendor().trim();
                String productName = getProductName().trim();
                //iniConfigFilePath: ini file name,format:[tpVendor]-[lcdVendor]-[chipModel]-[productName].ini
                String iniConfigFilePath = TP_CONFIG_FILE_PATH + touchVendor + "-" + lcdVendor + "-" + strRxCmd.toLowerCase().trim() + "-" + productName + ".ini";
                dbgLog(TAG, "iniConfigFilePath="+iniConfigFilePath, 'i');
                File file = new File(iniConfigFilePath);
                if(!file.exists()){
                    strDataList.add(iniConfigFilePath + " not exists!");
                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                    sendInfoPacketToCommServer(infoPacket);
                    return;
                }
                m_Test.loadConfig(iniConfigFilePath);
                reportFileName = createReportFileName();
                m_Test.createReport(Environment.getExternalStorageDirectory().getPath(), reportFileName);
                dbgLog(TAG, "reportFileName="+reportFileName, 'i');
                try{
                    tpTestResult = m_Test.startTestTP();
                }catch(Exception ex){
                    ex.printStackTrace();
                }
            }else{
                dbgLog(TAG, "readReg 0xA3 Error!", 'i');
                sendMsgToHandler(0, "Failed, readReg 0xA3 Error!");
            }
            String result = "";
            if(tpTestResult == 0){
                result = "Passed,testResult="+tpTestResult;
            }else{
                result = "Failed,testResult="+tpTestResult;
                dbgLog(TAG, "tpTestResult:"+result+", ReportPath="+Environment.getExternalStorageDirectory().getPath()+"/"+reportFileName, 'i');
            }
            strDataList.add(result);
            sendMsgToHandler(0, result);
            if(m_bDevice){
                m_Test.closeReport();
                m_Test.releaseDevice();
                m_Test = null;
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("");
        strHelpList.add("TOUCH_Focaltech - touch for focaltech test");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
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

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();

        startFocalTechTest();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))
        {

            contentRecord("testresult.txt", "Touch Screen - Touch_FT:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

    /** Confirm that the configuration file exists.
    *
    *@param iniConfigFileName The configuration file name
    *@return true The configuration file exists
    */
    private boolean checkConfigFileExists(String iniConfigFileName)
    {
        dbgLog(TAG, "iniConfigFileName = " + iniConfigFileName, 'd');

        File configFile= new File(iniConfigFileName);

        if(configFile.exists())
        {
            return true;
        }
        else
        {
            dbgLog(TAG, iniConfigFileName + " file not Exists", 'e');
            return false;
        }
    }

    /** Create a new thread for FocalTech testing.
    *
    */
    private void startFocalTechTest()
    {
        if(!wasActivityStartedByCommServer())
        {
            String ft5436ConfigFile = TP_CONFIG_FILE_PATH + "focaltech-tianma-ft5436-sanders.ini";
            String ft8716ConfigFile = TP_CONFIG_FILE_PATH + "focaltech-tianma-ft8716-sanders.ini";

            if (checkConfigFileExists(ft5436ConfigFile))
            {
                m_focalTechConfigFile = ft5436ConfigFile;
                dbgLog(TAG, m_focalTechConfigFile + " file Exists", 'd');

                m_Test = new FT_Test_FT5X46();
            }else if (checkConfigFileExists(ft8716ConfigFile)){
                m_focalTechConfigFile = ft8716ConfigFile;
                dbgLog(TAG, m_focalTechConfigFile + " file Exists", 'd');

                m_Test = new FT_Test_FT8716();
            }else{
                dbgLog(TAG, "There isn't the Focal Tech config File", 'e');
                Toast.makeText(Touch_FT.this, "There isn't the Focal Tech config File", Toast.LENGTH_SHORT).show();

                Touch_FT.this.finish();
                return;
            }

            new Thread(){
                @Override
                public void run(){

                    if (m_Test != null)
                    {
                        int tpTestResult = 999;
                        String reportFileName = "";

                        m_bDevice = m_Test.initDevice();

                        int iVID = m_Test.readReg(0xA3);
                        if(iVID == 0x54)
                        {
                            m_Test.loadConfig(m_focalTechConfigFile);

                            reportFileName = createReportFileName();
                            /*Create Report file in the "/mnt/sdcard/" directory*/
                            m_Test.createReport(Environment.getExternalStorageDirectory().getPath(), reportFileName);
                            dbgLog(TAG, "reportFileName = "+reportFileName, 'i');
                            try
                            {
                                tpTestResult = m_Test.startTestTP();
                            }catch(Exception ex){
                                ex.printStackTrace();
                            }
                        }else{
                            dbgLog(TAG, "readReg 0xA3 Error!", 'i');
                            sendMsgToHandler(0, "Failed, readReg 0xA3 Error!");
                        }

                        String result = "";
                        if(tpTestResult == 0)
                        {
                            result = "Passed,testResult="+tpTestResult;
                        }else{
                            result = "Failed,testResult="+tpTestResult;
                            dbgLog(TAG, "tpTestResult:"+result+", ReportPath="+Environment.getExternalStorageDirectory().getPath()+"/"+reportFileName, 'i');
                        }

                        /*send the test result to handle and update the UI*/
                        sendMsgToHandler(0, result);

                        if(m_bDevice)
                        {
                            m_Test.closeReport();
                            m_Test.releaseDevice();
                            m_Test = null;
                        }
                    }else{
                        dbgLog(TAG, "m_Test" + " is null!", 'e');
                        return;
                    }
                }
            }.start();
        }else{
            dbgLog(TAG, "ActivityStartedByCommServer" , 'd');
            return;
        }
    }
}
