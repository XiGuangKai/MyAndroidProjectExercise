/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *    Date          CR            Author                Description
 * 2012/06/18   IKHSS7-38742    Ken Moy - wkm039      Changes to make FindBugs happy
 * 2012/02/02   IKHSS7-7734     Ken Moy - wkm039      Created new service for use with NexTest testing
 */

package com.motorola.motocit.bluetooth;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;

public class BluetoothScanService extends Service
{
    private static final String TAG = "Bluetooth_Scan_Service";

    private final IBinder mBinder = new LocalBinder();

    // Notification manager allows the service icon to show up in the
    // notification window.
    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.bluetooth_scan_service_started;

    private BluetoothAdapter mBtAdapter;
    private static long BT_ENABLE_TIMEOUT_MSECS = 10000;


    private static final Lock lockScanResultsForCommServer = new ReentrantLock();
    private static HashMap<String, HashMap<String, String>> scanResultsForCommServer = new HashMap<String, HashMap<String, String>>();

    public static final String KEY_DEVICE_NAME = "NAME";
    public static final String KEY_DEVICE_RSSI = "RSSI";


    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        BluetoothScanService getService()
        {
            dbgLog(TAG, "Retrieving binding service", 'i');
            return BluetoothScanService.this;
        }
    }

    /**
     * @return
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        dbgLog(TAG, "Binding to BluetoothScanService", 'i');
        return mBinder;
    }


    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate()
    {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        dbgLog(TAG, "OnCreate of BluetoothScanService called", 'i');

        // Enable bluetooth if not enabled
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter.isEnabled() == false)
        {
            mBtAdapter.enable();

            long startTime = System.currentTimeMillis();
            while (!mBtAdapter.isEnabled())
            {
                dbgLog(TAG, "Waiting for BT adapter to start", 'v');

                if ((System.currentTimeMillis() - startTime) > BT_ENABLE_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Failed to start BT adapter", 'e');
                    break;
                }
            }
        }


        // setup broadcast receivers
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mBroadcastReceiver, filter);

        // start scanning
        mBtAdapter.startDiscovery();

        // Display a notification about us starting. We put an icon in the status bar.
        showNotification();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dbgLog(TAG, "Received start id " + startId + ": " + intent, 'i');
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        dbgLog(TAG, "onDestroy of BluetoothScanService called", 'i');

        mBtAdapter.cancelDiscovery();

        this.unregisterReceiver(this.mBroadcastReceiver);
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
    }


    /**
     * Create a BroadcastReceiver for
     * ACTION_FOUND/ACTION_DISCOVERY_STARTED/ACTION_DISCOVERY_FINISHED
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
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

                // add results so CommServer can read them back
                addScanResultsForCommServer(intent);
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action))
            {
                dbgLog(TAG, "Scan starting ...", 'd');
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
            {
                dbgLog(TAG, "Scan finished.. start discovery again", 'd');
                mBtAdapter.startDiscovery();
            }
        }
    };

    // add scan results into data structure that will be read by
    // requests from CommServer
    private void addScanResultsForCommServer(Intent intent)
    {
        try
        {
            lockScanResultsForCommServer.lock();

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (device != null)
            {
                HashMap<String, String> deviceInfoMap = new HashMap<String, String>();

                String deviceName    = device.getName();
                String deviceAddress = device.getAddress();
                short deviceRssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                if (deviceName == null)
                {
                    deviceName = "UNKNOWN";
                }

                deviceAddress = standardizeBtAddress(deviceAddress);
                if (deviceAddress == null)
                {
                    deviceAddress = "UNKNOWN";
                }

                deviceInfoMap.put(KEY_DEVICE_NAME, deviceName);
                deviceInfoMap.put(KEY_DEVICE_RSSI, String.format("%d", deviceRssi));

                scanResultsForCommServer.put(deviceAddress, deviceInfoMap);
            }

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            lockScanResultsForCommServer.unlock();
        }

    }

    public static String standardizeBtAddress(String btAddress)
    {
        String standardizedAddress = null;

        // must contain ":"
        // make sure all chars are uppercase
        // make sure all fields are at least 2 digits/chars
        if (btAddress.contains(":"))
        {
            String[] splitResult  = btAddress.split(":");

            for (int i = 0; i < splitResult.length; i++)
            {
                if (splitResult[i].length() < 2)
                {
                    splitResult[i] = String.format("%2S", splitResult[i]);
                    splitResult[i] = splitResult[i].replaceAll(" ", "0");
                }

                splitResult[i] = splitResult[i].toUpperCase();

            }

            standardizedAddress = TextUtils.join(":", splitResult);
        }

        return standardizedAddress;
    }


    public static void getScanResults(HashMap<String, HashMap<String, String>>  scanResults)
    {
        if (scanResults != null)
        {
            scanResults.clear();

            try
            {
                lockScanResultsForCommServer.lock();
                scanResults.putAll(scanResultsForCommServer);
            }
            finally
            {
                lockScanResultsForCommServer.unlock();
            }
        }
    }


    // clear all scan results from data structure that will be read by
    // requests from CommServer
    public static void clearScanResults()
    {
        try
        {
            lockScanResultsForCommServer.lock();
            scanResultsForCommServer.clear();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            lockScanResultsForCommServer.unlock();
        }
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification()
    {
        // Setup the text from the string resource
        CharSequence text = getText(R.string.bluetooth_scan_service_started);

        // Set the icon, scrolling text and time stamp
        Notification notification = new Notification(R.drawable.bt_service_icon, text, System.currentTimeMillis());

        notification.flags |= Notification.FLAG_NO_CLEAR;

        // The PendingIntent to launch our activity if the user selects this notification
        // Right now, this launches the Test_Main activity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, com.motorola.motocit.wlan.WlanUtilityNexTest.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.bluetooth_scan_service_label), text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
