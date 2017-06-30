/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.version;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Version extends Test_Base
{
    // private String strCQATestVersion;
    private String bpVersion;
    private String mFlexVersion;
    private String apVersion_RO;
    private String mcfgVersion;
    private TelephonyManager telephonyManager = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Version";
        super.onCreate(savedInstanceState);

        // Obtain TELEPHONY_SERVICE for IMEI information
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.version);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        TextView apVersionText = (TextView) findViewById(com.motorola.motocit.R.id.version_ap);
        TextView bpVersionText = (TextView) findViewById(com.motorola.motocit.R.id.version_bp);
        TextView CQATestVersionText = (TextView) findViewById(com.motorola.motocit.R.id.version_CQATest_package);

        // Retrieve AP Version from Build.DISPLAY
        apVersionText.setTextColor(Color.GREEN);
        apVersionText.setText(Build.DISPLAY);

        // Retrieve AP Version from "ro.build.description"
        apVersion_RO = SystemProperties.get("ro.build.description", "RO_BUILD_AP_UNKNOWN");

        // Retrieve BP Version from "gsm.version.baseband"
        bpVersion = SystemProperties.get("gsm.version.baseband", "BP_UNKNOWN");
        // Retrieve mcfg version
        mcfgVersion =  SystemProperties.get("persist.radio.mcfg_version", "");
        bpVersionText.setTextColor(Color.GREEN);
        bpVersionText.setText(bpVersion + " " + mcfgVersion);

        // Retrieve Package version
        CQATestVersionText.setTextColor(Color.GREEN);
        CQATestVersionText.setText(versionName);

        // Retrieve Flex version
        mFlexVersion = SystemProperties.get("ro.gsm.flexversion", "UNKNOWN FLEX");
        if (mFlexVersion.contentEquals("UNKNOWN FLEX"))
        {
            mFlexVersion = SystemProperties.get("ro.flexversion", "UNKNOWN FLEX");
            if (mFlexVersion.contentEquals("UNKNOWN FLEX"))
            {
                mFlexVersion = SystemProperties.get("ro.cdma.flexversion", "UNKNOWN FLEX");
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP))
        {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            {
                contentRecord("testresult.txt", "Version Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "Version Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "AP: " + Build.DISPLAY + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "BP: " + bpVersion + "\r\n\r\n", MODE_APPEND);

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
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_VERSION"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strDataList = new ArrayList<String>();

                for (int i = 0; i < strRxCmdDataList.size(); i++)
                {
                    // -----------------------------------------------
                    // ALL - returns all the versions listed
                    // -----------------------------------------------
                    // Motorola Version
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("AP_VERSION") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("AP_VERSION=" + apVersion_RO);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("BP_VERSION") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("BP_VERSION=" + bpVersion);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("CQATEST_VERSION") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("CQATEST_VERSION=" + versionName);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("FLEX_VERSION") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("FLEX_VERSION=" + mFlexVersion);
                    }
                    // Android OS Build
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("BOARD") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("BOARD=" + Build.BOARD);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("BOOTLOADER") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (Build.VERSION.SDK_INT >= 8)
                        {
                            strDataList.add("BOOTLOADER=" + Build.BOOTLOADER);
                        }
                        else
                        {
                            strDataList.add("BOOTLOADER=NOT SUPPORTED UNTIL API LEVEL 8");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("BRAND") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("BRAND=" + Build.BRAND);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("CPU_ABI") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("CPU_ABI=" + Build.CPU_ABI);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("CPU_ABI2") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (Build.VERSION.SDK_INT >= 8)
                        {
                            strDataList.add("CPU_ABI2=" + Build.CPU_ABI2);
                        }
                        else
                        {
                            strDataList.add("CPU_ABI2=NOT SUPPORTED UNTIL API LEVEL 8");
                        }

                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("DEVICE") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("DEVICE=" + Build.DEVICE);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("DEVICE_ID") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (telephonyManager != null)
                        {
                            strDataList.add("DEVICE_ID=" + telephonyManager.getDeviceId());
                        }
                        else
                        {
                            strDataList.add("DEVICE_ID=UNAVAILABLE");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("DEVICE_ID2") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (telephonyManager != null)
                        {
                            strDataList.add("DEVICE_ID2=" + telephonyManager.getDeviceId(1));
                        }
                        else
                        {
                            strDataList.add("DEVICE_ID2=UNAVAILABLE");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("DEVICE_SW_VERSION") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (telephonyManager != null)
                        {
                            strDataList.add("DEVICE_SW_VERSION=" + telephonyManager.getDeviceSoftwareVersion());
                        }
                        else
                        {
                            strDataList.add("DEVICE_SW_VERSION=UNAVAILABLE");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("DISPLAY") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("DISPLAY=" + Build.DISPLAY);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("FINGERPRINT") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("FINGERPRINT=" + Build.FINGERPRINT);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("HARDWARE") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (Build.VERSION.SDK_INT >= 8)
                        {
                            strDataList.add("HARDWARE=" + Build.HARDWARE);
                        }
                        else
                        {
                            strDataList.add("HARDWARE=NOT SUPPORTED UNTIL API LEVEL 8");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("HOST") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("HOST=" + Build.HOST);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("ID") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("ID=" + Build.ID);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("MANUFACTURER") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("MANUFACTURER=" + Build.MANUFACTURER);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("MODEL") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("MODEL=" + Build.MODEL);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("PHONE_TYPE") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (telephonyManager != null)
                        {
                            int phoneType = telephonyManager.getPhoneType();
                            switch (phoneType)
                            {
                            case TelephonyManager.PHONE_TYPE_NONE:
                                strDataList.add("PHONE_TYPE=NONE");
                                break;
                            case TelephonyManager.PHONE_TYPE_GSM:
                                strDataList.add("PHONE_TYPE=GSM");
                                break;
                            case TelephonyManager.PHONE_TYPE_CDMA:
                                strDataList.add("PHONE_TYPE=CDMA");
                                break;
                            case TelephonyManager.PHONE_TYPE_SIP:
                                strDataList.add("PHONE_TYPE=SIP");
                                break;
                            default:
                                strDataList.add("PHONE_TYPE=UNKNOWN");
                            }
                        }
                        else
                        {
                            strDataList.add("PHONE_TYPE=UNAVAILABLE");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("PRODUCT") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("PRODUCT=" + Build.PRODUCT);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("RADIO") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if ((Build.VERSION.SDK_INT >= 8) && (Build.VERSION.SDK_INT < 14))
                        {
                            strDataList.add("RADIO=" + Build.RADIO);
                        }
                        else if (Build.VERSION.SDK_INT >= 14)
                        {
                            strDataList.add("RADIO=" + Build.getRadioVersion());
                        }
                        else
                        {
                            strDataList.add("RADIO=NOT SUPPORTED UNTIL API LEVEL 8");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("SERIAL") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        if (Build.VERSION.SDK_INT >= 9)
                        {
                            strDataList.add("SERIAL=" + Build.SERIAL);
                        }
                        else
                        {
                            strDataList.add("SERIAL=NOT SUPPORTED UNTIL API LEVEL 9");
                        }
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("TAGS") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("TAGS=" + Build.TAGS);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("TIME") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("TIME=" + String.valueOf(Build.TIME));
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("TYPE") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("TYPE=" + Build.TYPE);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("USER") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("USER=" + Build.USER);
                    }
                    // Android OS Build.Version
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("VERSION_CODENAME") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("VERSION_CODENAME=" + Build.VERSION.CODENAME);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("VERSION_INCREMENTAL") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("VERSION_INCREMENTAL=" + Build.VERSION.INCREMENTAL);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("VERSION_RELEASE") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("VERSION_RELEASE=" + Build.VERSION.RELEASE);
                    }
                    if (strRxCmdDataList.get(i).equalsIgnoreCase("VERSION_SDK_INT") || strRxCmdDataList.get(i).equalsIgnoreCase("ALL"))
                    {
                        strDataList.add("VERSION_SDK_INT=" + Build.VERSION.SDK_INT);
                    }
                    // Unknown command
                    if (strDataList.isEmpty())
                    {
                        strDataList.add("UNKNOWN=" + strRxCmdDataList.get(i));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strDataList);
                    }

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                    sendInfoPacketToCommServer(infoPacket);
                    strDataList.clear();
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
        else if (strRxCmd.equalsIgnoreCase("GET_SYSTEM_PROPERTY"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strDataList = new ArrayList<String>();

                for (int i = 0; i < strRxCmdDataList.size(); i++)
                {
                    String sysPropName = strRxCmdDataList.get(i);

                    // make sure sysPropName does not contain a space
                    if (sysPropName.contains(" ") || sysPropName.contains("\t"))
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("system property name '%s' cannot contain whitespace", sysPropName));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    String sysPropValue = SystemProperties.get(sysPropName, "SYSTEM_PROPERTY_NOT_DEFINED");
                    strDataList.add(sysPropName + "=" + sysPropValue);
                }

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
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
        strHelpList.add("This function returns the different SW Versions");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_VERSION - Which SW Version to read");
        strHelpList.add("  ");
        strHelpList.add("  ALL - Returns all Versions");
        strHelpList.add("  ");
        strHelpList.add("  AP_VERSION - Read Motorola AP Version");
        strHelpList.add("  ");
        strHelpList.add("  BP_VERSION - Read Motorola BP Version");
        strHelpList.add("  ");
        strHelpList.add("  CQATEST_VERSION - Read CQATest Version");
        strHelpList.add("  ");
        strHelpList.add("  FLEX_VERSION - Read Motorola Flex Version");
        strHelpList.add("  ");
        strHelpList.add("  BOARD - Read Board Version");
        strHelpList.add("  ");
        strHelpList.add("  BOOTLOADER - Read Bootloader Version");
        strHelpList.add("  ");
        strHelpList.add("  BRAND - Read Brand");
        strHelpList.add("  ");
        strHelpList.add("  CPU_ABI - Read CPU_ABI");
        strHelpList.add("  ");
        strHelpList.add("  CPU_ABI2 - Read CPU_ABI2");
        strHelpList.add("  ");
        strHelpList.add("  DEVICE - Read Device");
        strHelpList.add("  ");
        strHelpList.add("  DEVICE_ID - Read Device ID");
        strHelpList.add("  ");
        strHelpList.add("  DEVICE_ID2 - Read Device ID 2");
        strHelpList.add("  ");
        strHelpList.add("  DEVICE_SW_VERSION - Read Device SW Version");
        strHelpList.add("  ");
        strHelpList.add("  DISPLAY - Read Display");
        strHelpList.add("  ");
        strHelpList.add("  FINGERPRINT - Read Fingerprint");
        strHelpList.add("  ");
        strHelpList.add("  HARDWARE - Read Hardware");
        strHelpList.add("  ");
        strHelpList.add("  HOST - Read Host");
        strHelpList.add("  ");
        strHelpList.add("  ID - Read ID");
        strHelpList.add("  ");
        strHelpList.add("  MANUFACTURER - Read Manufacturer");
        strHelpList.add("  ");
        strHelpList.add("  MODEL - Read Model");
        strHelpList.add("  ");
        strHelpList.add("  PHONE_TYPE - Read Phone Type");
        strHelpList.add("  ");
        strHelpList.add("  PRODUCT - Read Product");
        strHelpList.add("  ");
        strHelpList.add("  RADIO - Read Radio");
        strHelpList.add("  ");
        strHelpList.add("  SERIAL - Read Serial");
        strHelpList.add("  ");
        strHelpList.add("  TAGS - Read Tags");
        strHelpList.add("  ");
        strHelpList.add("  TIME - Read Time");
        strHelpList.add("  ");
        strHelpList.add("  TYPE - Read Type Version");
        strHelpList.add("  ");
        strHelpList.add("  USER - Read User Version");
        strHelpList.add("  ");
        strHelpList.add("  VERSION_CODENAME - Read Version Codename");
        strHelpList.add("  ");
        strHelpList.add("  VERSION_INCREMENTAL - Read Version Incremental");
        strHelpList.add("  ");
        strHelpList.add("  VERSION_RELEASE - Read Version Release");
        strHelpList.add("  ");
        strHelpList.add("  VERSION_SDK - Read Version Sdk_int");
        strHelpList.add("  ");
        strHelpList.add("GET_SYSTEM_PROPERTY <sysprop_name> - read specified system property.");
        strHelpList.add("                                     If not defined then returns SYSTEM_PROPERTY_NOT_DEFINED");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Version Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "AP: " + Build.DISPLAY + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "BP: " + bpVersion + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_FAIL);

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
        contentRecord("testresult.txt", "Version Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "AP: " + Build.DISPLAY + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "BP: " + bpVersion + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_PASS);

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

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();
        testResultName.add("AP_VERSION");
        testResultName.add("BP_VERSION");
        testResultName.add("CQATEST_VERSION");
        testResultName.add("DISPLAY");

        testResultValues.add(String.valueOf(apVersion_RO));
        testResultValues.add(String.valueOf(bpVersion));
        testResultValues.add(String.valueOf(versionName));
        testResultValues.add(String.valueOf(Build.DISPLAY));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
