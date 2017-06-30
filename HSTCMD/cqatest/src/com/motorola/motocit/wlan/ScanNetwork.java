/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.wlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

public class ScanNetwork extends Test_Base
{

    String service = Context.WIFI_SERVICE;
    WifiManager mWifiManager = null;
    private ListView mlistView;
    private boolean isWiFiOffDefault = false;
    private boolean mIsGpsStateDefault = false;

    ListView myListView = null;
    TextView wifiNetworkTextView = null;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {

            final String action = intent.getAction();
            // Begin IKSWN-2525 startScan after receive WIFI_STATE_CHANGED_ACTION, make sure wifi is enabled when startScan.
            dbgLog(TAG, "action = " + action, 'd');
            if ((action != null) && (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)))
            {
                if (mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                    dbgLog(TAG, "startScan", 'd');
                    mWifiManager.startScan(); // Start Scan here, or it will take long time
                }
            }
            // End IKSWN-2525

            if ((action != null) && (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)))
            {
                ScanNetwork.this.receivedBroadcastScanResult(intent);
            }
        }
    };

    @Override
    protected void onPause()
    {
        super.onPause();

        if(isFinishing())
        {
            dbgLog(TAG, "Activity finishing.", 'v');

            if ((Build.VERSION.SDK_INT >= 23) && !mIsGpsStateDefault)
            {
                // If Android M or later and GPS was off when started then turn GPS back off
                dbgLog(TAG, "Setting GPS back to be disabled", 'v');

                LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

                Context context = getApplicationContext();

                final ContentResolver resolver = context.getContentResolver();
                new AsyncTask<Void, Void, Boolean>()
                {
                    @Override
                    protected Boolean doInBackground(Void... args)
                    {
                        Settings.Secure.setLocationProviderEnabled(resolver, LocationManager.GPS_PROVIDER, false);
                        return true;
                    }

                }.execute();

                dbgLog(TAG, "Wait for GPS to be disabled", 'v');

                long startTime = System.currentTimeMillis();
                while (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
                {
                    dbgLog(TAG, "Waiting for GPS to stop", 'v');

                    if ((System.currentTimeMillis() - startTime) > 10000)
                    {
                        dbgLog(TAG, "Failed to stop GPS", 'e');
                    }

                    try
                    {
                        Thread.sleep(50);
                    }
                    catch (Exception e)
                    {

                    }
                }

                dbgLog(TAG, "GPS should be disabled", 'v');
            }
        }

        this.unregisterReceiver(this.mBroadcastReceiver);
    }

    protected void receivedBroadcastScanResult(Intent intent)
    {
        dbgLog(TAG, "Scan result ready", 'd');

        wifiNetworkTextView.setText("");

        ArrayList<HashMap<String, String>> ssidlist = new ArrayList<HashMap<String, String>>();

        List<ScanResult> availableAcessPoint = mWifiManager.getScanResults();

        if (availableAcessPoint != null)
        {
            int i = 1;
            dbgLog(TAG, "number of available access points: " + availableAcessPoint.size(), 'd');
            for (ScanResult result : availableAcessPoint)
            {
                HashMap<String, String> map = new HashMap<String, String>();
                map.put("ItemSsid", (i++) + " " + result.SSID);
                map.put("ItemInfo", result.level + "dBm " + (result.frequency * 0.001) + "GHz" + "  (" + result.BSSID + ")");
                ssidlist.add(map);
            }
            SimpleAdapter mlistAdapter = new SimpleAdapter(ScanNetwork.this, ssidlist, com.motorola.motocit.R.layout.ssidlist_item, new String[]
                    { "ItemSsid", "ItemInfo" }, new int[]
                            { com.motorola.motocit.R.id.ItemSsid, com.motorola.motocit.R.id.ItemInfo });

            mlistView.setAdapter(mlistAdapter);
            if (mGestureListener != null)
            {
                mlistView.setOnTouchListener(mGestureListener);
            }
        }
        else
        {
            wifiNetworkTextView.setText("No Network Found");
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        // Begin IKSWN-2525 Receive WIFI_STATE_CHANGED_ACTION, not NETWORK_STATE_CHANGED_ACTION,
        //and remove startScan() to onReceive(), ensure startScan() after wifi is enabled
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, filter);
        // End IKSWN-2525

        sendStartActivityPassed();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "WLAN_ScanNetwork";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.ssid);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        init();
    }

    private void init()
    {
        mWifiManager = (WifiManager) getSystemService(service);
        if (!mWifiManager.isWifiEnabled())
        {
            if (mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLING)
            {
                isWiFiOffDefault = true;
                mWifiManager.setWifiEnabled(true);
            }
        }

        if (Build.VERSION.SDK_INT >= 23)
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1001);
            }

            LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

            mIsGpsStateDefault = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);

            if(!mIsGpsStateDefault)
            {
                Context context = getApplicationContext();

                final ContentResolver resolver = context.getContentResolver();
                new AsyncTask<Void, Void, Boolean>()
                {
                    @Override
                    protected Boolean doInBackground(Void...args)
                    {
                        Settings.Secure.setLocationProviderEnabled(resolver, LocationManager.GPS_PROVIDER, true);
                        return true;
                    }

                }.execute();

                dbgLog(TAG, "Wait for GPS to be enabled", 'v');

                long startTime = System.currentTimeMillis();
                while (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER))
                {
                    dbgLog(TAG, "Waiting for GPS to start", 'v');

                    if ((System.currentTimeMillis() - startTime) > 10000)
                    {
                        dbgLog(TAG, "Failed to start GPS", 'e');
                    }

                    try
                    {
                        Thread.sleep(50);
                    }
                    catch (Exception e)
                    {

                    }
                }

                dbgLog(TAG, "GPS should be enabled", 'v');
            }
        }

        wifiNetworkTextView = (TextView) findViewById(com.motorola.motocit.R.id.ssid_1);
        mlistView = (ListView) findViewById(com.motorola.motocit.R.id.mlistView1);
        wifiNetworkTextView.setTextColor(Color.GREEN);
        wifiNetworkTextView.setText("Starting scan networks...");
    }

    private void turnOffWiFi()
    {
        if (isWiFiOffDefault)
        {
            if (mWifiManager.isWifiEnabled())
            {
                if (mWifiManager.getWifiState() != WifiManager.WIFI_STATE_DISABLING)
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

            contentRecord("testresult.txt", "WLAN - ScanNetworks:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            turnOffWiFi();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "WLAN - ScanNetworks:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            turnOffWiFi();

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
                turnOffWiFi();
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
        strHelpList.add("This function will perform a WiFi network scan");
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
        contentRecord("testresult.txt", "WLAN - ScanNetworks:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        turnOffWiFi();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "WLAN - ScanNetworks:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        turnOffWiFi();

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
            turnOffWiFi();
            systemExitWrapper(0);
        }
        return true;
    }
}
