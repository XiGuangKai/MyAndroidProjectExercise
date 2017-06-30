/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.light;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class FrontLight extends Test_Base implements SensorEventListener
{
    private SensorManager mSensorManager;
    double fLux = -9999;

    private TextView lightText;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 500; // millisecond
    private final int PRECISION = 2;

    private boolean sensorListenerRegistered = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_FrontLight";
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.light);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        lightText = (TextView) findViewById(com.motorola.motocit.R.id.light_value);

        lastUiUpdateTime = System.currentTimeMillis() - UI_UPDATE_RATE * 2;
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (sensorListenerRegistered == false)
        {
            dbgLog(TAG, "onResume() register sensor listener", 'i');

            if (wasActivityStartedByCommServer())
            {
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_UI);
            }

            sensorListenerRegistered = true;
        }

        sendStartActivityPassed();

        lightText.setTextColor(Color.GREEN);
    }

    //
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {

            lastUiUpdateTime = System.currentTimeMillis();

            final String text;

            if (fLux > 240)
            {
                text = "Light Sensor: BRIGHTEST";
            }
            else if ((fLux > 180) && (fLux <= 240))
            {
                text = "Light Sensor: BRIGHTER";
            }
            else if ((fLux > 120) && (fLux <= 180))
            {
                text = "Light Sensor: BRIGHT";
            }
            else if ((fLux > 60) && (fLux <= 120))
            {
                text = "Light Sensor: NORMAL";
            }
            else
            {
                text = "Light Sensor: DARK";
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    lightText.setText(text);
                }
            });

            dbgLog(TAG, "sensor: front light sensor " + "raw value = " + fLux, 'd');
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT)
        {
            fLux = event.values[0];
            // limit UI updates as not to slam CPU and make activity unresponsive
	        if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
	        {
	        	// Handle text in background thread.
	            mHandler.sendEmptyMessage(0);
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

            contentRecord("testresult.txt", "FrontLight Sensor:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            mSensorManager.unregisterListener(this);
            sensorListenerRegistered = false;
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "FrontLight Sensor:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            mSensorManager.unregisterListener(this);
            sensorListenerRegistered = false;
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
                mSensorManager.unregisterListener(this);
                sensorListenerRegistered = false;
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void onPause() {
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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_READING"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("LUX=" + fLux));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

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
        strHelpList.add("This function will read the Light Sensor");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns Light Sensor SI Lux");
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

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "FrontLight Sensor:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        mSensorManager.unregisterListener(this);
        sensorListenerRegistered = false;
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "FrontLight Sensor:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        mSensorManager.unregisterListener(this);
        sensorListenerRegistered = false;
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
            mSensorManager.unregisterListener(this);
            sensorListenerRegistered = false;
            systemExitWrapper(0);
        }
        return true;
    }
}
