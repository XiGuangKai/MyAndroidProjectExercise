/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import com.motorola.motocit.TestUtils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ParallelVibratorELPannelService extends Service
{

    @Override
    public void onCreate()
    {
        TestUtils.dbgLog("ParallelVibratorELPannelService", "onCreate", 'i');
        super.onCreate();
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        super.onStart(intent, startId);
        TestUtils.dbgLog("ParallelVibratorELPannelService", "onStart", 'i');
        try
        {
            Intent mIntent = new Intent(this, ParallelVibratorELPannelActivity.class);
            mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(mIntent);
        }
        catch (Exception e)
        {}
        finally
        {
            this.stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent arg0)
    {
        return null;
    }

}
