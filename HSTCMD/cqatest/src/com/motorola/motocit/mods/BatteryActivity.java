/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.mods;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.mod.ModManager;

import java.util.List;

import com.motorola.mod.IModManager;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModDisplay;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModProtocol.Protocol;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class BatteryActivity extends MotoMods
{

    private ModManager mModManager;

    private Handler mHandlerBattery;
    private TextView mBatteryLevelTextView;
    private TextView mBatteryPresentTextView;
    private TextView mBatteryStatusTextView;

    private String mBatteryModAttachStatus = "";

    private BroadcastReceiver mModBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            dbgLog(TAG, "Receive broadcaset intent " + action, 'i');

            if (ModManager.ACTION_MOD_ATTACH.equals(action))
            {
                dbgLog(TAG, "Mod attached", 'i');
                mBatteryModAttachStatus = "Mod attached";
            }
            if (ModManager.ACTION_MOD_DETACH.equals(action))
            {
                dbgLog(TAG, "Mod detached, going to update battery data", 'i');
                mBatteryModAttachStatus = "Mod detached";
                mHandlerBattery.postDelayed(new Runnable() {
                    @Override
                    public void run()
                    {
                        updateModsBattery();
                    }
                }, 500);
            }

            updateModsBattery();
        }
    };

    @Override
    public void onCreate(Bundle bundle)
    {
        TAG = "MotoMods_Battery";
        super.onCreate(bundle);

        mModManager = MotoMods.getModManager();
        dbgLog(TAG, "mModManager=" + mModManager, 'i');

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.batterymodinfo);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // Set initial battery level text
        mBatteryLevelTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_level);
        mBatteryLevelTextView.setTextColor(Color.WHITE);
        mBatteryLevelTextView.setText("Battery Level: NA");

        // Set initial battery charging status text
        mBatteryStatusTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_status);
        mBatteryStatusTextView.setTextColor(Color.WHITE);
        mBatteryStatusTextView.setText("Battery Status: NA");

        // Set initial battery mod status text
        mBatteryPresentTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_present);
        mBatteryPresentTextView.setTextColor(Color.WHITE);
        mBatteryPresentTextView.setText("Mod Status: " + mBatteryModAttachStatus);
    }

    @Override
    public void onStart()
    {
        super.onStart();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        dbgLog(TAG, "BatteryActivity- register receiver", 'i');
        registerReceiver(mModBroadcastReceiver, filter);
        mHandlerBattery = new Handler();

        if (ModInformation.isAttached())
        {
            mBatteryModAttachStatus = "Mod attached";
        }
        else
        {
            mBatteryModAttachStatus = "Mod detached";
        }

        updateModsBattery();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        dbgLog(TAG, "BatteryActivity - onPause, unregister receiver", 'i');

        this.unregisterReceiver(this.mModBroadcastReceiver);

        if (mHandlerBattery != null)
        {
            mHandlerBattery.removeCallbacksAndMessages(null);
            mHandlerBattery = null;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    private void updateModsBattery()
    {
        Intent intent = registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        int level = intent.getIntExtra(BatteryManager.EXTRA_MOD_LEVEL, -1);
        if (level < 0)
        {
            dbgLog(TAG, "No Mod battery level info", 'i');
            mBatteryLevelTextView.setText("Battery Level: unknown");
        }
        else
        {
            dbgLog(TAG, "Mod battery level = " + level, 'i');
            mBatteryLevelTextView.setText("Battery Level: " + level + "%");
        }

        String modBatteryStatus = getModBatteryStatus(intent);

        dbgLog(TAG, "battery status:" + modBatteryStatus, 'i');

        mBatteryStatusTextView.setText("Battery Status: " + modBatteryStatus);

        mBatteryPresentTextView.setText("Mod Status: " + mBatteryModAttachStatus + "\n\n"
                + "Firmware version: " + ModInformation.getFirmwareVersion() + "\n"
                + "Product: " + ModInformation.getProduct() + "\n"
                + "Vendor: " + ModInformation.getVendor());
    }

    private static String getModBatteryStatus(Intent batteryChangedIntent)
    {
        final Intent intent = batteryChangedIntent;

        int plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int status = intent.getIntExtra("mod_status", BatteryManager.BATTERY_STATUS_UNKNOWN);
        return getBatteryStatusString(plugType, status);
    }

    private static String getBatteryStatusString(int plugType, int status)
    {
        String statusString;
        if (status == BatteryManager.BATTERY_STATUS_CHARGING)
        {
            if (plugType == BatteryManager.BATTERY_PLUGGED_AC)
            {
                statusString = "Charging on AC";
            }
            else if (plugType == BatteryManager.BATTERY_PLUGGED_USB)
            {
                statusString = "Charging over USB";
            }
            else if (plugType == BatteryManager.BATTERY_PLUGGED_WIRELESS)
            {
                statusString = "Charging wirelessly";
            }
            else
            {
                statusString = "Charging";
            }
        }
        else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING)
        {
            statusString = "Not charging";
        }
        else if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        {
            statusString = "Not charging";
        }
        else if (status == BatteryManager.BATTERY_STATUS_FULL)
        {
            statusString = "Full";
        }
        else
        {
            statusString = "Unknown";
        }

        return statusString;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
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
    protected void handleTestSpecificActions() throws CmdFailException,
            CmdPassException
    {}

    @Override
    protected void printHelp()
    {}

    @Override
    protected boolean onSwipeRight()
    {
        return false;
    }

    @Override
    protected boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    protected boolean onSwipeDown()
    {
        return false;
    }

    @Override
    protected boolean onSwipeUp()
    {
        return false;
    }
}
