/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.settings;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class GetSettingsInfo extends Test_Base {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "GetSettingsInfo";
        super.onCreate (savedInstanceState);
    }

    @Override
    protected void onStart()
    {
        super.onStart ();
    }

    @Override
    protected void onResume()
    {
        super.onResume ();

        sendStartActivityPassed ();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy ();
    }

    protected int getBrightnessMode()
    {
        int brightnessMode = -1;
        Context context = getApplicationContext ();

        try
        {
            brightnessMode = Settings.System.getInt (context.getContentResolver (), Settings.System.SCREEN_BRIGHTNESS_MODE);
        }
        catch (SettingNotFoundException snfe)
        {}
        return brightnessMode;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase ("GET_SETTINGS_INFO_AUTOBRIGHTNESS"))
        {
            List<String> strDataList = new ArrayList<String> ();

            if (getBrightnessMode () == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            {
                strDataList.add ("SETTINGS_AUTO_BRIGHTNESS=ENABLED");
            }
            else if (getBrightnessMode () == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            {
                strDataList.add ("SETTINGS_AUTO_BRIGHTNESS=DISABLED");
            }
            else
            {
                strDataList.add ("SETTINGS_AUTO_BRIGHTNESS=UNKNOWN");
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket (nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer (infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String> ();
            throw new CmdPassException (nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase ("help"))
        {
            printHelp ();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String> ();
            strReturnDataList.add (String.format ("%s help printed", TAG));
            throw new CmdPassException (nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else
        {
            // Generate an exception to send FAIL result and mesg back to
            // CommServer
            List<String> strErrMsgList = new ArrayList<String> ();
            strErrMsgList.add (String.format ("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog (TAG, strErrMsgList.get (0), 'i');
            throw new CmdFailException (nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String> ();

        strHelpList.add (TAG);
        strHelpList.add ("");
        strHelpList.add ("This function is to get device Settings app status");
        strHelpList.add ("");

        strHelpList.addAll (getBaseHelp ());

        strHelpList.add ("Activity Specific Commands");
        strHelpList.add ("  ");
        strHelpList.add ("GET_SETTINGS_INFO_AUTOBRIGHTNESS - Get current auto brightness status.");
        strHelpList.add ("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket (nRxSeqTag, strRxCmd, TAG, strHelpList);

        sendInfoPacketToCommServer (infoPacket);
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
