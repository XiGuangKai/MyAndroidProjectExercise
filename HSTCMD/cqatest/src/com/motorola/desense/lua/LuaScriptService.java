/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense.lua;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class LuaScriptService extends Service {

    public static final int MSG_REGISTER_CLIENT = 1;

    public static final int MSG_UNREGISTER_CLIENT = 2;

    public static final int MSG_RUN_SCRIPT = 3;

    public static final int MSG_SCRIPT_RESULT = 4;

    public static final String EXTRA_DIRECTORY = "com.motorola.desense.lua.directory";

    public static final String EXTRA_FILENAME = "com.motorola.desense.lua.filename";

    public static final int RESULT_PASS = 1;

    public static final int RESULT_FAIL = 0;

    Messenger mClient = null;

    private final Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch(msg.what){
            case MSG_REGISTER_CLIENT:
                mClient = msg.replyTo;
                break;
            case MSG_UNREGISTER_CLIENT:
                mClient = null;
                LuaScriptExecutor.finish();
                break;
            case MSG_RUN_SCRIPT:
                Bundle data = msg.getData();
                String dir = (null !=  data) ? data.getString(EXTRA_DIRECTORY) : null;
                String file = (null !=  data) ? data.getString(EXTRA_FILENAME) : null;
                if(null == dir || null == file || null == mClient) return;

                int result = LuaScriptExecutor.executeScript(dir, file) ? RESULT_PASS : RESULT_FAIL;

                // Send the result back
                msg = Message.obtain(null, MSG_SCRIPT_RESULT, result, 0);
                msg.setData(data);
                try {
                    mClient.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    mClient = null; // The client is dead.
                }
                break;
            default:
                super.handleMessage(msg);
            }
        }

    };

    private final Messenger mMessenger = new Messenger(mHandler);

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

}
