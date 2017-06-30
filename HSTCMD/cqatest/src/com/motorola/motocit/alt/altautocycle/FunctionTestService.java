/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import android.util.Log;

public class FunctionTestService extends ALTBaseService
{
    private int testStep;
    private SharedPreferences settings;
    private boolean startFlag;

    @Override
    void process()
    {
        init();
        startTest();
    }

    private void init()
    {
        Constant.setAutoCycleItems();

        try
        {
            this.settings = this.getSharedPreferences("altautocycle", 0);
            if (this.settings != null)
            {
                int curStep = this.settings.getInt("current_step", 0);
                this.testStep = curStep;
                TestUtils.dbgLog("FunctionTestService", "testStep = " + curStep, 'i');
                this.logFile = settings.getString("log_file", Constant.SD_LOG_FILE_PATH);
                this.startFlag = settings.getBoolean("start_flag", true);
                TestUtils.dbgLog("FunctionTestService", "startFlag = " + startFlag, 'i');
            }
            else
            {
                this.testStep = 0;
            }
        }
        catch (Exception e)
        {
            this.testStep = 0;
        }
    }

    private void startTest()
    {
        try
        {
            if (this.startFlag)
            {
                TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=true", 'i');
                TestUtils.dbgLog("FunctionTestService", "Checking test time settings", 'i');
                TestUtils.dbgLog("FunctionTestService", "CYCLE_WAITING_TIME=" + Constant.CYCLE_WAITING_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "CYCLE_INTERVAL_TIME=" + Constant.CYCLE_INTERVAL_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "CYCLE_RUNNING_LOG=" + Constant.CYCLE_RUNNING_LOG, 'i');
                TestUtils.dbgLog("FunctionTestService", "ELPANNEL_INTERVAL_TIME=" + Constant.ELPANNEL_INTERVAL_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "cycle_item_len=" + Constant.AUTO_CYCLE_ITEMS.length, 'i');

                if (this.testStep == (Constant.AUTO_CYCLE_ITEMS.length))
                {
                    TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=true, going to reset waitAlarm", 'i');
                    resetWaitAlarm(Constant.CYCLE_WAITING_TIME);
                }
                else
                {
                    TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=true, going to do auto cycle test", 'i');
                    Intent intent = generateIntent(Constant.AUTO_CYCLE_ITEMS[this.testStep]);
                    int currentStep = this.testStep;
                    if (currentStep == 0)
                    {
                        TestUtils.dbgLog("FunctionTestService", "ALT auto cycle test started", 'i');
                        logWriter("************************************************** ALT auto cycle test", "STARTED");
                    }
                    saveNextStep();
                    // logWriter("Saved next step", "[" + this.testStep + "]");
                    if (intent != null)
                    {
                        TestUtils.dbgLog("FunctionTestService", "intent is not null", 'i');
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        this.startActivity(intent);
                        TestUtils.dbgLog("FunctionTestService", "startFlag=true. Starting test - " + Constant.AUTO_CYCLE_ITEMS[currentStep], 'i');
                        logWriter(Constant.AUTO_CYCLE_ITEMS[currentStep], "Starting");
                        this.stopSelf();
                    }
                }
            }
            else
            {
                TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=false", 'i');
                TestUtils.dbgLog("FunctionTestService", "Checking test time settings", 'i');
                TestUtils.dbgLog("FunctionTestService", "VERIFY_CYCLE_WAITING_TIME=" + Constant.VERIFY_CYCLE_WAITING_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "VERIFY_CYCLE_INTERVAL_TIME=" + Constant.VERIFY_CYCLE_INTERVAL_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "VERIFY_CYCLE_RUNNING_LOG=" + Constant.VERIFY_CYCLE_RUNNING_LOG, 'i');
                TestUtils.dbgLog("FunctionTestService", "ELPANNEL_INTERVAL_TIME=" + Constant.ELPANNEL_INTERVAL_TIME, 'i');
                TestUtils.dbgLog("FunctionTestService", "cycle_item_len=" + Constant.AUTO_CYCLE_ITEMS.length, 'i');

                if (this.testStep == (Constant.AUTO_CYCLE_ITEMS.length))
                {
                    TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=false, currect test step reaches the last, reset alarm", 'i');
                    resetWaitAlarm(Constant.VERIFY_CYCLE_WAITING_TIME);
                }
                else
                {
                    TestUtils.dbgLog("FunctionTestService", "startTest, startFlag=false, going to test", 'i');
                    Intent intent = generateIntent(Constant.AUTO_CYCLE_ITEMS[this.testStep]);
                    int currentStep = this.testStep;
                    if (currentStep == 0)
                    {
                        logWriter("************************************************** ALT auto cycle test", "STARTED");
                    }
                    saveNextStep();
                    // logWriter("Saved next step", "[" + this.testStep + "]");
                    if (intent != null)
                    {
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        this.startActivity(intent);
                        TestUtils.dbgLog("FunctionTestService", "startFlag=false. Starting test - " + Constant.AUTO_CYCLE_ITEMS[currentStep], 'i');
                        logWriter(Constant.AUTO_CYCLE_ITEMS[currentStep], "Starting");
                        this.stopSelf();
                    }
                }
            }
        }
        catch (Exception e)
        {}
    }

    private void setNextStep()
    {
        try
        {
            if (this.testStep < Constant.AUTO_CYCLE_ITEMS.length)
            {
                this.testStep++;
            }
            else
            {
                this.testStep = 0;
            }
        }
        catch (Exception e)
        {}
    }

    private Intent generateIntent(String name)
    {
        Intent intent = null;
        try
        {
            intent = new Intent(this, getTestClass(name));
        }
        catch (Exception e)
        {}
        return intent;
    }

    // @SuppressWarnings("unchecked")
    private Class<Activity> getTestClass(String className)
            throws ClassNotFoundException
    {
        return (Class<Activity>) Class.forName(Constant.PACKAGE_NAME + "." + className);
    }

    private void saveNextStep()
    {
        try
        {
            this.settings = this.getSharedPreferences("altautocycle", 0);
            SharedPreferences.Editor editor = settings.edit();
            if (this.testStep == Constant.AUTO_CYCLE_ITEMS.length)
            {
                editor.putBoolean("wait_start", true);
            }
            else
            {
                editor.putBoolean("wait_start", false);
            }
            setNextStep();
            editor.putInt("current_step", this.testStep);
            editor.commit();
        }
        catch (Exception e)
        {}
    }

    private void resetWaitAlarm(int waitTime)
    {
        try
        {
            Intent intent = new Intent(this, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + waitTime, waitTime, pendingIntent);
            saveNextStep();
            // logWriter("Saved next step after reset Alarm", "[" +
            // this.testStep + "]");
            this.stopSelf();
        }
        catch (Exception e)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e1)
            {}
            finally
            {
                resetWaitAlarm(waitTime);
            }
        }
    }

    @Override
    void release()
    {
        if (this.settings != null)
        {
            this.settings = null;
        }
    }
}
