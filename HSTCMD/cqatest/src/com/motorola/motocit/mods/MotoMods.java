/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.mods;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.mod.IModManager;
import com.motorola.mod.ModBattery;
import com.motorola.mod.ModDisplay;
import com.motorola.mod.ModBacklight;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModContract;
import com.motorola.mod.ModManager;
import com.motorola.mod.ModProtocol;
import com.motorola.mod.ModConnection;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModProtocol.Protocol;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.R;
import com.motorola.motocit.Test_Base;
import com.motorola.motocit.audio.AudioPlayMediaFile;

import java.text.NumberFormat;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.lang.Long;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MotoMods extends Test_Base
{

    private IModManager mModService;
    private static ModManager mModManager;
    private boolean mModServiceConnected = false;
    private ModDevice mAttachedMod = null;

    private View mModsAttachView;
    private View mModsDetachView;
    private TextView mPrimaryTextView;
    private TextView mSecondaryTextView;
    private TextView mVersionTextView;
    private View mBatteryView;
    private TextView mBatteryLevelTextView;

    List<ModDevice> mModConnectedList = null;
    private final Lock lockModConnectedList = new ReentrantLock();

    private String mModBatteryStatus = "UNKNOWN";
    private int mModBatteryLevel = -1;
    private String mBatteryPluggedRaw = "UNKNOWN";

    private Handler mHandler;
    private Protocol mPrimaryProtocol;

    private BroadcastReceiver modAttachDetachReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            dbgLog(TAG, "Receive broadcaset intent " + action, 'i');

            DoModUpdate(action, true);
        }
    };

    private void ClearModPermissionList()
    {
        // This is required to clear permissions so that mod settings does not
        // re-start our application
        try
        {
            ContentResolver cr = getApplicationContext().getContentResolver();
            int count = cr.delete(ModContract.CONTENT_URI_UID_PERMS,
                    ModContract.COLUMN_PUID + "=?",
                    new String[] { Integer.toString(getApplicationContext().getPackageManager().getApplicationInfo("com.motorola.motocit", 0).uid) });
        }
        catch (Exception e)
        {
            // do nothing
        }
    }

    private void DoModUpdate(String action, boolean updateDisplay)
    {
        try
        {
            lockModConnectedList.lock();
            mModConnectedList = null;

            ModInformation.isAttached = false;
            ModInformation.firmwareVersion = "";
            ModInformation.product = "";
            ModInformation.vendor = "";

            int modCheckAttempt = 1;
            long startTime = SystemClock.uptimeMillis();

            // Check for Mod Connection up to 5 seconds after receiving
            // ACTION_MOD_ATTACH intent
            do
            {
                dbgLog(TAG, "Checking for Mod Connection, attempt: " + modCheckAttempt++, 'i');
                updateConnectedModsList();
                if (mModConnectedList == null || mModConnectedList.isEmpty())
                {
                    try
                    {
                        Thread.sleep(5);
                    }
                    catch (Exception e)
                    {
                        // do nothing
                    }
                }

            }
            while (ModManager.ACTION_MOD_ATTACH.equals(action) && (mModConnectedList == null || mModConnectedList.isEmpty()) && ((SystemClock.uptimeMillis() - startTime) < 5000));
        }
        finally
        {
            lockModConnectedList.unlock();
        }

        if (updateDisplay)
        {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run()
                {
                    updateModsData();
                }
            }, 10);
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder)
        {
            dbgLog(TAG, "Mods service connected", 'i');

            mModService = IModManager.Stub.asInterface(binder);
            mModManager = new ModManager(MotoMods.this, mModService);
            mModServiceConnected = true;

            try
            {
                lockModConnectedList.lock();

                mModConnectedList = null;
                if (mModServiceConnected)
                {
                    try
                    {
                        dbgLog(TAG, "Getting connected Mods list", 'i');
                        mModConnectedList = mModManager.getModList(true);
                    }
                    catch (Exception e)
                    {
                        dbgLog(TAG, "Error when getting connected Mods list, " + e.getMessage(), 'e');
                    }
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }

            updateModsData();
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            dbgLog(TAG, "Mods service disconnected", 'i');

            if (isFinishing() == false)
            {
                updateModsData();
            }

            mModManager = null;
            mModService = null;
            mModServiceConnected = false;
        }
    };

    public static ModManager getModManager()
    {
        return mModManager;
    }

    @Override
    public void onCreate(Bundle bundle)
    {
        TAG = "MotoMods";
        super.onCreate(bundle);
        setContentView(com.motorola.motocit.R.layout.mods);
        init();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        mHandler = new Handler();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();

        DoModUpdate("", true);

        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);

        IntentFilter modAttachDetachFilter = new IntentFilter();
        modAttachDetachFilter.addAction(ModManager.ACTION_MOD_ATTACH);
        modAttachDetachFilter.addAction(ModManager.ACTION_MOD_DETACH);
        registerReceiver(modAttachDetachReceiver, modAttachDetachFilter);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        unregisterReceiver(modAttachDetachReceiver);
        unregisterReceiver(batteryLevelReceiver);
    }

    @Override
    public void onStop()
    {
        super.onStop();

        if (mHandler != null)
        {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        unbindService(mConnection);
    }

    private void init()
    {
        mModsDetachView = findViewById(R.id.mods_detach);
        mModsAttachView = findViewById(R.id.mods_attach);
        mPrimaryTextView = (TextView) findViewById(R.id.primary_text);
        mSecondaryTextView = (TextView) findViewById(R.id.secondary_text);
        mVersionTextView = (TextView) findViewById(R.id.version_text);
        mBatteryView = findViewById(R.id.batt_area);
        mBatteryLevelTextView = (TextView) findViewById(R.id.mod_battery_level);

        Intent intent = new Intent(ModManager.ACTION_BIND_MANAGER);
        intent.setComponent(ModManager.MOD_SERVICE_NAME);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            DoBatteryUpdate(intent, true);
        }
    };

    private void DoBatteryUpdate(Intent intent, boolean updateDisplay)
    {
        mModBatteryStatus = getBatteryStatus(intent.getIntExtra("mod_status", -1));
        mModBatteryLevel = intent.getIntExtra("mod_level", -1);
        mBatteryPluggedRaw = getBatteryPluggedRaw(intent.getIntExtra("plugged_raw", -1));

        if (updateDisplay)
        {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run()
                {
                    updateModsBattery();
                }
            }, 10);
        }
    }

    private String getBatteryStatus(int batteryStatus)
    {
        String statusString;

        switch (batteryStatus)
        {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                statusString = "BATTERY_STATUS_CHARGING";
                break;

            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                statusString = "BATTERY_STATUS_DISCHARGING";
                break;

            case BatteryManager.BATTERY_STATUS_FULL:
                statusString = "BATTERY_STATUS_FULL";
                break;

            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                statusString = "BATTERY_STATUS_NOT_CHARGING";
                break;

            case BatteryManager.BATTERY_STATUS_UNKNOWN:
                statusString = "BATTERY_STATUS_UNKNOWN";
                break;

            default:
                statusString = "UNKNOWN";
                break;
        }

        return statusString;
    }

    private String getBatteryPluggedRaw(int batteryStatus)
    {
        String statusString;

        switch (batteryStatus)
        {
            case BatteryManager.BATTERY_PLUGGED_AC:
                statusString = "BATTERY_PLUGGED_AC";
                break;

            case BatteryManager.BATTERY_PLUGGED_USB:
                statusString = "BATTERY_PLUGGED_USB";
                break;

            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                statusString = "BATTERY_PLUGGED_WIRELESS";
                break;

            case 8: // BatteryManager.BATTERY_PLUGGED_MOD
                statusString = "BATTERY_PLUGGED_MOD";
                break;

            default:
                statusString = "UNKNOWN";
                break;
        }

        return statusString;
    }

    public void updateModsData()
    {
        try
        {
            lockModConnectedList.lock();
            mAttachedMod = null;

            if (mModConnectedList == null || mModConnectedList.isEmpty())
            {
                mModsDetachView.setVisibility(View.VISIBLE);
                mModsAttachView.setVisibility(View.GONE);
                dbgLog(TAG, "No Mod detected", 'i');
                return;
            }

            mModsDetachView.setVisibility(View.GONE);
            mModsAttachView.setVisibility(View.VISIBLE);

            mAttachedMod = mModConnectedList.get(0);
            mPrimaryTextView.setText(mAttachedMod.getProductString());
            mSecondaryTextView.setText(mAttachedMod.getVendorString());
            mVersionTextView.setText("Firmware Version: " + mAttachedMod.getFirmwareVersion());

            getModInfo();
        }
        finally
        {
            lockModConnectedList.unlock();
        }

        mPrimaryProtocol = getPrimaryProtocol(mAttachedMod);
        dbgLog(TAG, "Mod primary protocol = " + mPrimaryProtocol, 'i');
    }

    private void updateConnectedModsList()
    {
        try
        {
            lockModConnectedList.lock();
            mModConnectedList = null;
            if (mModServiceConnected)
            {
                try
                {
                    dbgLog(TAG, "Getting connected Mods list", 'i');
                    mModConnectedList = mModManager.getModList(true);
                    dbgLog(TAG, "Number of Mods= " + mModConnectedList.size(), 'i');
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Error when getting connected Mods list, " + e.getMessage(), 'e');
                }
            }
        }
        finally
        {
            lockModConnectedList.unlock();
        }
    }

    public ModInformation getModInfo()
    {
        boolean isAttached = false;
        String firmwareVersion = "";
        String product = "";
        String vendor = "";

        if (mAttachedMod != null)
        {
            isAttached = true;
            firmwareVersion = mAttachedMod.getFirmwareVersion();
            product = mAttachedMod.getProductString();
            vendor = mAttachedMod.getVendorString();
        }

        return new ModInformation(isAttached, firmwareVersion, product, vendor);
    }

    public static class ModInformation
    {
        private static boolean isAttached = false;
        private static String firmwareVersion = "";
        private static String product = "";
        private static String vendor = "";

        public ModInformation(boolean isAttached, String firmwareVersion, String product, String vendor)
        {
            this.isAttached = isAttached;
            this.firmwareVersion = firmwareVersion;
            this.product = product;
            this.vendor = vendor;
        }

        public static boolean isAttached()
        {
            return isAttached;
        }

        public static String getFirmwareVersion()
        {
            return firmwareVersion;
        }

        public static String getProduct()
        {

            return product;
        }

        public static String getVendor()
        {
            return vendor;
        }
    }

    private void updateModsBattery()
    {
        if (mModBatteryLevel < 0)
        {
            dbgLog(TAG, "No Mod battery level info", 'i');
            mBatteryView.setVisibility(View.GONE);
        }
        else
        {
            mBatteryView.setVisibility(View.VISIBLE);
            mBatteryLevelTextView.setText(formatPercentage(mModBatteryLevel));
            dbgLog(TAG, "Mod battery level = " + mModBatteryLevel, 'i');
        }
    }

    private Protocol getPrimaryProtocol(ModDevice device)
    {
        List<Protocol> protocols = device.getDeclaredProtocols();
        if (protocols == null || protocols.size() == 0)
        {
            dbgLog(TAG, "Mod declared protocols empty", 'e');
            return null;
        }

        dbgLog(TAG, "Mod declared protocols = " + protocols.toString(), 'i');

        if (protocols.contains(Protocol.MODS_DISPLAY))
        { // Projector
            return Protocol.MODS_DISPLAY;
        }
        else if (protocols.contains(Protocol.DISPLAY))
        { // Display
            return Protocol.DISPLAY;
        }
        else if (protocols.contains(Protocol.CAMERA))
        { // Red Carpet
            return Protocol.CAMERA;
        }
        else if (protocols.contains(Protocol.CAMERA_EXT))
        { // Red Carpet
            return Protocol.CAMERA_EXT;
        }
        else if (protocols.contains(Protocol.BATTERY))
        { // Battery
            return Protocol.BATTERY;
        }
        else
        {
            return null;
        }
    }

    public static String formatPercentage(int percentage)
    {
        return NumberFormat.getPercentInstance().format(
                ((double) percentage) / 100.0);
    }

    public void onStartTest(View view)
    {
        Intent intent = new Intent();

        if ((mPrimaryProtocol == Protocol.MODS_DISPLAY) && (!mAttachedMod.getProductString().equalsIgnoreCase("HDK-FACTORY")))
        { // Projector
            intent.setClass(this, ProjectorActivity.class);
        }
        else if (mPrimaryProtocol == Protocol.DISPLAY)
        { // Display
        }
        else if (mPrimaryProtocol == Protocol.CAMERA
                || mPrimaryProtocol == Protocol.CAMERA_EXT)
        {
            // Camera
            intent.setClass(this, CameraActivity.class);
        }
        else if ((mPrimaryProtocol == Protocol.BATTERY) && (!mAttachedMod.getProductString().toUpperCase().contains("JBL")))
        {
            // Battery
            intent.setClass(this, BatteryActivity.class);
        }
        else if (mAttachedMod.getProductString().toUpperCase().contains("JBL"))
        {
            // JBL
            intent.setClass(this, AudioPlayMediaFile.class);
        }
        else if (mAttachedMod.getProductString().equalsIgnoreCase("HDK-FACTORY"))
        {
            // HDK
            intent.setClass(this, HdkTest.class);
        }
        else
        {
            return;
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if (wasActivityStartedByCommServer() == true)
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this,
                        getString(com.motorola.motocit.R.string.mode_notice),
                        Toast.LENGTH_SHORT).show();

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
        DoModUpdate("", false);

        if (strRxCmd.equalsIgnoreCase("GET_CONNECTED_MODS"))
        {
            try
            {
                lockModConnectedList.lock();
                List<String> strDataList = new ArrayList<String>();

                if (mModConnectedList == null || mModConnectedList.isEmpty())
                {
                    strDataList.add(String.format("NUMBER_OF_MODS=0"));
                }
                else
                {
                    int modNumber = 0;
                    strDataList.add(String.format("NUMBER_OF_MODS=" + String.valueOf(mModConnectedList.size())));
                    for (ModDevice attachedMod : mModConnectedList)
                    {
                        strDataList.add(String.format("VERSION_MAJOR_MOD_" + modNumber + "=" + Byte.toString(attachedMod.getVersionMajor())));
                        strDataList.add(String.format("VERSION_MINOR_MOD_" + modNumber + "=" + Byte.toString(attachedMod.getVersionMinor())));
                        strDataList.add(String.format("VENDOR_ID_MOD_" + modNumber + "=" + String.valueOf(attachedMod.getVendorId())));
                        strDataList.add(String.format("PRODUCT_ID_MOD_" + modNumber + "=" + String.valueOf(attachedMod.getProductId())));
                        strDataList.add(String.format("VERSION_STRING_MOD_" + modNumber + "=" + attachedMod.getVendorString()));
                        strDataList.add(String.format("PRODUCT_STRING_MOD_" + modNumber + "=" + attachedMod.getProductString()));
                        strDataList.add(String.format("UNIQUE_ID_MOD_" + modNumber + "=" + attachedMod.getUniqueId().toString()));
                        strDataList.add(String.format("FIRMWARE_VERSION_MOD_" + modNumber + "=" + attachedMod.getFirmwareVersion()));
                        strDataList.add(String.format("FIRMWARE_TYPE_MOD_" + modNumber + "=" + attachedMod.getFirmwareType()));
                        strDataList.add(String.format("PACKAGE_MOD_" + modNumber + "=" + attachedMod.getPackage()));
                        strDataList.add(String.format("MIN_SDK_MOD_" + modNumber + "=" + attachedMod.getMinSdk()));
                        strDataList.add(String.format("ID_MOD_" + modNumber + "=" + Short.toString(attachedMod.getId())));
                        strDataList.add(String.format("CAPABILITY_LEVEL_MOD_" + modNumber + "=" + String.valueOf(attachedMod.getCapabilityLevel())));
                        strDataList.add(String.format("CAPABILITY_REASON_MOD_" + modNumber + "=" + String.valueOf(attachedMod.getCapabilityReason())));
                        strDataList.add(String.format("CAPABILITY_VENDOR_MOD_" + modNumber + "=" + String.valueOf(attachedMod.getCapabilityVendor())));
                        strDataList.add(String.format("SYS_PATH_MOD_" + modNumber + "=" + attachedMod.getSysPath()));

                        try
                        {
                            strDataList.add(String.format("SETUP_COMPLETE_MOD_" + modNumber + "=" + String.valueOf(mModManager.initSetupComplete(attachedMod))));
                        }
                        catch (Exception e)
                        {
                            strDataList.add(String.format("SETUP_COMPLETE_MOD_" + modNumber + "=FALSE"));
                        }

                        switch (mModManager.isModServicesAvailable(getApplicationContext()))
                        {
                            case ModManager.SUCCESS:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SUCCESS"));
                                break;
                            case ModManager.SERVICE_MISSING:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SERVICE_MISSING"));
                                break;
                            case ModManager.SERVICE_UPDATING:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SERVICE_UPDATING "));
                                break;
                            case ModManager.SERVICE_VERSION_UPDATE_REQUIRED:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SERVICE_VERSION_UPDATE_REQUIRED "));
                                break;
                            case ModManager.SERVICE_DISABLED:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SERVICE_DISABLED "));
                                break;
                            default:
                                strDataList.add(String.format("IS_SERVICES_AVAILABLE_MOD_" + modNumber + "=" + "SERVICE_INVALID"));
                                break;
                        }

                        try
                        {
                            List<ModConnection> modConnections = mModManager.getConnectionsByDevice(attachedMod);

                            if (modConnections == null || modConnections.size() == 0)
                            {
                                strDataList.add(String.format("NUMBER_CONNECTIONS_MOD_" + modNumber + "=0"));
                            }
                            else
                            {
                                int modConnectionsNumber = 0;
                                strDataList.add(String.format("NUMBER_CONNECTIONS_MOD_" + modNumber + "=" + modConnections.size()));

                                for (ModConnection modConnection : modConnections)
                                {
                                    strDataList.add(String.format("PROTOCOL_CONNECTION_" + modConnectionsNumber + "_MOD_" + modNumber + "=" + modConnection.getProtocol()));
                                    strDataList.add(String.format("STATE_CONNECTION_" + modConnectionsNumber + "_MOD_" + modNumber + "=" + modConnection.getState()));
                                    strDataList.add(String.format("INTERFACE_CONNECTION_" + modConnectionsNumber + "_MOD_" + modNumber + "=" + modConnection.getInterface()));
                                    strDataList.add(String.format("SYS_PATH_CONNECTION_" + modConnectionsNumber + "_MOD_" + modNumber + "=" + modConnection.getSysPath()));
                                    modConnectionsNumber++;
                                }

                            }
                        }
                        catch (Exception e)
                        {
                            dbgLog(TAG, "getConnectionsByDevice threw exception" + e.toString(), 'i');
                            strDataList.add(String.format("NUMBER_CONNECTIONS_MOD_" + modNumber + "=0"));
                        }

                        List<ModDevice.Subclass> declaredClasses = attachedMod.getDeclaredClass();
                        if (declaredClasses == null || declaredClasses.size() == 0)
                        {
                            strDataList.add(String.format("NUMBER_DECLARED_CLASSES_MOD_" + modNumber + "=0"));
                        }
                        else
                        {
                            int classNumber = 0;
                            strDataList.add(String.format("NUMBER_DECLARED_CLASSES_MOD_" + modNumber + "=" + declaredClasses.size()));
                            for (ModDevice.Subclass attachedModClass : declaredClasses)
                            {
                                strDataList.add(String.format("DECLARED_CLASS_" + classNumber + "_MOD_" + modNumber + "=" + attachedModClass));
                                classNumber++;
                            }
                        }

                        List<Protocol> protocols = attachedMod.getDeclaredProtocols();
                        if (protocols == null || protocols.size() == 0)
                        {
                            strDataList.add(String.format("NUMBER_DECLARED_PROTOCOLS_MOD_" + modNumber + "=0"));
                        }
                        else
                        {
                            int protocolNumber = 0;
                            strDataList.add(String.format("NUMBER_DECLARED_PROTOCOLS_MOD_" + modNumber + "=" + protocols.size()));
                            for (Protocol attachedModProtocol : protocols)
                            {
                                strDataList.add(String.format("DECLARED_PROTOCOL_" + protocolNumber + "_MOD_" + modNumber + "=" + attachedModProtocol));
                                protocolNumber++;
                            }
                        }

                        List<ModDevice.InterfaceInfo> declaredInterfaces = attachedMod.getDeclaredInterfaces();
                        if (declaredInterfaces == null || declaredInterfaces.size() == 0)
                        {
                            strDataList.add(String.format("NUMBER_DECLARED_INTERFACES_MOD_" + modNumber + "=0"));
                        }
                        else
                        {
                            int interfaceNumber = 0;
                            strDataList.add(String.format("NUMBER_DECLARED_INTERFACES_MOD_" + modNumber + "=" + declaredInterfaces.size()));
                            for (ModDevice.InterfaceInfo attachedModInterface : declaredInterfaces)
                            {
                                strDataList.add(String.format("DECLARED_INTERFACES_" + interfaceNumber + "_MOD_" + modNumber + "=" + attachedModInterface));
                                interfaceNumber++;
                            }
                        }

                        modNumber++;
                    }
                }

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_MOD_BATTERY_INFO"))
        {
            Intent batteryIntent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            DoBatteryUpdate(batteryIntent, false);

            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("MOD_BATTERY_STATUS=" + mModBatteryStatus));
            strDataList.add(String.format("MOD_BATTERY_LEVEL=" + String.valueOf(mModBatteryLevel)));
            strDataList.add(String.format("MOD_BATTERY_PLUGGED_RAW=" + mBatteryPluggedRaw));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_MOD_BATTERY_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();

                if (mModConnectedList == null || mModConnectedList.isEmpty())
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("No Mod Connected");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                if (mModConnectedList.size() == 1)
                {
                    ModBattery modBattery = null;
                    if (mModManager != null)
                    {
                        dbgLog(TAG, "Trying to get ModBattery Class", 'i');
                        modBattery = (ModBattery) mModManager.getClassManager(Protocol.BATTERY);
                    }

                    if (modBattery != null)
                    {
                        List<String> strDataList = new ArrayList<String>();

                        strDataList.add(String.format("BATTERY_ATTACH_START_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_ATTACH_START_SOC)));
                        strDataList.add(String.format("BATTERY_ATTACH_STOP_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_ATTACH_STOP_SOC)));
                        strDataList.add(String.format("BATTERY_RECHARGE_START_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_RECHARGE_START_SOC)));
                        strDataList.add(String.format("BATTERY_RECHARGE_STOP_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_RECHARGE_STOP_SOC)));
                        strDataList.add(String.format("BATTERY_LOW_START_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_LOW_START_SOC)));
                        strDataList.add(String.format("BATTERY_LOW_STOP_SOC=" + modBattery.getIntProperty(ModBattery.BATTERY_LOW_STOP_SOC)));
                        strDataList.add(String.format("BATTERY_USAGE_TYPE=" + modBattery.getIntProperty(ModBattery.BATTERY_USAGE_TYPE)));
                        strDataList.add(String.format("BATTERY_ON_SW=" + modBattery.getIntProperty(ModBattery.BATTERY_ON_SW)));

                        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                        sendInfoPacketToCommServer(infoPacket);

                        // Generate an exception to send data back to CommServer
                        List<String> strReturnDataList = new ArrayList<String>();
                        throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("Failed to get Mod Battery Class");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("GET_MOD_BATTERY_PROPERTIES Currently only supports a single connected Mod.");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_MOD_BATTERY_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();
                if (strRxCmdDataList.size() > 0)
                {
                    List<String> strReturnDataList = new ArrayList<String>();

                    if (mModConnectedList == null || mModConnectedList.isEmpty())
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("No Mod Connected");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    if (mModConnectedList.size() == 1)
                    {
                        ModBattery modBattery = null;
                        if (mModManager != null)
                        {
                            dbgLog(TAG, "Trying to get ModBattery Class", 'i');
                            modBattery = (ModBattery) mModManager.getClassManager(Protocol.BATTERY);
                        }

                        if (modBattery != null)
                        {
                            for (String keyValuePair : strRxCmdDataList)
                            {
                                String splitResult[] = splitKeyValuePair(keyValuePair);
                                String key = splitResult[0];
                                String value = splitResult[1];
                                int intValue = Integer.parseInt(value);

                                if (key.equalsIgnoreCase("BATTERY_ATTACH_START_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_ATTACH_START_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_ATTACH_STOP_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_ATTACH_STOP_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_RECHARGE_START_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_RECHARGE_START_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_RECHARGE_STOP_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_RECHARGE_START_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_LOW_START_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_LOW_START_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_LOW_STOP_SOC"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_LOW_STOP_SOC, intValue);
                                }
                                else if (key.equalsIgnoreCase("BATTERY_ON_SW"))
                                {
                                    modBattery.setIntProperty(ModBattery.BATTERY_ON_SW, intValue);
                                }
                                else
                                {
                                    strReturnDataList.add("UNKNOWN key: " + key);
                                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                                }
                            }
                            // Generate an exception to send data back to CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add("Failed to get Mod Battery Class");
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("GET_MOD_BATTERY_PROPERTIES Currently only supports a single connected Mod.");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_MOD_DISPLAY_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();

                if (mModConnectedList == null || mModConnectedList.isEmpty())
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("No Mod Connected");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                if (mModConnectedList.size() == 1)
                {
                    ModDisplay modDisplay = null;
                    if (mModManager != null)
                    {
                        List<String> strDataList = new ArrayList<String>();

                        dbgLog(TAG, "Trying to get ModDisplay Class", 'i');
                        modDisplay = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                    }

                    if (modDisplay != null)
                    {
                        List<String> strDataList = new ArrayList<String>();

                        switch (modDisplay.getModDisplayState())
                        {
                            case ModDisplay.STATE_ON:
                                strDataList.add(String.format("MOD_DISPLAY_STATE=ON"));
                                break;
                            case ModDisplay.STATE_STANDBY:
                                strDataList.add(String.format("MOD_DISPLAY_STATE=STANDBY"));
                                break;
                            case ModDisplay.STATE_OFF:
                                strDataList.add(String.format("MOD_DISPLAY_STATE=OFF"));
                                break;
                            default:
                                strDataList.add(String.format("MOD_DISPLAY_STATE=UNKNOWN"));
                                break;
                        }

                        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                        sendInfoPacketToCommServer(infoPacket);

                        // Generate an exception to send data back to CommServer
                        List<String> strReturnDataList = new ArrayList<String>();
                        throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("Failed to get Mod Display Class");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("GET_MOD_DISPLAY_PROPERTIES Currently only supports a single connected Mod.");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_MOD_DISPLAY_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();

                if (strRxCmdDataList.size() > 0)
                {
                    List<String> strReturnDataList = new ArrayList<String>();

                    if (mModConnectedList == null || mModConnectedList.isEmpty())
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("No Mod Connected");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    if (mModConnectedList.size() == 1)
                    {
                        ModDisplay modDisplay = null;
                        if (mModManager != null)
                        {
                            List<String> strDataList = new ArrayList<String>();

                            dbgLog(TAG, "Trying to get ModDisplay Class", 'i');
                            modDisplay = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                        }

                        if (modDisplay != null)
                        {
                            for (String keyValuePair : strRxCmdDataList)
                            {
                                String splitResult[] = splitKeyValuePair(keyValuePair);
                                String key = splitResult[0];
                                String value = splitResult[1];

                                if (key.equalsIgnoreCase("MOD_DISPLAY_STATE"))
                                {
                                    if (value.equalsIgnoreCase("ON"))
                                    {
                                        modDisplay.setModDisplayState(ModDisplay.STATE_ON);
                                    }
                                    else if (value.equalsIgnoreCase("STANDBY"))
                                    {
                                        modDisplay.setModDisplayState(ModDisplay.STATE_STANDBY);
                                    }
                                    else if (value.equalsIgnoreCase("OFF"))
                                    {
                                        modDisplay.setModDisplayState(ModDisplay.STATE_OFF);
                                    }
                                    else
                                    {
                                        strReturnDataList.add("UNKNOWN MOD_DISPLAY_STATE: " + value);
                                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                                    }
                                }
                                else
                                {
                                    strReturnDataList.add("UNKNOWN key: " + key);
                                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                                }
                            }
                            // Generate an exception to send data back to CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add("Failed to get Mod Display Class");
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("SET_MOD_DISPLAY_PROPERTIES Currently only supports a single connected Mod.");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_MOD_BACKLIGHT_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();

                if (mModConnectedList == null || mModConnectedList.isEmpty())
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("No Mod Connected");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                if (mModConnectedList.size() == 1)
                {
                    ModBacklight modBacklight = null;
                    if (mModManager != null)
                    {
                        List<String> strDataList = new ArrayList<String>();

                        dbgLog(TAG, "Trying to get ModBacklight Class", 'i');
                        modBacklight = (ModBacklight) mModManager.getClassManager(Protocol.LIGHTS);
                    }

                    if (modBacklight != null)
                    {
                        List<String> strDataList = new ArrayList<String>();

                        strDataList.add(String.format("MOD_BACKLIGHT_BRIGHTNESS=" + modBacklight.getModBacklightBrightness()));

                        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                        sendInfoPacketToCommServer(infoPacket);

                        // Generate an exception to send data back to CommServer
                        List<String> strReturnDataList = new ArrayList<String>();
                        throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("Failed to get Mod Backlight Class");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("GET_MOD_BACKLIGHT_PROPERTIES Currently only supports a single connected Mod.");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_MOD_BACKLIGHT_PROPERTIES"))
        {
            try
            {
                lockModConnectedList.lock();

                if (strRxCmdDataList.size() > 0)
                {
                    List<String> strReturnDataList = new ArrayList<String>();

                    if (mModConnectedList == null || mModConnectedList.isEmpty())
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("No Mod Connected");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    if (mModConnectedList.size() == 1)
                    {
                        ModBacklight modBacklight = null;
                        if (mModManager != null)
                        {
                            List<String> strDataList = new ArrayList<String>();

                            dbgLog(TAG, "Trying to get ModBacklight Class", 'i');
                            modBacklight = (ModBacklight) mModManager.getClassManager(Protocol.LIGHTS);
                        }

                        if (modBacklight != null)
                        {
                            for (String keyValuePair : strRxCmdDataList)
                            {
                                String splitResult[] = splitKeyValuePair(keyValuePair);
                                String key = splitResult[0];
                                String value = splitResult[1];

                                if (key.equalsIgnoreCase("MOD_BACKLIGHT_BRIGHTNESS"))
                                {
                                    int modBacklightBrightness = Integer.parseInt(value);
                                    if (modBacklightBrightness >= 0 && modBacklightBrightness <= 255)
                                    {
                                        modBacklight.setModBacklightBrightness((byte) modBacklightBrightness);
                                    }
                                    else
                                    {
                                        strReturnDataList.add("INVALID MOD_BACKLIGHT_BRIGHTNESS: " + value);
                                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                                    }
                                }
                                else
                                {
                                    strReturnDataList.add("UNKNOWN key: " + key);
                                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                                }
                            }
                            // Generate an exception to send data back to CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add("Failed to get Mod Backlight Class");
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }

                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("SET_MOD_BACKLIGHT_PROPERTIES Currently only supports a single connected Mod.");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SEND_RAW_DATA"))
        {
            try
            {
                lockModConnectedList.lock();

                List<String> strDataList = new ArrayList<String>();
                List<String> strErrMsgList = new ArrayList<String>();

                if (strRxCmdDataList.size() > 0)
                {
                    if (strRxCmdDataList.size() != 2)
                    {
                        strErrMsgList.add("INCORRECT NUMBER OF PARAMETERS");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    if (mModConnectedList == null || mModConnectedList.isEmpty())
                    {
                        strErrMsgList.add("No Mod Connected");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    String rawData = null;
                    String dataFormat = null;

                    for (String keyValuePair : strRxCmdDataList)
                    {
                        String splitResult[] = splitKeyValuePair(keyValuePair);
                        String key = splitResult[0];
                        String value = splitResult[1];

                        if (key.equalsIgnoreCase("RAW_DATA"))
                        {
                            rawData = value;
                        }
                        else if (key.equalsIgnoreCase("DATA_FORMAT"))
                        {
                            dataFormat = value;
                        }
                        else
                        {
                            strErrMsgList.add("UNKNOWN key: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                    }

                    byte payload[] = null;
                    byte message[] = null;

                    int numberOfReceivedBytes = 0;

                    switch (dataFormat.toUpperCase())
                    {
                        case "FACTORY":
                            // Factory Raw Command Format
                            // 4 BYTES PID
                            // 4 BYTES VID
                            // 1 BYTE VERSION
                            // 1 BYTE COMMAND ID
                            // 1 BYTE PAYLOAD SIZE
                            // VARIABLE BYTES PAYLOAD
                            byte VERSION = 0x01;
                            byte[] HEADER = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, VERSION }; // pid,vid,version

                            byte[] bPid = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mModConnectedList.get(0).getProductId()).array();
                            byte[] bVid = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mModConnectedList.get(0).getVendorId()).array();

                            System.arraycopy(bPid, 0, HEADER, 0, bPid.length);
                            System.arraycopy(bVid, 0, HEADER, 4, bVid.length);

                            switch (rawData.toUpperCase())
                            {
                                case "MYDP":
                                    payload = new byte[] { 0x00, 0x01, 0x02 };
                                    message = concat(HEADER, payload);
                                    break;

                                case "DSI":
                                    payload = new byte[] { 0x00, 0x01, 0x01 };
                                    message = concat(HEADER, payload);
                                    break;

                                default:
                                    try
                                    {
                                        if (rawData.length() % 2 > 0)
                                        {
                                            strErrMsgList.add("RAW DATA MUST BE IN BYTES");
                                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                                        }

                                        payload = hexStringToByteArray(rawData);
                                        message = concat(HEADER, payload);
                                    }
                                    catch (Exception e)
                                    {
                                        // NumberFormatException thrown by hexStringToByteArray
                                        strErrMsgList.add("RAW DATA MUST BE HEX FORMAT");
                                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                                    }
                            }
                            break;

                        case "RAW":
                        case "TEMPERATURE_PCARD":
                            try
                            {
                                if (rawData.length() % 2 > 0)
                                {
                                    strErrMsgList.add("RAW DATA MUST BE IN BYTES");
                                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                                }

                                message = hexStringToByteArray(rawData);
                            }
                            catch (Exception e)
                            {
                                // NumberFormatException thrown by hexStringToByteArray
                                strErrMsgList.add("RAW DATA MUST BE HEX FORMAT");
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                            }
                            break;

                        default:
                            strErrMsgList.add("UNKNOWN DATA_FORMAT: " + dataFormat.toUpperCase());
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    String dataString = "";
                    if (message != null)
                    {
                        for (byte b : message)
                        {
                            dataString += String.format("%02x", b);
                        }
                    }

                    strDataList.add(String.format("SENT_RAW_MESSAGE=" + dataString));

                    byte[] responseData = sendRawProtocolData(message);

                    if (responseData == null)
                    {
                        strDataList.add(String.format("RESPONSE_LENGTH=0"));
                    }
                    else
                    {
                        String responseDataString = "";

                        numberOfReceivedBytes = 0;
                        for (byte b : responseData)
                        {
                            numberOfReceivedBytes++;
                            responseDataString += String.format("%02x", b);
                        }

                        strDataList.add(String.format("RESPONSE_LENGTH=" + numberOfReceivedBytes));
                        strDataList.add(String.format("RESPONSE_DATA=" + responseDataString));
                    }

                    switch (dataFormat.toUpperCase())
                    {
                        case "FACTORY":
                            String factoryResponsePID = ""; // 4 bytes
                            String factoryResponseVID = ""; // 4 bytes
                            String factoryResponseVersion = ""; // 1 byte
                            String factoryResponseCommandId = ""; // 1 byte
                            String factoryResponseDataLength = ""; // 1 byte
                            String factoryResponseStatus = ""; // 1 byte
                            String factoryResponseData = ""; // Variable bytes(0-254)
                            numberOfReceivedBytes = 0;

                            for (byte b : responseData)
                            {
                                if (numberOfReceivedBytes <= 3)
                                {
                                    factoryResponsePID += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes <= 7)
                                {
                                    factoryResponseVID += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes <= 8)
                                {
                                    factoryResponseVersion += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes <= 9)
                                {
                                    factoryResponseCommandId += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes <= 10)
                                {
                                    factoryResponseDataLength += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes <= 11)
                                {
                                    factoryResponseStatus += String.format("%02x", b);
                                }
                                else
                                {
                                    factoryResponseData += String.format("%02x", b);
                                }
                                numberOfReceivedBytes++;
                            }

                            strDataList.add(String.format("FACTORY_RESPONSE_PID=" + factoryResponsePID));
                            strDataList.add(String.format("FACTORY_RESPONSE_VID=" + factoryResponseVID));
                            strDataList.add(String.format("FACTORY_RESPONSE_VERSION=" + factoryResponseVersion));
                            strDataList.add(String.format("FACTORY_RESPONSE_COMMAND_ID=" + factoryResponseCommandId));
                            strDataList.add(String.format("FACTORY_RESPONSE_DATA_LENGTH=" + factoryResponseDataLength));
                            if (numberOfReceivedBytes >= 11)
                            {
                                strDataList.add(String.format("FACTORY_RESPONSE_STATUS=" + factoryResponseStatus));
                            }
                            if (numberOfReceivedBytes >= 12)
                            {
                                strDataList.add(String.format("FACTORY_RESPONSE_DATA=" + factoryResponseData));
                            }
                            break;

                        case "TEMPERATURE_PCARD":
                            String temperatureResponseCommandId = ""; // 1 byte
                            String temperatureResponseDataLength = ""; // 1 byte
                            String temperatureResponseData = ""; // Variable bytes (0-254)
                            String temperatureResponseDataLittleEndian = ""; // Variable bytes (0-254)
                            numberOfReceivedBytes = 0;

                            for (byte b : responseData)
                            {
                                if (numberOfReceivedBytes < 1)
                                {
                                    temperatureResponseCommandId += String.format("%02x", b);
                                }
                                else if (numberOfReceivedBytes < 2)
                                {
                                    temperatureResponseDataLength += String.format("%02x", b);
                                }
                                else
                                {
                                    // convert to little endian
                                    temperatureResponseData += String.format("%02x", b);
                                    temperatureResponseDataLittleEndian = String.format("%02x", b) + temperatureResponseDataLittleEndian;
                                }
                                numberOfReceivedBytes++;
                            }

                            strDataList.add(String.format("TEMPERATURE_PCARD_RESPONSE_COMMAND_ID=" + temperatureResponseCommandId));
                            strDataList.add(String.format("TEMPERATURE_PCARD_RESPONSE_DATA_LENGTH=" + temperatureResponseDataLength));

                            if (numberOfReceivedBytes > 2)
                            {
                                strDataList.add(String.format("TEMPERATURE_PCARD_RESPONSE_DATA=" + temperatureResponseData));
                                strDataList.add(String.format("TEMPERATURE_PCARD_RESPONSE_DATA_LITTLE_ENDIAN=" + temperatureResponseDataLittleEndian));
                                strDataList.add(String.format("TEMPERATURE_PCARD_DATA=" + Integer.parseInt(temperatureResponseDataLittleEndian.trim(), 16)));
                            }
                            break;

                        default:
                            // Default has normal raw data responses only
                    }

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                    sendInfoPacketToCommServer(infoPacket);

                    // Generate an exception to send data back to CommServer
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back to CommServer
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            finally
            {
                lockModConnectedList.unlock();
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
            // Generate an exception to send FAIL result and mesg back to CommServer
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
        strHelpList.add("This function will control Moto Mods");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  GET_CONNECTED_MODS - Gets information on any connected Mods");
        strHelpList.add("    NUMBER_OF_MODS - Number of Mods Connected");
        strHelpList.add("    VERSION_MAJOR_MOD_XX - Version Major for Mod# XX");
        strHelpList.add("    VERSION_MINOR_MOD_XX - Version Minor for Mod# XX");
        strHelpList.add("    VENDOR_ID_MOD_XX - Version ID for Mod# XX");
        strHelpList.add("    PRODUCT_ID_MOD_XX - Product ID for Mod# XX");
        strHelpList.add("    VERSION_STRING_MOD_XX - Version String for Mod# XX");
        strHelpList.add("    PRODUCT_STRING_MOD_XX - Product String for Mod# XX");
        strHelpList.add("    UNIQUE_ID_MOD_XX - Unique ID for Mod# XX");
        strHelpList.add("    FIRMWARE_VERSION_MOD_XX - Firmware Version for Mod# XX");
        strHelpList.add("    FIRMWARE_TYPE_MOD_XX - Firmware Type for Mod# XX");
        strHelpList.add("    PACKAGE_MOD_XX - Package for Mod# XX");
        strHelpList.add("    MIN_SDK_MOD_XX - Min SDK for Mod# XX");
        strHelpList.add("    ID_MOD_XX - ID for Mod# XX");
        strHelpList.add("    CAPABILITY_LEVEL_MOD_XX - Capability Level for Mod# XX");
        strHelpList.add("    CAPABILITY_REASON_MOD_XX - Capability Reason for Mod# XX");
        strHelpList.add("    CAPABILITY_VENDOR_MOD_XX - Capability Vendor for Mod# XX");
        strHelpList.add("    SYS_PATH_MOD_XX - Sys Path for Mod# XX");
        strHelpList.add("    SETUP_COMPLETE_MOD_XX - Is Setup Complete for Mod# XX");
        strHelpList.add("    IS_SERVICES_AVAILABLE_MOD_XX - Is Setup Available for Mod# XX, SUCCESS, SERVICE_MISSING, SERVICE_UPDATING, SERVICE_VERSION_UPDATE_REQUIRED, SERVICE_DISABLED, or SERVICE_INVALID");
        strHelpList.add("    NUMBER_CONNECTIONS_MOD_XX - Number of connections for Mod# XX");
        strHelpList.add("    PROTOCOL_CONNECTION_YY_MOD_XX - Protocol for Connection YY on Mod# XX");
        strHelpList.add("    STATE_CONNECTION_YY_MOD_XX - State for Connection YY on Mod# XX");
        strHelpList.add("    INTERFACE_CONNECTION_YY_MOD_XX - State for Connection YY on Mod# XX");
        strHelpList.add("    SYS_PATH_CONNECTION_YY_MOD_XX - State for Connection YY on Mod# XX");
        strHelpList.add("    NUMBER_DECLARED_CLASSES_MOD_XX - Number of Declared Classes for Mod# XX");
        strHelpList.add("    DECLARED_CLASS_YY_MOD_XX - Declared Class Name for Class YY on Mod# XX");
        strHelpList.add("    NUMBER_DECLARED_PROTOCOLS_MOD_XX - Number of Declared Protocols for Mod# XX");
        strHelpList.add("    DECLARED_PROTOCOL_YY_MOD_XX - Declared Protocol Name for Protocol YY on Mod# XX");
        strHelpList.add("    NUMBER_DECLARED_INTERFACES_MOD_XX - Number of Declared Interfaces for Mod# XX");
        strHelpList.add("    DECLARED_INTERFACES_YY_MOD_XX - Declared Interface Name for Interface YY on Mod# XX");
        strHelpList.add("  ");
        strHelpList.add("  GET_MOD_BATTERY_INFO - Gets battery information on any connected Mods");
        strHelpList.add("    MOD_BATTERY_STATUS - Status of Mod Battery");
        strHelpList.add("    MOD_BATTERY_LEVEL - Level of Mod Battery");
        strHelpList.add("    MOD_BATTERY_PLUGGED_RAW - Battery Plugged Raw data of Mod Battery");
        strHelpList.add("  ");
        strHelpList.add("  GET_MOD_BATTERY_PROPERTIES - Gets battery properties on any connected Mods");
        strHelpList.add("    BATTERY_ATTACH_START_SOC - Value for battery attach start soc");
        strHelpList.add("    BATTERY_ATTACH_STOP_SOC - Value for battery attach stop soc");
        strHelpList.add("    BATTERY_RECHARGE_START_SOC - Value for battery recharge start soc");
        strHelpList.add("    BATTERY_RECHARGE_STOP_SOC - Value for battery recharge stop soc");
        strHelpList.add("    BATTERY_LOW_START_SOC - Value for battery low start soc");
        strHelpList.add("    BATTERY_LOW_STOP_SOC - Value for battery low stop soc");
        strHelpList.add("    BATTERY_USAGE_TYPE - Value for battery usage state");
        strHelpList.add("    BATTERY_ON_SW - Value for battery on sw");
        strHelpList.add("  SET_MOD_BATTERY_PROPERTIES - Sets battery properties on any connected Mods");
        strHelpList.add("    BATTERY_ATTACH_START_SOC - Value for battery attach start soc");
        strHelpList.add("    BATTERY_ATTACH_STOP_SOC - Value for battery attach stop soc");
        strHelpList.add("    BATTERY_RECHARGE_START_SOC - Value for battery recharge start soc");
        strHelpList.add("    BATTERY_RECHARGE_STOP_SOC - Value for battery recharge stop soc");
        strHelpList.add("    BATTERY_LOW_START_SOC - Value for battery low start soc");
        strHelpList.add("    BATTERY_LOW_STOP_SOC - Value for battery low stop soc");
        strHelpList.add("    BATTERY_ON_SW - Value for battery on sw");
        strHelpList.add("  ");
        strHelpList.add("  GET_MOD_DISPLAY_PROPERTIES - Gets display state of any connected Mods");
        strHelpList.add("    MOD_DISPLAY_STATE - Status of Mod Display, ON, STANDBY, OFF");
        strHelpList.add("  ");
        strHelpList.add("  SET_MOD_DISPLAY_PROPERTIES - Sets display state of any connected Mods");
        strHelpList.add("    MOD_DISPLAY_STATE - Status of Mod Display, ON, STANDBY, OFF");
        strHelpList.add("  ");
        strHelpList.add("  GET_MOD_BACKLIGHT_PROPERTIES - Gets backlight properties of any connected Mods");
        strHelpList.add("    MOD_BACKLIGHT_BRIGHTNESS - Value of backlight brightness");
        strHelpList.add("  ");
        strHelpList.add("  SET_MOD_BACKLIGHT_PROPERTIES - Sets backlight properties of any connected Mods");
        strHelpList.add("    MOD_BACKLIGHT_BRIGHTNESS - Value of backlight brightness");
        strHelpList.add("  ");
        strHelpList.add("  SEND_RAW_DATA - Sets backlight properties of any connected Mods");
        strHelpList.add("    RAW_DATA - Raw Data to send to MOD. FACTORY DATA_FORMAT supports MYDP (switch to MyDP),");
        strHelpList.add("               DSI (switch to DSI), and HEX Data. TEMPERATURE_PCARD, and RAW supports HEX data only");
        strHelpList.add("    DATA_FORMAT - Format of Raw Data and expected Response. FACTORY (Supports HDK-Factory,");
        strHelpList.add("                  TEMPERATURE_PCARD (Supports HDK-Temperature), RAW (Hex Data only)");
        strHelpList.add("    SEND_RAW_DATA RESPONSES");
        strHelpList.add("        SENT_RAW_MESSAGE - RAW Message sent to MOD");
        strHelpList.add("        RESPONSE_LENGTH - Length of Response");
        strHelpList.add("        RESPONSE_DATA - Raw Data of Response");
        strHelpList.add("        FACTORY_RESPONSE_PID - PID for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_VID - VID for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_VERSION - Version for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_COMMAND_ID - ID of command for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_DATA_LENGTH - Data length of response data for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_STATUS - Status for FACTORY DATA_FORMAT Response");
        strHelpList.add("        FACTORY_RESPONSE_DATA - Response Data for FACTORY DATA_FORMAT Response");
        strHelpList.add("        TEMPERATURE_PCARD_RESPONSE_COMMAND_ID - Command ID for TEMPERATURE_PCARD DATA_FORMAT Response");
        strHelpList.add("        TEMPERATURE_PCARD_RESPONSE_DATA_LENGTH - Data length for TEMPERATURE_PCARD DATA_FORMAT Response");
        strHelpList.add("        TEMPERATURE_PCARD_RESPONSE_DATA - Data for TEMPERATURE_PCARD DATA_FORMAT Response");
        strHelpList.add("        TEMPERATURE_PCARD_RESPONSE_DATA_LITTLE_ENDIAN - Data in Little Endian format for " +
                "TEMPERATURE_PCARD DATA_FORMAT Response");
        strHelpList.add("        TEMPERATURE_PCARD_DATA - Data in integer format for TEMPERATURE_PCARD DATA_FORMAT Response");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    protected boolean onSwipeRight()
    {
        return true;
    }

    @Override
    protected boolean onSwipeLeft()
    {
        return true;
    }

    @Override
    protected boolean onSwipeDown()
    {
        return true;
    }

    @Override
    protected boolean onSwipeUp()
    {
        return true;
    }

    private byte[] sendRawProtocolData(byte[] rawData)
    {
        byte[] responseData = null;

        List<ModInterfaceDelegation> modInterfaceDelegationList = null;

        if (mModManager != null)
        {
            dbgLog(TAG, "Trying to get Mod Interface Delegation List", 'i');
            try
            {
                modInterfaceDelegationList = mModManager.getModInterfaceDelegationsByProtocol(mModConnectedList.get(0), Protocol.RAW);
            }
            catch (Exception e)
            {
                return responseData;
            }
        }

        if (getApplicationContext().checkSelfPermission("com.motorola.mod.permission.RAW_PROTOCOL") != 0)
        {
            this.requestPermissions(new String[] { ModManager.PERMISSION_USE_RAW_PROTOCOL }, 1);
        }

        if (modInterfaceDelegationList != null)
        {
            if (modInterfaceDelegationList.size() >= 1)
            {
                ModInterfaceDelegation modInterfaceDelegation = modInterfaceDelegationList.get(0);

                ParcelFileDescriptor pFD = null;
                try
                {
                    pFD = mModManager.openModInterface(modInterfaceDelegation, ParcelFileDescriptor.MODE_READ_WRITE);
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Error when opening mod interface, " + e.getMessage(), 'e');
                    return responseData;
                }

                if (pFD != null)
                {
                    FileDescriptor fd = null;
                    FileInputStream inputStream = null;
                    FileOutputStream outputStream = null;

                    try
                    {
                        fd = pFD.getFileDescriptor();

                        outputStream = new FileOutputStream(fd);
                        inputStream = new FileInputStream(fd);

                        outputStream.write(rawData);

                        StructPollfd[] pollfds = new StructPollfd[1];
                        StructPollfd readRawFd = new StructPollfd();
                        pollfds[0] = readRawFd;
                        readRawFd.fd = pFD.getFileDescriptor();
                        readRawFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);

                        dbgLog(TAG, "Wait for poll", 'i');
                        int ret = Os.poll(pollfds, -1);
                        dbgLog(TAG, "Poll finished " + ret + " rawEvents " + readRawFd.revents, 'i');

                        int MAX_BYTES = 2048;
                        byte[] readBuffer = new byte[MAX_BYTES];
                        int readLength = inputStream.read(readBuffer, 0, MAX_BYTES);

                        responseData = new byte[readLength];

                        for (int byteNumber = 0; byteNumber < readLength; byteNumber++)
                        {
                            responseData[byteNumber] = readBuffer[byteNumber];
                        }

                        // Clear activity from Mod Permission list so permission
                        // is not revoked on attach
                        ClearModPermissionList();
                    }
                    catch (Exception e)
                    {
                        dbgLog(TAG, "Error when opening mod interface, " + e.getMessage(), 'e');
                        return responseData;
                    }
                    finally
                    {
                        if (outputStream != null)
                        {
                            try
                            {
                                outputStream.close();
                            }
                            catch (Exception e)
                            {
                                // Do nothing
                            }
                        }

                        if (inputStream != null)
                        {
                            try
                            {
                                inputStream.close();
                            }
                            catch (Exception e)
                            {
                                // Do nothing
                            }
                        }

                        if (pFD != null)
                        {
                            try
                            {
                                pFD.close();
                                pFD = null;
                            }
                            catch (Exception e)
                            {
                                // Do nothing
                            }
                        }
                    }
                }
                else
                {
                    return responseData;
                }
            }
        }
        else
        {
            return responseData;
        }

        return responseData;
    }

    private byte[] hexStringToByteArray(String hexString) throws NumberFormatException
    {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            try
            {
                // Verify Data is in Hex format
                Long.parseLong(Character.toString(hexString.charAt(i)), 16);
            }
            catch (NumberFormatException nEx)
            {
                throw nEx;
            }
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    private byte[] concat(byte[] a, byte[] b)
    {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

}
