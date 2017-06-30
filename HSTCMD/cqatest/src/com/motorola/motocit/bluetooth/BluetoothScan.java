/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.bluetooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class BluetoothScan extends Test_Base
{
    private ListView mlistView;
    private BluetoothAdapter mbtAdapter;
    private Boolean mbtInitState = false;
    private int mLoop = 1;
    private ArrayList<HashMap<String, String>> mbtlist = new ArrayList<HashMap<String, String>>();

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Bluetooth_Scan";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.btscan);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        Init();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    private void Init()
    {
        mbtAdapter = BluetoothAdapter.getDefaultAdapter();
        mlistView = (ListView) findViewById(com.motorola.motocit.R.id.mlistBtScan);

        if (!mbtAdapter.isEnabled())
        {
            dbgLog(TAG, "bt is off. Will firstly enable it", 'd');

            setTextColor("bt is off. Will firstly enable it", "#ff00ff00");
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastEnableReceiver, filter);
            requestBTon(true);
        }
        else
        {
            mbtInitState = true;

            registerScanReceiver();
            Boolean ret = mbtAdapter.startDiscovery();
            if (ret != true)
            {
                setTextColor("Failed starting scan", "#ffff0000");
            }
        }

    }

    /**
     * Create a BroadcastReceiver for
     * ACTION_FOUND/ACTION_DISCOVERY_STARTED/ACTION_DISCOVERY_FINISHED
     */
    private final BroadcastReceiver mBroadcastScanReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action))
            {
                // Get the BluetoothDevice object from the Intent
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                dbgLog(TAG, "rssi =" + rssi, 'd');

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                /**
                 * Add the name and address to an array adapter to show in a
                 * ListView
                 */
                if (device == null)
                {
                    setTextColor("Error: discoverd device is null", "#ffff0000");
                }
                else
                {
                    dbgLog(TAG, "find device:" + device.getName() + device.getAddress(), 'd');

                    HashMap<String, String> map = new HashMap<String, String>();
                    map.put("BtName", (mLoop++) + " " + device.getName());
                    map.put("BtInfo", (rssi) + "dBm " + " (" + device.getAddress() + ")");
                    mbtlist.add(map);

                    SimpleAdapter mSchedule = new SimpleAdapter(BluetoothScan.this, mbtlist, com.motorola.motocit.R.layout.btscanlist_item,
                            new String[]
                                    { "BtName", "BtInfo" }, new int[]
                                            { com.motorola.motocit.R.id.BtName, com.motorola.motocit.R.id.BtInfo });
                    mlistView.setAdapter(mSchedule);
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
            {
                dbgLog(TAG, "Scan starting ...", 'd');

                setTextColor("Scanning ...", "#ff00ff00");
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                dbgLog(TAG, "Scan finished", 'd');

                setTextColor("Scan finished", "#ff00ff00");

            }
        }
    };

    // Create a BroadcastReceiver for ACTION_STATE_CHANGED
    private final BroadcastReceiver mBroadcastEnableReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            dbgLog(TAG, "in Eanble BT BroadcastReceiver", 'd');

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
            {
                if (mbtAdapter.getState() == 0x0c) /* STATE_ON */
                {
                    dbgLog(TAG, "BT is enabled.Will begin scan ...", 'd');

                    registerScanReceiver();
                    Boolean ret = mbtAdapter.startDiscovery();

                    if (ret == false)
                    {
                        dbgLog(TAG, "Failed starting scan", 'd');

                        setTextColor("Failed starting scan", "#ffff0000");
                    }
                    else
                    {
                        dbgLog(TAG, "Scanning...", 'd');

                        setTextColor("Scanning ...", "#ff00ff00");

                    }
                }
                else if (mbtAdapter.getState() == 0x0a) /* STATE_OFF */
                {
                    dbgLog(TAG, "BT is disabled", 'd');
                }
            }

        }
    };

    private void registerScanReceiver()
    {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mBroadcastScanReceiver, filter);
    }

    void requestBTon(boolean enableOnly)
    {
        Intent i = new Intent();
        i.setAction(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(i, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode != 1)
        {
            dbgLog(TAG, "Unexpected onActivityResult " + requestCode + " " + resultCode, 'e');
            return;
        }
        if (resultCode == Activity.RESULT_CANCELED)
        {
            setTextColor("Bluetooth MUST be enabled before scanning", "#ffff0000");
        }
        else if (resultCode == Activity.RESULT_OK)
        {
            setTextColor("Scanning ... ", "#ff00ff00");

        }
        else
        {
            setTextColor("Result = " + resultCode, "#ff00ff00");
        }
    }

    void restoreBtState()
    {
        mbtAdapter.cancelDiscovery();
        // restore mac initial state
        if ((mbtInitState == false) && (mbtAdapter.isEnabled()))
        {
            mbtAdapter.disable();
        }
    }

    private void setTextColor(String text, String rgb)
    {
        TextView macTextView = (TextView) findViewById(com.motorola.motocit.R.id.scan_status);
        macTextView.setTextColor(Color.parseColor(rgb));
        macTextView.setText(text);
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

            contentRecord("testresult.txt", "Bluetooth - Scan BT:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            restoreBtState();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Bluetooth - Scan BT:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            restoreBtState();

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
                restoreBtState();

                systemExitWrapper(0);
            }
        }

        return true;
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
        strHelpList.add("This function will read perform a Bluetooth scan");
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
        contentRecord("testresult.txt", "Bluetooth - Scan BT:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        restoreBtState();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Bluetooth - Scan BT:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        restoreBtState();

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
            restoreBtState();

            systemExitWrapper(0);
        }
        return true;
    }
}
