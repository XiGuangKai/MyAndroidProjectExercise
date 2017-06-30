/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.led;

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Led extends Test_Base
{

    private CheckBox mMsgRed;
    private CheckBox mMsgGreen;
    private CheckBox mMsgBlue;
    private CheckBox mMsgWhite;
    private CheckBox mMsgSmartNotification;

    private NotificationManager mNotificationManager;

    private Context mContext;

    private int mRedIntensity = 255;
    private int mBlueIntensity = 255;
    private int mGreenIntensity = 255;
    private int mWhiteIntensity = 255;
    private int mAlpha = 255;
    private int mLedOnMS = 1;
    private int mLedOffMS = 0;
    private boolean redOn = false;
    private boolean greenOn = false;
    private boolean blueOn = false;
    private boolean whiteOn = false;
    private boolean SmartNotificationOn = false;
    final Notification notification = new Notification();
    private static final int NOTIFICATION_ID = R.string.smart_notification_started;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Notification_LED";
        super.onCreate(savedInstanceState);

        mContext = this;

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.led);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        mMsgRed = (CheckBox) findViewById(com.motorola.motocit.R.id.MsgRed);
        mMsgGreen = (CheckBox) findViewById(com.motorola.motocit.R.id.MsgGreen);
        mMsgBlue = (CheckBox) findViewById(com.motorola.motocit.R.id.MsgBlue);
        mMsgWhite = (CheckBox) findViewById (com.motorola.motocit.R.id.MsgWhite);
        mMsgSmartNotification = (CheckBox) findViewById(com.motorola.motocit.R.id.MsgSmartNotification);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mMsgRed.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                redOn = mMsgRed.isChecked();
                if(!redOn)
                {
                    mNotificationManager.cancelAll();
                }
            }
        });

        mMsgGreen.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                greenOn = mMsgGreen.isChecked();
                if(!greenOn)
                {
                    mNotificationManager.cancelAll();
                }
            }
        });

        mMsgBlue.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                blueOn = mMsgBlue.isChecked();
                if(!blueOn)
                {
                    mNotificationManager.cancelAll();
                }
            }
        });

        mMsgWhite.setOnCheckedChangeListener (new CheckBox.OnCheckedChangeListener () {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                whiteOn = mMsgWhite.isChecked ();
                if(!whiteOn)
                {
                    mNotificationManager.cancelAll();
                }
            }
        });

        mMsgSmartNotification.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                SmartNotificationOn = mMsgSmartNotification.isChecked();
                showNotification();
            }
        });
    };

    protected void showNotification()
    {
        if (SmartNotificationOn)
        {
            // Setup the text from the string resource
            CharSequence text = getText(NOTIFICATION_ID);

            // Set the icon, scrolling text and time stamp
            Notification notification = new Notification(R.drawable.smart_notification_icon, text, System.currentTimeMillis());

            // The PendingIntent to launch our activity if the user selects this notification
            // Right now, this launches the Smart Notification activity
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, com.motorola.motocit.led.Led.class), 0);

            // Set the info for the views that show in the notification panel.
            notification.setLatestEventInfo(this, getText(R.string.smart_notification_label), text, contentIntent);

            // Send the notification.
            mNotificationManager.notify(NOTIFICATION_ID, notification);

        }
        else
        {
            mNotificationManager.cancelAll();
        }

    }

    protected void lightNotificationLED()
    {
        if (redOn || greenOn || blueOn)
        {
            int red = (redOn ? 1 : 0) * mRedIntensity;
            int green = (greenOn ? 1 : 0) * mGreenIntensity;
            int blue = (blueOn ? 1 : 0) * mBlueIntensity;

            notification.ledARGB = (mAlpha * 16777216) + (red * 65536) + (green * 256) + blue;

            notification.ledOnMS = mLedOnMS;
            notification.ledOffMS = mLedOffMS;
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;

            mNotificationManager.notify(0, notification);

        }
        else if (whiteOn)
        {
            int white = (whiteOn ? 1 : 0) * mWhiteIntensity;
            notification.ledARGB = (mAlpha * 16777216) + (white * 65793);

            notification.ledOnMS = mLedOnMS;
            notification.ledOffMS = mLedOffMS;
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;

            mNotificationManager.notify (0, notification);
        }
        else
        {
            mNotificationManager.cancelAll();
        }
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // make klocwork happy
            if (null == action)
            {
                return;
            }

            //This is required in ICS to re-enable the notification after the screen is off
            //because ICS turns off the LED when a user is present.
            if (action.equals(Intent.ACTION_SCREEN_OFF))
            {
                if (redOn || greenOn || blueOn || whiteOn)
                {
                    dbgLog(TAG, "received screen off, going to turn on notification led", 'i');
                    lightNotificationLED();
                }
            }

            if (action.equals(Intent.ACTION_SCREEN_ON))
            {
                if (redOn || greenOn || blueOn || whiteOn)
                {
                    dbgLog(TAG, "received screen on, going to turn off notification led", 'i');
                    mNotificationManager.cancelAll();
                }
            }

        }
    };

    @Override
    protected void onStart()
    {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        mContext.registerReceiver(mIntentReceiver, filter);

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Led Test:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            mNotificationManager.cancel(Notification.DEFAULT_LIGHTS);

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Led Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                mNotificationManager.cancel(Notification.DEFAULT_LIGHTS);
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mNotificationManager.cancelAll();
        mContext.unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("SET_LED_SETTINGS"))
        {
            int iIntensity = 0;
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (int i = 0; i < strRxCmdDataList.size(); i++)
                {
                    if (strRxCmdDataList.get(i).toUpperCase().contains("ALPHA") || strRxCmdDataList.get(i).toUpperCase().contains("RED")
                            || strRxCmdDataList.get(i).toUpperCase().contains("BLUE") || strRxCmdDataList.get(i).toUpperCase().contains("GREEN")
                            || strRxCmdDataList.get (i).toUpperCase ().contains ("WHITE"))
                    {
                        iIntensity = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));

                        if (iIntensity > 255)
                        {
                            iIntensity = 255;
                        }

                        if (iIntensity <= 0)
                        {
                            iIntensity = 0;
                        }

                        if (strRxCmdDataList.get(i).toUpperCase().contains("ALPHA"))
                        {
                            mAlpha = iIntensity;
                        }

                        if (strRxCmdDataList.get(i).toUpperCase().contains("RED"))
                        {
                            mRedIntensity = iIntensity;
                        }

                        if (strRxCmdDataList.get(i).toUpperCase().contains("GREEN"))
                        {
                            mGreenIntensity = iIntensity;
                        }

                        if (strRxCmdDataList.get(i).toUpperCase().contains("BLUE"))
                        {
                            mBlueIntensity = iIntensity;
                        }
                        if (strRxCmdDataList.get (i).toUpperCase ().contains ("WHITE"))
                        {
                            mWhiteIntensity = iIntensity;
                        }
                    }
                    else if (strRxCmdDataList.get(i).toUpperCase().contains("ON_TIME"))
                    {
                        mLedOnMS = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));
                    }
                    else if (strRxCmdDataList.get(i).toUpperCase().contains("OFF_TIME"))
                    {
                        mLedOffMS = Integer.parseInt(strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1));
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + strRxCmdDataList.get(i));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

        }
        else if (strRxCmd.equalsIgnoreCase("GET_LED_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("ALPHA=" + mAlpha);
            strDataList.add("RED=" + mRedIntensity);
            if (redOn)
            {
                strDataList.add("RED_STATE=ON");
            }
            else
            {
                strDataList.add("RED_STATE=OFF");
            }
            strDataList.add("BLUE=" + mBlueIntensity);
            if (blueOn)
            {
                strDataList.add("BLUE_STATE=ON");
            }
            else
            {
                strDataList.add("BLUE_STATE=OFF");
            }
            strDataList.add("GREEN=" + mGreenIntensity);
            if (greenOn)
            {
                strDataList.add("GREEN_STATE=ON");
            }
            else
            {
                strDataList.add("GREEN_STATE=OFF");
            }
            strDataList.add ("WHITE=" + mWhiteIntensity);
            if (whiteOn)
            {
                strDataList.add ("WHITE_STATE=ON");
            }
            else
            {
                strDataList.add ("WHITE_STATE=OFF");
            }
            strDataList.add("ON_TIME=" + mLedOnMS);
            strDataList.add("OFF_TIME=" + mLedOffMS);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_LED"))
        {
            if (mRedIntensity > 0)
            {
                redOn = true;
            }
            if (mBlueIntensity > 0)
            {
                blueOn = true;
            }
            if (mGreenIntensity > 0)
            {
                greenOn = true;
            }
            if (mWhiteIntensity > 0)
            {
                whiteOn = true;
            }

            lightNotificationLED();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_OFF_LED"))
        {
            redOn = false;
            greenOn = false;
            blueOn = false;
            whiteOn = false;

            mNotificationManager.cancelAll();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_SMART_NOTIFICATION"))
        {
            SmartNotificationOn = true;
            showNotification();
            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_SMART_NOTIFICATION"))
        {
            SmartNotificationOn = false;
            showNotification();
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
        strHelpList.add("This function enable the notification LED");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("SET_LED_SETTINGS - For Red, Green, Blue or White LED.");
        strHelpList.add("  ");
        strHelpList.add("GET_LED_SETTINGS - For Red, Green, Blue or White LED.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_LED - Turn On LED.");
        strHelpList.add("  ");
        strHelpList.add("TURN_OFF_LED - Turn off LED");
        strHelpList.add("  ");
        strHelpList.add("START_SMART_NOTIFICATION - Start smart notification");
        strHelpList.add("  ");
        strHelpList.add("STOP_SMART_NOTIFICATION - Stop smart notification");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Led Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Led Test:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        mNotificationManager.cancel(Notification.DEFAULT_LIGHTS);

        systemExitWrapper(0);
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
        if (modeCheck("Seq"))
        {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

            return false;
        }
        else
        {
            mNotificationManager.cancel(Notification.DEFAULT_LIGHTS);
            systemExitWrapper(0);
        }
        return true;
    }
}
