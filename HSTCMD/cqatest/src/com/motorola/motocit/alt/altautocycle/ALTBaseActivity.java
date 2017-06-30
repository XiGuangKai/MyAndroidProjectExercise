/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.LogTools;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public abstract class ALTBaseActivity extends Activity implements OnGestureListener
{
    protected boolean isRunning;
    protected String logFile = Constant.SD_LOG_FILE_PATH;
    protected int realIntervalTime = Constant.CYCLE_INTERVAL_TIME;
    protected int realLogTime = Constant.CYCLE_RUNNING_LOG;
    protected String TAG = "ALTBaseActivity";
    private GestureDetector gestureDetector;
    protected boolean mBringupCycle;

    private PowerManager pm = null;
    private KeyguardManager key_guard = null;
    private KeyguardLock mKeyguardLock = null;
    private PowerManager.WakeLock wl = null;

    // action used by stop cmd to stop alt base activity
    public static final String ACTION_ALT_TEST_BASE_FINISH = "motocit.commserver.intent.action.alt_test_base_finish";

    abstract void release();

    abstract void init();

    abstract void start();

    public void altTestSetup()
    {
        ContentResolver resolver = getContentResolver();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        key_guard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        if (null == wl)
        {
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG);
            wl.acquire();
        }

        if (null == mKeyguardLock)
        {
            mKeyguardLock = key_guard.newKeyguardLock(KEYGUARD_SERVICE);
            mKeyguardLock.disableKeyguard();
        }

        Settings.System.putInt(resolver, "screen_brightness_mode", 0);

        Window window = getWindow(); // keep klocwork happy
        if (null != window)
        {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.screenBrightness = 1.0f;
            window.setAttributes(lp);
        }
    }

    public void altTestSetupRelease()
    {
        if (null != wl)
        {
            wl.release();
            wl = null;
        }
        if (null != mKeyguardLock)
        {
            mKeyguardLock.reenableKeyguard();
            mKeyguardLock = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.gestureDetector = new GestureDetector(this);

        IntentFilter altTestBaseFilter = new IntentFilter(ACTION_ALT_TEST_BASE_FINISH);
        this.registerReceiver(altTestBaseReceiver, altTestBaseFilter);

        altTestSetup();
        getLogFilePath();
        getBringupCycle();
        init();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        start();
    }

    protected void showToast(String toastStr)
    {
        Toast.makeText(this, toastStr, Toast.LENGTH_SHORT).show();
    }

    protected void sendMessage(Handler handler, int msgInt)
    {
        Message msg = new Message();
        msg.what = msgInt;
        handler.sendMessage(msg);
    }

    protected BroadcastReceiver altTestBaseReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            TestUtils.dbgLog(TAG, "altTestBaseReceiver onReceive() called", 'i');

            String action = intent.getAction();
            if (null != action)
            {
                if (action.equalsIgnoreCase(ACTION_ALT_TEST_BASE_FINISH))
                {
                    TestUtils.dbgLog(TAG, "exiting test", 'i');
                    exitApps();
                }
            }
        }
    };

    protected void killActivity()
    {
        TestUtils.dbgLog(TAG, "in ALT base killActivity(), call finish", 'i');
        finish();
    }

    protected void getLogFilePath()
    {
        try
        {
            SharedPreferences settings = this.getSharedPreferences(
                    "altautocycle", 0);
            if (settings != null)
            {
                this.logFile = settings.getString("log_file",
                        Constant.SD_LOG_FILE_PATH);
                if (!settings.getBoolean("start_flag", true))
                {
                    this.realIntervalTime = Constant.VERIFY_CYCLE_INTERVAL_TIME;
                    this.realLogTime = Constant.VERIFY_CYCLE_RUNNING_LOG;
                }
            }
        }
        catch (Exception e)
        {}
    }

    protected void getBringupCycle()
    {
        mBringupCycle = false;
        SharedPreferences settings = this.getSharedPreferences(
                "altautocycle", 0);
        if (settings != null)
        {
            mBringupCycle = settings.getBoolean("bringup_cycle", false);
        }
    }

    protected void checkProcess()
    {
        try
        {
            SharedPreferences settings = this.getSharedPreferences(
                    "altautocycle", 0);
            if (settings != null)
            {
                TestUtils.dbgLog(TAG, "current step in check process=" + settings.getInt("current_step", 0), 'i');
                TestUtils.dbgLog(TAG, "getActivityStep=" + Constant.getActivityStep(TAG), 'i');
                if (settings.getInt("current_step", 0) != Constant
                        .getActivityStep(TAG))
                {
                    TestUtils.dbgLog(TAG, "call finish", 'i');
                    this.finish();
                }
            }
        }
        catch (Exception e)
        {}
    }

    protected void logWriter(String name, String action)
    {
        try
        {
            SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuffer sb = new StringBuffer();
            sb.append(name).append(" ").append(action).append(" at ")
                    .append(sf.format(new Date())).append("\r\n");
            LogTools.log(this.logFile, sb.toString());
        }
        catch (Exception e)
        {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        menu.add(0, Menu.FIRST, 0, "Exit Auto Cycling");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item)
    {
        TestUtils.dbgLog(TAG, "onMenuItemSelected-exitApps", 'i');
        exitApps();
        return super.onMenuItemSelected(featureId, item);
    }

    private void exitAppsDialog()
    {
        try
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Exit Auto Cycling");
            builder.setCancelable(false)
                    .setPositiveButton("Exit",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id)
                                {
                                    TestUtils.dbgLog(TAG, "AlertDialog exitApps", 'i');
                                    exitApps();
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id)
                                {}
                            });

            AlertDialog alert = builder.create();
            alert.show();
        }
        catch (Exception e)
        {}
    }

    private void exitApps()
    {
        this.isRunning = false;
        try
        {
            AlarmManager alarmManager = (AlarmManager) this
                    .getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(
                    this, AlarmReceiver.class), 0));
            alarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(
                    this, AlarmVibratorELPannelReceiver.class), 0));

        }
        catch (Exception e)
        {}
        try
        {
            this.stopService(new Intent(this, FunctionTestService.class));
            this.stopService(new Intent(this, ParallelAudioBTWiFiService.class));
            this.stopService(new Intent(this,
                    ParallelVibratorELPannelService.class));
            this.stopService(new Intent(this,
                    ParallelEarpieceProxLightAcceleBTWiFiService.class));
            this.stopService(new Intent(this, ResetAlarmService.class));
        }
        catch (Exception e)
        {}

        try
        {
            SharedPreferences settings = this.getSharedPreferences("altautocycle", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("start_flag", false);
            editor.commit();
        }
        catch (Exception e)
        {}

        killActivity();
    }

    @Override
    public void onBackPressed()
    {
        if (Constant.DEBUG_FLAG)
        {
            super.onBackPressed();
        }
        return;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (Constant.DEBUG_FLAG)
        {
            return super.onKeyDown(keyCode, event);
        }
        else
        {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (gestureDetector.onTouchEvent(event))
            return true;
        else
            return false;
        // return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent arg0)
    {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
            float arg3)
    {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent arg0)
    {
        exitAppsDialog();
    }

    @Override
    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2,
            float arg3)
    {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent arg0)
    {}

    @Override
    public boolean onSingleTapUp(MotionEvent arg0)
    {
        return false;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if (altTestBaseReceiver != null)
        {
            TestUtils.dbgLog(TAG, "OnDestroy() unregisterReceiver(altTestBaseReceiver)", 'i');
            this.unregisterReceiver(altTestBaseReceiver);
            altTestBaseReceiver = null;
        }

    }
}
