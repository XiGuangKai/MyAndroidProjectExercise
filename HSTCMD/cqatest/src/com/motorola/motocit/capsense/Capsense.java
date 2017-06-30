/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.capsense;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

public class Capsense extends Test_Base implements SensorEventListener
{
    private SensorManager mSensorManager;
    private TextView capTouchText;
    private long lastUiUpdateTime;
    private final long UI_UPDATE_RATE = 200; // millisecond
    private boolean sensorListenerRegistered = false;

    private final float CAPSENSE_ABSENCE_STATUS = 0;
    private final float CAPSENSE_PRESENCE_STATUS = 1;

    private float mCapsenseStatus =  -1;

    /**
      * Track design: Please ensure its value same with Sensor.TYPE_MOTO_CAP_TOUCH
      * It doesn't depend on the framework while compile the CQATest module.
    */
    private static final int  CAP_TOUCH_TYPE = 0x10000 + 16;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_Capsense";
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.capsense);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        capTouchText = (TextView) findViewById(com.motorola.motocit.R.id.cap_touch_x);

        lastUiUpdateTime = System.currentTimeMillis() - UI_UPDATE_RATE * 2;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (sensorListenerRegistered == false)
        {
            dbgLog(TAG, "onResume() register sensor listener", 'i');

            List<Sensor> list = mSensorManager.getSensorList(CAP_TOUCH_TYPE);
            if (list.size() > 0) {
                 Sensor sensorObject = (Sensor)list.get(0);
                 dbgLog(TAG,"cap sensor name:"  + sensorObject.getName(), 'i');

                 // Check if commServer started this activity
                 // - if so then change sensor update rate
                 if (wasActivityStartedByCommServer())
                 {
                     dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'i');
                     mSensorManager.registerListener(this, sensorObject, SensorManager.SENSOR_DELAY_FASTEST);
                 }
                 else
                 {
                     dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'i');
                     mSensorManager.registerListener(this, sensorObject, SensorManager.SENSOR_DELAY_UI);
                 }

                 sensorListenerRegistered = true;
            }
            else
            {
                Toast.makeText(Capsense.this, "The device does NOT support Cap sensor", Toast.LENGTH_SHORT).show();
                Capsense.this.finish();
            }
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
        if ((event.sensor.getType() == CAP_TOUCH_TYPE) && !isFinishing())
        {
            dbgLog(TAG, "event.values[0]:"  + event.values[0], 'i');
            mCapsenseStatus = event.values[0];

            // limit UI updates as not to slam CPU and make activity
            // unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                 lastUiUpdateTime = System.currentTimeMillis();
                 if(CAPSENSE_PRESENCE_STATUS==mCapsenseStatus)
                 {
                       capTouchText.setText("Status: presence");
                 }
                 else if(CAPSENSE_ABSENCE_STATUS==mCapsenseStatus)
                 {
                       capTouchText.setText("Status: absence");
                 }
                 else
                 {
                       capTouchText.setText("Status: unknown");
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
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Capsense:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
                contentRecord("testresult.txt", "Capsense Test: PASS" + "\r\n", MODE_APPEND);

                logTestResults(TAG, TEST_PASS, null, null);
            }
            else
            {
                contentRecord("testresult.txt", "Capsense Test: FAILED" + "\r\n", MODE_APPEND);

                logTestResults(TAG, TEST_FAIL, null, null);
            }

            contentRecord("testresult.txt", "Status: " + mCapsenseStatus + "\r\n", MODE_APPEND);

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

            if(CAPSENSE_PRESENCE_STATUS == mCapsenseStatus) {
                 strDataList.add(String.format("CAP_SENSOR_STATUS=PRESENCE"));
            }
            else if(CAPSENSE_ABSENCE_STATUS == mCapsenseStatus)
            {
                 strDataList.add(String.format("CAP_SENSOR_STATUS=ABSENCE"));
            } else {
                 strDataList.add(String.format("CAP_SENSOR_STATUS=UNKNOWN"));
            }

            strDataList.add(String.format("EVENT VALUES=" + mCapsenseStatus));
            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            List<Sensor> list = mSensorManager.getSensorList(CAP_TOUCH_TYPE);
            Sensor sensorObject = (Sensor)list.get(0);

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
        strHelpList.add("This function will read the Cap Sensor");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns CAP Sensor Status");
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
}
