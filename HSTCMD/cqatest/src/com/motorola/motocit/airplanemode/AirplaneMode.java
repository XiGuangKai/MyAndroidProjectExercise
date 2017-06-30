/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.airplanemode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AirplaneMode extends Test_Base
{
    private boolean mAirplaneModeOriginal;

    private TextView mAirplanemodeText;
    private CheckBox mAirplanemodeCheckBox;

    private static Class<?> mSettingsClass = null;
    private static Method mSettingsGetInt = null;
    private static Method mSettingsPutInt = null;
    private static final String GLOBAL_SETTINGS = "android.provider.Settings$Global";
    private static final String SYSTEM_SETTINGS = "android.provider.Settings$System";
    private static final String AIRPLANE_MODE_ON = "airplane_mode_on";

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (isAirplaneModeOn())
            {
                mAirplanemodeCheckBox.setChecked(true);
                mAirplanemodeText.setText("Airplane mode is ON");
            }
            else
            {
                mAirplanemodeCheckBox.setChecked(false);
                mAirplanemodeText.setText("Airplane mode is OFF");
            }

            mAirplanemodeCheckBox.setEnabled(true);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Airplane_Mode";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.airplanemode);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        //Initialize settings provider
        initSettingsProvider();

        //Record original airplane mode status so that can restore it when exit
        mAirplaneModeOriginal = isAirplaneModeOn();

        mAirplanemodeText = (TextView) findViewById(com.motorola.motocit.R.id.airplaneModeStatusTextView);
        mAirplanemodeCheckBox = (CheckBox) findViewById(com.motorola.motocit.R.id.airplaneModeCheckBox);

        mAirplanemodeCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked)
                {
                    mAirplanemodeText.setText("Turning on airplane mode...");

                    //Turn on airplane mode
                    toggleAirplaneMode(true);
                }
                else
                {
                    mAirplanemodeText.setText("Turning off airplane mode...");

                    //Turn off airplane mode
                    toggleAirplaneMode(false);
                }

                mAirplanemodeCheckBox.setEnabled(false);
            }
        });
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

            contentRecord("testresult.txt", "Airplane Mode Test:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

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
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Airplane Mode Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

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

    @Override
    protected void onResume()
    {
        super.onResume();

        final TextView mAirplanemodeText = (TextView) findViewById(com.motorola.motocit.R.id.airplaneModeStatusTextView);
        final CheckBox mAirplanemodeCheckBox = (CheckBox) findViewById(com.motorola.motocit.R.id.airplaneModeCheckBox);

        if (isAirplaneModeOn())
        {
            mAirplanemodeCheckBox.setChecked(true);
            mAirplanemodeText.setTextColor(Color.GREEN);
            mAirplanemodeText.setText("Airplane mode is ON");
        }
        else
        {
            mAirplanemodeCheckBox.setChecked(false);
            mAirplanemodeText.setTextColor(Color.GREEN);
            mAirplanemodeText.setText("Airplane mode is OFF");
        }

        mAirplanemodeCheckBox.setEnabled(true);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);

        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        this.unregisterReceiver(this.mBroadcastReceiver);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        //Restore airplane mode to original status
        if (isAirplaneModeOn() != mAirplaneModeOriginal)
        {
            toggleAirplaneMode(mAirplaneModeOriginal);
        }
    }

    private static void initSettingsProvider()
    {
        String settingsProvider;

        if (Build.VERSION.SDK_INT >= 17)
        {
            settingsProvider = GLOBAL_SETTINGS;
        }
        else
        {
            settingsProvider = SYSTEM_SETTINGS;
        }

        try
        {
            mSettingsClass = Class.forName(settingsProvider);
            if (mSettingsClass != null)
            {

                mSettingsGetInt = mSettingsClass.getMethod("getInt", new Class[] {
                        ContentResolver.class, String.class, int.class});
                mSettingsPutInt = mSettingsClass.getMethod("putInt", new Class[] {
                        ContentResolver.class, String.class, int.class});
            }
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }
    }

    protected boolean isAirplaneModeOn()
    {
        Context context = getApplicationContext();
        Integer ret = 0;

        try
        {
            if (mSettingsGetInt != null)
            {
                ret = (Integer)mSettingsGetInt.invoke(mSettingsClass, context.getContentResolver(),
                        AIRPLANE_MODE_ON, 0);
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "fail to invoke getInt", 'i');
        }

        return (ret == 1);
    }

    protected void toggleAirplaneMode(boolean bTurnOn)
    {
        int value = (bTurnOn) ? 1 : 0;

        if (mSettingsPutInt != null)
        {
            try
            {
                mSettingsPutInt.invoke(mSettingsClass, getContentResolver(),
                        AIRPLANE_MODE_ON, value);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "fail to invoke putInt", 'i');
            }

            //Post an intent to reload
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", bTurnOn);
            sendBroadcast(intent);
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_AIRPLANE_MODE_STATE"))
        {
            boolean isEnabled = isAirplaneModeOn();
            List<String> strDataList = new ArrayList<String>();

            String airplaneModeState = (isEnabled) ? "ON" : "OFF";

            strDataList.add("STATE=" + airplaneModeState);
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("ENABLE_AIRPLANE_MODE"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (!isAirplaneModeOn())
            {
                toggleAirplaneMode(true);
            }

            strDataList.add("AIRPLANE_MODE=ON");
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_AIRPLANE_MODE"))
        {
            List<String> strDataList = new ArrayList<String>();

            if (isAirplaneModeOn())
            {
                toggleAirplaneMode(false);
            }

            strDataList.add("AIRPLANE_MODE=OFF");
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
        strHelpList.add("This function will get/set airplane mode");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  GET_AIRPLANE_MODE_STATE    - Get airplane mode state");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_AIRPLANE_MODE    - Turn on airplane mode");
        strHelpList.add("  ");
        strHelpList.add("  DISABLE_AIRPLANE_MODE    - Turn off airplane mode");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Airplane Mode Test:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

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
        contentRecord("testresult.txt", "Airplane Mode Test:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

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
}

