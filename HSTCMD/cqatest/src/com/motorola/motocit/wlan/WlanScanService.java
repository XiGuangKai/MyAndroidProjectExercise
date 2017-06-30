/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *    Date          CR            Author                Description
 * 2012/06/18   IKHSS7-38742    Ken Moy - wkm039      Changes to make FindBugs happy
 * 2012/02/02   IKHSS7-9355     Ken Moy - wkm039      Increase Wlan scan speed.
 * 2012/02/02   IKHSS7-7734     Ken Moy - wkm039      Created new service for use with NexTest testing
 */

package com.motorola.motocit.wlan;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;

public class WlanScanService extends Service
{
    private static final String TAG = "WLAN_Scan_Service";

    private final IBinder mBinder = new LocalBinder();

    // Notification manager allows the service icon to show up in the
    // notification window.
    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.wlan_scan_service_started;

    private WifiManager mWifiManager = null;

    private static List<ScanResult> prevScanResults = new ArrayList<ScanResult>();
    private static final Lock lockPrevScanResults = new ReentrantLock();


    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        WlanScanService getService()
        {
            dbgLog(TAG, "Retrieving binding service", 'i');
            return WlanScanService.this;
        }
    }

    /**
     * @return
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        dbgLog(TAG, "Binding to WlanScanService", 'i');
        return mBinder;
    }


    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate()
    {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        dbgLog(TAG, "OnCreate of WlanScanService called", 'i');

        // Enable WLAN if not enabled
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!mWifiManager.isWifiEnabled())
        {
            if (mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLING)
            {
                mWifiManager.setWifiEnabled(true);
            }
        }

        // setup broadcast receivers
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, filter);



        // start scanning
	// startScanActive is not in 4.3, rework scan method
        startWlanScan();

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
        dbgLog(TAG, "OnDestroy() of WlanScanService called", 'i');

        this.unregisterReceiver(this.mBroadcastReceiver);
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if ((action != null) && (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)))
            {
                WlanScanService.this.receivedBroadcastScanResult(intent);

                // Kick off scan again to get new scan result asap
                startWlanScan();
            }
        }
    };


    protected void startWlanScan()
    {
        Method activeScan = null;

        try
        {
            activeScan = mWifiManager.getClass().getMethod("startScanActive");
        }
        catch (SecurityException e1)
        {
            dbgLog(TAG, "no permission for startScanActive", 'e');
            e1.printStackTrace();
        }
        catch (NoSuchMethodException e1)
        {
            dbgLog(TAG, "no permission for startScanActive", 'e');
            e1.printStackTrace();
        }

        if(activeScan != null)
        {
            try
            {
                dbgLog(TAG, "Invoke active scan method", 'i');
                activeScan.invoke(mWifiManager);
            }
            catch (IllegalArgumentException e)
            {
                dbgLog(TAG, "bad argument for startScanActive", 'e');
                e.printStackTrace();
            }
            catch (IllegalAccessException e)
            {
                dbgLog(TAG, "no access to startScanActive", 'e');
                e.printStackTrace();
            }
            catch (InvocationTargetException e)
            {
                dbgLog(TAG, "cannot invoke startScanActive", 'e');
                e.printStackTrace();
            }
        }
        else
        {
            dbgLog(TAG, "Invoke passive scan", 'i');
            mWifiManager.startScan();
        }
    }

    protected void receivedBroadcastScanResult(Intent intent)
    {
        dbgLog(TAG, "Scan result ready", 'd');

        try
        {
            lockPrevScanResults.lock();
            prevScanResults = mWifiManager.getScanResults();
        }
        finally
        {
            lockPrevScanResults.unlock();
        }
    }

    public static void getScanResults(List<ScanResult> scanResults)
    {
        if (scanResults != null)
        {
            scanResults.clear();

            try
            {
                lockPrevScanResults.lock();
                scanResults.addAll(prevScanResults);
            }
            finally
            {
                lockPrevScanResults.unlock();
            }
        }
    }


    /**
     * Show a notification while this service is running.
     */
    private void showNotification()
    {
        // Setup the text from the string resource
        CharSequence text = getText(R.string.wlan_scan_service_started);

        // Set the icon, scrolling text and time stamp
        Notification notification = new Notification(R.drawable.wlan_service_icon, text, System.currentTimeMillis());

        notification.flags |= Notification.FLAG_NO_CLEAR;

        // The PendingIntent to launch our activity if the user selects this notification
        // Right now, this launches the Test_Main activity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, com.motorola.motocit.wlan.WlanUtilityNexTest.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.wlan_scan_service_label), text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
