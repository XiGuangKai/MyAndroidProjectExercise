/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.hostmode;

import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Hostmode extends Test_Base
{
    public static final int DELAY_MILLIS = 500;
    public static final int MESSAGE_REFRESH = 0;

    private String hostmode;
    private TextView hostmodeTextView = null;
    private UsbManager mUsbManager = null;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "USB_Hostmode";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.hostmode);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        hostmodeTextView = (TextView) findViewById(com.motorola.motocit.R.id.hostmode_text);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mHandler = new USBHandler();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        refresh();
        sendStartActivityPassed();
    }

    private void refresh() {
        if (isHostMode())
        {
            hostmode = "Device is in USB HOST MODE";
            hostmodeTextView.setTextColor(Color.RED);
        }
        else
        {
            hostmode = "Device is NOT in USB HOST MODE";
            hostmodeTextView.setTextColor(Color.GREEN);
        }

        hostmodeTextView.setText(hostmode);
        mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, DELAY_MILLIS);
    }

    private boolean isHostMode()
    {
        boolean result = false;

        try
        {
            dbgLog(TAG, "checking /d/usb/device", 'i');
            BufferedReader breader = new BufferedReader(new FileReader("/d/usb/devices"));
            String line = "";

            if ((line = breader.readLine()) != null)
            {
                result = true;
            }
            breader.close();
        }
        catch (Exception e)
        {
            dbgLog(TAG, "!!! Some exception in checking d usb devices", 'd');
        }

        if (result == false)
        {
            try
            {
                dbgLog(TAG, "checking /sys/bus/usb/devices", 'i');
                File sysBusDirectory = new File("/sys/bus/usb/devices");

                if (sysBusDirectory.isDirectory())
                {
                    if (sysBusDirectory.list().length != 0)
                    {
                        result = true;
                    }
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "!!! Some exception in checking sys bus usb devices", 'd');
            }
        }

        return result;
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
                contentRecord("testresult.txt", "USB Host Mode: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "USB Host Mode: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "STATE: " + hostmode + "\r\n", MODE_APPEND);

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
        if (strRxCmd.equalsIgnoreCase("GET_USBHOSTMODE"))
        {
            List<String> strDataList = new ArrayList<String>();
            if (isHostMode())
            {
                strDataList.add("USBHOSTMODE=YES");
            }
            else
            {
                strDataList.add("USBHOSTMODE=NO");
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_USB_DEVICE_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            HashMap<String, UsbDevice> usbDeviceList = mUsbManager.getDeviceList();

            strDataList.add("NUMBER_USB_ACCESSORIES=" + usbDeviceList.size());

            if(usbDeviceList.size() > 0)
            {
                int usbDeviceNumber = 0;
                for (String usbDeviceListKey : usbDeviceList.keySet())
                {
                    UsbDevice currentUsbDevice = usbDeviceList.get(usbDeviceListKey);

                    strDataList.add("USB_DEVICE_NAME_" + usbDeviceNumber + "=" + currentUsbDevice.getDeviceName());
                    strDataList.add("USB_DEVICE_MANUFACTURER_NAME_" + usbDeviceNumber + "=" + currentUsbDevice.getManufacturerName());
                    strDataList.add("USB_DEVICE_ID_" + usbDeviceNumber + "=" + currentUsbDevice.getDeviceId());
                    strDataList.add("USB_DEVICE_PRODUCT_ID_" + usbDeviceNumber + "=" + currentUsbDevice.getProductId());
                    strDataList.add("USB_DEVICE_PRODUCT_NAME_" + usbDeviceNumber + "=" + currentUsbDevice.getProductName());
                    strDataList.add("USB_DEVICE_SERIAL_NUMBER_" + usbDeviceNumber + "=" + currentUsbDevice.getSerialNumber());
                    strDataList.add("USB_DEVICE_VENDOR_ID_" + usbDeviceNumber + "=" + currentUsbDevice.getVendorId());
                    strDataList.add("USB_DEVICE_VERSION_" + usbDeviceNumber + "=" + currentUsbDevice.getVersion());
                    strDataList.add("USB_DEVICE_CLASS_" + usbDeviceNumber + "=" + currentUsbDevice.getDeviceClass());
                    strDataList.add("USB_DEVICE_PROTOCOL_" + usbDeviceNumber + "=" + currentUsbDevice.getDeviceProtocol());
                    strDataList.add("USB_DEVICE_SUBCLASS_" + usbDeviceNumber + "=" + currentUsbDevice.getDeviceSubclass());
                    strDataList.add("USB_DEVICE_INTERFACE_COUNT_" + usbDeviceNumber + "=" + currentUsbDevice.getInterfaceCount());

                    if(currentUsbDevice.getInterfaceCount() >0)
                    {
                        for (int interfaceIndex = 0; interfaceIndex < currentUsbDevice.getInterfaceCount(); interfaceIndex++)
                        {
                            UsbInterface usbInterface = currentUsbDevice.getInterface(interfaceIndex);

                            strDataList.add("ALTERNATE_SETTING_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getAlternateSetting());
                            strDataList.add("ENDPOINT_COUNT_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getEndpointCount());
                            strDataList.add("ID_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getId());
                            strDataList.add("INTERFACE_CLASS_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getInterfaceClass());
                            strDataList.add("INTERFACE_PROTOCOL_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getInterfaceProtocol());
                            strDataList.add("INTERFACE_SUBCLASS_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getInterfaceSubclass());
                            strDataList.add("NAME_INTERFACE_" + interfaceIndex +"_USB_DEVICE_" + usbDeviceNumber + "=" + usbInterface.getName());
                        }
                    }
                    usbDeviceNumber++;
                }
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
        strHelpList.add("This function is to check device usb host mode");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_USBHOSTMODE");
        strHelpList.add("  USBHOSTMODE=YES or USBHOSTMODE=NO");
        strHelpList.add("  ");
        strHelpList.add("  GET_USB_DEVICE_INFO - Gets information on any USB Host Mode devices");
        strHelpList.add("    NUMBER_USB_ACCESSORIES - Number of USB Host Mode accessories");
        strHelpList.add("    USB_DEVICE_NAME_XX - Device Name of accessory XX");
        strHelpList.add("    USB_DEVICE_MANUFACTURER_NAME_XX - Manufacturer Name of accessory XX");
        strHelpList.add("    USB_DEVICE_ID_XX - ID of accessory XX");
        strHelpList.add("    USB_DEVICE_PRODUCT_ID_XX - Product ID Name of accessory XX");
        strHelpList.add("    USB_DEVICE_PRODUCT_NAME_XX - Product Name of accessory XX");
        strHelpList.add("    USB_DEVICE_SERIAL_NUMBER_XX - Serial Number of accessory XX");
        strHelpList.add("    USB_DEVICE_VENDOR_ID_XX - Vendor ID of accessory XX");
        strHelpList.add("    USB_DEVICE_VERSION_XX - Version of accessory XX");
        strHelpList.add("    USB_DEVICE_CLASS_XX - Class of accessory XX");
        strHelpList.add("    USB_DEVICE_PROTOCOL_XX - Protocol of accessory XX");
        strHelpList.add("    USB_DEVICE_SUBCLASS_XX - Subclass of accessory XX");
        strHelpList.add("    USB_DEVICE_INTERFACE_COUNT_XX -Number of interfaces for accessory XX");
        strHelpList.add("    ALTERNATE_SETTING_INTERFACE_YY_USB_DEVICE_XX - Alternate Setting of interface YY for accessory XX");
        strHelpList.add("    ENDPOINT_COUNT_INTERFACE_YY_USB_DEVICE_XX - Endpoint Count of interface YY for accessory XX");
        strHelpList.add("    ID_INTERFACE_YY_USB_DEVICE_XX - ID of interface YY for accessory XX");
        strHelpList.add("    INTERFACE_CLASS_INTERFACE_YY_USB_DEVICE_XX - Interface Class of interface YY for accessory XX");
        strHelpList.add("    INTERFACE_PROTOCOL_INTERFACE_YY_USB_DEVICE_XX - Interface Protocol of interface YY for accessory XX");
        strHelpList.add("    INTERFACE_SUBCLASS_INTERFACE_YY_USB_DEVICE_XX - Interface Subclass of interface YY for accessory XX");
        strHelpList.add("    NAME_INTERFACE_YY_USB_DEVICE_XX - Name of interface YY for accessory XX");


        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "USB Host Mode: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "STATE: " + hostmode + "\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "USB Host Mode: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "STATE: " + hostmode + "\r\n", MODE_APPEND);

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
        testResultName.add("USBHOSTMODE");

        if (isHostMode())
        {
            testResultValues.add(String.valueOf("YES"));
        }
        else
        {
            testResultValues.add(String.valueOf("NO"));
        }

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }

    private class USBHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            if(null == msg) return;
            switch (msg.what){
                case MESSAGE_REFRESH:
                    refresh();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

}
