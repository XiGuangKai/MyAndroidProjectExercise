/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.ringtone;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class Ringtone extends Test_Base
{

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Ringtone";
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_RINGTONE_TYPE"))
        {
            List<String> strDataList = new ArrayList<String>();
            Uri uri = null;
            android.media.Ringtone ringtone = null;
            ContentResolver cr;
            cr = getContentResolver();
            String[] projection = { MediaStore.MediaColumns.DATA };
            Cursor cur;

            uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);

            if (uri != null)
            {
                strDataList.add("RINGTONE_DEFAULT_URI=" + uri.getPath());
                ringtone = RingtoneManager.getRingtone(this, uri);
                strDataList.add("RINGTONE_DEFAULT_TITLE=" + ringtone.getTitle(this));

                cur = cr.query(uri, projection, null, null, null);
                if (cur != null)
                {

                    cur.moveToFirst();

                    String filePath = cur.getString(0);
                    strDataList.add("RINGTONE_DEFAULT_PATH=" + filePath);
                }
                else
                {
                    strDataList.add("RINGTONE_DEFAULT_PATH=NULL");
                }
            }
            else
            {
                strDataList.add("RINGTONE_DEFAULT_URI=NULL");
                strDataList.add("RINGTONE_DEFAULT_TITLE=NULL");
                strDataList.add("RINGTONE_DEFAULT_PATH=NULL");
            }

            uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
            if (uri != null)
            {
                strDataList.add("NOTIFICATION_DEFAULT_URI=" + uri.getPath());
                ringtone = RingtoneManager.getRingtone(this, uri);
                strDataList.add("NOTIFICATION_DEFAULT_TITLE=" + ringtone.getTitle(this));

                cur = cr.query(uri, projection, null, null, null);
                if (cur != null)
                {

                    cur.moveToFirst();

                    String filePath = cur.getString(0);
                    strDataList.add("NOTIFICATION_DEFAULT_PATH=" + filePath);
                }
                else
                {
                    strDataList.add("NOTIFICATION_DEFAULT_PATH=NULL");
                }
            }
            else
            {
                strDataList.add("NOTIFICATION_DEFAULT_URI=NULL");
                strDataList.add("NOTIFICATION_DEFAULT_TITLE=NULL");
                strDataList.add("NOTIFICATION_DEFAULT_PATH=NULL");
            }

            uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (uri != null)
            {
                strDataList.add("ALARM_DEFAULT_URI=" + uri.getPath());
                ringtone = RingtoneManager.getRingtone(this, uri);
                strDataList.add("ALARM_DEFAULT_TITLE=" + ringtone.getTitle(this));

                cur = cr.query(uri, projection, null, null, null);
                if (cur != null)
                {

                    cur.moveToFirst();

                    String filePath = cur.getString(0);
                    strDataList.add("ALARM_DEFAULT_PATH=" + filePath);
                }
                else
                {
                    strDataList.add("ALARM_DEFAULT_PATH=NULL");
                }
            }
            else
            {
                strDataList.add("ALARM_DEFAULT_URI=NULL");
                strDataList.add("ALARM_DEFAULT_TITLE=NULL");
                strDataList.add("ALARM_DEFAULT_PATH=NULL");
            }

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
            /* Generate an exception to send FAIL result and mesg back to */
            /* CommServer */
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
        strHelpList.add("This function is to return Ringtone source");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_RINGTONE_TYPE: Return path for URI, TITLE, and PATH for RINGTONE, NOTIFICATION, and ALARM");
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
