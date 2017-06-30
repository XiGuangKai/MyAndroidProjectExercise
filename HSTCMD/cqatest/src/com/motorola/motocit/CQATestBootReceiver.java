/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.io.File;
import java.util.Locale;

import com.motorola.motocit.alt.altautocycle.AlarmReceiver;
import com.motorola.motocit.alt.altautocycle.AlertDialogActivity;
import com.motorola.motocit.alt.altautocycle.AltMainActivity;
import com.motorola.motocit.alt.altautocycle.util.Constant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;

public class CQATestBootReceiver extends BroadcastReceiver
{
    private SharedPreferences settings;
    private boolean isSetAlarm;
    private Context contextALT;

    public CQATestBootReceiver()
    {
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();
        contextALT = context;

        TestUtils.dbgLog("CQATest:CQATestBootReceiver", "onReceive() "+action, 'i');

        PackageManager pm = context.getPackageManager();
        ComponentName comp = new ComponentName(context, AppMainActivity.class);

        if ((pm != null) && (comp != null))
        {
            int enabled = pm.getComponentEnabledSetting(comp);
            String bootmode = SystemProperties.get("ro.bootmode", "normal");
            StrictMode.ThreadPolicy old = StrictMode.getThreadPolicy();
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(old).permitDiskWrites().build());

            if (TestUtils.isFactoryCableBoot() || bootmode.equals("bp-tools"))
            {
                if (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                {
                    pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                }
            }
            else if ((TestUtils.isUserdebugEngBuild() || TestUtils.isOdmDevice())
                    && (TestUtils.getAutoStartCommServer().toUpperCase(Locale.US).contains("YES") || TestUtils.getAutoCQAStart()
                            .toUpperCase(Locale.US).contains("YES")))
            {
                if (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                {
                    pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                }
            }
            else if (enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            {
                pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            }

            StrictMode.setThreadPolicy(old);

        }

        if ((TestUtils.isFactoryCableBoot() == true)
                || ((TestUtils.isUserdebugEngBuild() || TestUtils.isOdmDevice()) && TestUtils.getAutoStartCommServer().toUpperCase(Locale.US).contains("YES")))
        {
            if (TestUtils.isCommServerRunning(context) == false)
            {
                TestUtils.dbgLog("CQATest:CQATestBootReceiver", "Starting CommServer", 'i');
                Intent startCommServer = new Intent();
                startCommServer.setClass(context, CommServer.class);
                context.startService(startCommServer);

                if (TestUtils.getAutoCQAStartFactory().toUpperCase(Locale.US).contains("YES"))
                {
                    TestUtils.dbgLog("CQATest:CQATestBootReceiver", "Auto Starting CQA Menu Mode Factory", 'i');
                    try
                    {
                        Intent startTestMain = new Intent();
                        startTestMain.setClass(context, AppMainActivity.class);
                        startTestMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(startTestMain);
                    }
                    catch (Exception e)
                    {
                        TestUtils.dbgLog("CQATest:CQATestBootReceiver", "Exception: " + e.toString(), 'i');
                    }
                }
            }
        }
        else if ((TestUtils.isUserdebugEngBuild() || TestUtils.isOdmDevice()) && TestUtils.getAutoCQAStart().toUpperCase(Locale.US).contains("YES"))
        {
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "Auto Starting CQA Menu Mode", 'i');
            Intent startTestMain = new Intent();
            startTestMain.setClass(context, AppMainActivity.class);
            startTestMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startTestMain);
        }

        if (Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState()))
        {
            File file = new File(Constant.SD_PATH);
            if (!file.exists())
            {
                if (file.mkdirs())
                {
					TestUtils.dbgLog("CQATest:CQATestBootReceiver", "create folder for alt test", 'i');
                }
            }
        }
        TestUtils.dbgLog("CQATest:CQATestBootReceiver", "device boot completed", 'i');

        boolean result = false;
        try
        {
            settings = context.getSharedPreferences("altautocycle", 0);
            result = settings.getBoolean("start_flag", false);
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "In CQATestBootReceiver, startFlag = " + result, 'i');
        }
        catch (Exception e)
        {
            result = false;
        }

        if (!result)
        {
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "no need to start test, start_flag=false", 'i');
            return;
        }
        else
        {
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "start the test, start_flag=true", 'i');
        }

        if (TestUtils.isFactoryCableBoot() == false)
        {
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "device not boot to factory mode", 'i');
            Intent i = new Intent();
            i.setClass(context, AlertDialogActivity.class);
            i.putExtra("title", context.getResources().getString(com.motorola.motocit.R.string.factory_mode_error));
            i.putExtra("message", context.getResources().getString(com.motorola.motocit.R.string.factory_mode_alert));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return;
        }

        if (Constant.AUTO_STARTING_CYCLE)
        {
            TestUtils.dbgLog("CQATest:CQATestBootReceiver", "autocycle restart", 'i');
            cancelAlarm();
            startTest();
        }
    }

    private void startTest()
    {
        setAlarm(Constant.CYCLE_INTERVAL_TIME + 30 * 1000);
        if (this.isSetAlarm)
        {
            initApplication();
        }
    }

    private void setAlarm(int waitTime)
    {
        this.isSetAlarm = false;
        try
        {
            Intent intent = new Intent(contextALT, com.motorola.motocit.alt.altautocycle.AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    contextALT, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) contextALT
                    .getSystemService(Context.ALARM_SERVICE);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(), waitTime, pendingIntent);
            this.isSetAlarm = true;
        }
        catch (Exception e)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e1)
            {}
            startTest();
        }
    }

    void cancelAlarm()
    {
        try
        {
            Intent intent = new Intent(contextALT, com.motorola.motocit.alt.altautocycle.AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    contextALT, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) contextALT
                    .getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        }
        catch (Exception e)
        {}
    }

    private void initApplication()
    {
        TestUtils.dbgLog("CQATest:CQATestBootReceiver", "init app", 'i');
        settings = contextALT.getSharedPreferences("altautocycle", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("current_step", 0);
        editor.commit();
    }
}
