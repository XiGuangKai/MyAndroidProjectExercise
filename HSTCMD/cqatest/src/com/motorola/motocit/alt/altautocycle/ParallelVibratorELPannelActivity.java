/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;

public class ParallelVibratorELPannelActivity extends ALTBaseActivity
{
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        init();
    }

    @Override
    void init()
    {
        this.setTitle("Vibrator ELPannel test");
        TestUtils.dbgLog("ParallelVibratorELPannelActivity", "init", 'i');
        setContentView(com.motorola.motocit.R.layout.alt_function_elpannel);
        this.vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try
        {
            Settings.System.putInt(this.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, Constant.ELPANNEL_INTERVAL_TIME);
        }
        catch (Exception e)
        {}
    }

    @Override
    void start()
    {
        TestUtils.dbgLog("ParallelVibratorELPannelActivity", "start vibration", 'i');
        this.vibrator.vibrate(Constant.ELPANNEL_INTERVAL_TIME);
        this.isRunning = true;
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    while (isRunning)
                    {
                        Thread.sleep(Constant.ELPANNEL_INTERVAL_TIME);
                        if (isRunning)
                        {
                            Message msg = new Message();
                            msg.what = 0;
                            handler.sendMessage(msg);
                        }
                        isRunning = false;
                    }
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void setAlarm()
    {
        try
        {
            this.isRunning = false;
            Intent intent = new Intent(this, AlarmVibratorELPannelReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + Constant.ELPANNEL_INTERVAL_TIME, pendingIntent);
        }
        catch (Exception e)
        {}
        finally
        {
            killActivity();
        }
    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg)
        {
            if (msg.what == 0)
            {
                setAlarm();
            }
        }
    };

    @Override
    protected void killActivity()
    {
        release();
        TestUtils.dbgLog("ParallelVibratorELPannelActivity", "calling finish", 'i');
        this.finish();
    }

    @Override
    void release()
    {
        try
        {
            // release vibrator resource
            if (this.vibrator != null)
            {
                try
                {
                    TestUtils.dbgLog("ParallelVibratorELPannelActivity", "release vibrator", 'i');
                    this.vibrator.cancel();
                }
                catch (Exception ex)
                {}
                this.vibrator = null;
            }

            TestUtils.dbgLog("ParallelVibratorELPannelActivity", "release completed", 'i');
            logWriter(TAG, "Completed");
        }
        catch (Exception e)
        {}
    }
}
