/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.wlan;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class WiFiMac extends Test_Base
{

    private WifiManager mWifiManager = null;
    private WifiInfo wifiConnectionInfo = null;
    private TextView macTextView;
    private boolean isWiFiOffDefault = false;
    private String wifiMac_to_result;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {

            final String action = intent.getAction();

            if ((action != null) && (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)))
            {
                if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED)
                {
                    WiFiMac.this.receivedWifiOn(intent);
                }
            }
        }
    };

    @Override
    protected void onPause()
    {
        super.onPause();
        dbgLog(TAG, "onPause", 'd');
        this.unregisterReceiver(this.mBroadcastReceiver);
    }

    protected void receivedWifiOn(Intent intent)
    {
        dbgLog(TAG, "receivedWifiOn", 'd');
        macTextView.setText("Getting wifi mac...");

        if ((wifiConnectionInfo = mWifiManager.getConnectionInfo()) != null)
        {
            dbgLog(TAG, "receivedWifiOn - get mac", 'd');
            macTextView.setText("WiFi MAC: " + wifiConnectionInfo.getMacAddress());
            wifiMac_to_result = wifiConnectionInfo.getMacAddress();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "WLAN_MacAddress";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.wifimac);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        macTextView = (TextView) findViewById(com.motorola.motocit.R.id.txtMac);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (mWifiManager.isWifiEnabled())
        {
            if ((wifiConnectionInfo = mWifiManager.getConnectionInfo()) != null)
            {
                macTextView.setText("WiFi MAC: " + wifiConnectionInfo.getMacAddress());
                wifiMac_to_result = wifiConnectionInfo.getMacAddress();
            }
            else
            {
                macTextView.setText("WiFi MAC: Failed to get Mac.");
                wifiMac_to_result = "Failed to get wifi mac";
            }
        }
        else
        {
            isWiFiOffDefault = true;
            macTextView.setText("Turning on wifi...");
        }
    }

    private void restoreWiFiState()
    {
        if (isWiFiOffDefault)
        {
            if (mWifiManager.isWifiEnabled())
            {
                mWifiManager.setWifiEnabled(false);
                try
                {
                    Thread.sleep(1500);
                }
                catch (InterruptedException e)
                {
                }
            }
        }
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
                contentRecord("testresult.txt", "WLAN - WiFiMac: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "WLAN - WiFiMac: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", wifiMac_to_result + "\r\n\r\n", MODE_APPEND);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            restoreWiFiState();

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
                restoreWiFiState();
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        IntentFilter filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, filter);

        mWifiManager.setWifiEnabled(true);

        sendStartActivityPassed();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS"))
        {

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
        strHelpList.add("This function will return the WiFi Mac Address");
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
        contentRecord("testresult.txt", "WLAN - WiFiMac: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", wifiMac_to_result + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_FAIL);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        restoreWiFiState();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "WLAN - WiFiMac: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", wifiMac_to_result + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_PASS);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        restoreWiFiState();

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
            restoreWiFiState();
            systemExitWrapper(0);
        }
        return true;
    }

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();
        testResultName.add("MAC_ADDRESS");

        testResultValues.add(String.valueOf(wifiMac_to_result));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
