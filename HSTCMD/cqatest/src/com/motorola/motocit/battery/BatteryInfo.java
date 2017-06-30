/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.battery;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
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

public class BatteryInfo extends Test_Base
{
    private TextView mBatteryLevelTextView;
    private TextView mBatteryVoltageTextView;
    private TextView mBatteryHealthTextView;
    private TextView mBatteryPluggedTextView;
    private TextView mBatteryPresentTextView;
    private TextView mBatteryStatusTextView;
    private TextView mBatteryTechnologyTextView;
    private TextView mBatteryTemperatureTextView;

    private double batteryLevel;
    private double batteryVoltage;
    private String batteryHealth;
    private String batteryPlugged;
    private String batteryPresent;
    private String batteryStatus;
    private String batteryTechnology;
    private double batteryTemperature;

    // Integer 4 equals BatteryManager.BATTERY_PLUGGED_WIRELESS introduced in
    // API Level 17
    private final int m_BATTERY_PLUGGED_WIRELESS = 4;


    private BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Get battery level
            batteryLevel = getBatteryLevel(intent);
            if (batteryLevel >= 80.0)
            {
                Resources res = getResources();
                Drawable drawable = res.getDrawable(com.motorola.motocit.R.drawable.batterybgcolorgreen);
                BatteryInfo.this.getWindow().setBackgroundDrawable(drawable);
                mBatteryLevelTextView.setTextColor(Color.WHITE);
            }
            else if ((batteryLevel >= 60.0) && (batteryLevel < 80.0))
            {
                Resources res = getResources();
                Drawable drawable = res.getDrawable(com.motorola.motocit.R.drawable.batterybgcolordarkgolden);
                BatteryInfo.this.getWindow().setBackgroundDrawable(drawable);
                mBatteryLevelTextView.setTextColor(Color.BLACK);
            }
            else if ((batteryLevel >= 50.0) && (batteryLevel < 60.0))
            {
                Resources res = getResources();
                Drawable drawable = res.getDrawable(com.motorola.motocit.R.drawable.batterybgcolorindianred);
                BatteryInfo.this.getWindow().setBackgroundDrawable(drawable);
                mBatteryLevelTextView.setTextColor(Color.WHITE);
            }
            else
            {
                Resources res = getResources();
                Drawable drawable = res.getDrawable(com.motorola.motocit.R.drawable.batterybgcolorblack);
                BatteryInfo.this.getWindow().setBackgroundDrawable(drawable);
                mBatteryLevelTextView.setTextColor(Color.RED);
            }

            mBatteryLevelTextView.setText("Battery Level: " + batteryLevel + "%");

            // Get battery voltage
            batteryVoltage = getBatteryVoltage(intent);
            mBatteryVoltageTextView.setText("Battery Voltage: " + batteryVoltage + " V");

            // Get battery health
            batteryHealth = getBatteryHealth(intent);
            mBatteryHealthTextView.setText("Battery Health: " + batteryHealth);

            // Get battery plugged
            batteryPlugged = getBatteryPlugged(intent);
            mBatteryPluggedTextView.setText("Battery Plugged: " + batteryPlugged);

            // Get battery present
            batteryPresent = getBatteryPresent(intent);
            mBatteryPresentTextView.setText("Battery Present: " + batteryPresent);

            // Get battery status
            batteryStatus = getBatteryStatus(intent);
            mBatteryStatusTextView.setText("Battery Status: " + batteryStatus);

            // Get battery technology
            batteryTechnology = getBatteryTechnology(intent);
            mBatteryTechnologyTextView.setText("Battery Technology: " + batteryTechnology);

            // Get battery temperature
            batteryTemperature = getBatteryTemperature(intent);
            mBatteryTemperatureTextView.setText("Battery Temperature: " + batteryTemperature + " C");
        }
    };

    private double getBatteryLevel(Intent intent)
    {
        int rawlevel  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale     = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double level = -1.0;
        if ((rawlevel >= 0) && (scale > 0)) {
            level = (double)(rawlevel * 100) / (double)scale;
        }

        return level;
    }

    private double getBatteryVoltage(Intent intent)
    {
        int voltageMV = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        double voltageV =  voltageMV/1000.0;

        return voltageV;
    }

    private String getBatteryHealth(Intent intent)
    {
        String healthString;

        int healthInt  = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);

        switch (healthInt)
        {
        case BatteryManager.BATTERY_HEALTH_COLD:
            healthString = "BATTERY_HEALTH_COLD";
            break;

        case BatteryManager.BATTERY_HEALTH_DEAD:
            healthString = "BATTERY_HEALTH_DEAD";
            break;

        case BatteryManager.BATTERY_HEALTH_GOOD:
            healthString = "BATTERY_HEALTH_GOOD";
            break;

        case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
            healthString = "BATTERY_HEALTH_OVER_VOLTAGE";
            break;

        case BatteryManager.BATTERY_HEALTH_OVERHEAT:
            healthString = "BATTERY_HEALTH_OVERHEAT";
            break;

        case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            healthString = "BATTERY_HEALTH_UNKNOWN";
            break;

        case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
            healthString = "BATTERY_HEALTH_UNSPECIFIED_FAILURE";
            break;

        default:
            healthString = "UNKNOWN";
            break;
        }

        return healthString;
    }

    private String getBatteryPlugged(Intent intent)
    {
        String pluggedString;

        int pluggedInt  = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

        switch (pluggedInt)
        {
        case BatteryManager.BATTERY_PLUGGED_AC:
            pluggedString = "BATTERY_PLUGGED_AC";
            break;

        case BatteryManager.BATTERY_PLUGGED_USB:
            pluggedString = "BATTERY_PLUGGED_USB";
            break;

        case m_BATTERY_PLUGGED_WIRELESS:
            pluggedString = "BATTERY_PLUGGED_WIRELESS";
            break;

        default:
            pluggedString = "UNKNOWN";
            break;
        }

        return pluggedString;
    }

    private String getBatteryPresent(Intent intent)
    {
        String presentString;

        boolean presentBool  = intent.getBooleanExtra (BatteryManager.EXTRA_PRESENT, false);

        if (presentBool)
        {
            presentString = "TRUE";
        }
        else
        {
            presentString = "FALSE";
        }

        return presentString;
    }

    private String getBatteryStatus(Intent intent)
    {
        String statusString;

        int statusInt  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        switch (statusInt)
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

    private String getBatteryTechnology(Intent intent)
    {
        String technologyString = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

        return technologyString;
    }

    private double getBatteryTemperature(Intent intent)
    {
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

        double batteryTempDouble = temperature/10.0;

        return batteryTempDouble;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "BatteryInfo";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.batteryinfo);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // Set initial battery level text
        mBatteryLevelTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_level);
        mBatteryLevelTextView.setTextColor(Color.WHITE);
        mBatteryLevelTextView.setText("Battery Level: NA");

        // Set initial battery voltage text
        mBatteryVoltageTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_voltage);
        mBatteryVoltageTextView.setTextColor(Color.WHITE);
        mBatteryVoltageTextView.setText("Battery Voltage: NA");

        // Set initial battery health text
        mBatteryHealthTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_health);
        mBatteryHealthTextView.setTextColor(Color.WHITE);
        mBatteryHealthTextView.setText("Battery Health: NA");

        // Set initial battery plugged state text
        mBatteryPluggedTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_plugged);
        mBatteryPluggedTextView.setTextColor(Color.WHITE);
        mBatteryPluggedTextView.setText("Battery Plugged: NA");

        // Set initial battery present text
        mBatteryPresentTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_present);
        mBatteryPresentTextView.setTextColor(Color.WHITE);
        mBatteryPresentTextView.setText("Battery Present: NA");

        // Set initial battery status text
        mBatteryStatusTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_status);
        mBatteryStatusTextView.setTextColor(Color.WHITE);
        mBatteryStatusTextView.setText("Battery Status: NA");

        // Set initial battery technology text
        mBatteryTechnologyTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_technology);
        mBatteryTechnologyTextView.setTextColor(Color.WHITE);
        mBatteryTechnologyTextView.setText("Battery Technology: NA");

        // Set initial battery temperature text
        mBatteryTemperatureTextView = (TextView) findViewById(com.motorola.motocit.R.id.battery_temperature);
        mBatteryTemperatureTextView.setTextColor(Color.WHITE);
        mBatteryTemperatureTextView.setText("Battery Temperature: NA");

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);

        sendStartActivityPassed();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.unregisterReceiver(this.batteryLevelReceiver);
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
                contentRecord("testresult.txt", "Battery Info Test: PASS" + "\r\n", MODE_APPEND);
                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "Battery Info Test: FAILED" + "\r\n", MODE_APPEND);
                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "Level: "       + batteryLevel       + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Voltage: "     + batteryVoltage     + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Health: "      + batteryHealth      + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Plugged: "     + batteryPlugged     + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Present: "     + batteryPresent     + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Status: "      + batteryStatus      + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Technology: "  + batteryTechnology  + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Temperature: " + batteryTemperature + "\r\n\r\n", MODE_APPEND);

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


    private void sendInfoData(String data)
    {
        List<String> strDataList = new ArrayList<String>();
        strDataList.add(data);

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {

        Intent batteryIntent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        List<String> strReturnDataList = new ArrayList<String>();

        // see if user wants all the data
        boolean getAll = strRxCmd.equalsIgnoreCase("GET_ALL");
        boolean sendPassAck = false;

        // perform correct action based on tell cmd
        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_LEVEL") || getAll)
        {
            double level = getBatteryLevel(batteryIntent);

            sendInfoData("BATTERY_LEVEL=" + level);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_VOLTAGE") || getAll)
        {
            double voltage = getBatteryVoltage(batteryIntent);

            sendInfoData("BATTERY_VOLTAGE=" + voltage);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_HEALTH") || getAll)
        {
            String health = getBatteryHealth(batteryIntent);

            sendInfoData("BATTERY_HEALTH=" + health);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_PLUGGED_STATE") || getAll)
        {
            String plugged = getBatteryPlugged(batteryIntent);

            sendInfoData("BATTERY_PLUGGED_STATE=" + plugged);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_PRESENT") || getAll)
        {
            String present = getBatteryPresent(batteryIntent);

            sendInfoData("BATTERY_PRESENT=" + present);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_STATUS") || getAll)
        {
            String status = getBatteryStatus(batteryIntent);

            sendInfoData("BATTERY_STATUS=" + status);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_TECHNOLOGY") || getAll)
        {
            String technology = getBatteryTechnology(batteryIntent);

            sendInfoData("BATTERY_TECHNOLOGY=" + technology);
            sendPassAck = true;
        }

        if (strRxCmd.equalsIgnoreCase("GET_BATTERY_TEMPERATURE") || getAll)
        {
            double temperature = getBatteryTemperature(batteryIntent);

            sendInfoData("BATTERY_TEMPERATURE=" + temperature);
            sendPassAck = true;
        }

        if (sendPassAck)
        {
            // info packets are generated in previous if statements.
            // only need to generate ack packet here

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        // If get here then unrecognised tell cmd
        // Generate an exception to send FAIL result and mesg back to CommServer
        List<String> strErrMsgList = new ArrayList<String>();
        strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
        dbgLog(TAG, strErrMsgList.get(0), 'i');
        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function returns information on the battery");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  GET_ALL             - returns all the battery info values");
        strHelpList.add("  GET_BATTERY_LEVEL   - returns charge level of battery in percentage");
        strHelpList.add("  GET_BATTERY_VOLTAGE - returns voltage of battery in Volts");
        strHelpList.add("  GET_BATTERY_HEALTH  - returns health of battery");
        strHelpList.add("  GET_PLUGGED_STATE   - returns plugged state");
        strHelpList.add("  GET_BATTERY_PRESENT - returns TRUE or FALSE if battery is present");
        strHelpList.add("  GET_BATTERY_STATUS  - returns the status of the battery");
        strHelpList.add("  GET_BATTERY_TECHNOLOGY  - returns the technology of the battery");
        strHelpList.add("  GET_BATTERY_TEMPERATURE - returns the temperature of the battery in C");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Battery Info Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "Level: " + batteryLevel + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Voltage: " + batteryVoltage + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Health: " + batteryHealth + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Plugged: " + batteryPlugged + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Present: " + batteryPresent + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Status: " + batteryStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Technology: " + batteryTechnology + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Temperature: " + batteryTemperature + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Battery Info Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "Level: " + batteryLevel + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Voltage: " + batteryVoltage + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Health: " + batteryHealth + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Plugged: " + batteryPlugged + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Present: " + batteryPresent + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Status: " + batteryStatus + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Technology: " + batteryTechnology + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Temperature: " + batteryTemperature + "\r\n\r\n", MODE_APPEND);

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
        testResultName.add("BATTERY_LEVEL");
        testResultName.add("BATTERY_VOLTAGE");
        testResultName.add("BATTERY_HEALTH");
        testResultName.add("PLUGGED_STATE");
        testResultName.add("BATTERY_PRESENT");
        testResultName.add("BATTERY_STATUS");
        testResultName.add("BATTERY_TECHNOLOGY");
        testResultName.add("BATTERY_TEMPERATURE");

        testResultValues.add(String.valueOf(batteryLevel));
        testResultValues.add(String.valueOf(batteryVoltage));
        testResultValues.add(batteryHealth);
        testResultValues.add(batteryPlugged);
        testResultValues.add(batteryPresent);
        testResultValues.add(batteryStatus);
        testResultValues.add(batteryTechnology);
        testResultValues.add(String.valueOf(batteryTemperature));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
