/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.LogTools;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;

public class ALTBaseReceiver extends BroadcastReceiver
{
    private String logFile = Constant.SD_LOG_FILE_PATH;

    @Override
    public void onReceive(Context context, Intent intent)
    {}

    protected void getLogFilePath(Context context)
    {
        try
        {
            SharedPreferences settings = context.getSharedPreferences(
                    "altautocycle", 0);
            if (settings != null)
            {
                this.logFile = settings.getString("log_file",
                        Constant.SD_LOG_FILE_PATH);
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
}
