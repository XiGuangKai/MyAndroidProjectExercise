/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.motorola.desense.lua.LuaScriptService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;

public class ScriptExecutor
{
    private String mTimestamp;

    private Context mContext;

    private WakeLock mWakelock;

    private Queue<String> mScripts;

    public interface Callback {
        void updateProgress(String band, String result, boolean logging);
        void finishTests();
    }

    protected Callback mCallback;

    private final Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            Bundle data = msg.getData();
            switch (msg.what) {
            case LuaScriptService.MSG_SCRIPT_RESULT:
                int result = msg.arg1;
                if(null != data){
                    String script = getScriptName(data.getString(LuaScriptService.EXTRA_FILENAME));
                    mCallback.updateProgress(script, (result == LuaScriptService.RESULT_PASS)? "PASS" : "FAIL", true);
                }
                runNextScripts();
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };

    private final Messenger mMessenger = new Messenger(mHandler);

    /** Messenger for communicating with service. */
    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            try {
                Message msg = Message.obtain(null, LuaScriptService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;

                mService = new Messenger(service);
                mService.send(msg);

                runNextScripts();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    void doBindService() {
        mContext.bindService(new Intent(mContext, LuaScriptService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, LuaScriptService.MSG_UNREGISTER_CLIENT);
                    mService.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            // Detach our existing connection.
            mContext.unbindService(mConnection);
            mIsBound = false;
        }
    }
    public ScriptExecutor(Context context, Callback callback, String timestamp) {
        mScripts = new LinkedList<String>();
        mContext = context;
        mCallback = callback;
        mTimestamp = timestamp;
    }

    public void execute(List<String> filenames) {
        for(String f : filenames){
            // Do not log Waiting...
            mCallback.updateProgress(getScriptName(f), "-", false);
        }
        // Start band test after 2s till window get visibility
        SystemClock.sleep(2000);

        mScripts.clear();
        mScripts.addAll(filenames);

        acquireWakelock();
        doBindService();
    }

    private void runNextScripts() {
        if (!mScripts.isEmpty()) {
            String filename = mScripts.poll();
            mCallback.updateProgress(getScriptName(filename), "Running...", true);
            try {
                Bundle data = new Bundle();
                Message msg = Message.obtain(null, LuaScriptService.MSG_RUN_SCRIPT);
                data.putString(LuaScriptService.EXTRA_DIRECTORY, getScriptDirectory(filename));
                data.putString(LuaScriptService.EXTRA_FILENAME, filename);
                msg.setData(data);
                mService.send(msg);
            } catch (Exception e) {
                mHandler.obtainMessage(LuaScriptService.MSG_SCRIPT_RESULT, LuaScriptService.RESULT_FAIL, 0).sendToTarget();
                e.printStackTrace();
            }
        } else {
            releaseWakelock();
            doUnbindService();
            mCallback.finishTests();
        }
    }

    public void acquireWakelock() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "DSENSE");
        mWakelock.acquire();
    }

    public void releaseWakelock() {
        if (null != mWakelock) {
            mWakelock.release();
        }
    }
    public String getLogDirectory() {
        String serial = android.os.Build.SERIAL;
        return getLogDirectory(serial + "." + mTimestamp);
    }

    public String getLogDirectory(String test_dir)
    {
        String dir = Environment.getExternalStorageDirectory() + "/alt_autocycle/bands/" + test_dir + "/";
        createDirectories(dir, 3, true); // Set all permissions on log directory
        return dir;
    }

    private static void createDirectories(String dir, int depth, boolean worldWritable)
    {
        File f = new File(dir);
        if(!f.exists()) f.mkdirs();

        for(int i = 0; i < depth; i++) // Set all permissions on script directory and parent directory
        {
            f.setReadable(true, false);
            f.setWritable(true, !worldWritable);
            f.setExecutable(true, false);
            f = f.getParentFile();
        }
    }

    public String getScriptDirectory(String script_name) {
        String root_dir = android.os.Build.SERIAL + "." + mTimestamp;
        File script_dir = new File(getScriptName(script_name));
        String dir = getLogDirectory(root_dir) + script_dir.getName() + "/";
        createDirectories(dir, 1, true); // Set all permissions on log directory
        return dir;
    }

    public String getExcelFileName(String script_name) {
        return getLogDirectory() + script_name + ".xls";
    }

    private String getScriptName(String path) {
        return path.replaceAll("^.+/", "").replaceAll("\\..+$", "");
    }

    protected static class DebugLog {
        FileWriter out;
        DateFormat date;

        public DebugLog(ScriptExecutor executor) {
            try {
                date = DateFormat.getDateTimeInstance();
                boolean exists = new File(executor.getLogDirectory() + "debug.log").exists();
                out = new FileWriter(executor.getLogDirectory() + "debug.log", true);

                if (!exists)
                    println("Starting bands test");
            } catch (IOException e) {
                e.printStackTrace(System.out);
                out = null;
            }
        }

        public void println(String line) {
            System.out.println(line);
            line = date.format(Calendar.getInstance().getTime()) + ": " + line;
            if (out != null)
                try {
                    out.write(line + "\n");
                    out.flush();
                } catch (IOException e) {
                }
        }

        public void close() {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e) {
                }
            out = null;
        }
    }
}
