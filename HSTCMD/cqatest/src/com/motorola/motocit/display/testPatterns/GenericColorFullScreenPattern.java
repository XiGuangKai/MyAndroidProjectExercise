/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;

public class GenericColorFullScreenPattern extends BlackPattern
{
    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    private int Alpha = 255;
    private int Red = 128;
    private int Green = 128;
    private int Blue = 128;
    private boolean sendRedrawPassPacket = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        if (TAG == null)
        {
            TAG = "TestPattern_GenericColorFullScreenPattern";
        }
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        GenericColorFullScreenPattern_View genericColorFullScreenPattern_View = new GenericColorFullScreenPattern_View(this);
        setContentView(genericColorFullScreenPattern_View);
        if (mGestureListener != null)
        {
            genericColorFullScreenPattern_View.setOnTouchListener(mGestureListener);
        }
    }

    public class GenericColorFullScreenPattern_View extends Pattern_View
    {

        public GenericColorFullScreenPattern_View(Context c)
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

            int originX = 0 + TestUtils.getDisplayXLeftOffset();
            int originY = 0 + TestUtils.getDisplayYTopOffset();
            int width = getWidth() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mStartBoxPaint.setARGB(Alpha, Red, Green, Blue);
            canvas.drawRect(originX, originY, width + originX, height + originY, mStartBoxPaint);

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

        setRequestedOrientation(1);

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
        else if (strRxCmd.equalsIgnoreCase("SET_ARGB"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    int data = 0;
                    if (key.equalsIgnoreCase("Alpha") || key.equalsIgnoreCase("Red") || key.equalsIgnoreCase("Green") || key.equalsIgnoreCase("Blue"))
                    {
                        data = Integer.parseInt(value);

                        if (data < 0)
                        {
                            data = 0;
                        }

                        if (data > 255)
                        {
                            data = 255;
                        }

                        if (key.equalsIgnoreCase("Alpha"))
                        {
                            Alpha = data;
                        }

                        if (key.equalsIgnoreCase("Red"))
                        {
                            Red = data;
                        }

                        if (key.equalsIgnoreCase("Green"))
                        {
                            Green = data;
                        }

                        if (key.equalsIgnoreCase("Blue"))
                        {
                            Blue = data;
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
        else if (strRxCmd.equalsIgnoreCase("GET_ARGB"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("Alpha=" + Alpha);
            strDataList.add("Red=" + Red);
            strDataList.add("Green=" + Green);
            strDataList.add("Blue=" + Blue);

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
        strHelpList.add("SET_ARGB - ALPHA and or RED and or GREEN and or BLUE (0 to 255)");
        strHelpList.add("  ");
        strHelpList.add("GET_ARGB - returns the currently defined values for ARGB");
        strHelpList.add("  ALPHA");
        strHelpList.add("  RED");
        strHelpList.add("  GREEN");
        strHelpList.add("  BLUE");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }
}
