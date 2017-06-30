/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.proximity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Proximity extends Test_Base implements SensorEventListener
{

    private SensorManager mSensorManager;
    private int fDistance = -9999;

    private TextView proximityText;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 200; // millisecond
    private final int PRECISION = 2;

    private int maximumRange;
    private int saturationPointValue = -999;

    private boolean proxDisabledDetect = false;
    private boolean proxEnabledDetect = false;
    private boolean proxSaturationDetect = false;

    private boolean mCheckSaturationPoint = false;

    private boolean sensorListenerRegistered = false;

    private final Lock proxReadLock = new ReentrantLock();

    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_Proximity";
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.proximity);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        proximityText = (TextView) findViewById(com.motorola.motocit.R.id.proximity_x);

        lastUiUpdateTime = System.currentTimeMillis() - UI_UPDATE_RATE * 2;

        getProxSettingsFromConfig();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (sensorListenerRegistered == false)
        {
            dbgLog(TAG, "onResume() register sensor listener", 'i');

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            maximumRange = (int)sensorObject.getMaximumRange();

            // Check if commServer started this activity
            // - if so then change sensor update rate
            if (wasActivityStartedByCommServer())
            {
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'd');
                mSensorManager.registerListener(this, sensorObject, SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'd');
                mSensorManager.registerListener(this, sensorObject, SensorManager.SENSOR_DELAY_UI);
            }

            sensorListenerRegistered = true;
        }

        sendStartActivityPassed();

    }

    private void showSensorDataUI()
    {
        if (fDistance == maximumRange)
        {
            proximityText.setTextColor(Color.RED);
            proximityText.setText("Status: Disable" + "\r\n" + "RawData=" + fDistance);
            proxDisabledDetect = true;

        }
        else if (mCheckSaturationPoint && (fDistance == saturationPointValue))
        {
            proximityText.setTextColor(Color.BLUE);
            proximityText.setText("Status: Saturation" + "\r\n" + "RawData=" + fDistance);
            proxSaturationDetect = true;

        }
        else
        {
            proximityText.setTextColor(Color.GREEN);
            proximityText.setText("Status: Enable" + "\r\n" + "RawData=" + fDistance);
            proxEnabledDetect = true;
        }

        dbgLog(TAG, "sensor: proximity " + "raw value = " + fDistance, 'd');

        if (proxEnabledDetect && proxDisabledDetect && !wasActivityStartedByCommServer())
        {
            boolean testComplete = false;

            if (mCheckSaturationPoint == false)
            {
                testComplete = true;
            }
            else if (mCheckSaturationPoint && proxSaturationDetect)
            {
                testComplete = true;
            }

            if (testComplete == true)
            {
                contentRecord("testresult.txt", "Proximity:  PASS" + "\r\n\r\n", MODE_APPEND);

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
        }
    }

    private Runnable runnable = new Runnable() {

        @Override
        public void run()
        {
            try
            {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run()
                    {
                        showSensorDataUI();
                        proxReadLock.unlock();
                    }
                }, 1000);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if ((event.sensor.getType() == Sensor.TYPE_PROXIMITY) && !isFinishing())
        {
            proxReadLock.lock();

            fDistance = (int)event.values[0];

            // limit UI updates as not to slam CPU and make activity
            // unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                lastUiUpdateTime = System.currentTimeMillis();
                showSensorDataUI();
                proxReadLock.unlock();
            }
            else
            {
                if (!wasActivityStartedByCommServer())
                {
                    // show sensor data on UI after 1s
                    dbgLog(TAG, "move fast, show data on UI after 1s", 'i');
                    runOnUiThread(runnable);
                }
            }
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if ((wasActivityStartedByCommServer() == false) || isFinishing())
        {
            dbgLog(TAG, "onPause() unregister sensor listener", 'i');
            mSensorManager.unregisterListener(this);
            sensorListenerRegistered = false;
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if (sensorListenerRegistered == true)
        {
            dbgLog(TAG, "onDestroy() unregister sensor listener", 'i');
            mSensorManager.unregisterListener(this);
            sensorListenerRegistered = false;
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

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Proximity:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_READING"))
        {
            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("PROXIMITY_DISTANCE=" + fDistance));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_RAW_READING"))
        {
            int lowValue = 0;
            int highValue = 0;
            int actualValue = 0;

            int registerFormat = 0;
            String registerLocation = null;

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("REGISTER_PATH"))
                    {
                        registerLocation = value;
                    }
                    else if (key.equalsIgnoreCase("REGISTER_FORMAT"))
                    {
                        try
                        {
                            registerFormat = Integer.parseInt(value);
                        }
                        catch (Exception e)
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add("Register_format is not a number");
                            dbgLog(TAG, strErrMsgList.get(0), 'i');
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                    }
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            List<String> strReturnDataList = new ArrayList<String>();

            try
            {
                // 8960 Register location
                // "/sys/bus/i2c/drivers/ct406/10-0039/registers"
                // Thor Register location
                // "/sys/bus/i2c/devices/i2c-5/5-0039/registers"
                BufferedReader breader = null;

                if (registerLocation != null)
                {
                    breader = new BufferedReader(new FileReader(registerLocation));
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to
                    // CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("Register Location Name is null");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                String line = "";

                if (registerFormat == 0)
                {
                    while ((line = breader.readLine()) != null)
                    {
                        if (line.contains("PDATA") == false)
                        {
                            continue;
                        }

                        String[] tokens = line.split("=");

                        tokens[0] = tokens[0].trim();
                        String temp = tokens[1].trim();

                        int value = Integer.parseInt(temp.substring(2), 16);

                        // PDATA is equal to low bits of Prox Sensor Data
                        if (tokens[0].equals("PDATA"))
                        {
                            lowValue = value;
                        }
                        // PDATAH is equal to high bits of Prox Sensor Data
                        if (tokens[0].equals("PDATAH"))
                        {
                            highValue = value;
                        }
                    }
                    breader.close();

                    actualValue = (highValue * 256) + lowValue;
                }
                else
                {
                    strReturnDataList.add(String.format("REGISTER_FORMAT=" + registerFormat + " is not supported"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }
            catch (Exception e)
            {
                strReturnDataList.add("UNABLE TO READ RAW PROXIMITY VALUE");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("PROXIMITY_RAW_READING=" + actualValue));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

            // if phone does not support sensor then sensorObject will be null
            if (sensorObject == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Sensor manager returned null for requested sensor");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            strDataList.add(String.format("MAXIMUM_RANGE=" + sensorObject.getMaximumRange()));
            strDataList.add(String.format("MIN_DELAY=" + sensorObject.getMinDelay()));
            strDataList.add(String.format("NAME=" + sensorObject.getName()));
            strDataList.add(String.format("POWER=" + sensorObject.getPower()));
            strDataList.add(String.format("RESOLUTION=" + sensorObject.getResolution()));
            strDataList.add(String.format("TYPE=" + sensorObject.getType()));
            strDataList.add(String.format("VENDOR=" + sensorObject.getVendor()));
            strDataList.add(String.format("VERSION=" + sensorObject.getVersion()));

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
        strHelpList.add("This function will read the Proximity Sensor");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns Proximity Sensor Distance");
        strHelpList.add("  ");
        strHelpList.add("GET_RAW_READING - Returns Proximity Sensor Data in DAC counts");
        strHelpList.add("  ");
        strHelpList.add("GET_SENSOR_INFO - Returns the following information about the sensor");
        strHelpList.add(" MAXIMUM_RANGE - maximum range of the sensor in the sensor's unit");
        strHelpList.add(" MIN_DELAY - the minimum delay allowed between two events in microsecond "
                + "or zero if this sensor only returns a value when the data it's measuring changes");
        strHelpList.add(" NAME - name string of the sensor");
        strHelpList.add(" POWER - the power in mA used by this sensor while in use");
        strHelpList.add(" RESOLUTION - resolution of the sensor in the sensor's unit");
        strHelpList.add(" TYPE - generic type of this sensor");
        strHelpList.add(" VENDOR - vendor string of this sensor");
        strHelpList.add(" VERSION - version of the sensor's module");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    public boolean getProxSettingsFromConfig()
    {
        boolean result = false;
        String SequenceFileInUse = TestUtils.getSequenceFileInUse();
        File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
        String config_file = null;

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else if (file_system_sdcard.exists())
        {
            config_file = file_system_sdcard.toString();
        }
        else
        {
            dbgLog(TAG, "!! CANN'T FIND PROX PARAMETERS IN CONFIG FILE", 'd');
        }

        if ((config_file != null) && (SequenceFileInUse != null))
        {
            try
            {
                BufferedReader breader = new BufferedReader(new FileReader(config_file));
                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    if (line.toUpperCase().contains("<PROX_SETTINGS>") == true)
                    {
                        result = true;
                        break;
                    }
                }

                if (null != line)
                {
                    dbgLog(TAG, "Settings: " + line, 'd');
                    String[] fields = line.split(",");
                    for (String field : fields)
                    {
                        if (field.contains("CHECK_SATURATION"))
                        {
                            String[] tokens = field.split("=");

                            if (tokens[1].equalsIgnoreCase("YES") || tokens[1].equalsIgnoreCase("TRUE"))
                            {
                                mCheckSaturationPoint = true;
                            }
                            else
                            {
                                mCheckSaturationPoint = false;
                            }
                        }

                        if (field.contains("SATURATION_VALUE"))
                        {
                            String[] tokens = field.split("=");

                            saturationPointValue = Integer.valueOf(tokens[1]);

                        }
                    }
                }

                breader.close();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "!!! Some exception in parsing KeyTest settings=" + e.toString(), 'd');
            }
        }

        return result;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Proximity:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
