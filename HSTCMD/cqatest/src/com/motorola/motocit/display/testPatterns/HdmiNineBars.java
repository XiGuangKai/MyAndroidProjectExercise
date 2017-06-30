/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;

public class HdmiNineBars extends BlackPattern
{
    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    private int VertDivs = 0;
    private int HorDivs = 9;
    private boolean sendRedrawPassPacket = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_HdmiNineBars";
        super.onCreate(savedInstanceState);

        // change orientation to be sideways so that test pattern output in
        // mirror mode HDMI appears correct
        setRequestedOrientation(0);

        HdmiNineBars_View hdmiNineBars_View = new HdmiNineBars_View(this);
        setContentView(hdmiNineBars_View);
        if (mGestureListener != null)
        {
            hdmiNineBars_View.setOnTouchListener(mGestureListener);
        }
    }

    public class HdmiNineBars_View extends Pattern_View
    {

        public HdmiNineBars_View(Context c)
        {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            // if activity is ending don't redraw
            if (isActivityEnding())
            {
                return;
            }

            // NOTE: X and Y values are reversed since this is a landscape
            // pattern!
            int originY = 0 + TestUtils.getDisplayXLeftOffset();
            int originX = 0 + TestUtils.getDisplayYTopOffset();
            int width = getWidth() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());

            // if Divs set to 0, use that as flag to divs equal to display
            // resolution
            if (VertDivs == 0)
            {
                VertDivs = height;
            }
            if (HorDivs == 0)
            {
                HorDivs = width;
            }

            float VertRatio = (float) height / VertDivs;
            float HorRatio = (float) width / HorDivs;
            float[] hsv =
                { 0, 0, 0 }; // array to hold HSV values; Hue, Saturation, Value
            Rect r = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            // cycle thru grid incrementally changing HSV
            for (int rows = 0; rows < VertDivs; rows++)
            {
                for (int cols = 0; cols < HorDivs; cols++)
                {
                    hsv[0] = (((float) cols / HorDivs) * 360); // Hue 0 to 360
                    // one half of vertical pattern has fixed Value and varies
                    // Sat
                    if (rows < (VertDivs / 2))
                    {
                        hsv[1] = ((2 * (float) rows) / VertDivs); // Saturation
                        hsv[2] = (float) 1.0; // Value fixed to 1.0
                    }
                    // other half of vertical pattern has fixed Sat and varies
                    // Value
                    else
                    {
                        hsv[1] = (float) 1.0; // Saturation fixed to 1.0
                        hsv[2] = (float) 2.0 - ((2 * (float) rows) / VertDivs); // Value
                    }

                    // Define color and draw cell in grid
                    mPathLinePaint.setColor(Color.HSVToColor(255, hsv));
                    r.set((int) (HorRatio * cols) + originX, (int) (VertRatio * rows) + originY, (int) (HorRatio * (cols + 1)) + originX,
                            (int) (VertRatio * (rows + 1)) + originY);
                    canvas.drawRect(r, mPathLinePaint);
                }
            }

            if (bInvalidateViewOn)
            {
                invalidate();
            }

            // only send Activity Passed once per call
            if ((sendPassPacket == true) && isExpectedOrientation())
            {
                sendPassPacket = false;
                sendStartActivityPassed();
            }

            // only send Activity Passed once per call
            if (sendRedrawPassPacket == true)
            {
                sendRedrawPassPacket = false;

                // you should normally throw a CmdPassException but
                // the onDraw() callback does not understand how to
                // handle CmdPassException since it's a specific to
                // motocit.
                sendCmdPassed();
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendPassPacket = true;

        setRequestedOrientation(0);

        // invalidate current view so onDraw is always called to make sure
        // commServer will get ACK packet
        this.getWindow().getDecorView().invalidate();
    }

    private class InvalidateWindow implements Runnable
    {
        @Override
        public void run()
        {
            getWindow().getDecorView().invalidate();
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (handleCommonDisplayTestSpecificActions() == true)
        {
            // Handle all of the Common Display Specific Actions first
        }
        else if (strRxCmd.equalsIgnoreCase("SET_HDMI_DIVS"))
        {
            // Change number of divisions
            // By default as defined above VertDivs = 0; HorDivs = 9;

            int divs = 0;
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("VERTICAL") || key.equalsIgnoreCase("HORIZONTAL"))
                    {
                        divs = Integer.parseInt(value);

                        if (divs < 0)
                        {
                            divs = 0;
                        }

                        if (key.equalsIgnoreCase("VERTICAL"))
                        {
                            VertDivs = divs;
                        }

                        if (key.equalsIgnoreCase("HORIZONTAL"))
                        {
                            HorDivs = divs;
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // signal onDraw to send pass packet back to CommServer
                sendRedrawPassPacket = true;

                // now that new divs are set; redraw test pattern
                InvalidateWindow invalidateWindow = new InvalidateWindow();
                runOnUiThread(invalidateWindow);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_HDMI_DIVS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("VERTICAL=" + VertDivs);
            strDataList.add("HORIZONTAL=" + HorDivs);

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
            // Generate an exception to send FAIL result and msg back to
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

        strHelpList.addAll(getCommonDisplayHelp());

        strHelpList.add("  ");
        strHelpList.add("SET_HDMI_DIVS - VERTICAL and or HORIZONTAL; set to 0 to make equal to display dimension");
        strHelpList.add("  ");
        strHelpList.add("GET_HDMI_DIVS - returns the HDMI pattern vertical and horizontal division settings");
        strHelpList.add("  VERTICAL");
        strHelpList.add("  HORIZONTAL");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
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
            contentRecord("testresult.txt", "Display Pattern - HDMI Nine Color Bars:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            contentRecord("testresult.txt", "Display Pattern - HDMI Nine Color Bars:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Display Pattern - HDMI Nine Color Bars:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - HDMI Nine Color Bars:  PASS" + "\r\n\r\n", MODE_APPEND);

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
