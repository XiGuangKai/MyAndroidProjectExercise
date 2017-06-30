/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

public class ResetAlarmService extends ALTBaseService
{
    @Override
    void process()
    {
        if (this.startFlag)
        {
            resetWaitAlarm(realIntervalTime + 30 * 1000);
        }
        else
        {
            resetWaitAlarm(realIntervalTime + 5 * 1000);
        }
    }

    private void resetWaitAlarm(int waitTime)
    {
        try
        {
            Intent intent = new Intent(this, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                    intent, 0);
            AlarmManager alarmManager = (AlarmManager) this
                    .getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000, waitTime,
                    pendingIntent);
            SharedPreferences settings = this.getSharedPreferences(
                    "altautocycle", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("wait_start", false);
            editor.commit();
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
    {}
}
