/*
 * Copyright (c) 2012 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.gyroscope;

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

public class Gyroscope extends Test_Base implements SensorEventListener
{
    private SensorManager mSensorManager;
    double fAngularSpeedX = -9999;
    double fAngularSpeedY = -9999;
    double fAngularSpeedZ = -9999;
    double fAngularMaxSpeedX = 0;
    double fAngularMaxSpeedY = 0;
    double fAngularMaxSpeedZ = 0;
    double fAngularMinSpeedX = 9999;
    double fAngularMinSpeedY = 9999;
    double fAngularMinSpeedZ = 9999;
    boolean activityStartedFromCommServer = false;

    private TextView xAxisText;
    private TextView xMaxAxisText;
    private TextView xMinAxisText;
    private TextView yAxisText;
    private TextView yMaxAxisText;
    private TextView yMinAxisText;
    private TextView zAxisText;
    private TextView zMaxAxisText;
    private TextView zMinAxisText;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 200; // millisecond
    private final int PRECISION = 2;

    private boolean sensorListenerRegistered = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_Gyroscope";

        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.gyroscope);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        xAxisText = (TextView) findViewById(com.motorola.motocit.R.id.angular_speed_x);
        xMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_angular_speed_x);
        xMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_angular_speed_x);
        yAxisText = (TextView) findViewById(com.motorola.motocit.R.id.angular_speed_y);
        yMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_angular_speed_y);
        yMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_angular_speed_y);
        zAxisText = (TextView) findViewById(com.motorola.motocit.R.id.angular_speed_z);
        zMaxAxisText = (TextView) findViewById(com.motorola.motocit.R.id.max_angular_speed_z);
        zMinAxisText = (TextView) findViewById(com.motorola.motocit.R.id.min_angular_speed_z);

        lastUiUpdateTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (sensorListenerRegistered == false)
        {
            // Check if commServer started this activity
            // - if so then change sensor update rate
            if (wasActivityStartedByCommServer())
            {
                activityStartedFromCommServer = true;
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                activityStartedFromCommServer = false;
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'd');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_UI);
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

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {
            //------------------------------------
            // Calculate values
            //------------------------------------

            // X values
            fAngularSpeedX = event.values[0];

            if (fAngularSpeedX > fAngularMaxSpeedX)
            {
                fAngularMaxSpeedX = fAngularSpeedX;
            }

            if (fAngularSpeedX < fAngularMinSpeedX)
            {
                fAngularMinSpeedX = fAngularSpeedX;
            }

            // Y values
            fAngularSpeedY = event.values[1];

            if (fAngularSpeedY > fAngularMaxSpeedY)
            {
                fAngularMaxSpeedY = fAngularSpeedY;
            }

            if (fAngularSpeedY < fAngularMinSpeedY)
            {
                fAngularMinSpeedY = fAngularSpeedY;
            }

            // Z values
            fAngularSpeedZ = event.values[2];

            if (fAngularSpeedZ > fAngularMaxSpeedZ)
            {
                fAngularMaxSpeedZ = fAngularSpeedZ;
            }

            if (fAngularSpeedZ < fAngularMinSpeedZ)
            {
                fAngularMinSpeedZ = fAngularSpeedZ;
            }

            // limit UI updates as not to slam CPU and make activity unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                lastUiUpdateTime = System.currentTimeMillis();

                xAxisText.setText("x:" + TestUtils.round(fAngularSpeedX, PRECISION));
                xMaxAxisText.setText("Max x:" + TestUtils.round(fAngularMaxSpeedX, PRECISION));
                xMinAxisText.setText("Min x:" + TestUtils.round(fAngularMinSpeedX, PRECISION));

                yAxisText.setText("y:" + TestUtils.round(fAngularSpeedY, PRECISION));
                yMaxAxisText.setText("Max y:" + TestUtils.round(fAngularMaxSpeedY, PRECISION));
                yMinAxisText.setText("Min y:" + TestUtils.round(fAngularMinSpeedY, PRECISION));

                zAxisText.setText("z:" + TestUtils.round(fAngularSpeedZ, PRECISION));
                zMaxAxisText.setText("Max z:" + TestUtils.round(fAngularMaxSpeedZ, PRECISION));
                zMinAxisText.setText("Min z:" + TestUtils.round(fAngularMinSpeedZ, PRECISION));

                dbgLog(TAG, "sensor: gyroscope " + "X=" + fAngularSpeedX + " Y=" + fAngularSpeedY + " Z=" + fAngularSpeedZ, 'd');
                dbgLog(TAG, "sensor: gyroscope " + "Max X=" + fAngularMaxSpeedX + " Max Y=" + fAngularMaxSpeedY + " Max Z=" + fAngularMaxSpeedZ, 'd');

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
            List<String> strDataList = new ArrayList<String>();

            strDataList.add(String.format("ANGULAR_SPEED_X=" + fAngularSpeedX));
            strDataList.add(String.format("ANGULAR_SPEED_Y=" + fAngularSpeedY));
            strDataList.add(String.format("ANGULAR_SPEED_Z=" + fAngularSpeedZ));
            strDataList.add(String.format("MAX_ANGULAR_SPEED_X=" + fAngularMaxSpeedX));
            strDataList.add(String.format("MAX_ANGULAR_SPEED_Y=" + fAngularMaxSpeedY));
            strDataList.add(String.format("MAX_ANGULAR_SPEED_Z=" + fAngularMaxSpeedZ));
            strDataList.add(String.format("MIN_ANGULAR_SPEED_X=" + fAngularMinSpeedX));
            strDataList.add(String.format("MIN_ANGULAR_SPEED_Y=" + fAngularMinSpeedY));
            strDataList.add(String.format("MIN_ANGULAR_SPEED_Z=" + fAngularMinSpeedZ));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLEAR_MAX_MIN_READINGS"))
        {
            fAngularMaxSpeedX = 0;
            fAngularMaxSpeedY = 0;
            fAngularMaxSpeedZ = 0;
            fAngularMinSpeedX = 9999;
            fAngularMinSpeedY = 9999;
            fAngularMinSpeedZ = 9999;

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

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
        strHelpList.add("This function will read the Gyroscope");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns Angular Speed Field X, Y, Z in Radians per second units");
        strHelpList.add("  ");
        strHelpList.add("CLEAR_MAX_MIN_READINGS - Clears Max and Min Angular Speed Field X, Y, Z values");
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
                contentRecord("testresult.txt", "Gyroscope Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "Gyroscope Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "X: " + String.valueOf(fAngularSpeedX) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Y: " + String.valueOf(fAngularSpeedY) + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "Z: " + String.valueOf(fAngularSpeedZ) + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Gyroscope Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fAngularSpeedX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fAngularSpeedY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fAngularSpeedZ) + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Gyroscope Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "X: " + String.valueOf(fAngularSpeedX) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Y: " + String.valueOf(fAngularSpeedY) + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "Z: " + String.valueOf(fAngularSpeedZ) + "\r\n\r\n", MODE_APPEND);

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
        testResultName.add("ANGULAR_SPEED_X");
        testResultName.add("ANGULAR_SPEED_Y");
        testResultName.add("ANGULAR_SPEED_Z");
        testResultName.add("MAX_ANGULAR_SPEED_X");
        testResultName.add("MAX_ANGULAR_SPEED_Y");
        testResultName.add("MAX_ANGULAR_SPEED_Z");
        testResultName.add("MIN_ANGULAR_SPEED_X");
        testResultName.add("MIN_ANGULAR_SPEED_Y");
        testResultName.add("MIN_ANGULAR_SPEED_Z");

        testResultValues.add(String.valueOf(fAngularSpeedX));
        testResultValues.add(String.valueOf(fAngularSpeedY));
        testResultValues.add(String.valueOf(fAngularSpeedZ));
        testResultValues.add(String.valueOf(fAngularSpeedZ));
        testResultValues.add(String.valueOf(fAngularMaxSpeedY));
        testResultValues.add(String.valueOf(fAngularMaxSpeedZ));
        testResultValues.add(String.valueOf(fAngularMinSpeedX));
        testResultValues.add(String.valueOf(fAngularMinSpeedY));
        testResultValues.add(String.valueOf(fAngularMinSpeedZ));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
