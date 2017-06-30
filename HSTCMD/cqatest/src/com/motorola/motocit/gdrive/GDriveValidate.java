/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.gdrive;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.datablock.CIDValidationException;
import com.motorola.datablock.CIDValidator;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class GDriveValidate extends Test_Base
{
    private static final String CID_BLOCK_PATH = "/dev/block/platform/msm_sdcc.1/by-name/cid";

    TextView gDriveValidateText;

    private int gdrivestatus = -999;
    private final int cidBlockDataLen = 2630;
    private boolean cidBlockReadStatus = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "GDriveValidate";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.gdrivevalidate);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        gDriveValidateText = (TextView) findViewById(com.motorola.motocit.R.id.text_gdrive);
        validateGDrive();
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

        if ((cidBlockReadStatus == false) || (gdrivestatus == 0))
        {
            gdrivestatus = 0;
            gDriveValidateText.setTextColor(Color.BLUE);
            gDriveValidateText.setText("GDrive validate: Disabled. Value=" + gdrivestatus);
        }
        else if (gdrivestatus == 1)
        {
            gDriveValidateText.setTextColor(Color.GREEN);
            gDriveValidateText.setText("GDrive validate: Enabled. Value=" + gdrivestatus);
        }
        else if (gdrivestatus == -1)
        {
            gDriveValidateText.setTextColor(Color.RED);
            gDriveValidateText.setText("Failed to validate GDrive status. Value=" + gdrivestatus);
        }
        else
        {
            gDriveValidateText.setTextColor(Color.RED);
            gDriveValidateText.setText("Unknown error occured. Value=" + gdrivestatus);
        }

        sendStartActivityPassed();
    }

    protected void validateGDrive()
    {
        byte[] cidBlockData =  getCIDBlockData();

        gdrivestatus = -1;

        if ((cidBlockReadStatus == true) && (cidBlockData.length != 0))
        {
            try
            {
                CIDValidator val = new CIDValidator(cidBlockData);
                val.ValidateCIDBlock();
                String modelID = val.getDeviceModelID();
                dbgLog(TAG,"Model id from CID " + modelID, 'i');
                dbgLog(TAG,"Model id from device is " + Build.MODEL, 'i');
                if ((modelID != null) &&
                        (modelID.length() != 0) &&
                        (!modelID.equals("XT1080_DUMMY")))
                {
                    if (Build.MODEL.equalsIgnoreCase(modelID)) {
                        gdrivestatus = 1;
                        dbgLog(TAG, "validateGDrive() - gdrivestatus=" + gdrivestatus, 'i');
                    } else {
                        gdrivestatus = -1;
                        dbgLog(TAG, "validateGDrive() - gdrivestatus=" + gdrivestatus, 'i');
                        dbgLog(TAG, "Model ID Mismatch invalid CIDblock", 'i');
                    }
                }
                else
                {
                    gdrivestatus = 0;
                    dbgLog(TAG, "validateGDrive() - gdrivestatus=" + gdrivestatus, 'i');
                }
            }
            catch (CIDValidationException e)
            {
                dbgLog(TAG, "ValidateGDrive Exception=" + e.toString(), 'e');
                dbgLog(TAG, "validateGDrive() - gdrivestatus=" + gdrivestatus, 'i');
                e.printStackTrace();
            }
        }
        else
        {
            dbgLog(TAG, "cidBlockData length error", 'e');
        }
    }

    protected byte[] getCIDBlockData()
    {
        FileInputStream ios = null;
        byte[] buffer = new byte[cidBlockDataLen];

        cidBlockReadStatus = false;

        try{
            ios = new FileInputStream(CID_BLOCK_PATH);
            int startLen = 0;
            int readLen = 0;
            int totalLen = cidBlockDataLen;
            while (startLen != totalLen)
            {
                readLen = ios.read(buffer, startLen, (totalLen - startLen));
                dbgLog(TAG, "Bytes read from block=" + readLen, 'i');
                startLen += readLen;
            }
            cidBlockReadStatus = true;
        }
        catch(Exception e){
            e.printStackTrace();
            dbgLog(TAG, "CID Block Data Exception=" + e.toString(), 'e');
            dbgLog(TAG, "Failed to read CID block", 'e');
        }
        finally
        {
            try
            {
                if (ios != null)
                {
                    ios.close();
                }
            }
            catch (IOException e)
            {
                dbgLog(TAG, "Release ios error", 'e');
            }
        }
        dbgLog(TAG, "Return block data...", 'i');
        return buffer;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        /* When running from CommServer normally ignore KeyDown event */
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "GDriveValidate Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "GDriveValidate Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_GDRIVE_STATUS"))
        {
            List<String> strDataList = new ArrayList<String>();

            validateGDrive();

            // RETURN STATUS OF CID BLOCK READ
            if (cidBlockReadStatus == true)
            {
                strDataList.add(String.format("CID_BLOCK_READ=PASS"));
            }
            else
            {
                strDataList.add(String.format("CID_BLOCK_READ=FAIL"));
            }

            // RETURN GDRIVE STATUS && GDRIVE BIT VALUE
            if ((cidBlockReadStatus == false) || (gdrivestatus == 0))
            {
                strDataList.add(String.format("GDRIVE_STATUS=DISABLED"));
                strDataList.add(String.format("GDRIVE_VALUE=0"));
            }
            else if (gdrivestatus == 1)
            {
                strDataList.add(String.format("GDRIVE_STATUS=ENABLED"));
                strDataList.add(String.format("GDRIVE_VALUE=" + gdrivestatus));
            }
            else
            {
                strDataList.add(String.format("GDRIVE_STATUS=GENERIC_FAILURE"));
                strDataList.add(String.format("GDRIVE_VALUE=" + gdrivestatus));
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
        strHelpList.add("This function is to validate GDrive status");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_GDRIVE_STATUS - Get current GDrive status.");
        strHelpList.add(" CID_BLOCK_READ - returns PASS or FAIL");
        strHelpList.add(" GDRIVE_VALUE - returns numberic value for GDrive bit");
        strHelpList.add(" GDRIVE_STATUS - returns ENABLED, DISABLED, or GENERIC_FAIL");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);

        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "GDriveValidate Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "GDriveValidate Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
