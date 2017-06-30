/*
 * Copyright (c) 2014 - 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.irsensor;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
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

public class IRGuesture extends Test_Base implements SensorEventListener {
    public static final int mSENSOR_IR_RAW_DATA = 0x10006;

    double fIrGuestureHighTopRightValue = -9999;
    double fIrGuestureHighBottomLeftValue = -9999;
    double fIrGuestureHighBottomRightValue = -9999;
    double fIrGuestureHighBothBottomValue = -9999;
    double fIrGuestureLowTopRightValue = -9999;
    double fIrGuestureLowBottomLeftValue = -9999;
    double fIrGuestureLowBottomRightValue = -9999;
    double fIrGuestureLowBothBottomValue = -9999;
    double fIrGuestureAmbientHighValue = -9999;
    double fIrGuestureAmbientLowValue = -9999;

    double fMaxIrGuestureHighTopRightValue = 0;
    double fMaxIrGuestureHighBottomLeftValue = 0;
    double fMaxIrGuestureHighBottomRightValue = 0;
    double fMaxIrGuestureHighBothBottomValue = 0;
    double fMaxIrGuestureLowTopRightValue = 0;
    double fMaxIrGuestureLowBottomLeftValue = 0;
    double fMaxIrGuestureLowBottomRightValue = 0;
    double fMaxIrGuestureLowBothBottomValue = 0;
    double fMaxIrGuestureAmbientHighValue = 0;
    double fMaxIrGuestureAmbientLowValue = 0;

    double fMinIrGuestureHighTopRightValue = 9999;
    double fMinIrGuestureHighBottomLeftValue = 9999;
    double fMinIrGuestureHighBottomRightValue = 9999;
    double fMinIrGuestureHighBothBottomValue = 9999;
    double fMinIrGuestureLowTopRightValue = 9999;
    double fMinIrGuestureLowBottomLeftValue = 9999;
    double fMinIrGuestureLowBottomRightValue = 9999;
    double fMinIrGuestureLowBothBottomValue = 9999;
    double fMinIrGuestureAmbientHighValue = 9999;
    double fMinIrGuestureAmbientLowValue = 9999;

    private SensorManager mSensorManager;
    double mIRGuesture = -1;
    double mIrGuestureHighTopRightValue;
    double mIrGuestureHighBottomLeftValue;
    double mIrGuestureHighBottomRightValue;
    double mIrGuestureHighBothBottomValue;
    double mIrGuestureLowTopRightValue;
    double mIrGuestureLowBottomLeftValue;
    double mIrGuestureLowBottomRightValue;
    double mIrGuestureLowBothBottomValue;
    double mIrGuestureAmbientHighValue;
    double mIrGuestureAmbientLowValue;

    boolean activityStartedFromCommServer = false;

    private TextView mIrGuestureHighTopRight;
    private TextView mIrGuestureHighBottomLeft;
    private TextView mIrGuestureHighBottomRight;
    private TextView mIrGuestureHighBothBottom;
    private TextView mIrGuestureLowTopRight;
    private TextView mIrGuestureLowBottomLeft;
    private TextView mIrGuestureLowBottomRight;
    private TextView mIrGuestureLowBothBottom;
    private TextView mIrGuestureAmbientHigh;
    private TextView mIrGuestureAmbientLow;

    private TextView mIrGuestureHighTopRightMax;
    private TextView mIrGuestureHighTopRightMin;
    private TextView mIrGuestureHighBottomLeftMax;
    private TextView mIrGuestureHighBottomLeftMin;
    private TextView mIrGuestureHighBottomRightMax;
    private TextView mIrGuestureHighBottomRightMin;
    private TextView mIrGuestureHighBothBottomMax;
    private TextView mIrGuestureHighBothBottomMin;
    private TextView mIrGuestureLowTopRightMax;
    private TextView mIrGuestureLowTopRightMin;
    private TextView mIrGuestureLowBottomLeftMax;
    private TextView mIrGuestureLowBottomLeftMin;
    private TextView mIrGuestureLowBottomRightMax;
    private TextView mIrGuestureLowBottomRightMin;
    private TextView mIrGuestureLowBothBottomMax;
    private TextView mIrGuestureLowBothBottomMin;
    private TextView mIrGuestureAmbientHighMax;
    private TextView mIrGuestureAmbientHighMin;
    private TextView mIrGuestureAmbientLowMax;
    private TextView mIrGuestureAmbientLowMin;

    private long lastUiUpdateTime;

    private final long UI_UPDATE_RATE = 200; // millisecond
    private final int PRECISION = 2;

    private boolean sensorListenerRegistered = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_IRGuesture";

        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.irguesture);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        mIrGuestureHighTopRight = (TextView) findViewById(com.motorola.motocit.R.id.ir_high_topR);
        mIrGuestureHighBottomLeft = (TextView) findViewById(com.motorola.motocit.R.id.ir_high_bottomL);
        mIrGuestureHighBottomRight = (TextView) findViewById(com.motorola.motocit.R.id.ir_high_bottomR);
        mIrGuestureHighBothBottom = (TextView) findViewById(com.motorola.motocit.R.id.ir_high_bothBottom);
        mIrGuestureLowTopRight = (TextView) findViewById(com.motorola.motocit.R.id.ir_low_topR);
        mIrGuestureLowBottomLeft = (TextView) findViewById(com.motorola.motocit.R.id.ir_low_bottomL);
        mIrGuestureLowBottomRight = (TextView) findViewById(com.motorola.motocit.R.id.ir_low_bottomR);
        mIrGuestureLowBothBottom = (TextView) findViewById(com.motorola.motocit.R.id.ir_low_bothBottom);
        mIrGuestureAmbientHigh = (TextView) findViewById(com.motorola.motocit.R.id.ir_ambient_high);
        mIrGuestureAmbientLow = (TextView) findViewById(com.motorola.motocit.R.id.ir_ambient_low);

        mIrGuestureHighTopRightMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_high_topR);
        mIrGuestureHighTopRightMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_high_topR);
        mIrGuestureHighBottomLeftMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_high_bottomL);
        mIrGuestureHighBottomLeftMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_high_bottomL);
        mIrGuestureHighBottomRightMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_high_bottomR);
        mIrGuestureHighBottomRightMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_high_bottomR);
        mIrGuestureHighBothBottomMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_high_bothBottom);
        mIrGuestureHighBothBottomMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_high_bothBottom);
        mIrGuestureLowTopRightMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_low_topR);
        mIrGuestureLowTopRightMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_low_topR);
        mIrGuestureLowBottomLeftMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_low_bottomL);
        mIrGuestureLowBottomLeftMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_low_bottomL);
        mIrGuestureLowBottomRightMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_low_bottomR);
        mIrGuestureLowBottomRightMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_low_bottomR);
        mIrGuestureLowBothBottomMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_low_bothBottom);
        mIrGuestureLowBothBottomMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_low_bothBottom);
        mIrGuestureAmbientHighMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_ambient_high);
        mIrGuestureAmbientHighMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_ambient_high);
        mIrGuestureAmbientLowMax = (TextView) findViewById(com.motorola.motocit.R.id.max_ir_ambient_low);
        mIrGuestureAmbientLowMin = (TextView) findViewById(com.motorola.motocit.R.id.min_ir_ambient_low);

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
                activityStartedFromCommServer = true;
                dbgLog(TAG, "activity originated from commserver.. setting update rate to fastest", 'i');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(mSENSOR_IR_RAW_DATA), SensorManager.SENSOR_DELAY_FASTEST);
            }
            else
            {
                activityStartedFromCommServer = false;
                dbgLog(TAG, "activity originated UI .. setting update rate to UI rate", 'i');
                mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(mSENSOR_IR_RAW_DATA), SensorManager.SENSOR_DELAY_UI);
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
        if (event.sensor.getType() == mSENSOR_IR_RAW_DATA)
        {
            mIRGuesture = 0;

            mIrGuestureHighTopRightValue = event.values[0];
            if (mIrGuestureHighTopRightValue > fMaxIrGuestureHighTopRightValue)
            {
                fMaxIrGuestureHighTopRightValue = mIrGuestureHighTopRightValue;
            }

            if (mIrGuestureHighTopRightValue < fMinIrGuestureHighTopRightValue)
            {
                fMinIrGuestureHighTopRightValue = mIrGuestureHighTopRightValue;
            }

            mIrGuestureHighBottomLeftValue = event.values[1];
            if (mIrGuestureHighBottomLeftValue > fMaxIrGuestureHighBottomLeftValue)
            {
                fMaxIrGuestureHighBottomLeftValue = mIrGuestureHighBottomLeftValue;
            }

            if (mIrGuestureHighBottomLeftValue < fMinIrGuestureHighBottomLeftValue)
            {
                fMinIrGuestureHighBottomLeftValue = mIrGuestureHighBottomLeftValue;
            }

            mIrGuestureHighBottomRightValue = event.values[2];
            if (mIrGuestureHighBottomRightValue > fMaxIrGuestureHighBottomRightValue)
            {
                fMaxIrGuestureHighBottomRightValue = mIrGuestureHighBottomRightValue;
            }

            if (mIrGuestureHighBottomRightValue < fMinIrGuestureHighBottomRightValue)
            {
                fMinIrGuestureHighBottomRightValue = mIrGuestureHighBottomRightValue;
            }

            mIrGuestureHighBothBottomValue = event.values[3];
            if (mIrGuestureHighBothBottomValue > fMaxIrGuestureHighBothBottomValue)
            {
                fMaxIrGuestureHighBothBottomValue = mIrGuestureHighBothBottomValue;
            }

            if (mIrGuestureHighBothBottomValue < fMinIrGuestureHighBothBottomValue)
            {
                fMinIrGuestureHighBothBottomValue = mIrGuestureHighBothBottomValue;
            }

            mIrGuestureLowTopRightValue = event.values[4];
            if (mIrGuestureLowTopRightValue > fMaxIrGuestureLowTopRightValue)
            {
                fMaxIrGuestureLowTopRightValue = mIrGuestureLowTopRightValue;
            }

            if (mIrGuestureLowTopRightValue < fMinIrGuestureLowTopRightValue)
            {
                fMinIrGuestureLowTopRightValue = mIrGuestureLowTopRightValue;
            }

            mIrGuestureLowBottomLeftValue = event.values[5];
            if (mIrGuestureLowBottomLeftValue > fMaxIrGuestureLowBottomLeftValue)
            {
                fMaxIrGuestureLowBottomLeftValue = mIrGuestureLowBottomLeftValue;
            }

            if (mIrGuestureLowBottomLeftValue < fMinIrGuestureLowBottomLeftValue)
            {
                fMinIrGuestureLowBottomLeftValue = mIrGuestureLowBottomLeftValue;
            }

            mIrGuestureLowBottomRightValue = event.values[6];
            if (mIrGuestureLowBottomRightValue > fMaxIrGuestureLowBottomRightValue)
            {
                fMaxIrGuestureLowBottomRightValue = mIrGuestureLowBottomRightValue;
            }

            if (mIrGuestureLowBottomRightValue < fMinIrGuestureLowBottomRightValue)
            {
                fMinIrGuestureLowBottomRightValue = mIrGuestureLowBottomRightValue;
            }

            mIrGuestureLowBothBottomValue = event.values[7];
            if (mIrGuestureLowBothBottomValue > fMaxIrGuestureLowBothBottomValue)
            {
                fMaxIrGuestureLowBothBottomValue = mIrGuestureLowBothBottomValue;
            }

            if (mIrGuestureLowBothBottomValue < fMinIrGuestureLowBothBottomValue)
            {
                fMinIrGuestureLowBothBottomValue = mIrGuestureLowBothBottomValue;
            }

            mIrGuestureAmbientHighValue = event.values[8];
            if (mIrGuestureAmbientHighValue > fMaxIrGuestureAmbientHighValue)
            {
                fMaxIrGuestureAmbientHighValue = mIrGuestureAmbientHighValue;
            }

            if (mIrGuestureAmbientHighValue < fMinIrGuestureAmbientHighValue)
            {
                fMinIrGuestureAmbientHighValue = mIrGuestureAmbientHighValue;
            }

            mIrGuestureAmbientLowValue = event.values[9];
            if (mIrGuestureAmbientLowValue > fMaxIrGuestureAmbientLowValue)
            {
                fMaxIrGuestureAmbientLowValue = mIrGuestureAmbientLowValue;
            }

            if (mIrGuestureAmbientLowValue < fMinIrGuestureAmbientLowValue)
            {
                fMinIrGuestureAmbientLowValue = mIrGuestureAmbientLowValue;
            }

            // limit UI updates as not to slam CPU and make activity unresponsive
            if ((System.currentTimeMillis() - lastUiUpdateTime) > UI_UPDATE_RATE)
            {
                lastUiUpdateTime = System.currentTimeMillis();

                dbgLog(TAG, "sensor: IR Guesture sensor receive event, getting value", 'i');

                mIrGuestureHighTopRight.setTextColor(Color.GREEN);
                mIrGuestureHighTopRight.setText("" + TestUtils.round(mIrGuestureHighTopRightValue, PRECISION));
                mIrGuestureHighTopRightMax.setTextColor(Color.RED);
                mIrGuestureHighTopRightMax.setText("Max:" + TestUtils.round(fMaxIrGuestureHighTopRightValue, PRECISION));
                mIrGuestureHighTopRightMin.setTextColor(Color.YELLOW);
                mIrGuestureHighTopRightMin.setText("Min" + TestUtils.round(fMinIrGuestureHighTopRightValue, PRECISION));

                mIrGuestureHighBottomLeft.setTextColor(Color.GREEN);
                mIrGuestureHighBottomLeft.setText("" + TestUtils.round(mIrGuestureHighBottomLeftValue, PRECISION));
                mIrGuestureHighBottomLeftMax.setTextColor(Color.RED);
                mIrGuestureHighBottomLeftMax.setText("Max:" + TestUtils.round(fMaxIrGuestureHighBottomLeftValue, PRECISION));
                mIrGuestureHighBottomLeftMin.setTextColor(Color.YELLOW);
                mIrGuestureHighBottomLeftMin.setText("Min" + TestUtils.round(fMinIrGuestureHighBottomLeftValue, PRECISION));

                mIrGuestureHighBottomRight.setTextColor(Color.GREEN);
                mIrGuestureHighBottomRight.setText("" + TestUtils.round(mIrGuestureHighBottomRightValue, PRECISION));
                mIrGuestureHighBottomRightMax.setTextColor(Color.RED);
                mIrGuestureHighBottomRightMax.setText("Max:" + TestUtils.round(fMaxIrGuestureHighBottomRightValue, PRECISION));
                mIrGuestureHighBottomRightMin.setTextColor(Color.YELLOW);
                mIrGuestureHighBottomRightMin.setText("Min" + TestUtils.round(fMinIrGuestureHighBottomRightValue, PRECISION));

                mIrGuestureHighBothBottom.setTextColor(Color.GREEN);
                mIrGuestureHighBothBottom.setText("" + TestUtils.round(mIrGuestureHighBothBottomValue, PRECISION));
                mIrGuestureHighBothBottomMax.setTextColor(Color.RED);
                mIrGuestureHighBothBottomMax.setText("Max:" + TestUtils.round(fMaxIrGuestureHighBothBottomValue, PRECISION));
                mIrGuestureHighBothBottomMin.setTextColor(Color.YELLOW);
                mIrGuestureHighBothBottomMin.setText("Min" + TestUtils.round(fMinIrGuestureHighBothBottomValue, PRECISION));

                mIrGuestureLowTopRight.setTextColor(Color.GREEN);
                mIrGuestureLowTopRight.setText("" + TestUtils.round(mIrGuestureLowTopRightValue, PRECISION));
                mIrGuestureLowTopRightMax.setTextColor(Color.RED);
                mIrGuestureLowTopRightMax.setText("Max:" + TestUtils.round(fMaxIrGuestureLowTopRightValue, PRECISION));
                mIrGuestureLowTopRightMin.setTextColor(Color.YELLOW);
                mIrGuestureLowTopRightMin.setText("Min" + TestUtils.round(fMinIrGuestureLowTopRightValue, PRECISION));

                mIrGuestureLowBottomLeft.setTextColor(Color.GREEN);
                mIrGuestureLowBottomLeft.setText("" + TestUtils.round(mIrGuestureLowBottomLeftValue, PRECISION));
                mIrGuestureLowBottomLeftMax.setTextColor(Color.RED);
                mIrGuestureLowBottomLeftMax.setText("Max:" + TestUtils.round(fMaxIrGuestureLowBottomLeftValue, PRECISION));
                mIrGuestureLowBottomLeftMin.setTextColor(Color.YELLOW);
                mIrGuestureLowBottomLeftMin.setText("Min" + TestUtils.round(fMinIrGuestureLowBottomLeftValue, PRECISION));

                mIrGuestureLowBottomRight.setTextColor(Color.GREEN);
                mIrGuestureLowBottomRight.setText("" + TestUtils.round(mIrGuestureLowBottomRightValue, PRECISION));
                mIrGuestureLowBottomRightMax.setTextColor(Color.RED);
                mIrGuestureLowBottomRightMax.setText("Max:" + TestUtils.round(fMaxIrGuestureLowBottomRightValue, PRECISION));
                mIrGuestureLowBottomRightMin.setTextColor(Color.YELLOW);
                mIrGuestureLowBottomRightMin.setText("Min" + TestUtils.round(fMinIrGuestureLowBottomRightValue, PRECISION));

                mIrGuestureLowBothBottom.setTextColor(Color.GREEN);
                mIrGuestureLowBothBottom.setText("" + TestUtils.round(mIrGuestureLowBothBottomValue, PRECISION));
                mIrGuestureLowBothBottomMax.setTextColor(Color.RED);
                mIrGuestureLowBothBottomMax.setText("Max:" + TestUtils.round(fMaxIrGuestureLowBothBottomValue, PRECISION));
                mIrGuestureLowBothBottomMin.setTextColor(Color.YELLOW);
                mIrGuestureLowBothBottomMin.setText("Min" + TestUtils.round(fMinIrGuestureLowBothBottomValue, PRECISION));

                mIrGuestureAmbientHigh.setTextColor(Color.GREEN);
                mIrGuestureAmbientHigh.setText("" + TestUtils.round(mIrGuestureAmbientHighValue, PRECISION));
                mIrGuestureAmbientHighMax.setTextColor(Color.RED);
                mIrGuestureAmbientHighMax.setText("Max:" + TestUtils.round(fMaxIrGuestureAmbientHighValue, PRECISION));
                mIrGuestureAmbientHighMin.setTextColor(Color.YELLOW);
                mIrGuestureAmbientHighMin.setText("Min" + TestUtils.round(fMinIrGuestureAmbientHighValue, PRECISION));

                mIrGuestureAmbientLow.setTextColor(Color.GREEN);
                mIrGuestureAmbientLow.setText("" + TestUtils.round(mIrGuestureAmbientLowValue, PRECISION));
                mIrGuestureAmbientLowMax.setTextColor(Color.RED);
                mIrGuestureAmbientLowMax.setText("Max:" + TestUtils.round(fMaxIrGuestureAmbientLowValue, PRECISION));
                mIrGuestureAmbientLowMin.setTextColor(Color.YELLOW);
                mIrGuestureAmbientLowMin.setText("Min" + TestUtils.round(fMinIrGuestureAmbientLowValue, PRECISION));

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
            while ((delay_tries < 20) && (mIRGuesture == -1))
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

            strDataList.add(String.format("High_POWER_TOP_RIGHT=" + TestUtils.round(mIrGuestureHighTopRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_LEFT=" + TestUtils.round(mIrGuestureHighBottomLeftValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_RIGHT=" + TestUtils.round(mIrGuestureHighBottomRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTH_BOTTOM=" + TestUtils.round(mIrGuestureHighBothBottomValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_TOP_RIGHT=" + TestUtils.round(mIrGuestureLowTopRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_LEFT=" + TestUtils.round(mIrGuestureLowBottomLeftValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_RIGHT=" + TestUtils.round(mIrGuestureLowBottomRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTH_BOTTOM=" + TestUtils.round(mIrGuestureLowBothBottomValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_HIGH=" + TestUtils.round(mIrGuestureAmbientHighValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_LOW=" + TestUtils.round(mIrGuestureAmbientLowValue, PRECISION)));

            strDataList.add(String.format("High_POWER_TOP_RIGHT_MAX=" + TestUtils.round(fMaxIrGuestureHighTopRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_LEFT_MAX=" + TestUtils.round(fMaxIrGuestureHighBottomLeftValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_RIGHT_MAX=" + TestUtils.round(fMaxIrGuestureHighBottomRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTH_BOTTOM_MAX=" + TestUtils.round(fMaxIrGuestureHighBothBottomValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_TOP_RIGHT_MAX=" + TestUtils.round(fMaxIrGuestureLowTopRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_LEFT_MAX=" + TestUtils.round(fMaxIrGuestureLowBottomLeftValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_RIGHT_MAX=" + TestUtils.round(fMaxIrGuestureLowBottomRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTH_BOTTOM_MAX=" + TestUtils.round(fMaxIrGuestureLowBothBottomValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_HIGH_MAX=" + TestUtils.round(fMaxIrGuestureAmbientHighValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_LOW_MAX=" + TestUtils.round(fMaxIrGuestureAmbientLowValue, PRECISION)));

            strDataList.add(String.format("High_POWER_TOP_RIGHT_MIN=" + TestUtils.round(fMinIrGuestureHighTopRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_LEFT_MIN=" + TestUtils.round(fMinIrGuestureHighBottomLeftValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTTOM_RIGHT_MIN=" + TestUtils.round(fMinIrGuestureHighBottomRightValue, PRECISION)));
            strDataList.add(String.format("HIGH_POWER_BOTH_BOTTOM_MIN=" + TestUtils.round(fMinIrGuestureHighBothBottomValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_TOP_RIGHT_MIN=" + TestUtils.round(fMinIrGuestureLowTopRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_LEFT_MIN=" + TestUtils.round(fMinIrGuestureLowBottomLeftValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTTOM_RIGHT_MIN=" + TestUtils.round(fMinIrGuestureLowBottomRightValue, PRECISION)));
            strDataList.add(String.format("LOW_POWER_BOTH_BOTTOM_MIN=" + TestUtils.round(fMinIrGuestureLowBothBottomValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_HIGH_MIN=" + TestUtils.round(fMinIrGuestureAmbientHighValue, PRECISION)));
            strDataList.add(String.format("AMBIENT_PRE_LOW_MIN=" + TestUtils.round(fMinIrGuestureAmbientLowValue, PRECISION)));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLEAR_MAX_MIN_READINGS"))
        {
            fMaxIrGuestureHighTopRightValue = 0;
            fMaxIrGuestureHighBottomLeftValue = 0;
            fMaxIrGuestureHighBottomRightValue = 0;
            fMaxIrGuestureHighBothBottomValue = 0;
            fMaxIrGuestureLowTopRightValue = 0;
            fMaxIrGuestureLowBottomLeftValue = 0;
            fMaxIrGuestureLowBottomRightValue = 0;
            fMaxIrGuestureLowBothBottomValue = 0;
            fMaxIrGuestureAmbientHighValue = 0;
            fMaxIrGuestureAmbientLowValue = 0;

            fMinIrGuestureHighTopRightValue = 9999;
            fMinIrGuestureHighBottomLeftValue = 9999;
            fMinIrGuestureHighBottomRightValue = 9999;
            fMinIrGuestureHighBothBottomValue = 9999;
            fMinIrGuestureLowTopRightValue = 9999;
            fMinIrGuestureLowBottomLeftValue = 9999;
            fMinIrGuestureLowBottomRightValue = 9999;
            fMinIrGuestureLowBothBottomValue = 9999;
            fMinIrGuestureAmbientHighValue = 9999;
            fMinIrGuestureAmbientLowValue = 9999;

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SENSOR_INFO"))
        {
            List<String> strDataList = new ArrayList<String>();

            Sensor sensorObject = mSensorManager.getDefaultSensor(mSENSOR_IR_RAW_DATA);

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
        strHelpList.add("This function will read the IR sensor raw data");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_READING - Returns IR raw data");
        strHelpList.add("  ");
        strHelpList.add("CLEAR_MAX_MIN_READINGS - Clears Max and Min IR sensor raw values");
        strHelpList.add("  ");
        strHelpList.add("GET_SENSOR_INFO - Returns the following information about the sensor");
        strHelpList.add(" MAXIMUM_RANGE - maximum range of the sensor in the sensor's unit");
        strHelpList.add(" MIN_DELAY - the minimum delay allowed between two events in microsecond " + "or zero if this sensor only returns a value when the data it's measuring changes");
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
        contentRecord("testresult.txt", "IR Gesture Test: FAILED" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "HIGH_POWER_TOP_RIGHT=" + mIrGuestureHighTopRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_LEFT=" + mIrGuestureHighBottomLeftValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_RIGHT=" + mIrGuestureHighBottomRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTH_BOTTOM=" + mIrGuestureHighBothBottomValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_TOP_RIGHT=" + mIrGuestureLowTopRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTTOM_LEFT=" + mIrGuestureLowBottomLeftValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTTOM_RIGHT=" + mIrGuestureLowBottomRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTH_BOTTOM=" + mIrGuestureLowBothBottomValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "AMBIENT_PRE_HIGH=" + mIrGuestureAmbientHighValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "AMBIENT_PRE_LOW=" + mIrGuestureAmbientLowValue + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "IR Gesture Test: PASS" + "\r\n", MODE_APPEND);

        contentRecord("testresult.txt", "HIGH_POWER_TOP_RIGHT=" + mIrGuestureHighTopRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_LEFT=" + mIrGuestureHighBottomLeftValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_RIGHT=" + mIrGuestureHighBottomRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "HIGH_POWER_BOTH_BOTTOM=" + mIrGuestureHighBothBottomValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_TOP_RIGHT=" + mIrGuestureLowTopRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTTOM_LEFT=" + mIrGuestureLowBottomLeftValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTTOM_RIGHT=" + mIrGuestureLowBottomRightValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "LOW_POWER_BOTH_BOTTOM=" + mIrGuestureLowBothBottomValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "AMBIENT_PRE_HIGH=" + mIrGuestureAmbientHighValue + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "AMBIENT_PRE_LOW=" + mIrGuestureAmbientLowValue + "\r\n\r\n", MODE_APPEND);

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
                contentRecord("testresult.txt", "IR Gesture Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }
            else
            {
                contentRecord("testresult.txt", "IR Gesture Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }

            contentRecord("testresult.txt", "HIGH_POWER_TOP_RIGHT=" + mIrGuestureHighTopRightValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_LEFT=" + mIrGuestureHighBottomLeftValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HIGH_POWER_BOTTOM_RIGHT=" + mIrGuestureHighBottomRightValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "HIGH_POWER_BOTH_BOTTOM=" + mIrGuestureHighBothBottomValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "LOW_POWER_TOP_RIGHT=" + mIrGuestureLowTopRightValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "LOW_POWER_BOTTOM_LEFT=" + mIrGuestureLowBottomLeftValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "LOW_POWER_BOTTOM_RIGHT=" + mIrGuestureLowBottomRightValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "LOW_POWER_BOTH_BOTTOM=" + mIrGuestureLowBothBottomValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "AMBIENT_PRE_HIGH=" + mIrGuestureAmbientHighValue + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "AMBIENT_PRE_LOW=" + mIrGuestureAmbientLowValue + "\r\n\r\n", MODE_APPEND);

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
        testResultName.add("High_POWER_TOP_RIGHT");
        testResultName.add("HIGH_POWER_BOTTOM_LEFT");
        testResultName.add("HIGH_POWER_BOTTOM_RIGHT");
        testResultName.add("HIGH_POWER_BOTH_BOTTOM");
        testResultName.add("LOW_POWER_TOP_RIGHT");
        testResultName.add("LOW_POWER_BOTTOM_LEFT");
        testResultName.add("LOW_POWER_BOTTOM_RIGHT");
        testResultName.add("LOW_POWER_BOTH_BOTTOM");
        testResultName.add("AMBIENT_PRE_HIGH");
        testResultName.add("AMBIENT_PRE_LOW");

        testResultValues.add(String.valueOf(mIrGuestureHighTopRightValue));
        testResultValues.add(String.valueOf(mIrGuestureHighBottomLeftValue));
        testResultValues.add(String.valueOf(mIrGuestureHighBottomRightValue));
        testResultValues.add(String.valueOf(mIrGuestureHighBothBottomValue));
        testResultValues.add(String.valueOf(mIrGuestureLowTopRightValue));
        testResultValues.add(String.valueOf(mIrGuestureLowBottomLeftValue));
        testResultValues.add(String.valueOf(mIrGuestureLowBottomRightValue));
        testResultValues.add(String.valueOf(mIrGuestureLowBothBottomValue));
        testResultValues.add(String.valueOf(mIrGuestureAmbientHighValue));
        testResultValues.add(String.valueOf(mIrGuestureAmbientLowValue));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
