/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.screenlock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.TextView;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

/* The ScreenLockUtil activity TRIES to use the internal
   LockPatternUtils class to disable the lockscreen.
   The LockPatternUtils is an internal class that could
   change between different phone SWs.  So keep this
   in mind when using this activity.
 */

public class ScreenLockUtil extends Test_Base
{
    private TextView screenLockUtilMsgView;

    private KeyguardManager mKeyguardManager = null;
    private KeyguardLock mKeyguardLock = null;
    private PowerManager mPowerManager = null;
    private PowerManager.WakeLock mWakeLock = null;

    private final String LOCK_PATTERN_UTIL_PACKAGE_NAME = "com.android.internal.widget.LockPatternUtils";
    private Class<?> mLockPatternUtilClass = null;
    private Object mLockPatternUtilObject = null;
    private Method mSetLockScreenDisabledMethod = null;

    private Method mIsKeyguardLockedMethod = null;

    private boolean disableKeyguardLockOnDestroy = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "ScreenLockUtil";
        super.onCreate(savedInstanceState);

        SetupLockPatternUtilsFunctions();

        // this activity does not call testSetup()
        // because it can deviate from the standard
        // activity setup procedure
        doTestSetupAndRelease = false;

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (null != mPowerManager)
        {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "My Tag");
        }

        if (null != mWakeLock)
        {
            mWakeLock.acquire();
        }

        mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        if (null != mKeyguardManager)
        {
            mKeyguardLock = mKeyguardManager.newKeyguardLock(KEYGUARD_SERVICE);
        }

        if (null != mKeyguardLock)
        {
            mKeyguardLock.disableKeyguard();
        }

        setupIsKeyguardLockedMethod();

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.screenlockutil);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        screenLockUtilMsgView = (TextView) findViewById(com.motorola.motocit.R.id.screenlockutil_msg);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();
    }


    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if (null != mKeyguardLock)
        {
            if (true == disableKeyguardLockOnDestroy)
            {
                mKeyguardLock.disableKeyguard();
            }
            else
            {
                mKeyguardLock.reenableKeyguard();
            }
        }

        if (null != mWakeLock)
        {
            mWakeLock.release();
        }
    }

    private void SetupLockPatternUtilsFunctions()
    {
        // try to get LockPatternUtil and setLockScreenDisabled function
        try
        {
            // Load lockPatternUtil class
            mLockPatternUtilClass = Class.forName(LOCK_PATTERN_UTIL_PACKAGE_NAME);

            // get constructor
            Constructor<?> constructor = mLockPatternUtilClass.getConstructor(new Class[]
                    { Context.class });

            // create instance of lockPatternUtil class
            mLockPatternUtilObject = constructor.newInstance(this);

            //  get setLockScreenDisabled method
            mSetLockScreenDisabledMethod = mLockPatternUtilClass.getMethod("setLockScreenDisabled", new Class[]
                    { boolean.class });
        }
        catch (Exception e)
        {
            // if any exception then could not successfully setup
            // lockpatternutil via reflection.  Set related members to null
            mLockPatternUtilClass = null;
            mLockPatternUtilObject = null;
            mSetLockScreenDisabledMethod = null;
        }
    }

    private void setLockScreenDisabled(boolean disabled) throws CmdFailException
    {
        if (null != mSetLockScreenDisabledMethod)
        {
            // call setLockScreenDisabled method
            try
            {
                mSetLockScreenDisabledMethod.invoke(mLockPatternUtilObject, new Object[]
                        { disabled });
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("setLockScreenDisabled threw exception: " + e.toString());
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unable to access LockPatternUtils setLockScreenDisabled method");
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    private void setupIsKeyguardLockedMethod()
    {
        // try to get KeyguardManager.isKeyguardLocked()
        try
        {
            if (null != mKeyguardManager)
            {
                mIsKeyguardLockedMethod = KeyguardManager.class.getMethod("isKeyguardLocked");
            }
            else
            {
                mIsKeyguardLockedMethod = null;
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }
    }

    private boolean IsKeyguardLocked() throws CmdFailException
    {
        boolean result = false;

        if (null == mKeyguardManager)
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unable to access KeyguardManager");
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        if (null != mIsKeyguardLockedMethod)
        {
            try
            {
                result = (Boolean) mIsKeyguardLockedMethod.invoke(mKeyguardManager);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("mIsKeyguardLockedMethod threw exception: " + e.toString());
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unable to access KeyguardManager isKeyguardLocked method");
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        return result;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("DISABLE_SCREEN_LOCK"))
        {
            setLockScreenDisabled(true);

            UpdateUiTextView updateUi = new UpdateUiTextView(screenLockUtilMsgView, Color.WHITE, "Disabled Screen Lock");
            runOnUiThread(updateUi);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("ENABLE_SCREEN_LOCK"))
        {
            setLockScreenDisabled(false);

            UpdateUiTextView updateUi = new UpdateUiTextView(screenLockUtilMsgView, Color.WHITE, "Enabled Screen Lock");
            runOnUiThread(updateUi);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_KEYGUARD_ON_EXIT"))
        {
            disableKeyguardLockOnDestroy = true;

            UpdateUiTextView updateUi = new UpdateUiTextView(screenLockUtilMsgView, Color.WHITE, "Disabled Keyguard on Exit");
            runOnUiThread(updateUi);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("ENABLE_KEYGUARD_ON_EXIT"))
        {
            disableKeyguardLockOnDestroy = false;

            UpdateUiTextView updateUi = new UpdateUiTextView(screenLockUtilMsgView, Color.WHITE, "Enabled Keyguard on Exit");
            runOnUiThread(updateUi);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("BROADCAST_INTENT_ACTION_USER_PRESENT"))
        {
            Intent mUserPresentIntent = new Intent(Intent.ACTION_USER_PRESENT);
            mUserPresentIntent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            mUserPresentIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            sendBroadcast(mUserPresentIntent);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_KEYGUARDLOCK_STATE"))
        {
            boolean isKeyguardLocked = IsKeyguardLocked();
            boolean inKeyguardRestrictedInputMode = mKeyguardManager.inKeyguardRestrictedInputMode();

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("KEYGUARDLOCK=" + isKeyguardLocked);
            strDataList.add("KEYGUARDRESTRICTEDINPUTMODE=" + inKeyguardRestrictedInputMode);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This activity attempts to use the LockPatternUtils class to");
        strHelpList.add("disable the lockscreen.  This may or may NOT work on all phones");
        strHelpList.add("since this is accessing internal APIs that may not be constant");
        strHelpList.add("across all SW versions.");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("DISABLE_SCREEN_LOCK - Disable the screen lock setting");
        strHelpList.add("ENABLE_SCREEN_LOCK  - Enable the screen lock setting");
        strHelpList.add("DISABLE_KEYGUARD_ON_EXIT - Disable the keyguard when activity exits");
        strHelpList.add("ENABLE_KEYGUARD_ON_EXIT  - Enable the keyguard when activity exits");
        strHelpList.add("BROADCAST_INTENT_ACTION_USER_PRESENT  - Broadcast ACTION_USER_PRESENT intent");
        strHelpList.add("GET_KEYGUARDLOCK_STATE  - Get KeyguardLock state");
        strHelpList.add("  returns:");
        strHelpList.add("    KEYGUARDLOCK=<true or false>");
        strHelpList.add("    KEYGUARDRESTRICTEDINPUTMODE=<true or false>");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        return true;
    }

    @Override
    public boolean onSwipeUp()
    {
        return true;
    }

    @Override
    public boolean onSwipeDown()
    {
        return true;
    }
}
