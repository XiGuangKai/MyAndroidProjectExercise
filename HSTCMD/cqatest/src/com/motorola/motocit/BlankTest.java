/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;

public class BlankTest extends Test_Base
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "BlankTest";
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendStartActivityPassed();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("help"))
        {
            printHelp();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add(String.format("%s help printed", TAG));
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        // If get here then unrecognised tell cmd
        // Generate an exception to send FAIL result and mesg back to CommServer
        List<String> strErrMsgList = new ArrayList<String>();
        strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
        dbgLog(TAG, strErrMsgList.get(0), 'i');
        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function returns display a blank screen");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.clear();
        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return false;
    }

    @Override
    public boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    public boolean onSwipeUp()
    {
        return false;
    }

    @Override
    public boolean onSwipeDown()
    {
        return false;
    }
}
