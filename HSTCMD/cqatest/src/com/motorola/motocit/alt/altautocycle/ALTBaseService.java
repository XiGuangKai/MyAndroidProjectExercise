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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

public abstract class ALTBaseService extends Service
{
    protected String logFile = Constant.SD_LOG_FILE_PATH;
    protected int realIntervalTime = Constant.CYCLE_INTERVAL_TIME;
    protected int realLogTime = Constant.CYCLE_RUNNING_LOG;
    protected boolean startFlag;
    protected boolean isRunning;

    @Override
    public void onCreate()
    {
        super.onCreate();
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        super.onStart(intent, startId);
        TestUtils.dbgLog("CQATest:ALTBaseService", "onStart", 'i');
        getLogFilePath();
        process();
    }

    abstract void process();

    abstract void release();

    protected void logWriter(final String name, final String action)
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    StringBuffer sb = new StringBuffer();
                    sb.append(name).append(" ").append(action).append(" at ").append(sf.format(new Date())).append("\r\n");
                    LogTools.log(logFile, sb.toString());
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    protected void killService()
    {
        this.isRunning = false;
        TestUtils.dbgLog("CQATest:ALTBaseService", "killService,this.isRunning="+this.isRunning, 'i');
        release();
        this.stopSelf();
    }

    private void getLogFilePath()
    {
        try
        {
            SharedPreferences settings = this.getSharedPreferences(
                    "altautocycle", 0);
            if (settings != null)
            {
                this.logFile = settings.getString("log_file",
                        Constant.SD_LOG_FILE_PATH);
                this.startFlag = settings.getBoolean("start_flag", true);
                if (!this.startFlag)
                {
                    this.realIntervalTime = Constant.VERIFY_CYCLE_INTERVAL_TIME;
                    this.realLogTime = Constant.VERIFY_CYCLE_RUNNING_LOG;
                }
            }
        }
        catch (Exception e)
        {}
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        this.isRunning = false;
        TestUtils.dbgLog("CQATest:ALTBaseService", "onDestroy,this.isRunning="+this.isRunning, 'i');
        release();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
