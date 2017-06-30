/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.mag;

import java.util.ArrayList;
import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

public class Magnetometer extends Test_Base implements SensorEventListener
{

    private SensorManager mSensorManager;
    double fMagneticFieldX = 0;
    double fMagneticFieldY = 0;
    double fMagneticFieldZ = 0;
    boolean activityStartedFromCommServer = false;

    private TextView xAxisText;
    private TextView yAxisText;
    private TextView zAxisText;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 200; // millisecond
    private final int PRECISION = 2;

    private boolean sensorListenerRegistered = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_Magnetometer";

        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.mag);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        xAxisText = (TextView) findViewById(com.motorola.motocit.R.id.magnetic_x);
        yAxisText = (TextView) findViewById(com.motorola.motocit.R.id.magnetic_y);
        zAxisText = (TextView) findViewById(com.motorola.motocit.R.id.magnetic_z);

        lastUiUpdateTime = System.currentTimeMillis();
        if(TestUtils.hasMagnetometerSensor()==0)
        {
            Toast.makeText(Magnetometer.this, "The device does NOT support Magnetometer", Toast.LENGTH_SHORT).show();
            Magnetometer.this.finish();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (sensorListenerRegistered == false)
        {
            dbgLog(TAG, "onResume() register sensor listener", 'i');

            // Check if commServer started this activity
            // - if so then change sensor update rate
            if (wasActivityStartedByCommServer())
            {
                activityStartedFromCommServer = true;
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'i');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                activityStartedFromCommServer = false;
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'i');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
            }

            sensorListenerRegistered = true;
        }

        sendStartActivityPassed();

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
        {
            fMagneticFieldX = event.values[0];
            fMagneticFieldY = event.values[1];
            fMagneticFieldZ = event.values[2];

            // limit UI updates as not to slam CPU and make activity unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                lastUiUpdateTime = System.currentTimeMillis();

                xAxisText.setText("x:" + TestUtils.round(fMagneticFieldX, PRECISION));
                yAxisText.setText("y:" + TestUtils.round(fMagneticFieldY, PRECISION));
                zAxisText.setText("z:" + TestUtils.round(fMagneticFieldZ, PRECISION));

                dbgLog(TAG, "sensor: magnetometer " + "X=" + fMagneticFieldX + " Y=" + fMagneticFieldY + " Z=" + fMagneticFieldZ, 'i');
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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_READING"))
        {
            int delay_tries = 0;
            /* if no reading has been registered, add delay of up to 2s */
            while ((delay_tries < 20) && (fMagneticFieldX == 0) && (fMagneticFieldY == 0) && (fMagneticFieldZ == 0))
            {
                dbgLog(TAG, "delay to retry", 'i');
                try
                {
                    Thread.sleep(100, 0);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                delay_tries++;
            }

            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("MAGNETIC_FIELD_X=" + fMagneticFieldX));
            strDataList.add(String.format("MAGNETIC_FIELD_Y=" + fMagneticFieldY));
            strDataList.add(String.format("MAGNETIC_FIELD_Z=" + fMagneticFieldZ));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

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
        strHelpList.add("This function will read the Magnetometer");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns Magnetic Field X, Y, Z in micro-tesla units");
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
        contentRecord("testresult.txt", "Magnetometer Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fMagneticFieldX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fMagneticFieldY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fMagneticFieldZ) + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Magnetometer Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fMagneticFieldX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fMagneticFieldY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fMagneticFieldZ) + "\r\n\r\n", MODE_APPEND);

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
                contentRecord("testresult.txt", "Magnetometer Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "Magnetometer Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "X: " + String.valueOf(fMagneticFieldX) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Y: " + String.valueOf(fMagneticFieldY) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Z: " + String.valueOf(fMagneticFieldZ) + "\r\n\r\n", MODE_APPEND);

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

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();
        testResultName.add("MAGNETIC_FIELD_X");
        testResultName.add("MAGNETIC_FIELD_Y");
        testResultName.add("MAGNETIC_FIELD_Z");

        testResultValues.add(String.valueOf(fMagneticFieldX));
        testResultValues.add(String.valueOf(fMagneticFieldY));
        testResultValues.add(String.valueOf(fMagneticFieldZ));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
