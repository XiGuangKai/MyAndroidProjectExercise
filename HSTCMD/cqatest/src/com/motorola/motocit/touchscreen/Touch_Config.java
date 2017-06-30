/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.touchscreen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;

public class Touch_Config extends Touch_Base
{
    int mLineWidthDivisor = 4;
    List<Integer> mXList = new ArrayList<Integer>();
    List<Integer> mYList = new ArrayList<Integer>();
    boolean mDraw = false;
    TS_Config_View TS_View;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Touch_Config";
        passMessage = "Touch_Config Passed";
        failMessage = "Touch_Config Failed";
        super.onCreate(savedInstanceState);

        setContentView(new TS_Config_View(this));

        if (wasActivityStartedByCommServer() == false)
        {
            getTouchConfigSettingsFromConfig(mXList, mYList, mLineWidthDivisor);
            // Generate an exception to send data back to CommServer
            mDraw = true;
            InvalidateWindow invalidateWindow = new InvalidateWindow();
            runOnUiThread(invalidateWindow);
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    private class InvalidateWindow implements Runnable
    {
        @Override
        public void run()
        {
            getWindow().getDecorView().invalidate();
        }
    }

    public class TS_Config_View extends Touch_View
    {
        private float mViewHeight;
        private float mViewWidth;
        private boolean mFirstDraw = true;

        public TS_Config_View(Context c)
        {
            super(c);
            // Set the line size. For MXT224 devices, this is set at 1/4 the
            // shorter of the sides
            mPathLinePaint.setStrokeWidth(mLineSize);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            if (mDraw)
            {
                if (mFirstDraw)
                {
                    testStatus = TEST_STATUS_RUNNING;

                    mBottom = mHeaderBottom;
                    mViewHeight = getTouchHeight();
                    mViewWidth = getTouchWidth();
                    mLineSize = (getTouchWidth() < getTouchHeight() ? getTouchWidth() / mLineWidthDivisor : getTouchHeight() / mLineWidthDivisor);
                    mFirstDraw = false;

                    for (int i = 0; i < mXList.size(); i++)
                    {
                        addVertex(mXList.get(i), mYList.get(i), mViewHeight, mViewWidth, mLineSize, mBottom);
                    }

                    Paint notepaint = new Paint();
                    notepaint.setAntiAlias(true);
                    notepaint.setTextSize(20);
                    notepaint.setARGB(255, 255, 255, 0);

                    int h = getTouchHeight();
                    mPathLinePaint.setStrokeWidth(10);
                    String str = getString(com.motorola.motocit.R.string.touchscreen_note);
                    canvas.drawText(str, TestUtils.getDisplayXLeftOffset() + 1, TestUtils.getDisplayYTopOffset() + (h / 2), notepaint);
                }
                // Helper function to draw statistics at top of screen
                drawStats(canvas, mCurX, mCurY, mCurPressure, mCurSize, mVelocity);
                // Draw starting point
                drawLines(canvas);

                if ((mSegmentStatus >= 2) && !mCurDown)
                {
                    contentRecord("testresult.txt", "Touch Screen - Config:  PASS" + "\r\n\r\n", MODE_APPEND);

                    logTestResults(TAG, TEST_PASS, null, null);

                    try
                    {
                        testPassed();
                    }
                    catch (CmdPassException e)
                    {
                        // This command response was not triggered by the computer
                        // so
                        // create unsolicited packet back to CommServer reporting
                        // the test passed
                        CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "PASS", TAG, e.strReturnDataList);
                        sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                        testStatus =  TEST_STATUS_PASS;
                    }
                    catch (CmdFailException e)
                    {
                        // This command response was not triggered by the computer
                        // so
                        // create unsolicited packet back to CommServer reporting
                        // the test failed
                        CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "FAIL", TAG, e.strErrMsgList);
                        sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                    }
                    finally
                    {
                        // delay 1/2 second, simply to show pass
                        try
                        {
                            Thread.sleep(500, 0);
                        }
                        catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }

                        // Check if commServer started this activity
                        // - if not then close the activity else continue to display
                        // the result screen
                        if (!wasActivityStartedByCommServer())
                        {
                            finish();
                        }
                    }
                }

                drawTouchPath(canvas, mVelocity);
                drawCrossHairs(canvas);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN))
        {

            contentRecord("testresult.txt", "Touch Screen - Touch_Config:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_TOUCH_RESULT"))
        {
            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("TOUCH_RESULT=" + testStatus));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            List<String> strReturnDataList = new ArrayList<String>();
            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_TOUCH_SETTINGS"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("X_VERTICES"))
                    {
                        mXList.clear();

                        String splitValue[] = value.split(",");

                        for (String element : splitValue)
                        {
                            if ((Integer.parseInt(element) < 0) || (Integer.parseInt(element) > 1000))
                            {
                                List<String> strErrMsgList = new ArrayList<String>();
                                strErrMsgList.add(String.format("X_VERTICES MUST BE 0 <= X <= 1000"));
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                            }
                            mXList.add(Integer.parseInt(element));
                        }
                    }
                    else if (key.equalsIgnoreCase("Y_VERTICES"))
                    {
                        mYList.clear();

                        String splitValue[] = value.split(",");

                        for (String element : splitValue)
                        {
                            if ((Integer.parseInt(element) < 0) || (Integer.parseInt(element) > 1000))
                            {
                                List<String> strErrMsgList = new ArrayList<String>();
                                strErrMsgList.add(String.format("Y_VERTICES MUST BE 0 <= Y <= 1000"));
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                            }
                            mYList.add(Integer.parseInt(element));
                        }

                    }
                    else if (key.equalsIgnoreCase("TOUCH_WIDTH_DIVISOR"))
                    {
                        mLineWidthDivisor = Integer.parseInt(value);
                        if (mLineWidthDivisor < 1)
                        {
                            List<String> strErrMsgList = new ArrayList<String>();
                            strErrMsgList.add(String.format("TOUCH_WIDTH_DIVISOR MUST BE POSITIVE INTEGER"));
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                        }
                    }
                }

                // error checking
                if (mXList.size() != mYList.size())
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("NUMBER OF X_VERTICES MUST EQUAL Y_VERTICES "));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Generate an exception to send data back to CommServer
                mDraw = true;
                InvalidateWindow invalidateWindow = new InvalidateWindow();
                runOnUiThread(invalidateWindow);

                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add(String.format("%s help printed", TAG));
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_TOUCH_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            // X_VERTICES
            StringBuffer xList = new StringBuffer();

            for (int i = 0; i < mXList.size(); i++)
            {
                xList.append(mXList.get(i));

                // only append "," if not last element
                if ((i + 1) < mXList.size())
                {
                    xList.append(",");
                }
            }

            strDataList.add("X_VERTICES=" + xList.toString());

            // Y_VERTICES
            StringBuffer yList = new StringBuffer();

            for (int i = 0; i < mYList.size(); i++)
            {
                yList.append(mYList.get(i));

                // only append "," if not last element
                if ((i + 1) < mYList.size())
                {
                    yList.append(",");
                }
            }

            strDataList.add("Y_VERTICES=" + yList.toString());

            strDataList.add("TOUCH_WIDTH_DIVISOR=" + mLineWidthDivisor);

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
        strHelpList.add("This activity tests a touchscreen on a phone by having the operator trace a line as it creates an");
        strHelpList.add("hourglass pattern on the screen.  Volume Down to fail the test.  The vertices and line width can be ");
        strHelpList.add("configured using SET_TOUCH_SETTINGS.");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_TOUCH_RESULT - returns the TOUCH_RESULT value");
        strHelpList.add("  ");
        strHelpList.add("SET_TOUCH_SETTINGS - Sets values for all touch points");
        strHelpList.add("  ");
        strHelpList.add("  X_VERTICES=X0,X1 - X Vertices for each touch point");
        strHelpList.add("  ");
        strHelpList.add("  Y_VERTICES=Y0,Y1 - Y Vertices for each touch point");
        strHelpList.add("  ");
        strHelpList.add("  TOUCH_WIDTH_DIVISOR - Sets the size of the touch points");
        strHelpList.add("  ");
        strHelpList.add("GET_TOUCH_SETTINGS - Returns X_VERTICES=X0,X1,... Y_VERTICES=Y0,Y1,... TOUCH_WIDTH_DIVISOR=X)");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    public boolean getTouchConfigSettingsFromConfig(List<Integer> XList, List<Integer> YList, int lineWidthDivisor)
    {
        boolean result = false;
        String SequenceFileInUse = TestUtils.getSequenceFileInUse();
        File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
        String config_file = null;

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else if (file_system_sdcard.exists())
        {
            config_file = file_system_sdcard.toString();
        }
        else
        {
            dbgLog(TAG, "!! CANN'T FIND TOUCH CONFIG FILE", 'd');
        }

        if ((config_file != null) && (SequenceFileInUse != null))
        {
            try
            {
                BufferedReader breader = new BufferedReader(new FileReader(config_file));
                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    if (line.contains("<TOUCH CONFIG SETTINGS>") == true)
                    {
                        result = true;
                        break;
                    }
                }

                if (null != line)
                {
                    dbgLog(TAG, "Touch Config Settings: " + line, 'd');
                    String[] fields = line.split(",");
                    for (String field : fields)
                    {
                        if (field.contains("X_VERTICES"))
                        {
                            String[] tokens = field.split("=");
                            XList.clear();

                            String splitValue[] = tokens[1].split("\\|");

                            for (String element : splitValue)
                            {
                                if ((Integer.parseInt(element) < 0) || (Integer.parseInt(element) > 1000))
                                {
                                    dbgLog(TAG, "X_VERTICES MUST BE 0 <= X <= 1000" + line, 'd');
                                    result = false;
                                }
                                XList.add(Integer.parseInt(element));
                            }
                        }
                        if (field.contains("Y_VERTICES"))
                        {
                            String[] tokens = field.split("=");
                            YList.clear();

                            String splitValue[] = tokens[1].split("\\|");

                            for (String element : splitValue)
                            {
                                if ((Integer.parseInt(element) < 0) || (Integer.parseInt(element) > 1000))
                                {
                                    dbgLog(TAG, "Y_VERTICES MUST BE 0 <= Y <= 1000" + line, 'd');
                                    result = false;
                                }
                                YList.add(Integer.parseInt(element));
                            }
                        }
                        if (field.contains("TOUCH_WIDTH_DIVISOR :" + field))
                        {
                            String[] tokens = field.split("=");
                            mLineWidthDivisor = Integer.parseInt(tokens[1]);
                            if (mLineWidthDivisor < 1)
                            {
                                dbgLog(TAG, "TOUCH_WIDTH_DIVISOR MUST BE POSITIVE INTEGER" + line, 'd');
                                result = false;
                            }
                        }
                    }

                    dbgLog(TAG, "Parsed: X_VERTICES=" + XList.toString() + ", Y_VERTICES=" + YList.toString() + ", TOUCH_WIDTH_DIVISOR="
                            + mLineWidthDivisor, 'd');

                }

                breader.close();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "!!! Some exception in parsing touch config settings", 'd');
                dbgLog(TAG, "!Exception=" + e.toString(), 'd');
            }
        }

        return result;
    }
}
