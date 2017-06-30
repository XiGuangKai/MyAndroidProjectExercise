/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.graphics.Color;
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

public class BluetoothMac extends Test_Base
{

    private String mBluetoothMacAddress;
    private BluetoothAdapter mbtAdapter;
    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Bluetooth_MacAddress";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.btmac);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        mbtAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mbtAdapter == null)
        {
            setTextColor("Bluetooth is not available in device " , "#ffff0000"); //red
            this.finish();
        }
        else
        {
            mBluetoothMacAddress = mbtAdapter.getAddress();
            setTextColor("Bluetooth MAC: " + mBluetoothMacAddress, "#ff00ff00"); // Green
            dbgLog(TAG, "mBluetoothMacAddress:" + mBluetoothMacAddress, 'd');

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
                contentRecord("testresult.txt", "Bluetooth - BT Mac: PASS" + "\r\n", MODE_APPEND);
                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "Bluetooth - BT Mac:  FAILED" + "\r\n", MODE_APPEND);
                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", mBluetoothMacAddress + "\r\n\r\n", MODE_APPEND);

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

    private void setTextColor(String text, String rgb)
    {
        TextView macTextView = (TextView) findViewById(com.motorola.motocit.R.id.bt_mac);
        macTextView.setTextColor(Color.parseColor(rgb));
        macTextView.setText(text);
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_MAC_ADDRESS"))
        {
            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("BLUETOOTH_MAC_ADDRESS=%s", mBluetoothMacAddress));
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
        strHelpList.add("This function will read the Bluetooth MAC Address");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_MAC_ADDRESS - Returns Bluetooth MAC Address");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Bluetooth - BT Mac:  FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", mBluetoothMacAddress + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Bluetooth - BT Mac: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", mBluetoothMacAddress + "\r\n\r\n", MODE_APPEND);

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
        testResultName.add("BLUETOOTH_MAC_ADDRESS");

        testResultValues.add(String.valueOf(mBluetoothMacAddress));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
