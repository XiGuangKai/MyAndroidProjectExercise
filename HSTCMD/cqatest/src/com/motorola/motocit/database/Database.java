/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.database;

import java.util.ArrayList;
import java.util.List;

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

//Database is stored: data/data/com.motorola.motocit/databases and persists after power cycle

public class Database extends Test_Base
{
    private String table = null;
    private String record = null;
    List<String> columns = new ArrayList<String>();
    List<String> values = new ArrayList<String>();
    DatabaseHandler db = new DatabaseHandler(this);

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {

        table = null;
        record = null;
        columns.clear();
        values.clear();

        if (strRxCmd.equalsIgnoreCase("ADD_TABLE"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                    else if (key.equalsIgnoreCase("COLUMNS"))
                    {
                        columns.clear();
                        String splitValue[] = value.split(",");
                        for (String element : splitValue)
                        {
                            columns.add(element);
                        }
                    }
                }
                // error checking
                if ((table == null) || (columns.size() == 0))
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE AND COLUMNS MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    db.addTable(table, columns);
                }
                catch (SQLException e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("SQL ERROR"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE AND COLUMNS MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }

        else if (strRxCmd.equalsIgnoreCase("ADD_RECORD"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                    else if (key.equalsIgnoreCase("RECORD"))
                    {
                        record = value;
                    }
                    else if (key.equalsIgnoreCase("COLUMNS"))
                    {
                        columns.clear();
                        String splitValue[] = value.split(",");
                        for (String element : splitValue)
                        {
                            columns.add(element);
                        }
                    }
                    else if (key.equalsIgnoreCase("VALUES"))
                    {
                        values.clear();
                        String splitValue[] = value.split(",");
                        for (String element : splitValue)
                        {
                            values.add(element);
                        }
                    }
                }

                // error checking
                if ((table == null) || (record == null) || (columns.size() == 0) || (values.size() == 0) || (columns.size() != values.size()))
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE, RECORD, COLUMNS AND VALUES MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    DatabaseRecord databaseRecord = new DatabaseRecord(table, record, columns, values);
                    db.addRecord(databaseRecord);
                }
                catch (SQLException e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("SQL ERROR"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE, RECORD, COLUMNS AND VALUES MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }

        else if (strRxCmd.equalsIgnoreCase("GET_RECORD"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                    else if (key.equalsIgnoreCase("RECORD"))
                    {
                        record = value;
                    }
                }

                // error checking
                if ((table == null) || (record == null))
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE AND RECORD MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                DatabaseRecord databaseRecord = null;
                try
                {
                    databaseRecord = db.getRecord(table, record);
                }
                catch (SQLException e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("SQL ERROR"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                catch (CursorIndexOutOfBoundsException e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("RECORD DOES NOT EXIST"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                List<String> strDataList = new ArrayList<String>();
                strDataList.add("RECORD=" + record);
                strDataList.add("COLUMNS=" + TestUtils.join(databaseRecord.columns, ","));
                strDataList.add("VALUES=" + TestUtils.join(databaseRecord.values, ","));

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE AND RECORD MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }

        else if (strRxCmd.equalsIgnoreCase("GET_TABLE"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                }

                // error checking
                if (table == null)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                List<DatabaseRecord> databaseRecordList = new ArrayList<DatabaseRecord>();
                try
                {
                    databaseRecordList = db.getAllRecords(table);
                }
                catch (SQLException e)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("SQL ERROR"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
                catch (CursorIndexOutOfBoundsException e)
                {
                    // Do Nothing, Zero Records in Table
                }

                List<String> strDataList = new ArrayList<String>();
                strDataList.add("NUMBER_OF_RECORDS=" + databaseRecordList.size());

                if (databaseRecordList.size() > 0)
                {
                    strDataList.add("COLUMNS=" + TestUtils.join(databaseRecordList.get(0).columns, ","));
                }

                int i = 0;
                for (DatabaseRecord d : databaseRecordList)
                {
                    strDataList.add("RECORD_" + i + "=" + d.name);
                    strDataList.add("VALUES_" + i + "=" + TestUtils.join(d.values, ","));
                    i++;
                }

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }

        else if (strRxCmd.equalsIgnoreCase("LIST_TABLES"))
        {
            List<String> strTableList = new ArrayList<String>();
            List<String> strDataList = new ArrayList<String>();

            try
            {
                strTableList = db.getTables();
            }
            catch (SQLException e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("SQL ERROR"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            strDataList.add("NUMBER_OF_TABLES=" + strTableList.size());

            for (int tableNum = 0; tableNum < strTableList.size(); tableNum++)
            {
                strDataList.add("TABLE_" + tableNum + "=" + strTableList.get(tableNum));
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        else if (strRxCmd.equalsIgnoreCase("DELETE_RECORD"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                    else if (key.equalsIgnoreCase("RECORD"))
                    {
                        record = value;
                    }
                }

                // error checking
                if ((table == null) || (record == null))
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE AND RECORD MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    db.deleteRecord(table, record);
                }
                catch (SQLException e)
                {
                    // Ignore SQLException
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE AND RECORD MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }

        else if (strRxCmd.equalsIgnoreCase("DELETE_TABLE"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("TABLE"))
                    {
                        table = value;
                    }
                }

                // error checking
                if (table == null)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("TABLE MUST BE POPULATED"));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                try
                {
                    db.deleteTable(table);
                }
                catch (SQLException e)
                {
                    // Ignore SQLException
                }

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // error checking
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("TABLE MUST BE POPULATED"));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
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
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Database";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.database);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will store or retrieve items from a database");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add(" ADD_TABLE TABLE=table COLUMNS=c0,c1,c2...");
        strHelpList.add(" ADD_RECORD TABLE=table RECORD=record COLUMNS=c0,c1,c2... VALUES=v0,v1,v2,...");
        strHelpList.add(" GET_RECORD TABLE=table RECORD=record");
        strHelpList.add(" GET_TABLE TABLE=table");
        strHelpList.add(" LIST_TABLES");
        strHelpList.add(" DELETE_RECORD TABLE=table RECORD=record");
        strHelpList.add(" DELETE_TABLE TABLE=table");

        strHelpList.add("GET_ITEM RETURN FORMAT: VALUE=value");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Database Test: FAILED" + "\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Database Test: PASS" + "\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

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
            systemExitWrapper(0);
        }
        return true;
    }
}
