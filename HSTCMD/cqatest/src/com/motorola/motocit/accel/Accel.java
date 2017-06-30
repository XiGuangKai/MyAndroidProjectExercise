/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.accel;

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

public class Accel extends Test_Base implements SensorEventListener
{
    private SensorManager mSensorManager;
    double fAccelerometerX = -9999;
    double fAccelerometerY = -9999;
    double fAccelerometerZ = -9999;
    double fMaxAccelerometerX = 0;
    double fMaxAccelerometerY = 0;
    double fMaxAccelerometerZ = 0;
    double fMinAccelerometerX = 9999;
    double fMinAccelerometerY = 9999;
    double fMinAccelerometerZ = 9999;
    double fTotalAcceleration = -9999;

    private TextView xAxisText;
    private TextView xMaxAxisText;
    private TextView xMinAxisText;
    private TextView yAxisText;
    private TextView yMaxAxisText;
    private TextView yMinAxisText;
    private TextView zAxisText;
    private TextView zMaxAxisText;
    private TextView zMinAxisText;
    private TextView totalAccelText;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 200; // millisecond
    private final int PRECISION = 2;

    private boolean sensorListenerRegistered = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_Accelerometer";
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.accel);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        xAxisText = (TextView) findViewById(com.motorola.motocit.R.id.accel_x);
        xMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_accel_x);
        xMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_accel_x);
        yAxisText = (TextView) findViewById(com.motorola.motocit.R.id.accel_y);
        yMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_accel_y);
        yMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_accel_y);
        zAxisText = (TextView) findViewById(com.motorola.motocit.R.id.accel_z);
        zMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_accel_z);
        zMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_accel_z);
        totalAccelText = (TextView) findViewById(com.motorola.motocit.R.id.total_accel);

        lastUiUpdateTime = System.currentTimeMillis();
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
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
            }

            sensorListenerRegistered = true;
        }
        sendStartActivityPassed();
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            //------------------------------------
            // Calculate values
            //------------------------------------

            // X value
            fAccelerometerX = event.values[0];

            if (fAccelerometerX > fMaxAccelerometerX)
            {
                fMaxAccelerometerX = fAccelerometerX;
            }

            if (fAccelerometerX < fMinAccelerometerX)
            {
                fMinAccelerometerX = fAccelerometerX;
            }

            // Y value
            fAccelerometerY = event.values[1];

            if (fAccelerometerY > fMaxAccelerometerY)
            {
                fMaxAccelerometerY = fAccelerometerY;
            }

            if (fAccelerometerY < fMinAccelerometerY)
            {
                fMinAccelerometerY = fAccelerometerY;
            }

            // Z value
            fAccelerometerZ = event.values[2];

            if (fAccelerometerZ > fMaxAccelerometerZ)
            {
                fMaxAccelerometerZ = fAccelerometerZ;
            }

            if (fAccelerometerZ < fMinAccelerometerZ)
            {
                fMinAccelerometerZ = fAccelerometerZ;
            }

            // total acceleration
            fTotalAcceleration = Math.sqrt(Math.pow(fAccelerometerX, 2) + Math.pow(fAccelerometerY, 2) + Math.pow(fAccelerometerZ, 2));


            // limit UI updates as not to slam CPU and make activity unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                lastUiUpdateTime = System.currentTimeMillis();

                xAxisText.setText("x:" + TestUtils.round(fAccelerometerX, PRECISION));
                xMaxAxisText.setText("Max x:" + TestUtils.round(fMaxAccelerometerX, PRECISION));
                xMinAxisText.setText("Min x:" + TestUtils.round(fMinAccelerometerX, PRECISION));

                yAxisText.setText("y:" + TestUtils.round(fAccelerometerY, PRECISION));
                yMaxAxisText.setText("Max y:" + TestUtils.round(fMaxAccelerometerY, PRECISION));
                yMinAxisText.setText("Min y:" + TestUtils.round(fMinAccelerometerY, PRECISION));

                zAxisText.setText("z:" + TestUtils.round(fAccelerometerZ, PRECISION));
                zMaxAxisText.setText("Max z:" + TestUtils.round(fMaxAccelerometerZ, PRECISION));
                zMinAxisText.setText("Min z:" + TestUtils.round(fMinAccelerometerZ, PRECISION));

                totalAccelText.setText("Total Acceleration:" + TestUtils.round(fTotalAcceleration, PRECISION));

                dbgLog(TAG, "sensor: accelerometer" + ", x: " + fAccelerometerX + ", y: " + fAccelerometerY + ", z: " + fAccelerometerZ, 'd');
            }
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
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
            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("ACCELEROMETER_X=" + fAccelerometerX));
            strDataList.add(String.format("ACCELEROMETER_Y=" + fAccelerometerY));
            strDataList.add(String.format("ACCELEROMETER_Z=" + fAccelerometerZ));
            strDataList.add(String.format("MAX_ACCELEROMETER_X=" + fMaxAccelerometerX));
            strDataList.add(String.format("MAX_ACCELEROMETER_Y=" + fMaxAccelerometerY));
            strDataList.add(String.format("MAX_ACCELEROMETER_Z=" + fMaxAccelerometerZ));
            strDataList.add(String.format("MIN_ACCELEROMETER_X=" + fMinAccelerometerX));
            strDataList.add(String.format("MIN_ACCELEROMETER_Y=" + fMinAccelerometerY));
            strDataList.add(String.format("MIN_ACCELEROMETER_Z=" + fMinAccelerometerZ));
            strDataList.add(String.format("TOTAL_ACCELERATION=" + fTotalAcceleration));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLEAR_MAX_MIN_READINGS"))
        {
            fMaxAccelerometerX = 0;
            fMaxAccelerometerY = 0;
            fMaxAccelerometerZ = 0;
            fMinAccelerometerX = 9999;
            fMinAccelerometerY = 9999;
            fMinAccelerometerZ = 9999;

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

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
        strHelpList.add("This function will read the Accelerometer");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns Accelerometer X, Y, and Z values");
        strHelpList.add("  ");
        strHelpList.add("CLEAR_MAX_MIN_READINGS - Clears Max and Min Accelerometer X, Y, and Z values");
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
                contentRecord("testresult.txt", "ACCEL Test: PASS" + "\r\n", MODE_APPEND);
                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "ACCEL Test: FAILED" + "\r\n", MODE_APPEND);
                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "X: " + String.valueOf(fAccelerometerX) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Y: " + String.valueOf(fAccelerometerY) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Z: " + String.valueOf(fAccelerometerZ) + "\r\n\r\n", MODE_APPEND);


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
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "ACCEL Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fAccelerometerX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fAccelerometerY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fAccelerometerZ) + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "ACCEL Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fAccelerometerX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fAccelerometerY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fAccelerometerZ) + "\r\n\r\n", MODE_APPEND);

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
        testResultName.add("ACCELEROMETER_X");
        testResultName.add("ACCELEROMETER_Y");
        testResultName.add("ACCELEROMETER_Z");
        testResultName.add("MAX_ACCELEROMETER_X");
        testResultName.add("MAX_ACCELEROMETER_Y");
        testResultName.add("MAX_ACCELEROMETER_Z");
        testResultName.add("MIN_ACCELEROMETER_X");
        testResultName.add("MIN_ACCELEROMETER_Y");
        testResultName.add("MIN_ACCELEROMETER_Z");
        testResultName.add("TOTAL_ACCELERATION");

        testResultValues.add(String.valueOf(fAccelerometerX));
        testResultValues.add(String.valueOf(fAccelerometerY));
        testResultValues.add(String.valueOf(fAccelerometerZ));
        testResultValues.add(String.valueOf(fMaxAccelerometerX));
        testResultValues.add(String.valueOf(fMaxAccelerometerY));
        testResultValues.add(String.valueOf(fMaxAccelerometerZ));
        testResultValues.add(String.valueOf(fMinAccelerometerX));
        testResultValues.add(String.valueOf(fMinAccelerometerY));
        testResultValues.add(String.valueOf(fMinAccelerometerZ));
        testResultValues.add(String.valueOf(fTotalAcceleration));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
