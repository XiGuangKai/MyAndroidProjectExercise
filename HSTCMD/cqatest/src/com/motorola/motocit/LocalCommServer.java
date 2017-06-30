/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 * Date           CR         Author                Description
 * 2012/10/19  IKMAIN-49270 Min Dong - cqd487    Add support for LocalCommServer
 */

package com.motorola.motocit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.widget.Toast;


public class LocalCommServer extends CommServer
{
    private static final String TAG = "MotoTest_LocalCommServer";

    //For local socket
    public static final String SOCKET_NAME = "local_commserver";
    private LocalServerSocket localServer = null;
    private LocalSocket localClient = null;


    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate()
    {
        if (TestUtils.isUserdebugEngBuild() != true)
        {
            TestUtils.dbgLog(TAG, "Exit local commserver in user build", 'i');
            return;
        }

        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        TestUtils.dbgLog(TAG, "OnCreate of localCommServer called", 'i');

        // setup activity lookup hashes
        setupActivityClassLookup();

        // get apk version code
        try
        {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),0);
            apkVersionCode = info.versionCode;
        }
        catch (NameNotFoundException e)
        {
            apkVersionCode = -1;
        }


        // Start service in foreground and display a notification about us starting.
        startForegroundAndShowNotification();


       // Create local server
        try
        {
            localServer = new LocalServerSocket(SOCKET_NAME);
            TestUtils.dbgLog(TAG, "Create local server " + SOCKET_NAME, 'i');
        }
        catch (Exception ex)
        {
            TestUtils.dbgLog(TAG, "Exception found in onCreate, making local server socket: " + ex.getMessage(),'e');
            ex.printStackTrace();
            return;
        }
    }

    // This is the "connection" thread of the LocalCommServer. Basically this thread
    // continues to ensure that the server remains running and that if there is
    // no client connected it waits for a single connection
    private Runnable localCommServerRunnable = new Thread()
    {
        @Override
        public void run()
        {
            String connectionStatus;

            TestUtils.dbgLog(TAG, "Starting Local CommServer Thread", 'i');
            do
            {
                TestUtils.dbgLog(TAG, "local Comm Server Thread waiting for client", 'i');

                try
                {
                    // attempt to accept a connection, .accept() locks for an
                    // incoming connection.
                    localClient = localServer.accept();

                    TestUtils.dbgLog(TAG, "local Comm Server Thread: accepted client", 'i');

                    if(connected == true )
                    {
                        TestUtils.dbgLog(TAG, "a client already connected ", 'i');

                        // close client connection
                        localClient.close();
                        localClient = null;

                        continue;
                    }

                    ServerOutput serverOutputThread = new ServerOutput();
                    ServerInput serverInputThread = new ServerInput();
                    BuildOutputPacket buildOutputPacketThread = new BuildOutputPacket();

                    // A client is connecting, create input and output streams.
                    socketIn = new BufferedReader(new InputStreamReader(localClient.getInputStream()));
                    socketOut = new PrintWriter(localClient.getOutputStream(), true);

                    if (localClient != null)
                    {
                        try
                        {
                            lockConnStatus.lock();
                            connected = true;
                            socketConnectedType = LOCAL_SOCKET_CONNECTION;
                            TestUtils.dbgLog(TAG, "Connected socket type: LOCAL_SOCKET_CONNECTION", 'i');
                        }
                        finally
                        {
                            lockConnStatus.unlock();
                        }
                        // print out connection success
                        connectionStatus = "Connection was successful!";
                        TestUtils.dbgLog(TAG, "" + connectionStatus, 'i');

                        // Start the servers, each of these calls start a new
                        // thread.

                        serverOutputThread.start();
                        serverInputThread.start();
                        buildOutputPacketThread.start();

                        // The next section waits for the two preceding threads
                        // both finish. This
                        // ensures that only one client-server connection is
                        // active at a time.
                        try
                        {
                            serverOutputThread.join();
                            serverInputThread.join();
                            buildOutputPacketThread.join();

                            TestUtils.dbgLog(TAG, "Threads JOINED " + this.getId(), 'i');

                            socketIn.close();
                            socketOut.close();

                            socketIn = null;
                            socketOut = null;

                            localClient.close();
                            localClient = null;
                            socketConnectedType = INVALID_SOCKET_CONNECTION;

                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }

                    }
                    // Catch blocks for any exception possible above. If
                    // un-handled exceptions happen the
                    // code should be fixed.

                }
                catch (SocketTimeoutException e)
                {
                    // print out TIMEOUT
                    connectionStatus = "Connection has timed out! Please try again";
                    TestUtils.dbgLog(TAG, "" + connectionStatus, 'e');
                }
                catch (IOException e)
                {
                    TestUtils.dbgLog(TAG, "IOException: " + e.getMessage(), 'e');
                }
                catch (Exception e)
                {
                    connectionStatus = "Unknown Exception type caught in localCommServerRunnable run method.  Message Follows:";
                    TestUtils.dbgLog(TAG, "" + connectionStatus, 'e');
                    TestUtils.dbgLog(TAG, "" + e.getMessage(), 'e');
                    e.printStackTrace();

                    String strErrMsg = String.format("MESSAGE=%s(%d): %s() Unknown Exception type caught. %s", e.getStackTrace()[0].getFileName(),
                            e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());

                    // Create unsolicited packet back to CommServer reporting an error
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(strErrMsg);
                    CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, UnsolicitedResponseType.ERROR.toString(), TAG, strErrMsgList);
                    sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                }

            }
            while (true);

        }
     };


    @Override
    // The below function is called every time the user starts the LocalCommServer
    // service. We override it to launch the
    // localCommServerRunnable thread, ensuring that thread is always running.
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        TestUtils.dbgLog(TAG, "LocalCommServer: Received start id " + startId + ": " + intent, 'i');

        if (TestUtils.isUserdebugEngBuild() != true)
        {
            TestUtils.dbgLog(TAG, "Exit local commserver in user build", 'i');
            return (0);
        }

        // Acquire wakelock to prevent service from stopping if phone
        // tries to go to sleep
        TestUtils.dbgLog(TAG, "Setting partial wake lock", 'i');
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        new Thread(localCommServerRunnable).start();


        return START_STICKY;
    }

    @Override
    // Stop the CommServer
    public void onDestroy()
    {
        TestUtils.dbgLog(TAG, "onDestroy() Called", 'i');
 
        if (TestUtils.isUserdebugEngBuild() != true)
        {
            TestUtils.dbgLog(TAG, "Exit local commserver in user build", 'i');
            return;
        }

        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // release wake lock
        if(wl != null)
        {
            wl.release();
        }

        if (null != localServer)
        {
            try
            {
                localServer.close();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }


        // Tell the user we stopped.
        try
        {
            Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
        }
        catch (Resources.NotFoundException e)
        {
            TestUtils.dbgLog(TAG, "Exception in onDestroy Method, Bad Variables for toast", 'e');
            e.printStackTrace();
        }
    }

}
