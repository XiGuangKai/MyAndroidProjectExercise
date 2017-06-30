/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.bluetooth;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.view.View;
import android.widget.TextView;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class BluetoothUtilityNexTest extends Test_Base
{
    private boolean mIsBluetoothScanServiceBound = false;

    private BluetoothAdapter mBtAdapter;
    private static long BT_ENABLE_DISABLE_TIMEOUT_MSECS = 10000;

    private TextView mStatusTextView;

    private static Method mCreateInsecureL2capSocket = null;

    // constants for BLUETOOTH_CONNECT
    private final int L2CAP_PSM_SDP = 1;
    private final String SERVICE_SEARCH_REQUEST_PDU_ID = "02";
    private final String SERVICE_SEARCH_RESPONSE_PDU_ID = "03";
    private final String TRANSACTION_ID = "0123";
    private final String SSP_UUID = "00001101";
    private final int MIN_RESPONSE_BYTES_READ = 3; // at least PDU ID and
    // Transaction ID
    private final int PDU_ID_START_INDEX = 0;
    private final int PDU_ID_LENGTH = 2;
    private final int TRANSACTION_ID_START_INDEX = 2;
    private final int TRANSACTION_ID_LENGTH = 4;
    private BluetoothDevice tmpRemoteDevice;
    private boolean isConnected = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Bluetooth_Utility_NexTest";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.bluetooth_utility_nextest);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // set mBtAdapter variable
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        initializeCreateInsecureL2capSocketMethod();

        // Set initial battery level text
        mStatusTextView = (TextView) findViewById(com.motorola.motocit.R.id.bluetooth_utility_nextest_info);

        updateStatusText("Activity Started", Color.WHITE);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (intent.getAction().equals(BluetoothDevice.ACTION_UUID))
            {
                dbgLog(TAG, "Receive ACTION_UUID intent", 'i');
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null)
                {
                    dbgLog(TAG, "Unexpected error! device is null", 'e');
                    return;
                }

                if (device.equals(tmpRemoteDevice))
                {
                    dbgLog(TAG, "GetParcelableArrayExtra UUID", 'i');
                    Parcelable[] uuid = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    if ((uuid != null) && (uuid.length > 0))
                    {
                        dbgLog(TAG, "isConnected = true", 'i');
                        isConnected = true;
                    }
                    else
                    {
                        dbgLog(TAG, "uuid is null or length is zero", 'e');
                    }
                }
            }
        }
    };

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

    }

    @Override
    protected void onDestroy()
    {
        dbgLog(TAG, "OnDestroy() called", 'i');

        super.onDestroy();
        unbindBluetoothScanService();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("ENABLE_BLUETOOTH"))
        {
            if (enableBluetooth() == true)
            {
                updateStatusText("Successfully enabled Bluetooth", Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                updateStatusText("Failed to enabled Bluetooth", Color.RED);

                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to enable Bluetooth"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_BLUETOOTH"))
        {
            if (disableBluetooth() == true)
            {
                updateStatusText("Successfully disabled Bluetooth", Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                updateStatusText("Failed to disable Bluetooth", Color.RED);

                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to disable Bluetooth"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("START_BLUETOOTH_SCAN"))
        {
            if (isBluetoothScanServiceRunning() == false)
            {
                startService(new Intent(getApplicationContext(), BluetoothScanService.class));

                updateStatusText("Started Bluetooth scan service", Color.WHITE);
            }
            doBindBluetoothScanService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_BLUETOOTH_SCAN"))
        {

            if (isBluetoothScanServiceRunning() == true)
            {
                stopService(new Intent(getApplicationContext(), BluetoothScanService.class));
                updateStatusText("Stopped Bluetooth scan service", Color.WHITE);
            }
            unbindBluetoothScanService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_BLUETOOTH_SCAN_RESULTS"))
        {
            HashMap<String, HashMap<String, String>> scanResults = new HashMap<String, HashMap<String, String>>();
            BluetoothScanService.getScanResults(scanResults);

            List<String> deviceAddresses = new ArrayList<String>();
            deviceAddresses.addAll(scanResults.keySet());

            int i = 0;
            List<String> strDataList = new ArrayList<String>();

            for (String deviceAddress : deviceAddresses)
            {
                HashMap<String, String> deviceInfoMap = scanResults.get(deviceAddress);

                if (null != deviceInfoMap) // keep klockwork happy
                {
                    strDataList.add(String.format("ADDRESS_%d=%s", i, deviceAddress));
                    strDataList.add(String.format("NAME_%d=%s", i, deviceInfoMap.get(BluetoothScanService.KEY_DEVICE_NAME)));
                    strDataList.add(String.format("RSSI_%d=%s", i, deviceInfoMap.get(BluetoothScanService.KEY_DEVICE_RSSI)));
                }
                i++;
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            String statusMsg = "Bluetooth scan found " + i + " devices";
            updateStatusText(statusMsg, Color.WHITE);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        else if (strRxCmd.equalsIgnoreCase("GET_SINGLE_DEVICE_INFO"))
        {
            // strRxCmdDataList has MAC Address of device
            if (strRxCmdDataList.size() < 1)
            {
                String statusMsg = "Missing Device MAC address input parameter";
                updateStatusText(statusMsg, Color.RED);

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Missing Device MAC address input parameter");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String macAddress = BluetoothScanService.standardizeBtAddress(strRxCmdDataList.get(0));

            // Get all scan results
            HashMap<String, HashMap<String, String>> scanResults = new HashMap<String, HashMap<String, String>>();
            BluetoothScanService.getScanResults(scanResults);

            HashMap<String, String> deviceInfoMap = scanResults.get(macAddress);

            if ((scanResults.containsKey(macAddress) == false) || (null == deviceInfoMap))
            {
                String statusMsg = "Bluetooth scan did not find device " + macAddress;
                updateStatusText(statusMsg, Color.RED);

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Requested device not found");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String statusMsg = "Bluetooth scan found device " + macAddress;
            updateStatusText(statusMsg, Color.WHITE);

            // return info in info packet and return pass
            List<String> strDataList = new ArrayList<String>();
            strDataList.add("NAME=" + deviceInfoMap.get(BluetoothScanService.KEY_DEVICE_NAME));
            strDataList.add("RSSI=" + deviceInfoMap.get(BluetoothScanService.KEY_DEVICE_RSSI));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an 'pass' exception
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLEAR_SCAN_RESULTS"))
        {
            BluetoothScanService.clearScanResults();

            // Generate an 'pass' exception
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_BLUETOOTH_MAC"))
        {
            if (!mBtAdapter.isEnabled())
            {
                updateStatusText("Cannot read MAC because Bluetooth is not enabled", Color.RED);

                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot read MAC because Bluetooth is not enabled"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String macAddress = mBtAdapter.getAddress();
            updateStatusText("Bluetooth MAC address: " + macAddress, Color.WHITE);

            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("MAC_ADDRESS=%s", macAddress));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("BLUETOOTH_CONNECT_TEST"))
        {
            // I am testing to see if we can connect to the specified BT MAC by
            // manually
            // doing a Service Discovery Protocol (SDP) request and see if there
            // is response.
            // I don't care if no services are discovered just that the other
            // side
            // responded. Now I couldn't use the
            // BluetoothDevice.fetchUuidsWithSdp() because
            // it will return null for the UUIDs for both a BT device with no
            // services activated
            // or BT device that does not exist.
            // The SDP connect over L2CAP will fail the connect if the BT device
            // does not exist.

            // Restrict this TELL command to ICS and above
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format(strRxCmd + " command is only support on phones using API level %d and above.  "
                        + "Current phone has API level %d", Build.VERSION_CODES.ICE_CREAM_SANDWICH, Build.VERSION.SDK_INT));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (!mBtAdapter.isEnabled())
            {
                updateStatusText("Cannot perform " + strRxCmd + " because bluetooth is not enabled", Color.RED);

                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Cannot do " + strRxCmd + " because Bluetooth is not enabled"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // strRxCmdDataList has MAC Address of device
            if (strRxCmdDataList.size() < 1)
            {
                String statusMsg = strRxCmd + " Missing Device MAC address input parameter";
                updateStatusText(statusMsg, Color.RED);

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(statusMsg);
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String macAddress = BluetoothScanService.standardizeBtAddress(strRxCmdDataList.get(0));

            if (BluetoothAdapter.checkBluetoothAddress(macAddress) == false)
            {
                String statusMsg = strRxCmd + " Invalid MAC address input parameter " + strRxCmdDataList.get(0);
                updateStatusText(statusMsg, Color.RED);

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(statusMsg);
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Cancel any discovery because it will slow do not connection
            mBtAdapter.cancelDiscovery();

            BluetoothDevice remoteDevice = mBtAdapter.getRemoteDevice(macAddress);
            tmpRemoteDevice = remoteDevice;

            // If it's MR2 build, use method fetchUuidsWithSdp
            if (Build.VERSION.SDK_INT > 17)
            {
                if (remoteDevice.fetchUuidsWithSdp())
                {
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(BluetoothDevice.ACTION_UUID);
                    filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                    this.registerReceiver(mReceiver, filter);
                    dbgLog(TAG, "registerReceiver", 'i');
                }
                else
                {
                    String statusMsg = strRxCmd + " fetchUuidsWithSdp Failed";
                    updateStatusText(statusMsg, Color.RED);

                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(statusMsg);
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else
            {
                // Default method to do BT connection
                BluetoothSocket btSocket = null;
                OutputStream outStream = null;
                InputStream inStream = null;

                try
                {
                    // PSM definition can be found at
                    // http://www.palowireless.com/bluearticles/adapt.asp
                    // btSocket =
                    // remoteDevice.createInsecureL2capSocket(L2CAP_PSM_SDP);
                    btSocket = createInsecureL2capSocket(remoteDevice, L2CAP_PSM_SDP);
                    btSocket.connect();

                    dbgLog(TAG, "Established connection to " + macAddress, 'i');

                    outStream = btSocket.getOutputStream();

                    // Build PDU packet to request service request
                    // PDU packet format example can be found at:
                    // http://hccc.ee.ccu.edu.tw/courses/bt/vg/sdp.pdf
                    //
                    // In addition full spec can be found at:
                    // https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=241363

                    String sdpServiceSearchRequest = ""; // hex string
                    sdpServiceSearchRequest += SERVICE_SEARCH_REQUEST_PDU_ID; /* PDU ID */
                    sdpServiceSearchRequest += TRANSACTION_ID; /* transaction id */
                    sdpServiceSearchRequest += "000A"; /* parameter length */
                    sdpServiceSearchRequest += "35"; /* data element descriptor */
                    sdpServiceSearchRequest += "05"; /* data element size */
                    sdpServiceSearchRequest += "1A"; /* UUID descriptor */
                    sdpServiceSearchRequest += SSP_UUID; /* UUID */
                    sdpServiceSearchRequest += "0001"; /* Max Service Record Count */
                    sdpServiceSearchRequest += "00"; /* Continuation State */

                    dbgLog(TAG, "Sending PDU packet " + sdpServiceSearchRequest, 'i');

                    // Convert hex string to byte array and send to SDP server
                    byte[] cmdBuffer = hexStringToByteArray(sdpServiceSearchRequest);
                    outStream.write(cmdBuffer);
                    outStream.flush();

                    // Read back response from SDP server
                    inStream = new BufferedInputStream(btSocket.getInputStream());

                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    byte[] inBuffer = new byte[100];

                    dbgLog(TAG, "Reading response packet " + sdpServiceSearchRequest, 'i');

                    while (true)
                    {
                        if (byteStream.size() > MIN_RESPONSE_BYTES_READ)
                        {
                            break;
                        }

                        // first see if there is any data to read to prevent
                        // blocking
                        if (inStream.available() > 0)
                        {
                            int a = inStream.read(inBuffer, 0, 100);

                            if (a == -1)
                            {
                                break;
                            }

                            byteStream.write(inBuffer, 0, a);
                        }
                    }

                    // convert ByteStream to byte array
                    byte[] responseByteArray = byteStream.toByteArray();

                    // check to make sure that the PDU_ID is 0x03 and the
                    // transaction ID is ours
                    String response = byteArrayToHexString(responseByteArray);
                    dbgLog(TAG, "Response packet " + response, 'i');
                    String responsePduId = response.substring(PDU_ID_START_INDEX, PDU_ID_START_INDEX + PDU_ID_LENGTH);
                    String responseTransactionId = response.substring(TRANSACTION_ID_START_INDEX, TRANSACTION_ID_START_INDEX + TRANSACTION_ID_LENGTH);

                    // if PDU_ID or transaction id do not match expected results
                    // then fail
                    if ((responsePduId.equalsIgnoreCase(SERVICE_SEARCH_RESPONSE_PDU_ID) == false)
                            || (responseTransactionId.equalsIgnoreCase(TRANSACTION_ID) == false))
                    {

                        String statusMsg = strRxCmd
                                + String.format("Response PDU_ID (%s != %s) or TRANSACTION_ID (%s != %s) does not match", responsePduId,
                                        SERVICE_SEARCH_RESPONSE_PDU_ID, responseTransactionId, TRANSACTION_ID);
                        updateStatusText(statusMsg, Color.RED);

                        // Generate an exception to send FAIL result and mesg
                        // back to CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(statusMsg);
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                catch (IOException e1)
                {
                    String statusMsg = strRxCmd + " " + e1.toString();
                    updateStatusText(statusMsg, Color.RED);

                    // Generate an exception to send FAIL result and mesg back
                    // to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(e1.toString());
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                finally
                {
                    // close all streams and sockets
                    try
                    {
                        if (null != outStream)
                        {
                            outStream.close();
                        }

                        if (null != inStream)
                        {
                            inStream.close();
                        }

                        if (null != btSocket)
                        {
                            btSocket.close();
                        }
                    }
                    catch (IOException e)
                    {
                        // do nothing with exception since we already
                        // passed test conditions
                    }
                }

                String statusMsg = strRxCmd + " " + macAddress + " passed";
                updateStatusText(statusMsg, Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (Build.VERSION.SDK_INT > 17)
            {
                long startTime = System.currentTimeMillis();
                while (!isConnected)
                {
                    if ((System.currentTimeMillis() - startTime) > BT_ENABLE_DISABLE_TIMEOUT_MSECS)
                    {
                        String statusMsg = "Fail to receive message from receiver";
                        updateStatusText(statusMsg, Color.RED);

                        this.unregisterReceiver(this.mReceiver);
                        dbgLog(TAG, "Unregister mReceiver", 'i');
                        // Generate an exception to send data back to CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(statusMsg);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                dbgLog(TAG, "Received msg", 'i');

                this.unregisterReceiver(this.mReceiver);
                dbgLog(TAG, "Unregister mReceiver", 'i');

                isConnected = false;
                String statusMsg = strRxCmd + " " + macAddress + " passed";
                updateStatusText(statusMsg, Color.WHITE);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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

    private static void initializeCreateInsecureL2capSocketMethod()
    {
        // try to get BluetoothDevice.createInsecureL2capSocket()
        try
        {
            if (mCreateInsecureL2capSocket == null)
            {
                mCreateInsecureL2capSocket = BluetoothDevice.class.getDeclaredMethod("createInsecureL2capSocket", new Class[] { int.class });
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
            mCreateInsecureL2capSocket = null;
        }
    }

    private BluetoothSocket createInsecureL2capSocket(BluetoothDevice btDevice, int psm) throws CmdFailException
    {
        Object retValue = null;

        if (null != mCreateInsecureL2capSocket)
        {
            try
            {
                retValue = mCreateInsecureL2capSocket.invoke(btDevice, psm);
            }
            catch (Exception e)
            {
                // throw fail exception
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to invoke BluetoothDevice.createInsecureL2capSocket(). %s", e.getMessage()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else
        {
            // last ditch effort to get directly create BluetoothSocket
            dbgLog(TAG, "CreateInsecureL2capSocket reflected method not found.  Attempting last ditch effort to create socket directly", 'i');
            try
            {
                Field field = BluetoothSocket.class.getDeclaredField("TYPE_L2CAP");
                field.setAccessible(true);
                int type_l2cap = (Integer) field.get(BluetoothSocket.class);

                Constructor<?>[] cons = BluetoothSocket.class.getDeclaredConstructors();
                cons[0].setAccessible(true);
                retValue = cons[0].newInstance(type_l2cap, -1, false, false, btDevice, psm, null);
            }
            catch (Exception e)
            {
                retValue = null;
            }
        }

        if (retValue == null)
        {
            // throw fail exception
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Could not locate function BluetoothDevice.createInsecureL2capSocket() or directly create socket"));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        return (BluetoothSocket) retValue;
    }

    public static byte[] hexStringToByteArray(String s)
    {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String byteArrayToHexString(byte[] b)
    {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < b.length; i++)
        {
            result.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    protected boolean enableBluetooth()
    {
        if (mBtAdapter.isEnabled() == true)
        {
            return true;
        }

        mBtAdapter.enable();

        boolean turnOnResult = true;

        // wait for btAdapter to return enabled
        long startTime = System.currentTimeMillis();
        while (!mBtAdapter.isEnabled())
        {
            dbgLog(TAG, "Waiting for BT adapter to start", 'v');

            if ((System.currentTimeMillis() - startTime) > BT_ENABLE_DISABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to start BT adapter", 'e');
                turnOnResult = false;
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
                turnOnResult = false;
            }
        }
        return turnOnResult;
    }

    protected boolean disableBluetooth()
    {
        if (mBtAdapter.isEnabled() == false)
        {
            return true;
        }

        mBtAdapter.disable();

        boolean turnOffResult = true;

        // wait for btAdapter to return enabled
        long startTime = System.currentTimeMillis();
        while (mBtAdapter.isEnabled())
        {
            dbgLog(TAG, "Waiting for BT adapter to turn off", 'v');

            if ((System.currentTimeMillis() - startTime) > BT_ENABLE_DISABLE_TIMEOUT_MSECS)
            {
                dbgLog(TAG, "Failed to turn off BT adapter", 'e');
                turnOffResult = false;
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
                turnOffResult = false;
            }
        }
        return turnOffResult;
    }

    protected BluetoothScanService mBluetoothScanService;

    private ServiceConnection mBluetoothScanConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBluetoothScanService = ((BluetoothScanService.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            dbgLog(TAG, "Connected to BluetoothScanService", 'i');
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBluetoothScanService = null;
            dbgLog(TAG, "Disconnected from BluetoothScanService", 'i');

        }
    };

    void doBindBluetoothScanService()
    {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        // bindService(new Intent(Test_Base.this,
        // CommServer.class), mConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(getApplicationContext(), BluetoothScanService.class), mBluetoothScanConnection, Context.BIND_AUTO_CREATE);
        mIsBluetoothScanServiceBound = true;
    }

    void unbindBluetoothScanService()
    {
        if (mIsBluetoothScanServiceBound == true)
        {
            dbgLog(TAG, "OnDestroy() unbindService(mBluetoothScanConnection)", 'i');
            this.unbindService(mBluetoothScanConnection);

            mIsBluetoothScanServiceBound = false;
        }
    }

    private boolean isBluetoothScanServiceRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        // keep klocwork happy
        if (null == manager)
        {
            dbgLog(TAG, "isBluetoothScanServiceRunning() Could not retrieve ActivityManager", 'e');
            return false;
        }

        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        if (null == runningServices)
        {
            dbgLog(TAG, "isBluetoothScanServiceRunning() Could not retrieve list of running services", 'e');
            return false;
        }

        String ServiceName = this.getPackageName() + ".bluetooth.BluetoothScanService";
        dbgLog(TAG, "isBluetoothScanServiceRunning() looking for " + ServiceName, 'i');

        for (RunningServiceInfo service : runningServices)
        {
            // keep klocwork happy
            if (null == service)
            {
                continue;
            }

            dbgLog(TAG, "isBluetoothScanServiceRunning() check service named " + service.service.getClassName(), 'i');

            if (ServiceName.equals(service.service.getClassName()))
            {
                dbgLog(TAG, "isBluetoothScanServiceRunning() returned true", 'i');
                return true;
            }
        }
        dbgLog(TAG, "isBluetoothScanServiceRunning() returned false", 'i');
        return false;
    }

    private void updateStatusText(final String statusMsg, final int color)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mStatusTextView.setTextColor(color);
                mStatusTextView.setText(statusMsg);
            }
        });
    }

    @Override
    protected void printHelp()
    {

        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function is use by NexTest to test Bluetooth");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_BLUETOOTH     - enables Bluetooth adapter");
        strHelpList.add("  DISABLE_BLUETOOTH    - disables Bluetooth adapter");
        strHelpList.add("  START_BLUETOOTH_SCAN - starts Bluetooth scan background service");
        strHelpList.add("  STOP_BLUETOOTH_SCAN  - stops Bluetooth scan background service");
        strHelpList.add("  GET_BLUETOOTH_SCAN_RESULTS - returns all devices found by Bluetooth scan service");
        strHelpList.add("  GET_SINGLE_DEVICE_SCAN_RESULT <BT_DEVICE_ADDRESS> - returns device info for device matching supplied BT_DEVICE_ADDRESS");
        strHelpList.add("  CLEAR_SCAN_RESULTS   - clear cache of devices found so far by Bluetooth scan service");
        strHelpList.add("  GET_BLUETOOTH_MAC    - returns the MAC address of Bluetooth adapter");
        strHelpList.add("  BLUETOOTH_CONNECT_TEST <BT_DEVICE_ADDRESS> - Perform connection test to device matching supplied BT_DEVICE_ADDRESS");

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
