/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.hdmi;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.MotoSettingsNotFoundException;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class HdmiInfo extends Test_Base
{
    // define parsing positions for EDID
    // 1st 128 bytes(256 chars) defined at
    // http://faydoc.tripod.com/structures/01/0127.htm
    // 2nd 128 bytes are extended data
    // Current phones are returning 512 bytes but last 256 bytes are all zeroes
    private static final int EDID_MINIMUM_LENGTH = 512;
    private static final int EDID_HEADER_START_POS = 0;
    private static final int EDID_MANUFACTURER_START_POS = 16;
    private static final int EDID_BASIC_DISPLAY_PARAMETERS_START_POS = 40;
    private static final int EDID_CHROMATICITY_COORDINATES_START_POS = 50;
    private static final int EDID_ESTABLISHED_TIMING_START_POS = 70;
    private static final int EDID_STANDARD_TIMING_START_POS = 76;
    private static final int DETAILED_TIMING_DESCRIPTION_1_START_POS = 108;
    private static final int DETAILED_TIMING_DESCRIPTION_2_START_POS = 144;
    private static final int DETAILED_TIMING_DESCRIPTION_3_START_POS = 180;
    private static final int DETAILED_TIMING_DESCRIPTION_4_START_POS = 216;
    private static final int CHECKSUM_START_POS = 252;
    private static final int EXTENDED_DATA_START_POS = 256;

    private static final String EXTDISP_PUBLIC_STATE = "com.motorola.intent.action.externaldisplaystate";
    private static final String HDMI_SETTING_INTENT = "com.motorola.intent.action.EXTDISP_CONTROL_SETTING";
    private static final String EXTRA_HDMI = "hdmi";
    private static final String EXTRA_HDCP = "hdcp";
    private static final String HDMI_AUDIO_SUPPORT = "audio";
    private static final String HDMI_WIDTH = "width";
    private static final String HDMI_HEIGHT = "height";

    private int cableStatus = -1;
    private int hdcpStatus = -1;
    private int audioSupport = -1;
    private int hdmiWidth = -1;
    private int hdmiHeight = -1;
    private int hdmiConnectionState = -999;
    private String edid = null;

    private HDMIAppStatusReceiver mHDMIAppStatusReceiver = null;

    private TextView CableStatusText = null;
    private TextView HdcpStatusText = null;
    private TextView AudioSupportText = null;
    private TextView HdmiWidthText = null;
    private TextView HdmiHeightText = null;
    private TextView EdidText = null;
    private TextView EdidHeaderText = null;
    private TextView EdidManufacturerText = null;
    private TextView EdidBasicDisplayParametersText = null;
    private TextView EdidChromaticityCoordinatesText = null;
    private TextView EdidEstablishedTimingText = null;
    private TextView EdidStandardTimingText = null;
    private TextView DetailedTimingDescription1Text = null;
    private TextView DetailedTimingDescription2Text = null;
    private TextView DetailedTimingDescription3Text = null;
    private TextView DetailedTimingDescription4Text = null;
    private TextView ChecksumText = null;
    private TextView ExtendedDataText = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "HdmiInfo";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.hdmiinfo);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // pointers to update display fields in layout
        CableStatusText = (TextView) findViewById(com.motorola.motocit.R.id.CableStatus);
        HdcpStatusText = (TextView) findViewById(com.motorola.motocit.R.id.HdcpStatus);
        AudioSupportText = (TextView) findViewById(com.motorola.motocit.R.id.AudioSupport);
        HdmiWidthText = (TextView) findViewById(com.motorola.motocit.R.id.HdmiWidth);
        HdmiHeightText = (TextView) findViewById(com.motorola.motocit.R.id.HdmiHeight);
        EdidText = (TextView) findViewById(com.motorola.motocit.R.id.EdidLeader);
        EdidHeaderText = (TextView) findViewById(com.motorola.motocit.R.id.EdidHeader);
        EdidManufacturerText = (TextView) findViewById(com.motorola.motocit.R.id.EdidManufacturer);
        EdidBasicDisplayParametersText = (TextView) findViewById(com.motorola.motocit.R.id.EdidBasicDisplayParameters);
        EdidChromaticityCoordinatesText = (TextView) findViewById(com.motorola.motocit.R.id.EdidChromaticityCoordinates);
        EdidEstablishedTimingText = (TextView) findViewById(com.motorola.motocit.R.id.EdidEstablishedTiming);
        EdidStandardTimingText = (TextView) findViewById(com.motorola.motocit.R.id.EdidStandardTiming);
        DetailedTimingDescription1Text = (TextView) findViewById(com.motorola.motocit.R.id.DetailedTimingDescription1);
        DetailedTimingDescription2Text = (TextView) findViewById(com.motorola.motocit.R.id.DetailedTimingDescription2);
        DetailedTimingDescription3Text = (TextView) findViewById(com.motorola.motocit.R.id.DetailedTimingDescription3);
        DetailedTimingDescription4Text = (TextView) findViewById(com.motorola.motocit.R.id.DetailedTimingDescription4);
        ChecksumText = (TextView) findViewById(com.motorola.motocit.R.id.Checksum);
        ExtendedDataText = (TextView) findViewById(com.motorola.motocit.R.id.ExtendedData);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        mHDMIAppStatusReceiver = new HDMIAppStatusReceiver();
        IntentFilter if1 = new IntentFilter(EXTDISP_PUBLIC_STATE);
        this.registerReceiver(mHDMIAppStatusReceiver, if1);

        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (mHDMIAppStatusReceiver != null)
        {
            this.unregisterReceiver(mHDMIAppStatusReceiver);
        }

        // Clear out variables since unregistered and will not detect changes
        // while paused
        cableStatus = -1;
        hdcpStatus = -1;
        audioSupport = -1;
        hdmiWidth = -1;
        hdmiHeight = -1;
        edid = null;
    }

    private void sendInfoData(String data)
    {
        List<String> strDataList = new ArrayList<String>();
        strDataList.add(data);

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
        sendInfoPacketToCommServer(infoPacket);
    }

    private int getHdmiExtra(Intent intent, String extra)
    {
        int value = -1;

        if (intent != null)
        {
            String action = intent.getAction();
            if (null != action)
            {
                if (action.equals(EXTDISP_PUBLIC_STATE))
                {
                    Bundle extras = intent.getExtras();
                    value = extras.getInt(extra);
                }
            }
        }
        return value;
    }

    private int getHdmiMenuSetting(List<String> strErrMsgList)
    {
        int status = 0;

        try
        {
            hdmiConnectionState = TestUtils.getMotoSettingInt(getContentResolver(), "hdmi_autodetection", -1);
        }
        catch (MotoSettingsNotFoundException e)
        {
            // Generate an exception to send FAIL result
            // and message back to CommServer
            strErrMsgList.add(e.toString());
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            status = -1;
        }

        return status;
    }

    private int setHdmiMenuSetting(int value, List<String> strErrMsgList)
    {
        int status = 0;
        if ((0 == value) || (1 == value))
        {
            // Set autodetection via broadcast
            // Change setting in frame work
            // This does not change checkbox in menu->settings->display
            Intent intent = new Intent(HDMI_SETTING_INTENT);
            intent.putExtra("setting", "autodetect");
            intent.putExtra("autodetect", value);
            this.sendBroadcast(intent);

            try
            {
                // Change checkbox in menu->settings->display
                // This does not change setting in frame work
                if (TestUtils.putMotoSettingInt(getContentResolver(), "hdmi_autodetection", value) == false)
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to CommServer
                    strErrMsgList.add("Could not set hdmi_autodetection");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    status = -1;
                }
            }
            catch (MotoSettingsNotFoundException e)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(e.toString());
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                status = -1;
            }
        }
        else
        {
            strErrMsgList.add("Invalid Set Hdmi Menu Setting Value");
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            status = -1;
        }

        return status;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        Intent hdmiIntent = this.registerReceiver(null, new IntentFilter(EXTDISP_PUBLIC_STATE));
        List<String> strReturnDataList = new ArrayList<String>();

        // Get the status of the cable insertion
        if (strRxCmd.equalsIgnoreCase("GET_CABLE_STATUS"))
        {
            int cableTellStatus = getHdmiExtra(hdmiIntent, EXTRA_HDMI);
            sendInfoData("CABLE_STATUS=" + cableTellStatus);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        // Get the status of HDCP
        else if (strRxCmd.equalsIgnoreCase("GET_HDCP_STATUS"))
        {
            int hdcpTellStatus = getHdmiExtra(hdmiIntent, EXTRA_HDCP);
            sendInfoData("HDCP_STATUS=" + hdcpTellStatus);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        // Get the status of the audio support
        else if (strRxCmd.equalsIgnoreCase("GET_AUDIO_SUPPORT"))
        {
            int audioTellSupport = getHdmiExtra(hdmiIntent, HDMI_AUDIO_SUPPORT);
            sendInfoData("AUDIO_SUPPORT=" + audioTellSupport);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        // Get the output width and height
        else if (strRxCmd.equalsIgnoreCase("GET_HDMI_OUTPUT_DIMENSIONS"))
        {
            List<String> strDataList = new ArrayList<String>();

            int hdmiTellWidth = getHdmiExtra(hdmiIntent, HDMI_WIDTH);
            int hdmiTellHeight = getHdmiExtra(hdmiIntent, HDMI_HEIGHT);
            strDataList.add("HDMI_OUTPUT_WIDTH=" + hdmiTellWidth);
            strDataList.add("HDMI_OUTPUT_HEIGHT=" + hdmiTellHeight);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        // Get the EDID
        else if (strRxCmd.equalsIgnoreCase("GET_EDID"))
        {
            // Short term method of getting EDID until it is added into
            // intent. Planned for HSS8.
            String edidTell = null;

            // Only check EDID file if a cable is connected
            if (getHdmiExtra(hdmiIntent, EXTRA_HDMI) == 1)
            {
                String path = "/sys/class/graphics/fb1/edid_data";
                edidTell = genericReadFile(path);
            }

            if (edidTell == null)
            {
                edidTell = "Not Found";
            }

            sendInfoData("EDID=" + edidTell);
            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_HDMI_MENU_SETTING"))
        {
            List<String> strErrMsgList = new ArrayList<String>();

            int status = getHdmiMenuSetting(strErrMsgList);

            if (0 != status)
            {
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                List<String> strDataList = new ArrayList<String>();
                strDataList.add("HDMI_MENU_SETTING=" + hdmiConnectionState);
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_HDMI_MENU_SETTING"))
        {
            List<String> strErrMsgList = new ArrayList<String>();
            if (strRxCmdDataList.size() != 1)
            {
                strErrMsgList.add("Invalid number of arguments. Expecting 1.");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            int newValue = Integer.parseInt(strRxCmdDataList.get(0));

            int status = setHdmiMenuSetting(newValue, strErrMsgList);
            if (0 != status)
            {
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            hdmiConnectionState = newValue;
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
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
        strHelpList.add("This function will read the HDMI info");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_CABLE_STATUS - Returns the HDMI cable status");
        strHelpList.add("  ");
        strHelpList.add("GET_HDCP_STATUS - Returns the status of HDCP");
        strHelpList.add("  ");
        strHelpList.add("GET_AUDIO_SUPPORT - Returns the audio support field");
        strHelpList.add("  ");
        strHelpList.add("GET_HDMI_OUTPUT_DIMENSIONS - Returns the output resolution width and height");
        strHelpList.add("  ");
        strHelpList.add("GET_EDID - Returns the EDID");
        strHelpList.add("  ");
        strHelpList.add("GET_HDMI_MENU_SETTING - Gets the HDMI Connection setting in the display menu");
        strHelpList.add("  ");
        strHelpList.add("SET_HDMI_MENU_SETTING <VALUE> - Sets the HDMI Connection setting in the display menu.");
        strHelpList.add("  <VALUE> - 0 (disable) or 1 (enable).");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "HDMI Info Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "Cable Status: " + cableStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDCP Status: " + hdcpStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Audio Support: " + audioSupport + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDMI Ouput Width: " + hdmiWidth + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDMI Ouput Height: " + hdmiHeight + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "EDID: " + edid + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "HDMI Info Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "Cable Status: " + cableStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDCP Status: " + hdcpStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Audio Support: " + audioSupport + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDMI Ouput Width: " + hdmiWidth + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HDMI Ouput Height: " + hdmiHeight + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "EDID: " + edid + "\r\n\r\n", MODE_APPEND);

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
                contentRecord("testresult.txt", "HDMI Info Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "HDMI Info Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "Cable Status: " + cableStatus + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HDCP Status: " + hdcpStatus + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Audio Support: " + audioSupport + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HDMI Ouput Width: " + hdmiWidth + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HDMI Ouput Height: " + hdmiHeight + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "EDID: " + edid + "\r\n\r\n", MODE_APPEND);

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

    class HDMIAppStatusReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent != null)
            {
                // make klocwork happy
                String action = intent.getAction();
                if (null == action)
                {
                    return;
                }

                if (action.equals(EXTDISP_PUBLIC_STATE))
                {
                    Bundle extras = intent.getExtras();
                    // Blank out display fields so no old values are left
                    CableStatusText.setText("HDMI CABLE:");
                    HdcpStatusText.setText("HDCP:");
                    AudioSupportText.setText("HDMI Audio Support:");
                    HdmiWidthText.setText("Hdmi Output Width:");
                    HdmiHeightText.setText("Hdmi Output Height:");
                    EdidText.setText("EDID:");
                    EdidHeaderText.setText("");
                    EdidManufacturerText.setText("");
                    EdidBasicDisplayParametersText.setText("");
                    EdidChromaticityCoordinatesText.setText("");
                    EdidEstablishedTimingText.setText("");
                    EdidStandardTimingText.setText("");
                    DetailedTimingDescription1Text.setText("");
                    DetailedTimingDescription2Text.setText("");
                    DetailedTimingDescription3Text.setText("");
                    DetailedTimingDescription4Text.setText("");
                    ChecksumText.setText("");
                    ExtendedDataText.setText("");

                    dbgLog(TAG, action + " Received", 'i');

                    // Get the info from the intent
                    if (extras != null)
                    {
                        // Get data from intent
                        cableStatus = extras.getInt(EXTRA_HDMI);
                        hdcpStatus = extras.getInt(EXTRA_HDCP);
                        audioSupport = extras.getInt(HDMI_AUDIO_SUPPORT);
                        hdmiWidth = extras.getInt(HDMI_WIDTH);
                        hdmiHeight = extras.getInt(HDMI_HEIGHT);

                        dbgLog(TAG, "Extras: cableStatus=" + cableStatus + " hdcpStatus=" + hdcpStatus + " audioSupport=" + audioSupport + " hdmiWidth="
                                + hdmiWidth + " hdmiHeight=" + hdmiHeight, 'i');

                        if (cableStatus == 1)
                        {
                            CableStatusText.setText("HDMI Cable: Connected");
                            CableStatusText.setTextColor(Color.GREEN);
                        }
                        else
                        {
                            CableStatusText.setText("HDMI Cable: Disconnected");
                            CableStatusText.setTextColor(Color.RED);
                        }

                        if (hdcpStatus == 1)
                        {
                            HdcpStatusText.setText("HDCP: Enabled");
                            HdcpStatusText.setTextColor(Color.GREEN);
                        }
                        else
                        {
                            HdcpStatusText.setText("HDCP: Disabled");
                            HdcpStatusText.setTextColor(Color.RED);
                        }

                        AudioSupportText.setText("HDMI Audio Support:" + String.valueOf(audioSupport));
                        HdmiWidthText.setText("Hdmi Output Width:" + String.valueOf(hdmiWidth));
                        HdmiHeightText.setText("Hdmi Output Height:" + String.valueOf(hdmiHeight));
                    }

                    // Short term method of getting EDID until it is added into
                    // intent. Planned for HSS8.
                    edid = null;

                    // Only check EDID file if a cable is connected
                    if (cableStatus == 1)
                    {
                        String path = "/sys/class/graphics/fb1/edid_data";
                        edid = genericReadFile(path);
                    }

                    if (edid == null)
                    {
                        edid = "Not Found";
                        EdidText.setText("EDID:" + edid);
                    }
                    else
                    {
                        EdidText.setText("EDID:");


                        // Attempt to parse data from EDID to display to screen
                        // To do so, make sure there is enough data
                        int strlen = edid.length();
                        String value = null;
                        if (strlen >= EDID_MINIMUM_LENGTH)
                        {
                            // parse out edid data and update display field
                            // http://faydoc.tripod.com/structures/01/0127.htm
                            value = null;
                            value = edid.substring(EDID_HEADER_START_POS, EDID_MANUFACTURER_START_POS);
                            if (value != null)
                            {
                                EdidHeaderText.setText("Edid Header: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EDID_MANUFACTURER_START_POS, EDID_BASIC_DISPLAY_PARAMETERS_START_POS);
                            if (value != null)
                            {
                                EdidManufacturerText.setText("Edid Manufacturer: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EDID_BASIC_DISPLAY_PARAMETERS_START_POS, EDID_CHROMATICITY_COORDINATES_START_POS);
                            if (value != null)
                            {
                                EdidBasicDisplayParametersText.setText("Edid Basic Display Parameters: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EDID_CHROMATICITY_COORDINATES_START_POS, EDID_ESTABLISHED_TIMING_START_POS);
                            if (value != null)
                            {
                                EdidChromaticityCoordinatesText.setText("EdidChromaticityCoordinates: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EDID_ESTABLISHED_TIMING_START_POS, EDID_STANDARD_TIMING_START_POS);
                            if (value != null)
                            {
                                EdidEstablishedTimingText.setText("Edid Established Timing: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EDID_STANDARD_TIMING_START_POS, DETAILED_TIMING_DESCRIPTION_1_START_POS);
                            if (value != null)
                            {
                                EdidStandardTimingText.setText("Edid Standard Timing: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(DETAILED_TIMING_DESCRIPTION_1_START_POS, DETAILED_TIMING_DESCRIPTION_2_START_POS);
                            if (value != null)
                            {
                                DetailedTimingDescription1Text.setText("Detailed Timing Description 1: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(DETAILED_TIMING_DESCRIPTION_2_START_POS, DETAILED_TIMING_DESCRIPTION_3_START_POS);
                            if (value != null)
                            {
                                DetailedTimingDescription2Text.setText("Detailed Timing Description 2: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(DETAILED_TIMING_DESCRIPTION_3_START_POS, DETAILED_TIMING_DESCRIPTION_4_START_POS);
                            if (value != null)
                            {
                                DetailedTimingDescription3Text.setText("Detailed Timing Description 3: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(DETAILED_TIMING_DESCRIPTION_4_START_POS, CHECKSUM_START_POS);
                            if (value != null)
                            {
                                DetailedTimingDescription4Text.setText("Detailed Timing Description 4: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(CHECKSUM_START_POS, EXTENDED_DATA_START_POS);
                            if (value != null)
                            {
                                ChecksumText.setText("Checksum: " + String.valueOf(value));
                            }

                            value = null;
                            value = edid.substring(EXTENDED_DATA_START_POS, EDID_MINIMUM_LENGTH);
                            if (value != null)
                            {
                                ExtendedDataText.setText("Extended Data: " + String.valueOf(value));
                            }

                            // On test unit, it is returning another 512 char but
                            // they are all zeroes
                        }
                    }
                    dbgLog(TAG, "EDID: " + edid, 'i');
                }
            }
        }
    }

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();
        testResultName.add("CABLE_STATUS");
        testResultName.add("HDCP_STATUS");
        testResultName.add("AUDIO_SUPPORT");
        testResultName.add("HDMI_OUTPUT_WIDTH");
        testResultName.add("HDMI_OUTPUT_HEIGHT");
        testResultName.add("EDID");

        testResultValues.add(String.valueOf(cableStatus));
        testResultValues.add(String.valueOf(hdcpStatus));
        testResultValues.add(String.valueOf(audioSupport));
        testResultValues.add(String.valueOf(hdmiWidth));
        testResultValues.add(String.valueOf(hdmiHeight));
        testResultValues.add(String.valueOf(edid));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }

}
