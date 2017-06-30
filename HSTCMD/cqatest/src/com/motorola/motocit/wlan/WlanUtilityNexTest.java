/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.wlan;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.IpConfiguration;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class WlanUtilityNexTest extends Test_Base
{
    private String service = Context.WIFI_SERVICE;
    private WifiManager mWifiManager = null;
    private boolean mIsWlanScanServiceBound = false;
    private static long WLAN_ENABLE_DISABLE_TIMEOUT_MSECS = 10000;
    private static long WLAN_DISCONNECT_TIMEOUT_MSECS = 60000;

    private TextView mStatusTextView;

    // ICS methods
    private static Method asyncConnect = null;
    private static Method connectNetwork = null;
    private static Method forgetNetwork = null;
    private static Method forgetAllNetwork = null;

    // JB methods
    private static Method initializeJB = null;
    private static Method connectJB = null;
    private static Method forgetJB = null;
    private static Method forgetAllNetworkJB = null;
    private static Class<?> channelClass = null;
    private Object mChannel = null;
    private static Class<?> actionListenerClass = null;
    private static Class<?> channelListenerClass = null;

    private class WifiServiceHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {

            default:
                //Ignore
                break;
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "WLAN_Utility_NexTest";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.wlan_utility_nextest);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // set wifi manager variable
        mWifiManager = (WifiManager) getSystemService(service);

        configureWifiAsyncMethods();

        // Set initial battery level text
        mStatusTextView = (TextView) findViewById(com.motorola.motocit.R.id.wlan_utility_nextest_info);

        updateStatusText("Activity Started", Color.WHITE);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();
    }


    @Override
    protected void onDestroy()
    {
        dbgLog(TAG, "OnDestroy() called", 'i');

        super.onDestroy();
        unbindWlanScanService();
    }


    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("ENABLE_WLAN"))
        {
            if (enableWlan() == true)
            {
                updateStatusText("Successfully enabled WLAN", Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                updateStatusText("Failed to enabled WLAN", Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to enable wlan"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_WLAN"))
        {
            if (disableWlan() == true)
            {
                updateStatusText("Successfully disabled WLAN", Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                updateStatusText("Failed to disable WLAN", Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to disable wlan"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("START_WLAN_SCAN"))
        {
            if (isWlanScanServiceRunning() == false)
            {
                startService(new Intent(getApplicationContext(), WlanScanService.class));

                updateStatusText("Started WLAN scan service", Color.WHITE);
            }
            doBindWlanScanService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_WLAN_SCAN"))
        {

            if (isWlanScanServiceRunning() == true)
            {
                stopService(new Intent(getApplicationContext(), WlanScanService.class));
                updateStatusText("Stopped WLAN scan service", Color.WHITE);
            }
            unbindWlanScanService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        else if (strRxCmd.equalsIgnoreCase("GET_WLAN_SCAN_RESULTS"))
        {
            List<ScanResult> scanResults = new ArrayList<ScanResult>();
            WlanScanService.getScanResults(scanResults);

            int i = 0;
            List<String> strDataList = new ArrayList<String>();
            for (ScanResult result : scanResults)
            {
                strDataList.add(String.format("SSID_%d=%s", i, result.SSID));
                strDataList.add(String.format("BSSID_%d=%s", i, result.BSSID));
                strDataList.add(String.format("CAPABILITIES_%d=%s", i, result.capabilities));
                strDataList.add(String.format("FREQUENCY_%d=%d", i, result.frequency));
                strDataList.add(String.format("LEVEL_%d=%d", i, result.level));

                i++;
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            String statusMsg = "WLAN scan found " + i + " APs";
            updateStatusText(statusMsg, Color.WHITE);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);


        }
        else if (strRxCmd.equalsIgnoreCase("GET_WLAN_MAC"))
        {
            if (!mWifiManager.isWifiEnabled())
            {
                updateStatusText("Cannot read MAC because WLAN is not enabled", Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot read MAC because WLAN is not enabled"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            WifiInfo wifiConnectionInfo = null;
            if ((wifiConnectionInfo = mWifiManager.getConnectionInfo()) == null)
            {
                updateStatusText("Cannot read MAC because getConnectionInfo() returned NULL", Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot read MAC because getConnectionInfo() returned NULL"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String macAddress = wifiConnectionInfo.getMacAddress();
            updateStatusText("WLAN MAC address: " + macAddress, Color.WHITE);

            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("MAC_ADDRESS=%s", macAddress));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);


            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CONNECT"))
        {
            // Restrict this TELL command to ICS and above
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("CONNECT command is only support on phones using API level %d and above.  "
                        + "Current phone has API level %d", Build.VERSION_CODES.ICE_CREAM_SANDWICH, Build.VERSION.SDK_INT));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            //==============================
            // Get configurable CONNECT parameters
            //==============================
            String SSID = null;
            IpConfiguration.IpAssignment ipAssignment = IpConfiguration.IpAssignment.DHCP; // default to DHCP
            String ipAddress = null;
            int networkPrefixLength = -1;
            String gateway = null;
            String dns1 = null;
            String dns2 = null;

            // if list has no data .. return NACK
            if (strRxCmdDataList.size() == 0)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("No key=value pairs sent for CONNECT cmd");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // retrieve key=value pairs
            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                // Keys that are in common to both mono and stereo tones
                if (key.equalsIgnoreCase("SSID"))
                {
                    SSID = value;
                }
                else if (key.equalsIgnoreCase("IP_ASSIGNMENT"))
                {
                    if (value.equalsIgnoreCase(IpConfiguration.IpAssignment.DHCP.name()))
                    {
                        ipAssignment = IpConfiguration.IpAssignment.DHCP;
                    }
                    else if (value.equalsIgnoreCase(IpConfiguration.IpAssignment.STATIC.name()))
                    {
                        ipAssignment = IpConfiguration.IpAssignment.STATIC;
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("'%s' is set to an invalid value '%s'", key, value));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                else if (key.equalsIgnoreCase("IP_ADDRESS"))
                {
                    ipAddress = value;
                }
                else if (key.equalsIgnoreCase("NETWORK_PREFIX_LENGTH"))
                {
                    networkPrefixLength = Integer.parseInt(value);
                }
                else if (key.equalsIgnoreCase("GATEWAY"))
                {
                    gateway = value;
                }
                else if (key.equalsIgnoreCase("DNS_1"))
                {
                    dns1 = value;
                }
                else if (key.equalsIgnoreCase("DNS_2"))
                {
                    dns2 = value;
                }
            }

            if (!mWifiManager.isWifiEnabled())
            {
                updateStatusText(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd), Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            //==============================
            // get list existing networks and remove them
            //==============================
            boolean needToSaveConfig = false;
            List<WifiConfiguration> wifiConfigs = mWifiManager.getConfiguredNetworks();

            if (null != wifiConfigs)
            {
                for (WifiConfiguration c : wifiConfigs)
                {
                    // don't try to remove preloaded configs
                    if (isWifiConfigPreloaded(c) == true)
                    {
                        continue;
                    }

                    needToSaveConfig |= removeNetwork(c);
                }
            }

            // needToSaveConfig may or may not be set depending on how the
            // network was removed by removeNetwork()
            if (needToSaveConfig)
            {
                boolean saveConfiguration = mWifiManager.saveConfiguration();

                dbgLog(TAG, "saveConfiguration returned " + saveConfiguration, 'i');

                if (false == saveConfiguration)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("saveConfiguration failed"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }

            //==============================
            // setup WifiConfiguration object
            //==============================
            if (null == SSID)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("SSID key not set"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "Setting up config object for SSID " + SSID, 'i');

            WifiConfiguration config = new WifiConfiguration();

            // below are the common configs for both DHCP and STATIC ip assignment
            config.SSID = "\"" + SSID + "\"";
            config.priority = 0;
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.status = WifiConfiguration.Status.ENABLED;

            // try to speed up locating SSID by directly a probe request directly at SSID
            config.hiddenSSID = true;

            // Below are the settings specific for STATIC ip assignment
            if (ipAssignment == IpConfiguration.IpAssignment.STATIC)
            {
                // Set static IP
                //config.ipAssignment = ipAssignment;
                //LinkProperties linkProperties = config.linkProperties;

                // IP address
                if (null == ipAddress)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("IP_ADDRESS key not set"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                InetAddress inetAddr = null;

                try
                {
                    inetAddr = NetworkUtils.numericToInetAddress(ipAddress);
                }
                catch (IllegalArgumentException e)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalid IP address: " + ipAddress));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Network prefix length
                if (-1 == networkPrefixLength)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("NETWORK_PREFIX_LENGTH key not set"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                if ((networkPrefixLength < 0) || (networkPrefixLength > 32))
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalid networkPrefixLength: " + networkPrefixLength));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Set IP address
                try
                {
                    setIpAddress (inetAddr, networkPrefixLength, config);
                }
                catch (Throwable e)
                {
                    e.printStackTrace ();
                }

                // Gateway
                if (null == gateway)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("GATEWAY key not set"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                InetAddress gatewayAddr = null;
                try
                {
                    gatewayAddr = NetworkUtils.numericToInetAddress(gateway);
                }
                catch (IllegalArgumentException e)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalid gateway: " + gateway));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                //linkProperties.addRoute(new RouteInfo(gatewayAddr));

                // DNS 1
                if (null == dns1)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("DNS_1 key not set"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                InetAddress dnsAddr = null;
                try
                {
                    dnsAddr = NetworkUtils.numericToInetAddress(dns1);
                }
                catch (IllegalArgumentException e)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalid dns 1: " + dns1));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                //linkProperties.addDns(dnsAddr);

                // DNS 2
                if (null == dns2)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("DNS_2 key not set"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    dnsAddr = NetworkUtils.numericToInetAddress(dns2);
                }
                catch (IllegalArgumentException e)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Invalid dns 2: " + dns2));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                //linkProperties.addDns(dnsAddr);
            }

            connectToNetwork(config);

            updateStatusText(String.format("CONNECT to '%s' via %s IP assignment", SSID, ipAssignment.name()), Color.WHITE);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("DISCONNECT"))
        {
            if (!mWifiManager.isWifiEnabled())
            {
                updateStatusText(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd), Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // disconnect from current network if any
            disconnectFromNetwork();

            updateStatusText(String.format("DISCONNECT from current wlan network"), Color.WHITE);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CONNECTION_INFO"))
        {
            if (!mWifiManager.isWifiEnabled())
            {
                updateStatusText(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd), Color.RED);

                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot execute %s because WLAN is not enabled", strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String SSID = "NULL";
            String BSSID = "NULL";
            boolean hiddenSSID = false;
            int rssi = -9999;
            int linkSpeed = -1;
            int ipAddress = 0;
            NetworkInfo.State wifiConnectState = NetworkInfo.State.UNKNOWN;

            Intent intent = this.registerReceiver(null, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

            if (intent != null)
            {
                final Bundle extras = intent.getExtras();
                if (extras != null)
                {
                    final NetworkInfo info = (NetworkInfo) extras.get(WifiManager.EXTRA_NETWORK_INFO);

                    if (info != null)
                    {
                        wifiConnectState = info.getState();

                        if ((wifiConnectState == NetworkInfo.State.CONNECTED))
                        {
                            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

                            if (wifiInfo != null)
                            {
                                wifiInfo.getSSID();
                                dbgLog(TAG, "SSID=" + wifiInfo.getSSID(), 'i');

                                SSID = wifiInfo.getSSID();

                                // If SSID is surrounded by double quotes,
                                // remove them
                                SSID = SSID.replaceFirst("^\\s*\"", "");
                                SSID = SSID.replaceFirst("\"\\s*$", "");

                                BSSID = wifiInfo.getBSSID();
                                hiddenSSID = wifiInfo.getHiddenSSID();
                                rssi = wifiInfo.getRssi();
                                linkSpeed = wifiInfo.getLinkSpeed();
                                ipAddress = wifiInfo.getIpAddress();
                            }
                        }
                    }
                }
            }

            List<String> strDataList = new ArrayList<String>();
            strDataList.add("CONNECT_STATE=" + wifiConnectState.name());
            strDataList.add("SSID=" + SSID);
            strDataList.add("BSSID=" + BSSID);
            strDataList.add("HIDDEN_RSSI=" + (hiddenSSID ? "YES" : "NO"));
            strDataList.add("RSSI=" + rssi);
            strDataList.add("IP_ADDRESS=" + intToIp(ipAddress));
            strDataList.add("LINK_SPEED=" + linkSpeed);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            updateStatusText(String.format("CONNECTION_INFO: CONNECT_STATE=%s, SSID='%s', RSSI=%d", wifiConnectState.name(), SSID, rssi), Color.WHITE);

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
            // Generate an exception to send FAIL result and mesg back to CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    protected void setIpAddress(InetAddress addr, int prefixLength, WifiConfiguration wifiConf) throws Exception
    {
        Object linkProperties = getDeclaredField(wifiConf, "linkProperties");

        if (linkProperties == null)
        {
            return;
        }

        Class<?> laClass = Class.forName("android.net.LinkAddress");
        Constructor<?> laConstructor = laClass.getConstructor(new Class[] { InetAddress.class, int.class });
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);
        ArrayList mLinkAddresses = (ArrayList) getDeclaredField(linkProperties, "mLinkAddresses");
        mLinkAddresses.clear();
        dbgLog(TAG, "mLinkAddress add link address", 'i');
        mLinkAddresses.add(linkAddress);
    }

    protected Object getDeclaredField(Object obj, String name) throws Exception
    {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    protected boolean enableWlan()
    {
        boolean status = true;

        if (mWifiManager.isWifiEnabled() == true)
        {
            return status;
        }

        status = mWifiManager.setWifiEnabled(true);

        // wait for wifimanager to return enabled
        long startTime = System.currentTimeMillis();
        while (!mWifiManager.isWifiEnabled())
        {
            dbgLog(TAG, "Waiting for WLAN adapter to start", 'v');

            if ((System.currentTimeMillis() - startTime) > WLAN_ENABLE_DISABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to start WLAN adapter", 'e');
                status = false;
                break;
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
                status = false;
            }
        }

        return status;
    }

    protected boolean disableWlan()
    {
        boolean status = true;

        if (mWifiManager.isWifiEnabled() == false)
        {
            return status;
        }

        status = mWifiManager.setWifiEnabled(false);

        // wait for wifimanager to return disabled
        long startTime = System.currentTimeMillis();
        while (mWifiManager.isWifiEnabled())
        {
            dbgLog(TAG, "Waiting for WLAN adapter to stop", 'v');

            if ((System.currentTimeMillis() - startTime) > WLAN_ENABLE_DISABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to stop WLAN adapter", 'e');
                status = false;
                break;
            }

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
                status = false;
            }
        }

        return status;
    }


    protected WlanScanService mWlanScanService;

    private ServiceConnection mWlanScanConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mWlanScanService = ((WlanScanService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            dbgLog(TAG, "Connected to WlanScanService", 'i');
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mWlanScanService = null;
            dbgLog(TAG, "Disconnected from WlanScanService", 'i');

        }
    };

    void doBindWlanScanService()
    {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        // bindService(new Intent(Test_Base.this,
        // CommServer.class), mConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(getApplicationContext(), WlanScanService.class), mWlanScanConnection, Context.BIND_AUTO_CREATE);
        mIsWlanScanServiceBound = true;
    }

    void unbindWlanScanService()
    {
        if (mIsWlanScanServiceBound == true)
        {
            dbgLog(TAG, "OnDestroy() unbindService(mWlanScanConnection)", 'i');
            this.unbindService(mWlanScanConnection);

            mIsWlanScanServiceBound = false;
        }
    }

    private boolean isWlanScanServiceRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        // keep klocwork happy
        if (null == manager)
        {
            dbgLog(TAG, "isWlanScanServiceRunning() Could not retrieve ActivityManager", 'e');
            return false;
        }

        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        if (null == runningServices)
        {
            dbgLog(TAG, "isWlanScanServiceRunning() Could not retrieve list of running services", 'e');
            return false;
        }

        String ServiceName = this.getPackageName() + ".wlan.WlanScanService";
        dbgLog(TAG, "isWlanScanServiceRunning() looking for " + ServiceName, 'i');

        for (RunningServiceInfo service : runningServices)
        {
            // keep klocwork happy
            if (null == service)
            {
                continue;
            }

            dbgLog(TAG, "isWlanScanServiceRunning() check service named " + service.service.getClassName(), 'i');

            if (ServiceName.equals(service.service.getClassName()))
            {
                dbgLog(TAG, "isWlanScanServiceRunning() returned true", 'i');
                return true;
            }
        }
        dbgLog(TAG, "isWlanScanServiceRunning() returned false", 'i');
        return false;
    }

    private void updateStatusText(final String statusMsg, final int color)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setTextColor(color);
                mStatusTextView.setText(statusMsg);
            }
        });
    }

    public static String intToIp(int i)
    {
        return (i & 0xFF) + "." +

        ((i >> 8) & 0xFF) + "." +

        ((i >> 16) & 0xFF) + "." +

        ((i >> 24) & 0xFF);
    }

    private boolean isWifiConfigPreloaded(WifiConfiguration config)
    {
        boolean isPreloaded = false;

        try
        {
            Class<? extends WifiConfiguration> c = config.getClass();
            Field field = c.getDeclaredField("preloaded_ssid");

            field.setAccessible(true);
            isPreloaded = (Boolean) field.get(config);
        }
        catch (Exception ignoreException)
        {
            // ignore exception
        }

        return isPreloaded;
    }

    private void configureWifiAsyncMethods()
    {
        // try to setup JB async connect methods
        try
        {
            channelClass = Class.forName("android.net.wifi.WifiManager$Channel");

            actionListenerClass = Class.forName("android.net.wifi.WifiManager$ActionListener");
            channelListenerClass = Class.forName("android.net.wifi.WifiManager$ChannelListener");

            initializeJB = WifiManager.class.getMethod("initialize", new Class[]
                    { Context.class, Looper.class, channelListenerClass });

            connectJB = WifiManager.class.getMethod("connect", new Class[]
                    { channelClass, WifiConfiguration.class, actionListenerClass });

            forgetJB = WifiManager.class.getMethod("forget", new Class[]
                    { channelClass, int.class, actionListenerClass });

            forgetAllNetworkJB = WifiManager.class.getMethod("forgetAllNetwork", new Class[]
                    { channelClass, int.class, actionListenerClass });

            mChannel = initializeJB.invoke(mWifiManager, getApplicationContext(), getMainLooper(), null);

        }
        catch (Exception ex)
        {
            channelClass = null;
            actionListenerClass = null;
            channelListenerClass = null;
            initializeJB = null;
            connectJB = null;
            forgetJB = null;
            forgetAllNetworkJB = null;
            mChannel = null;
        }

        // try to call ICS mWifiManager.asyncConnect()
        try
        {
            asyncConnect = WifiManager.class.getMethod("asyncConnect", new Class[]
                    { Context.class, Handler.class });

            asyncConnect.invoke(mWifiManager, getApplicationContext(), new WifiServiceHandler());
        }
        catch (Exception ex)
        {
            // method does not exist
            asyncConnect = null;
        }

        // setup other methods if asyncConnect was successful
        if (asyncConnect != null)
        {
            // forgetNetwork
            try
            {
                forgetNetwork = WifiManager.class.getMethod("forgetNetwork", new Class[]
                        { int.class });
            }
            catch (NoSuchMethodException e)
            {
                forgetNetwork = null;
            }

            // forgetAllNetwork
            try
            {
                forgetAllNetwork = WifiManager.class.getMethod("forgetAllNetwork");
            }
            catch (NoSuchMethodException e)
            {
                forgetAllNetwork = null;
            }

            // connectNetwork
            try
            {
                connectNetwork = WifiManager.class.getMethod("connectNetwork", new Class[]
                        { WifiConfiguration.class });
            }
            catch (NoSuchMethodException e)
            {
                connectNetwork = null;
            }
        }
    }

    private void connectToNetwork(WifiConfiguration c) throws CmdFailException
    {
        // JB connect method
        if (connectJB != null)
        {
            dbgLog(TAG, "calling JB connect", 'i');
            try
            {
                connectJB.invoke(mWifiManager, mChannel, c, null);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("connect(%s) threw exception: " + e.toString(), c.SSID));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finish calling JB connect", 'i');

        }
        // ICS connect method
        else if (connectNetwork != null)
        {
            dbgLog(TAG, "calling ICS connectNetwork", 'i');
            try
            {
                connectNetwork.invoke(mWifiManager, c);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("connectNetwork(%s) threw exception: " + e.toString(), c.SSID));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finish calling ICS connectNetwork", 'i');
        }
        // fall back method
        else
        {
            int networkId = mWifiManager.addNetwork(c);
            c.networkId = networkId;

            boolean saveConfiguration = mWifiManager.saveConfiguration();

            dbgLog(TAG, "post add network saveConfiguration returned " + saveConfiguration, 'i');

            if (false == saveConfiguration)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("saveConfiguration failed"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            //==============================
            // enable newly configured wifi config
            //==============================
            boolean enabled = mWifiManager.enableNetwork(networkId, true);

            dbgLog(TAG, "enableNetwork returned " + enabled, 'i');

            if (false == enabled)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("enableNetwork failed"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            //==============================
            // Trigger reconnect to network
            //==============================
            boolean reconnect = mWifiManager.reconnect();
            dbgLog(TAG, "reconnect returned " + reconnect, 'i');

            if (false == reconnect)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("reconnect failed"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

        }

        return;
    }

    private boolean removeNetwork(WifiConfiguration c) throws CmdFailException
    {
        boolean needToSaveConfig = false;

        // JB method
        if (forgetJB != null)
        {
            dbgLog(TAG, "calling JB forget " + c.SSID, 'i');
            try
            {
                forgetJB.invoke(mWifiManager, mChannel, c.networkId, null);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("forget(%s) threw exception: " + e.toString(), c.SSID));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finished calling JB forget", 'i');
        }
        // ICS method
        else if (forgetNetwork != null)
        {
            dbgLog(TAG, "calling ICS forgetNetwork " + c.SSID, 'i');
            try
            {
                forgetNetwork.invoke(mWifiManager, c.networkId);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("forgetNetwork(%s) threw exception: " + e.toString(), c.SSID));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finished calling ICS forgetNetwork", 'i');
        }
        // fall back method
        else
        {
            dbgLog(TAG, "removing configured network " + c.SSID, 'i');
            boolean removeNetwork = mWifiManager.removeNetwork(c.networkId);
            dbgLog(TAG, "removeNetwork() returned " + removeNetwork, 'i');

            if (false == removeNetwork)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("removeNetwork(%s) failed", c.SSID));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            needToSaveConfig = true;
        }

        return needToSaveConfig;
    }

    protected void disconnectFromNetwork() throws CmdFailException
    {
        // JB method
        if (forgetAllNetworkJB != null)
        {
            dbgLog(TAG, "calling JB forgetAllNetwork", 'i');
            try
            {
                forgetAllNetworkJB.invoke(mWifiManager, mChannel, 0, null);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("JB forgetAllNetwork() threw exception: " + e.toString()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finished calling JB forgetAllNetwork", 'i');
        }
        // ICS method
        else if (forgetAllNetwork != null)
        {
            dbgLog(TAG, "calling ICS forgetAllNetwork", 'i');
            try
            {
                forgetAllNetwork.invoke(mWifiManager);
            }
            catch (Exception e)
            {
                // Generate an exception to send FAIL result and mesg back to CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("ICS forgetAllNetwork() threw exception: " + e.toString()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "finished calling ICS forgetAllNetwork", 'i');
        }
        // fall back method
        else
        {
            // disconnect from current network if any
            mWifiManager.disconnect();

            // wait for wifimanager to return disabled
            long startTime = System.currentTimeMillis();
            while (true)
            {
                dbgLog(TAG, "Waiting for WLAN adapter to disconnect from AP", 'v');

                if ((System.currentTimeMillis() - startTime) > WLAN_DISCONNECT_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Failed to disconnect from WLAN network", 'e');

                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("WLAN connection failed to disconnect within %d ms", WLAN_DISCONNECT_TIMEOUT_MSECS));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // wait for connection to be disconnected
                WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

                if (null == wifiInfo)
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("getConnectionInfo() returned NULL"));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                String SSID = wifiInfo.getSSID();

                if ((null == SSID) || (wifiInfo.getSupplicantState() == SupplicantState.DISCONNECTED))
                {
                    dbgLog(TAG, "Successfully disconnected from WLAN network", 'v');
                    break;
                }
            }
        }

        return;
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function is use by NexTest to test WLAN");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_WLAN     - enables WLAN adapter");
        strHelpList.add("  DISABLE_WLAN    - disables WLAN adapter");
        strHelpList.add("  START_WLAN_SCAN - starts WLAN scan background service");
        strHelpList.add("  STOP_WLAN_SCAN  - stops WLAN scan background service");
        strHelpList.add("  GET_WLAN_SCAN_RESULTS - returns APs found by WLAN scan service");
        strHelpList.add("  GET_WLAN_MAC    - returns the MAC address of WLAN adapter");
        strHelpList.add("  ");
        strHelpList.add("  CONNECT - takes the following key-value pairs to connect to the specified AP");
        strHelpList.add("    SSID=<SSID> - SSID of AP to connect to");
        strHelpList.add("    IP_ASSIGNMENT=<STATIC or DHCP> - connect with static ip or request ip thru DHCP.");
        strHelpList.add("                                     If not supplied then default is DHCP");
        strHelpList.add("    IP_ADDRESS=<IP> - STATIC only: IP address to connect to AP with");
        strHelpList.add("    NETWORK_PREFIX_LENGTH=<PREFIX_LENGTH> - STATIC only: Integer value greater than 0 and less than 32");
        strHelpList.add("    GATEWAY=<IP> - STATIC only: IP address of gateway");
        strHelpList.add("    DNS_1=<IP> - STATIC only: IP address of DNS 1");
        strHelpList.add("    DNS_2=<IP> - STATIC only: IP address of DNS 2");
        strHelpList.add("  ");
        strHelpList.add("  DISCONNECT - Disconnect from currently connected AP (if any)");
        strHelpList.add("  ");
        strHelpList.add("  GET_CONNECTION_INFO - Retrieve information current connection (if any).");
        strHelpList.add("                        Returns the following key-value pairs");
        strHelpList.add("    CONNECT_STATE=<CONNECTING, CONNECTED, SUSPENDED, DISCONNECTING, DISCONNECTED, or UNKNOWN>");
        strHelpList.add("    SSID=<SSID> - CONNECTED state only: SSID of connected AP");
        strHelpList.add("    BSSID=<MAC_ADDR> - CONNECTED state only: MAC address of connected AP");
        strHelpList.add("    HIDDEN_RSSI=<YES or NO> - CONNECTED state only: If connected AP SSID is hidden or not");
        strHelpList.add("    RSSI=<RSSI> - CONNECTED state only: Rssi of connected AP");
        strHelpList.add("    IP_ADDRESS=<IP> - CONNECTED state only: IP address assigned to the phone when connected to the AP");
        strHelpList.add("    LINK_SPEED=<LINK_SPEED> - CONNECTED state only: Link speed in Mbps that the phone is connected to the AP");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
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
        return true;
    }

}
