/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.SQLException;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.format.Time;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.database.DatabaseHandler;
import com.motorola.motocit.database.DatabaseRecord;

public abstract class Test_Base extends Activity
{
    Boolean mIsBound = false;

    protected String TAG;
    protected String passMessage;
    protected String failMessage;

    public static boolean BinaryWaitForAck = false;

    protected String strRxCmd;
    protected List<String> strRxCmdDataList;
    protected int nRxSeqTag;

    private PowerManager pm = null;
    private KeyguardManager key_guard = null;
    private KeyguardLock mKeyguardLock = null;
    private PowerManager.WakeLock wl = null;
    public String versionName = "";

    private Intent stopIntent = null;
    private boolean activityFinishing = false;
    public boolean isCQASeqResolveInfoListCleared = false;
    public boolean isCQAPatResolveInfoListCleared = false;

    private Handler mHandler = new Handler();
    private SystemUiHider navKeyHider = null;

    protected HandleReceivedBroadcast handleRxBroadcast;
    protected Boolean doTestSetupAndRelease = true;

    public View.OnTouchListener mGestureListener = null;
    public GestureDetector mGestureDetector;

    public final String TEST_PASS = "PASS";
    public final String TEST_FAIL = "FAIL";

    public boolean isTouchScreenTest = false;

    // Broadcast receiver to all CommServer to broadcast intents to
    // all activities based off Test_Base/
    protected BroadcastReceiver globalTestBaseReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            dbgLog(TAG, "globalTestBaseReceiver onReceive() called", 'i');

            String action = intent.getAction();
            if (null != action)
            {
                // see if CommServer wants all activities to STOP
                if (action.equalsIgnoreCase(CommServer.ACTION_TEST_BASE_FINISH))
                {
                    activityFinishing = true;
                    finish();
                }
            }
        }

    };

    protected BroadcastReceiver commReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            dbgLog(TAG, "commReceiver - onReceive() called", 'i');

            if (wasActivityStartedByCommServer())
            {
                setIntent(intent);
            }

            try
            {
                Bundle extras = intent.getExtras();

                if (null != extras)
                {
                    nRxSeqTag = extras.getInt("seq_tag");
                    strRxCmd = extras.getString("cmd");
                    strRxCmdDataList = intent.getStringArrayListExtra("cmd_data");

                    // Inform CommServer that activity received command
                    CommServer.setActivityReceivedCmd(nRxSeqTag);

                    // keep klockwork happy
                    if (null == strRxCmdDataList)
                    {
                        strRxCmdDataList = new ArrayList<String>();
                    }

                    if (strRxCmd != null)
                    {
                        // Handle STOP in main thread so finish() is executed as
                        // fast as possible
                        if (strRxCmd.equalsIgnoreCase("stop"))
                        {
                            // save intent into stopIntent to use
                            // to generate ACK packet in onDestroy;
                            stopIntent = intent;

                            dbgLog(TAG, "commReceiver - Stopping activity due to broadcast message from CommServer to stop", 'i');
                            activityFinishing = true;
                            finish();
                            overridePendingTransition(0, 0);

                            return;
                        }
                        else
                        {
                            handleRxBroadcast = new HandleReceivedBroadcast();
                            handleRxBroadcast.start();
                        }
                    }
                }

            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unknown Exception type caught in onReceive method of commReceiver.  Message Follows:", 'e');
                dbgLog(TAG, "" + e.getMessage(), 'e');
                e.printStackTrace();

                // Create packet object and send it to sendCmdFailToCommServer
                String strErrMsg = String.format("%s(%d) UNKNOWN_EXCEPTION in %s() %s", e.getStackTrace()[0].getFileName(),
                        e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);
            }
        }
    };

    protected class HandleReceivedBroadcast extends Thread
    {
        @Override
        public void run()
        {
            try
            {
                handleTestSpecificActions();
            }

            catch (CmdFailException e)
            {
                dbgLog(TAG, "CmdFailException Caught", 'e');
                dbgLog(TAG, "SeqTag " + e.nSeqTag, 'e');
                dbgLog(TAG, "strCmd " + e.strCmd, 'e');

                CommServerDataPacket failPacket = new CommServerDataPacket(e.nSeqTag, e.strCmd, TAG, e.strErrMsgList);
                sendCmdFailToCommServer(failPacket);
            }
            catch (CmdPassException e)
            {
                dbgLog(TAG, "CmdPassException Caught", 'e');
                dbgLog(TAG, "SeqTag " + e.nSeqTag, 'e');
                dbgLog(TAG, "strCmd " + e.strCmd, 'e');

                CommServerDataPacket passPacket = new CommServerDataPacket(e.nSeqTag, e.strCmd, TAG, e.strReturnDataList);
                sendCmdPassToCommServer(passPacket);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unknown Exception type caught in HandleReceivedBroadcast run method.  Message Follows:", 'e');
                dbgLog(TAG, "" + e.getMessage(), 'e');
                e.printStackTrace();

                // Create exception object and send it to
                // sendCmdFailToCommServer
                String strErrMsg = String.format("%s(%d) UNKNOWN_EXCEPTION in %s() %s", e.getStackTrace()[0].getFileName(),
                        e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);
                CommServerDataPacket failPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        dbgLog(TAG, "onCreate()", 'i');

        if ((TestUtils.isMotDevice() == true) || (TestUtils.isOdmDevice() == true))
        {
            dbgLog(TAG, "Continue following creation", 'i');
            super.onCreate(savedInstanceState);

            PackageManager pkm = getPackageManager();

            try
            {
                PackageInfo pi = pkm.getPackageInfo("com.motorola.motocit", 0);
                versionName = pi.versionName;
            }
            catch (NameNotFoundException e)
            {
                e.printStackTrace();
            }

            // See if the CommServer is already running, if not, start it.
            // Only if phone was powered up with a factory cable or this is an
            // ODM device
            if ((TestUtils.isFactoryCableBoot() == true) || (TestUtils.isUserdebugEngBuild() == true) || (TestUtils.isOdmDevice() == true))
            {
                try
                {
                    // disable animations if running from CommServer
                    if (wasActivityStartedByCommServer())
                    {
                        // Disable all animations
                        Window window = getWindow(); // keep klocwork happy
                        if (null != window)
                        {
                            window.setWindowAnimations(0);
                        }
                    }

                    if (TAG == null)
                    {
                        Context context = getApplicationContext();
                        CharSequence text = "ERROR IN APPLICATION.  TAG variable not set correctly";
                        int duration = Toast.LENGTH_SHORT;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();

                        finish();
                    }
                    else
                    {
                        IntentFilter intentFilter = new IntentFilter(TAG.toLowerCase());

                        if ((TestUtils.isFactoryCableBoot() == true) && !isCommServerRunning())
                        {
                            startService(new Intent(getApplicationContext(), CommServer.class));
                        }
                        this.registerReceiver(commReceiver, intentFilter);

                        // register receiver to all CommServer to send intent to
                        // ALL activities
                        IntentFilter globalTestBaseFilter = new IntentFilter(CommServer.ACTION_TEST_BASE_FINISH);
                        this.registerReceiver(globalTestBaseReceiver, globalTestBaseFilter);

                        if ((TestUtils.isFactoryCableBoot() == true) ||
                                (TestUtils.isUserdebugEngBuild() && TestUtils.getAutoStartCommServer().toUpperCase(Locale.US).contains("YES"))
                                    || (TestUtils.isOdmDevice() == true))
                        {
                            doBindService();
                        }
                    }
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "Unknown Exception type caught in onCreate method.  Message Follows:", 'e');
                    dbgLog(TAG, "" + e.getMessage(), 'e');
                    e.printStackTrace();

                    // Create unsolicited packet back to CommServer reporting an
                    // error
                    String strErrMsg = String.format("MESSAGE=%s(%d) UNKNOWN_EXCEPTION in %s() %s", e.getStackTrace()[0].getFileName(),
                            e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(strErrMsg);

                    CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "ERROR", TAG, strErrMsgList);
                    sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                }
            }
        }
        else
        {
            dbgLog(TAG, "Cancelling activity creation", 'e');
            System.exit(0);
        }

        // Register Gesture Detector to detect touch screen for pass/fail input
        // method

        if (TestUtils.getPassFailMethods().equalsIgnoreCase("TOUCHSCREEN"))
        {
            mGestureDetector = new GestureDetector(this, new CQAGestureDetector());
            mGestureListener = new View.OnTouchListener()
            {
                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    return mGestureDetector.onTouchEvent(event);
                }
            };
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        dbgLog(TAG, "onConfigurationChanged", 'i');
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        dbgLog(TAG, "onWindowFocusChanged: Focus = " + hasFocus, 'i');
        if (hasFocus != true)
        {
            collapseNotificationPanel();
        }

        // IKVPREL1L-5494, workaround to fix navigation keys pop up issue.
        // Root cause IKXP-3484
        Window window = this.getWindow();
        boolean isNavKeyShown = ((window.getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)==0) ? true : false;
        dbgLog(TAG, "isNavKeyShown=" + isNavKeyShown + " isTouchScreenTest=" + isTouchScreenTest, 'i');
        if (hasFocus && wasActivityStartedByCommServer()&&(!isTouchScreenTest))
        {
            dbgLog(TAG, "onWindowFocusChanged, hiding navigation keys", 'i');
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            mHandler.postDelayed(mHider,500);
            mHandler.postDelayed(mHider,1000);
            mHandler.postDelayed(mHider,1500);
        }
    }

    private final Runnable mHider = new Runnable()
    {
        @Override public void run()
        {
            dbgLog(TAG, "In Test Base mHider handler", 'i');
            Window window = getWindow();
            if ( wasActivityStartedByCommServer() )
            {
                dbgLog(TAG, "In Test Base mHider handler,hiding navigation keys", 'i');
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        dbgLog(TAG, "onPostCreate  called", 'i');

        // setup navKeyHider to hide soft keys if started by CommServer
        Window window = getWindow(); // keep klocwork happy
        if (null != window)
        {
            navKeyHider = new SystemUiHider(window.getDecorView());
            if (wasActivityStartedByCommServer())
            {
                if (!isTouchScreenTest)
                {
                    navKeyHider.setup();
                }

                // perform testSetup() here is running from CommServer
                // else do testSetup() in onPostResume()
                if (doTestSetupAndRelease)
                {
                    testSetup();
                }
            }
            else
            {
                navKeyHider.enable(false);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        dbgLog(TAG, "onNewIntent  called", 'i');

        // update original intent with intent from onNewIntent.
        // this will cause OnResume to get the 'new' intent
        if (wasActivityStartedByCommServer())
        {
            setIntent(intent);
        }
    }

    @Override
    protected void onStart()
    {
        dbgLog(TAG, "onStart called", 'i');
        super.onStart();
    }

    @Override
    public void onRestart()
    {
        dbgLog(TAG, "onRestart called", 'i');
        super.onRestart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // The activity has become visible (it is now "resumed").
        dbgLog(TAG, "onResume called", 'i');

        // do testSetup here if not running from commServer
        if (doTestSetupAndRelease && (wasActivityStartedByCommServer() == false))
        {
            testSetup();
        }
    }

    @Override
    protected void onPause()
    {
        dbgLog(TAG, "onPause called", 'i');
        if (wasActivityStartedByCommServer())
        {
            try
            {
                List<String> strDataList = new ArrayList<String>();
                strDataList.add(String.format("MESSAGE=CLOSE: %s Lost Focus", TAG));
                CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "LOST_FOCUS", TAG, strDataList);
                sendUnsolicitedPacketToCommServer(unsolicitedPacket);

                dbgLog(TAG, "Lost Focus", 'i');
                super.onPause();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Unknown Exception type caught in onPause method.  Message Follows:", 'e');
                dbgLog(TAG, "" + e.getMessage(), 'e');
                e.printStackTrace();

                // Create unsolicited packet back to CommServer reporting an
                // error
                String strErrMsg = String.format("MESSAGE=%s(%d) UNKNOWN_EXCEPTION in %s() %s", e.getStackTrace()[0].getFileName(),
                        e.getStackTrace()[0].getLineNumber(), e.getStackTrace()[0].getMethodName(), e.getMessage());
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);

                CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "ERROR", TAG, strErrMsgList);
                sendUnsolicitedPacketToCommServer(unsolicitedPacket);
            }
        }
        else
        {
            super.onPause();

            // if not running from CommServer then run testSetupRelease in
            // onPause
            if (doTestSetupAndRelease)
            {
                testSetupRelease();
            }
        }
    }

    @Override
    protected void onStop()
    {
        dbgLog(TAG, "onStop called", 'i');
        super.onStop();
    }

    protected void testPassed() throws CmdFailException, CmdPassException
    {
        if (passMessage != null)
        {
            dbgLog(TAG, "PASS:" + passMessage, 'i');

            // Generate an exception to send PASS result and data back to
            // CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add("MESSAGE=" + passMessage);
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            Context context = getApplicationContext();
            CharSequence text = "ERROR IN APPLICATION.  passMessage variable not set correctly";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("MESSAGE=PassMessage variable not set correctly"));
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev)
    {
        dbgLog(TAG, "onKeyUp() saw " + keyCode, 'i');

        if (wasActivityStartedByCommServer())
        {
            // Send an unsolicited response back through the CommServer
            // so NexTest can use it to implement operator passing and
            // failing the unit through the volume keys

            // create unsolicited message back to report user pass/fail
            List<String> msgList = new ArrayList<String>();

            msgList.add("KEYCODE_DECIMAL=" + keyCode);

            // Create unsolicited packet back to CommServer reporting
            CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "USER_PASS_FAIL", "UNSOLICITED_KEYPRESS", msgList);
            sendUnsolicitedPacketToCommServer(unsolicitedPacket);
        }

        return true;
    }

    protected void testFailed() throws CmdFailException
    {
        if (failMessage != null)
        {
            dbgLog(TAG, "FAIL:" + failMessage, 'i');

            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("MESSAGE=" + failMessage);
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
        else
        {
            Context context = getApplicationContext();
            CharSequence text = "ERROR IN APPLICATION.  failMessage variable not set correctly";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("MESSAGE=FailMessage variable not set correctly"));
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    // Following function should be Overridden, allowing specific actions to be
    // taken
    protected abstract void handleTestSpecificActions() throws CmdFailException, CmdPassException;

    protected void sendCmdFailToCommServer(CommServerDataPacket failPacket)
    {
        CommServer.sendCmdFailToCommServer(failPacket);
    }

    protected void sendCmdPassToCommServer(CommServerDataPacket passPacket)
    {
        CommServer.sendCmdPassToCommServer(passPacket);
    }

    protected void sendInfoPacketToCommServer(CommServerDataPacket infoPacket)
    {
        CommServer.sendInfoPacketToCommServer(infoPacket);
    }

    protected void sendBinaryPacketToCommServer(CommServerBinaryPacket binaryPacket)
    {
        CommServer.sendBinaryPacketToCommServer(binaryPacket);
    }

    protected void sendUnsolicitedPacketToCommServer(CommServerDataPacket infoPacket)
    {
        CommServer.sendUnsolicitedPacketToCommServer(infoPacket);
    }

    protected abstract void printHelp();

    protected List<String> getBaseHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add("Common Activity Commands");
        strHelpList.add("  HELP \t\t\t\t-print activity specific help information");
        strHelpList.add("");

        return strHelpList;
    }

    protected CommServer mCommServer;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mCommServer = ((CommServer.LocalBinder) service).getService();

            // Tell the user about this for our demo.
            dbgLog(TAG, "Connected to CommServer", 'i');
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mCommServer = null;
            dbgLog(TAG, "Disconnected from CommServer", 'i');

        }
    };

    void doBindService()
    {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        // bindService(new Intent(Test_Base.this,
        // CommServer.class), mConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(getApplicationContext(), CommServer.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private boolean isCommServerRunning()
    {
        return TestUtils.isCommServerRunning(getApplicationContext());
    }

    @Override
    protected void onDestroy()
    {
        dbgLog(TAG, "onDestroy() called", 'i');

        super.onDestroy();

        if ((TestUtils.isFactoryCableBoot() == true) || (TestUtils.isUserdebugEngBuild() == true) || (TestUtils.isOdmDevice() == true))
        {
            if (mConnection != null)
            {
                dbgLog(TAG, "OnDestroy() unbindService(mConnection)", 'i');
                if(mIsBound)
                {
                    this.unbindService(mConnection);
                }

                mConnection = null;
                mIsBound = false;
            }

            if (globalTestBaseReceiver != null)
            {
                dbgLog(TAG, "OnDestroy() unregisterReceiver(globalTestBaseReceiver)", 'i');
                this.unregisterReceiver(globalTestBaseReceiver);
                globalTestBaseReceiver = null;
            }

            if (commReceiver != null)
            {
                dbgLog(TAG, "OnDestroy() unregisterReceiver(commReceiver)", 'i');
                this.unregisterReceiver(commReceiver);
                commReceiver = null;
            }

            if (handleRxBroadcast != null)
            {
                handleRxBroadcast = null;
            }

            // send ack to STOP command if necessary.
            // must do it before nulling mCommServer
            if ((isFinishing() == true) && (null != stopIntent))
            {
                Bundle extras = stopIntent.getExtras();

                if (extras != null)
                {
                    int nSeqTag = extras.getInt("seq_tag");
                    String strCmd = extras.getString("cmd");

                    List<String> strReturnDataList = new ArrayList<String>();
                    strReturnDataList.add(String.format("\"%s successfully stopped\"", TAG));

                    CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strReturnDataList);
                    sendCmdPassToCommServer(passPacket);
                }
            }

            if (mCommServer != null)
            {
                dbgLog(TAG, "OnDestroy() set mCommServer to null", 'i');
                mCommServer = null;
            }
        }

        // if running from CommServer then run testSetupRelease in onDestroy
        if (doTestSetupAndRelease && wasActivityStartedByCommServer())
        {
            testSetupRelease();
        }

    }

    public boolean searchFile(String filename)
    {
        File file = new File("/mnt/sdcard-ext/" + filename);
        boolean rtn = false;

        if (file.exists())
        {
            rtn = true;
        }

        return rtn;
    }

    /**
     * searchSDcard function is to search sd card when device supports multiple
     * storage. Phone internal storage and SD card storage. In Android 2.3.3, if
     * device has multiple storage, we can verify if SD card mount or unmounted
     * by Environment.getExternalStorageState() We can search the string of
     * '/mnt/sdcard' in /proc/mounts if device has ONLY one external storage.
     * i.e. SD card Search string of '/mnt/sdcard-ext' in /proc/mounts if device
     * has multiple storage. /mnt/sdcard-ext is for real SD card.
     * */

    public boolean searchSDcard(String pathofcard)
    {
        File file = new File("/proc/mounts");
        dbgLog(TAG, "Searching /proc/mounts/ " + pathofcard, 'd');
        try
        {
            FileInputStream in = new FileInputStream(file);
            int len = 0;
            byte[] data1 = new byte[10000];
            try
            {
                while (-1 != (len = in.read(data1)))
                {

                    if (new String(data1, 0, len).contains(pathofcard))
                    {
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
            catch (IOException e)
            {

                e.printStackTrace();
            }
            finally
            {
                try
                {
                    if (null != in)
                    {
                        in.close();
                        in = null;
                    }
                }
                catch (IOException e)
                {
                }
            }
        }
        catch (FileNotFoundException e)
        {

            e.printStackTrace();
        }

        return false;
    }

    /**
     * This function called in every activity, light on screen always and
     * disable key guard Release settings by call testSetupRelease()
     * */
    public void testSetup()
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

        collapseNotificationPanel();

        Settings.System.putInt(resolver, "screen_brightness_mode", 0);

        Window window = getWindow(); // keep klocwork happy
        if (null != window)
        {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.screenBrightness = 1.0f;
            window.setAttributes(lp);
        }
    }

    /**
     * This function called in every activity after calling the function
     * testSetup()
     * */
    public void testSetupRelease()
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

    public void sendStartActivityPassed()
    {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null)
        {
            int nSeqTag = extras.getInt("seq_tag");
            String strCmd = extras.getString("cmd");
            ArrayList<String> strCmdDataList = getIntent().getStringArrayListExtra("cmd_data");

            // prevent multiple response commands from being sent.
            boolean cmdSent = extras.getBoolean("cmd_sent", false);

            // only send pass packet if cmd is START
            // we have seen strange things where the activity loses focus when a
            // TELL cmd
            // is being processed and when the activity resumes it calls this
            // function
            // again generates an erroneous pass packet with the TELL cmd's
            // information.
            // This cause the receiver to think the TELL cmd finished when it
            // really didn't.
            if ((cmdSent == false) && (strCmd != null) && (strCmd.equalsIgnoreCase(CommServer.ServerCmdType.START.toString())))
            {
                // keep klockwork happy
                if (null == strCmdDataList)
                {
                    strCmdDataList = new ArrayList<String>();
                }

                CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strCmdDataList);
                sendCmdPassToCommServer(passPacket);

                // mark in this intent that command was sent already
                intent.putExtra("cmd_sent", true);
                setIntent(intent);
            }
        }
    }

    public void sendStartActivityFailed(String strErrMsg)
    {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null)
        {
            int nSeqTag = extras.getInt("seq_tag");
            String strCmd = extras.getString("cmd");

            // prevent multiple response commands from being sent.
            boolean cmdSent = extras.getBoolean("cmd_sent", false);

            // only send fail packet if cmd is START
            // we have seen strange things where the activity loses focus when a
            // TELL cmd
            // is being processed and when the activity resumes it calls this
            // function
            // again generates an erroneous packet with the TELL cmd's
            // information.
            // This cause the receiver to think the TELL cmd finished when it
            // really didn't.
            if ((cmdSent == false) && (strCmd != null) && strCmd.equalsIgnoreCase(CommServer.ServerCmdType.START.toString()))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);

                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);

                // mark in this intent that command was sent already
                intent.putExtra("cmd_sent", true);
                setIntent(intent);
            }
        }
    }

    // You should normally throw a CmdPassException to indicate the cmd has
    // passed.
    // The sendCmdPassed() is ONLY used when it is necessary to send a pass back
    // from an
    // Android callback that does not understand the pass exception.
    public void sendCmdPassed()
    {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null)
        {
            int nSeqTag = extras.getInt("seq_tag");
            String strCmd = extras.getString("cmd");

            // This function is not intended to be used for acking START
            // command. Use the SendStartActivity*() functions
            if ((strCmd != null) && (strCmd.equalsIgnoreCase(CommServer.ServerCmdType.START.toString()) == false))
            {
                ArrayList<String> strCmdDataList = getIntent().getStringArrayListExtra("cmd_data");

                // keep klockwork happy
                if (null == strCmdDataList)
                {
                    strCmdDataList = new ArrayList<String>();
                }

                CommServerDataPacket passPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strCmdDataList);
                sendCmdPassToCommServer(passPacket);
            }

        }
    }

    // You should normally throw a fail CmdFailException to indicate the cmd has
    // failed.
    // The sendCmdFailed() is ONLY used when it is necessary to send a fail back
    // from an
    // Android callback that does not understand the fail exception.
    public void sendCmdFailed(String strErrMsg)
    {
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null)
        {
            int nSeqTag = extras.getInt("seq_tag");
            String strCmd = extras.getString("cmd");

            // This function is not intended to be used for acking START
            // command. Use the SendStartActivity*() functions
            if ((strCmd != null) && (strCmd.equalsIgnoreCase(CommServer.ServerCmdType.START.toString()) == false))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(strErrMsg);

                CommServerDataPacket failPacket = new CommServerDataPacket(nSeqTag, strCmd, TAG, strErrMsgList);
                sendCmdFailToCommServer(failPacket);
            }
        }
    }

    /**
     * This function is ONLY called by sequential test to init test result
     * */
    protected void testResultInit()
    {
        // Clear test results database
        DatabaseHandler db = new DatabaseHandler(this);
        db.deleteTable(TestUtils.getSequenceFileInUse() + "_results");

        Time t = new Time();

        t.setToNow();
        int year = t.year;
        int month = t.month;
        int date = t.monthDay;
        int hour = t.hour; // 24H
        int minute = t.minute;
        int second = t.second;

        String timestamp = year + "/" + month + "/" + date + "/" + hour + ":" + minute + ":" + second;

        FileOutputStream os = null;
        String data = "CQA SEQUENTIAL TEST RESULT" + "\r\n" + "(" + timestamp + ")" + "\r\n\r\n";
        try
        {

            os = this.openFileOutput("testresult.txt", MODE_WORLD_WRITEABLE);

            os.write(data.getBytes());

        }
        catch (FileNotFoundException e)
        {

        }
        catch (IOException e)
        {
        }
        finally
        {
            try
            {
                if (null != os)
                {
                    os.close();
                    os = null;
                }
            }
            catch (IOException e)
            {
            }
        }
    }

    /**
     * This function can be used to record data to specific file
     * */
    protected void contentRecord(String file, String data, int mode)
    {
        FileOutputStream os = null;

        try
        {

            os = this.openFileOutput(file, mode);

            os.write(data.getBytes());

        }
        catch (FileNotFoundException e)
        {

        }
        catch (IOException e)
        {
        }
        finally
        {
            try
            {
                if (null != os)
                {
                    os.close();
                    os = null;
                }
            }
            catch (IOException e)
            {
            }
        }
    }

    /**
     * This function can be used to read content from the file. Return the test
     * result string
     * */
    protected String contentRead(String file)
    {
        String data = "";

        FileInputStream stream = null;

        try
        {

            stream = this.openFileInput(file);

            StringBuffer sb = new StringBuffer();

            int c;

            while ((c = stream.read()) != -1)
            {

                sb.append((char) c);

            }

            data = sb.toString();

        }
        catch (FileNotFoundException e)
        {

        }
        catch (IOException e)
        {

        }
        finally
        {
            if (stream != null)
            {
                try
                {
                    stream.close();
                }
                catch (IOException e)
                {
                }
            }
        }
        return data;
    }

    protected boolean modeCheck(String mode)
    {
        boolean rtn = false;
        FileInputStream in = null;

        try
        {
            in = new FileInputStream("/data/data/com.motorola.motocit/files/mode.txt");
            int len = 0;
            byte[] data1 = new byte[10000];
            try
            {
                while (-1 != (len = in.read(data1)))
                {
                    if (new String(data1, 0, len).contains(mode))
                    {
                        rtn = true;
                    }
                    else
                    {
                        rtn = false;
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                }
            }
        }
        return rtn;
    }

    protected void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }

    protected void dbgLog(String tag, String msg, Throwable tr, char type)
    {
        TestUtils.dbgLog(tag, msg, tr, type);
    }

    /*
     * The function wasActivityStartedByCommServer is used to check if the
     * activity is started by CommServer. Can use it to set sensor refresh rate,
     * determine the method of activity exit and release object resource used by
     * CommServer.
     */
    protected boolean wasActivityStartedByCommServer()
    {
        if (getIntent().hasExtra("cmd"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    // Only call finish if activity was not started by commServer.
    // When running from commServer, the commServer (user) will generate
    // an intent to stop the activity
    protected void systemExitWrapper(int exitCode)
    {
        if (wasActivityStartedByCommServer() == false)
        {
            finish();
        }
    }

    // split key=value pairs sent to activity via TELL command
    protected String[] splitKeyValuePair(String keyValuePair) throws CmdFailException
    {
        String splitResult[] = keyValuePair.split("=");

        if (splitResult.length != 2)
        {
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add("Incorrectly formatted key=value pair: " + keyValuePair);
            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        return splitResult;
    }

    public Boolean isExpectedOrientation()
    {
        int requestedOrientation = getRequestedOrientation();
        int currentOrientation = getResources().getConfiguration().orientation;

        boolean isOrientationCorrect = false;

        // check if requested orientation is landscape
        switch (requestedOrientation)
        {
        case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
        case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
        case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
            {
                isOrientationCorrect = true;
            }

            break;

        case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
        case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
        case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
            if (currentOrientation == Configuration.ORIENTATION_PORTRAIT)
            {
                isOrientationCorrect = true;
            }
            break;

        default:
            // not supported orientation

            break;

        }

        dbgLog(TAG, "isExpectedOrientation() returned " + isOrientationCorrect, 'i');
        return isOrientationCorrect;
    }

    public class UpdateUiTextView implements Runnable
    {
        private TextView tView;
        private int color;
        private String text;

        public UpdateUiTextView(TextView tView, int color, String text)
        {
            this.tView = tView;
            this.color = color;
            this.text = text;
        }

        @Override
        public void run()
        {
            tView.setTextColor(color);
            tView.setText(text);
        }
    }

    public class UpdateUiCheckbox implements Runnable
    {
        private CheckBox tView;
        private boolean check;

        public UpdateUiCheckbox(CheckBox tView, boolean check)
        {
            this.tView = tView;
            this.check = check;
        }

        @Override
        public void run()
        {
            if (check)
            {
                tView.setChecked(true);
            }
            else
            {
                tView.setChecked(false);
            }
        }
    }

    public class UpdateUiDropBox implements Runnable
    {
        private Spinner tView;
        private int position;

        public UpdateUiDropBox(Spinner tView, int position)
        {
            this.tView = tView;
            this.position = position;
        }

        @Override
        public void run()
        {
            tView.setSelection(position, true);
        }
    }

    public class UpdateUiButton implements Runnable
    {
        private Button tView;
        private boolean click;

        public UpdateUiButton(Button tView, boolean click)
        {
            this.tView = tView;
            this.click = click;
        }

        @Override
        public void run()
        {
            if (click)
            {
                tView.performClick();
            }
            else
            {
                tView.setPressed(false);
            }
        }
    }

    // read file on phone and return string
    protected String genericReadFile(String path)
    {
        String dump = null;
        try
        {
            String tempData;
            StringBuffer res = new StringBuffer();

            BufferedReader in = new BufferedReader(new FileReader(path));
            while ((tempData = in.readLine()) != null)
            {
                res.append(tempData);
            }
            // in.close();
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            dump = res.toString();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

        return dump;
    }

    protected boolean enableSystemUiHider(boolean enable)
    {
        if (navKeyHider == null)
        {
            return false;
        }

        navKeyHider.enable(enable);

        return true;
    }

    protected boolean invalidateScreenWhenSoftKeysHidden(boolean enable)
    {
        if (navKeyHider == null)
        {
            return false;
        }

        navKeyHider.invalidateScreenWhenHidden(enable);

        return true;

    }

    protected boolean isActivityEnding()
    {
        return activityFinishing;
    }

    // Adjust display size to fit within oddly shaped displays (I.E. Round)
    protected View adjustViewDisplayArea(int layoutId)
    {
        return adjustViewDisplayArea(layoutId, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, false);
    }

    protected View adjustViewDisplayArea(int layoutId, int displayOrientation, boolean allowOverride)
    {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view = layoutInflater.inflate(layoutId, null);

        dbgLog(TAG, "Adjusting View Display Area", 'i');

        if (TestUtils.getDisplayHideTitle())
        {
            dbgLog(TAG, "Disabling Title", 'i');
            try
            {
                requestWindowFeature(Window.FEATURE_NO_TITLE);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Disabling Title Exception=" + e.toString(), 'i');
            }
        }

        if (TestUtils.getDisplayHideNotification())
        {
            dbgLog(TAG, "Switching to Full SCreen", 'i');
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        try
        {
            int XOffset = 0;
            int YOffset = 0;

            int requestedOrientation = getRequestedOrientation();

            // This needs to be done because Orientation change needs to go
            // through TestBase setRequestedOrientation
            if (displayOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            {
                dbgLog(TAG, "Forcing orientation", 'i');
                setRequestedOrientation(displayOrientation, allowOverride);
            }
            else
            {
                dbgLog(TAG, "Setting orientation", 'i');
                setRequestedOrientation(getRequestedOrientation());
            }

            requestedOrientation = getRequestedOrientation();

            int CQASettingDisplayOrientation = TestUtils.getDisplayOrientation();

            // check if requested orientation is landscape
            switch (CQASettingDisplayOrientation)
            {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // Only reverse for portrait
                    dbgLog(TAG, "Detected Orientation CQA=LANDSCAPE, REQUESTED=LANDSCAPE", 'i');
                    XOffset = TestUtils.getDisplayXLeftOffset();
                    YOffset = TestUtils.getDisplayYTopOffset();
                    break;
                default:
                    // if Portrait, reverse X and Y
                    dbgLog(TAG, "Detected Orientation CQA=LANDSCAPE, REQUESTED=PORTRAIT", 'i');
                    XOffset = TestUtils.getDisplayYTopOffset();
                    YOffset = TestUtils.getDisplayXLeftOffset();
                    break;
                }
                break;
            default:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    // if Landscape, reverse X and Y
                    dbgLog(TAG, "Detected Orientation CQA=PORTRAIT, REQUESTED=LANDSCAPE", 'i');
                    XOffset = TestUtils.getDisplayYTopOffset();
                    YOffset = TestUtils.getDisplayXLeftOffset();
                    break;
                default:
                    // Only reverse for landscape
                    dbgLog(TAG, "Detected Orientation CQA=LANDSCAPE, REQUESTED=LANDSCAPE", 'i');
                    XOffset = TestUtils.getDisplayXLeftOffset();
                    YOffset = TestUtils.getDisplayYTopOffset();
                    break;
                }
                break;
            }

            view.setX(XOffset);
            view.setY(YOffset);

            dbgLog(TAG, "adjustLinearLayoutDisplayArea SetY: " + YOffset + " SetX: " + XOffset, 'i');

            setContentView(view);

            if ((TestUtils.getDisplayXLeftOffset() > 0) || (TestUtils.getDisplayXRightOffset() > 0) || (TestUtils.getDisplayYTopOffset() > 0)
                    || (TestUtils.getDisplayYBottomOffset() > 0))
            {
                LayoutParams layoutParams = null;

                layoutParams = view.getLayoutParams();

                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                int width = size.x;
                int height = size.y;

                dbgLog(TAG, "layoutParams before offsets added Width: " + width + " Height: " + height, 'i');

                // check if requested orientation is landscape
                switch (CQASettingDisplayOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                    switch (requestedOrientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                        // Only reverse for portrait
                        layoutParams.width = width - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                        layoutParams.height = height - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                        break;
                    default:
                        // if portrait, reverse X and Y Offsets
                        layoutParams.width = width - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                        layoutParams.height = height - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                        break;
                    }
                    break;
                default:
                    // check if requested orientation is landscape
                    switch (requestedOrientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                        // if Landscape, reverse X and Y Offsets
                        layoutParams.width = width - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                        layoutParams.height = height - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                        break;
                    default:
                        // Only reverse for landscape
                        layoutParams.width = width - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset();
                        layoutParams.height = height - TestUtils.getDisplayYTopOffset() - TestUtils.getDisplayYBottomOffset();
                        break;
                    }
                }

                dbgLog(TAG, "setContent offsets added Width: " + view.getWidth() + " Height: " + view.getHeight(), 'i');
                dbgLog(TAG, "layoutParams offsets added Width: " + layoutParams.width + " Height: " + layoutParams.height, 'i');
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Exception: " + e.toString(), 'i');
        }


        return view;
    }

    public class CQAGestureDetector extends SimpleOnGestureListener
    {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            boolean flingResult = false;

            // When running from CommServer normally ignore Fling event
            if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("TOUCHSCREEN"))
            {
                return true;
            }

            dbgLog(TAG, "FLING DETECTED: X1=" + e1.getX() + "Y1=" + e1.getY(), 'i');
            dbgLog(TAG, "FLING DETECTED: X2=" + e2.getX() + "Y2=" + e2.getY(), 'i');
            dbgLog(TAG, "FLING DETECTED: Velocity X=" + velocityX + "Velocity Y=" + velocityY, 'i');

            float diffY = 0;
            float diffX = 0;

            int requestedOrientation = getRequestedOrientation();
            int CQASettingDisplayOrientation = TestUtils.getDisplayOrientation();

            switch (CQASettingDisplayOrientation)
            {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    // if Landscape, reverse X and Y
                    dbgLog(TAG, "CQASetting = Portrait, Reverse Landscape Detected", 'i');
                    diffX = e2.getY() - e1.getY();
                    diffY = e1.getX() - e2.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    // if Landscape, reverse X and Y and diff X calc
                    dbgLog(TAG, "CQASetting = Portrait, Landscape Detected", 'i');
                    diffX = e1.getY() - e2.getY();
                    diffY = e2.getX() - e1.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    // if reverse portrait, reverse diff X and Y calc
                    dbgLog(TAG, "CQASetting = Portrait, Reverse Portrait Detected", 'i');
                    diffY = e1.getY() - e2.getY();
                    diffX = e1.getX() - e2.getX();
                    break;
                default:
                    // Only reverse for landscape
                    dbgLog(TAG, "CQASetting = Portrait, Portrait Detected", 'i');
                    diffY = e2.getY() - e1.getY();
                    diffX = e2.getX() - e1.getX();
                    break;
                }
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    // if Landscape, reverse X and Y
                    dbgLog(TAG, "CQASetting = Reverse Portrait, Reverse Landscape Detected", 'i');
                    diffX = e1.getY() - e2.getY();
                    diffY = e2.getX() - e1.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    // if Landscape, reverse X and Y and diff X calc
                    dbgLog(TAG, "CQASetting = Reverse Portrait, Landscape Detected", 'i');
                    diffX = e2.getY() - e1.getY();
                    diffY = e1.getX() - e2.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    // if reverse portrait, reverse diff X and Y calc
                    dbgLog(TAG, "CQASetting = Reverse Portrait, Reverse Portrait Detected", 'i');
                    diffY = e2.getY() - e1.getY();
                    diffX = e2.getX() - e1.getX();
                    break;
                default:
                    // Only reverse for landscape
                    dbgLog(TAG, "CQASetting = Reverse Portrait, Portrait Detected", 'i');
                    diffY = e1.getY() - e2.getY();
                    diffX = e1.getX() - e2.getX();
                    break;
                }
                break;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    dbgLog(TAG, "CQASetting = Reverse Landscape, Reverse Landscape Detected", 'i');
                    diffY = e1.getY() - e2.getY();
                    diffX = e1.getX() - e2.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    dbgLog(TAG, "CQASetting = Reverse Landscape, Landscape Detected", 'i');
                    diffY = e2.getY() - e1.getY();
                    diffX = e2.getX() - e1.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    dbgLog(TAG, "CQASetting = Reverse Landscape, Reverse Portrait Detected", 'i');
                    diffX = e2.getY() - e1.getY();
                    diffY = e1.getX() - e2.getX();
                    break;
                default:
                    dbgLog(TAG, "CQASetting = Reverse Landscape, Portrait Detected", 'i');
                    diffX = e1.getY() - e2.getY();
                    diffY = e2.getX() - e1.getX();
                    break;
                }
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                switch (requestedOrientation)
                {
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                    dbgLog(TAG, "CQASetting = Landscape, Reverse Landscape Detected", 'i');
                    diffY = e2.getY() - e1.getY();
                    diffX = e2.getX() - e1.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                    dbgLog(TAG, "CQASetting = Landscape, Landscape Detected", 'i');
                    diffY = e1.getY() - e2.getY();
                    diffX = e1.getX() - e2.getX();
                    break;
                case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                    dbgLog(TAG, "CQASetting = Landscape, Reverse Portrait Detected", 'i');
                    diffX = e1.getY() - e2.getY();
                    diffY = e2.getX() - e1.getX();
                    break;
                default:
                    dbgLog(TAG, "CQASetting = Landscape, Portrait Detected", 'i');
                    diffX = e2.getY() - e1.getY();
                    diffY = e1.getX() - e2.getX();
                    break;
                }
                break;
            }

            if (Math.abs(diffX) > Math.abs(diffY))
            {
                if ((Math.abs(diffX) > 100))
                {
                    if (diffX > 0)
                    {
                        dbgLog(TAG, "SWIPE RIGHT DETECTED", 'i');
                        flingResult = onSwipeRight();
                    }
                    else
                    {
                        dbgLog(TAG, "SWIPE LEFT DETECTED", 'i');
                        flingResult = onSwipeLeft();
                    }
                }
            }
            else
            {
                if ((Math.abs(diffY) > 100))
                {
                    if (diffY > 0)
                    {
                        dbgLog(TAG, "SWIPE DOWN DETECTED", 'i');
                        flingResult = onSwipeDown();
                    }
                    else
                    {
                        dbgLog(TAG, "SWIPE UP DETECTED", 'i');
                        flingResult = onSwipeUp();
                    }
                }
            }

            return flingResult;
        }

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }
    }

    // Following function should be Overridden, allowing specific actions to be
    // taken
    protected abstract boolean onSwipeRight();

    protected abstract boolean onSwipeLeft();

    protected abstract boolean onSwipeDown();

    protected abstract boolean onSwipeUp();

    @Override
    public void setRequestedOrientation(int orientation)
    {
        setRequestedOrientation(orientation, true);
    }

    public void setRequestedOrientation(int orientation, boolean allowOverride)
    {
        int correctedOrientation = orientation;

        if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        {
            // APK DEFAULT ORIENTATION IS SCREEN_ORIENTATION_PORTRAIT
            dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
        }
        else
        {
            if (allowOverride == false)
            {
                // EVEN IF ORIENTATION OVERRIDE IS TRUE, USE REVERSE ORIENTATION IF
                // SETTING IS REVERSE
                if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)
                {
                    switch (orientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        break;
                    }
                }
                else if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
                {
                    switch (orientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                        break;
                    }
                }
            }
            else
            {
                if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)
                {
                    switch (orientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                        break;
                    }
                }
                else if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                {
                    switch (orientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                        break;
                    }
                }
                else if (TestUtils.getDisplayOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
                {
                    switch (orientation)
                    {
                    case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_PORTRAIT", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                        break;
                    case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                        dbgLog(TAG, "Requested Orientation prior to adjustment: SCREEN_ORIENTATION_REVERSE_LANDSCAPE", 'i');
                        correctedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                        break;
                    }
                }
            }
        }

        if (correctedOrientation != orientation)
        {
            dbgLog(TAG, "Display Orientation Being Updated Per CQA Setting", 'i');
            switch (correctedOrientation)
            {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                dbgLog(TAG, "Requested Orientation after adjustment: SCREEN_ORIENTATION_PORTRAIT", 'i');
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                dbgLog(TAG, "Requested Orientation after adjustment: SCREEN_ORIENTATION_REVERSE_PORTRAIT", 'i');
                break;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                dbgLog(TAG, "Requested Orientation after adjustment: SCREEN_ORIENTATION_LANDSCAPE", 'i');
                break;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                dbgLog(TAG, "Requested Orientation after adjustment: SCREEN_ORIENTATION_REVERSE_LANDSCAPE", 'i');
                break;
            }
        }

        super.setRequestedOrientation(correctedOrientation);
    }

    public void logTestResults(String tagName, String passFail, List<String> testResultName, List<String> testResultValues)
    {
        List<String> overallResultsColumns = new ArrayList<String>();
        List<String> overallResultsValues = new ArrayList<String>();

        DatabaseHandler db = new DatabaseHandler(this);

        String overallResultsTableName = TestUtils.getSequenceFileInUse() + "_results";
        String testResultsTableName = tagName + "_results";

        overallResultsColumns.add("PASS_FAIL");
        overallResultsColumns.add("RESULT_DATABASE");

        db.addTable(overallResultsTableName, overallResultsColumns);

        overallResultsValues.add(passFail);

        if ((testResultName != null) && (testResultValues != null))
        {
            List<String> testResultsColumns = new ArrayList<String>();
            List<String> testResultsValues = new ArrayList<String>();

            overallResultsValues.add(testResultsTableName);

            testResultsColumns.add("RESULT");

            db.deleteTable(testResultsTableName);

            db.addTable(testResultsTableName, testResultsColumns);

            for (int resultNum = 0; resultNum < testResultName.size(); resultNum++)
            {
                testResultsValues.clear();
                testResultsValues.add(testResultValues.get(resultNum));

                db.addRecord(new DatabaseRecord(testResultsTableName, testResultName.get(resultNum), testResultsColumns, testResultsValues));
            }
        }
        else
        {
            overallResultsValues.add(null);
        }

        try
        {
            DatabaseRecord databaseRecord = new DatabaseRecord(overallResultsTableName, tagName, overallResultsColumns, overallResultsValues);
            db.addRecord(databaseRecord);
        }
        catch (SQLException e)
        {

        }
    }

    public void collapseNotificationPanel()
    {
        // Hide notification panel if it is pulled down
        Object statusBarService = getSystemService("statusbar");
        Class<?> statusBarManager = null;

        try
        {
            statusBarManager = Class.forName("android.app.StatusBarManager");
        }
        catch (ClassNotFoundException e)
        {
            dbgLog(TAG, "StatusBarManager Class not found", 'i');
        }

        Method collapseNotificationPanel = null;

        try
        {
            // Before API 17, the notification panel method is 'collapse()'
            // API 17 and later, the notification panel method is
            // `collapsePanels()`
            if (Build.VERSION.SDK_INT > 16)
            {
                collapseNotificationPanel = statusBarManager.getMethod("collapsePanels");
            }
            else
            {
                collapseNotificationPanel = statusBarManager.getMethod("collapse");
            }
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }

        collapseNotificationPanel.setAccessible(true);

        try
        {
            collapseNotificationPanel.invoke(statusBarService);
            dbgLog(TAG, "invoking Collapse StatusBar complete", 'i');
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Exception invoking Collapse StatusBar" + e.toString(), 'i');
        }
    }

}
