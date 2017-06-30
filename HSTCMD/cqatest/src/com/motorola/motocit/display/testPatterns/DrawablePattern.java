/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.math.BigInteger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;

public class DrawablePattern extends BlackPattern
{
    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    private boolean sendRedrawPassPacket = false;

    private List<DrawObject> mDrawObjectList;
    private DrawablePattern_View mDrawablePattern_View;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        if (TAG == null)
        {
            TAG = "TestPattern_Drawable";
        }
        super.onCreate(savedInstanceState);

        mDrawObjectList = new ArrayList<DrawObject>();

        mDrawablePattern_View = new DrawablePattern_View(this);
        setContentView(mDrawablePattern_View);
        if (mGestureListener != null)
        {
            mDrawablePattern_View.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        sendPassPacket = true;

        setRequestedOrientation(1);

        Window window = this.getWindow();
        if(null != window)
        {
            if((window.getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)==0)
            {
                dbgLog(TAG, "see the nav keys, hide them", 'i');
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }

        // invalidate current view so onDraw is always called to make sure
        // commServer will get ACK packet
        InvalidateWindow invalidateWindow = new InvalidateWindow();
        runOnUiThread(invalidateWindow);
    }

    public class DrawablePattern_View extends Pattern_View
    {

        public DrawablePattern_View(Context c)
        {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            // if activity is ending don't redraw
            if (isActivityEnding())
            {
                dbgLog(TAG, "onDraw Activity is ending so do nothing", 'i');
                return;
            }

            dbgLog(TAG, "onDraw Called", 'i');

            int originX = 0 + TestUtils.getDisplayXLeftOffset();
            int originY = 0 + TestUtils.getDisplayYTopOffset();
            int width = getWidth() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());
            // draw black background
            setRequestedOrientation(1);
            canvas.drawRect(originX, originY, width + originX, height + originY, mStartBoxPaint);

            if(mDrawObjectList != null || mDrawObjectList.size() > 0)
            {
                for(final DrawObject drawObject : mDrawObjectList)
                {
                    switch (drawObject.drawObjectType.toUpperCase())
                    {
                        case "CIRCLE":
                            canvas.drawCircle(drawObject.startX, drawObject.startY, drawObject.radius, drawObject.paint);
                            break;
                        case "LINE":
                            canvas.drawLine(drawObject.startX, drawObject.startY, drawObject.stopX, drawObject.stopY, drawObject.paint);
                            break;
                        case "POINT":
                            canvas.drawPoint(drawObject.startX, drawObject.startY, drawObject.paint);
                            break;
                        case "RECTANGLE":
                            canvas.drawRect(drawObject.startX, drawObject.startY, drawObject.stopX, drawObject.stopY, drawObject.paint);
                            break;
                        case "OVAL":
                            canvas.drawOval(drawObject.startX, drawObject.startY, drawObject.stopX, drawObject.stopY, drawObject.paint);
                            break;
                        default:
                            //Do Nothing
                    }
                }
            }

            if (bInvalidateViewOn)
            {
                invalidate();
            }

            if (sendPassPacket == true)
            {
                sendPassPacket = false;
                sendStartActivityPassed();
            }

            dbgLog(TAG, "onDraw Finished", 'i');
        }
    }

    private class InvalidateWindow implements Runnable
    {
        @Override
        public void run()
        {
            dbgLog(TAG, "Running InvalidateWindow", 'i');
            if(mDrawablePattern_View != null)
            {
                dbgLog(TAG, "Invalidating mDrawablePattern_View", 'i');
                mDrawablePattern_View.setWillNotDraw(false);
                mDrawablePattern_View.invalidate();
            }
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (handleCommonDisplayTestSpecificActions() == true)
        {
            // Handle all of the Common Display Specific Actions first
        }
        else if (strRxCmd.equalsIgnoreCase("ADD_DRAW_OBJECT"))
        {
            List<String> strErrMsgList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();

            List<String> strDrawObjectTypes = new ArrayList<String>();
            List<String> strStartX = new ArrayList<String>();
            List<String> strStartY = new ArrayList<String>();
            List<String> strStopX = new ArrayList<String>();
            List<String> strStopY = new ArrayList<String>();
            List<String> strRadius = new ArrayList<String>();
            List<String> strPaintColorARGB = new ArrayList<String>();
            List<String> strPaintStrokeWidth = new ArrayList<String>();
            List<String> strPaintStyle = new ArrayList<String>();

            int numberOfDrawObjects = 0;

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("DRAW_OBJECT_TYPE"))
                    {
                        strDrawObjectTypes = Arrays.asList(value.split(","));
                        numberOfDrawObjects = strDrawObjectTypes.size();
                    }
                    else if (key.equalsIgnoreCase("START_X"))
                    {
                        strStartX = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("START_Y"))
                    {
                        strStartY = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("STOP_X"))
                    {
                        strStopX = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("STOP_Y"))
                    {
                        strStopY = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("RADIUS"))
                    {
                        strRadius = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("PAINT_COLOR_ARGB"))
                    {
                        strPaintColorARGB = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("PAINT_STROKE_WIDTH"))
                    {
                        strPaintStrokeWidth = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("PAINT_STYLE"))
                    {
                        strPaintStyle = Arrays.asList(value.split(","));
                    }
                    else if (key.equalsIgnoreCase("CLEAR_DRAW_OBJECTS"))
                    {
                        if(value.equalsIgnoreCase("TRUE") || value.equalsIgnoreCase("YES"))
                        {
                            if(mDrawObjectList != null || mDrawObjectList.size() > 0)
                            {
                                mDrawObjectList.clear();
                            }
                        }
                    }
                    else
                    {
                        strErrMsgList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            dbgLog(TAG, "Number of draw objects:" + numberOfDrawObjects, 'i');

            if(strStartX != null && strStartX.size() > 0 && strStartX.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of START_X items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strStartY != null && strStartY.size() > 0 && strStartY.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of START_Y items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strStopX != null && strStopX.size() > 0 && strStopX.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of STOP_X items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strStopY != null && strStopY.size() > 0 && strStopY.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of STOP_Y items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strRadius != null && strRadius.size() > 0 && strRadius.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of RADIUS items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strPaintColorARGB != null && strPaintColorARGB.size() > 0 && strPaintColorARGB.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of PAINT_COLOR_ARGB items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strPaintStrokeWidth != null && strPaintStrokeWidth.size() > 0 && strPaintStrokeWidth.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of PAINT_STROKE_WIDTH items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if(strPaintStyle != null && strPaintStyle.size() > 0 && strPaintStyle.size() != numberOfDrawObjects)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                strErrMsgList.add(String.format("Number of PAINT_STYLE items does not match number of draw objects in DRAW_OBJECT_TYPE"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            int  drawObjectIndex = 0;

            for (String drawObjectType : strDrawObjectTypes)
            {
                DrawObject newDrawObject = new DrawObject();

                switch (drawObjectType.toUpperCase())
                {
                    case "CIRCLE":
                    case "LINE":
                    case "POINT":
                    case "RECTANGLE":
                    case "OVAL":
                        newDrawObject.drawObjectType = drawObjectType.toUpperCase();
                        break;
                    default:
                        strErrMsgList.add("UNKNOWN DRAW_OBJECT_TYPE: " + drawObjectType);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                if(strStartX != null && strStartX.size() > 0)
                {
                    String startX = strStartX.get(drawObjectIndex);
                    if(startX.contains("%"))
                    {
                        startX = startX.replace("%","");
                        newDrawObject.startX = ((Float.parseFloat(startX) / 100.0f) * DisplayWidth) - 1;
                    }
                    else
                    {
                        newDrawObject.startX = Float.parseFloat(startX);
                        if(newDrawObject.startX < 0)
                        {
                            newDrawObject.startX = DisplayWidth + newDrawObject.startX - 1;
                        }
                    }
                }

                if(strStartY != null && strStartY.size() > 0)
                {
                    String startY = strStartY.get(drawObjectIndex);
                    if(startY.contains("%"))
                    {
                        startY = startY.replace("%","");
                        newDrawObject.startY = ((Float.parseFloat(startY) / 100.0f) * DisplayHeight) - 1;
                    }
                    else
                    {
                        newDrawObject.startY = Float.parseFloat(startY);
                        if(newDrawObject.startY < 0)
                        {
                            newDrawObject.startY = DisplayHeight + newDrawObject.startY - 1;
                        }
                    }
                }

                if(strStopX != null && strStopX.size() > 0)
                {
                    String stopX = strStopX.get(drawObjectIndex);
                    if(stopX.contains("%"))
                    {
                        stopX = stopX.replace("%","");
                        newDrawObject.stopX = ((Float.parseFloat(stopX) / 100.0f) * DisplayWidth) - 1;
                    }
                    else
                    {
                        newDrawObject.stopX = Float.parseFloat(stopX);
                        if(newDrawObject.stopX < 0)
                        {
                            newDrawObject.stopX = DisplayWidth + newDrawObject.stopX - 1;
                        }
                    }
                }

                if(strStopY != null && strStopY.size() > 0)
                {
                    String stopY = strStopY.get(drawObjectIndex);
                    if(stopY.contains("%"))
                    {
                        stopY = stopY.replace("%","");
                        newDrawObject.stopY = ((Float.parseFloat(stopY) / 100.0f) * DisplayHeight) - 1;
                    }
                    else
                    {
                        newDrawObject.stopY = Float.parseFloat(stopY);
                        if(newDrawObject.stopY < 0)
                        {
                            newDrawObject.stopY = DisplayHeight + newDrawObject.stopY - 1;
                        }
                    }
                }

                if(strRadius != null && strRadius.size() > 0)
                {
                    newDrawObject.radius = Float.parseFloat(strRadius.get(drawObjectIndex));
                }

                if(strPaintColorARGB != null && strPaintColorARGB.size() > 0)
                {
                    int color = 0;
                    String paintColorARGB = strPaintColorARGB.get(drawObjectIndex);

                    try
                    {
                        color = new BigInteger(paintColorARGB, 16).intValue();
                    }
                    catch (Exception e)
                    {
                        // NumberFormatException thrown by hexStringToByteArray
                        dbgLog(TAG, "HEX String to Int failed: " + e.toString(), 'e');

                        strErrMsgList.add("PAINT_COLOR_ARGB MUST BE HEX FORMAT");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }

                    newDrawObject.paint.setColor(color);
                }

                if(strPaintStrokeWidth != null && strPaintStrokeWidth.size() > 0)
                {
                    newDrawObject.paint.setStrokeWidth(Float.parseFloat(strPaintStrokeWidth.get(drawObjectIndex)));
                }

                if(strPaintStyle != null && strPaintStyle.size() > 0)
                {
                    String paintStyle = strPaintStyle.get(drawObjectIndex);

                    switch(paintStyle.toUpperCase())
                    {
                        case "FILL":
                            newDrawObject.paint.setStyle(Paint.Style.FILL);
                            break;
                        case "FILL_AND_STROKE":
                            newDrawObject.paint.setStyle(Paint.Style.FILL_AND_STROKE);
                            break;
                        case "STROKE":
                            newDrawObject.paint.setStyle(Paint.Style.STROKE);
                            break;
                        default:
                            strErrMsgList.add("UNKNOWN PAINT_STYLE");
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }

                mDrawObjectList.add(newDrawObject);
                drawObjectIndex++;
            }

            runOnUiThread(new InvalidateWindow());

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_DRAW_OBJECTS"))
        {
            List<String> strDataList = new ArrayList<String>();

            if(mDrawObjectList == null || mDrawObjectList.size() == 0)
            {
                strDataList.add("NUMBER_OF_DRAW_OBJECTS=0");
            }
            else
            {
                strDataList.add("NUMBER_OF_DRAW_OBJECTS=" + mDrawObjectList.size());

                int objectNumber = 0;

                for(final DrawObject drawObject : mDrawObjectList)
                {
                    strDataList.add("DRAW_OBJECT_TYPE_OBJECT_ " + objectNumber + "=" + drawObject.drawObjectType);

                    strDataList.add("START_X_OBJECT_ " + objectNumber + "=" + drawObject.startX);
                    strDataList.add("START_Y_OBJECT_ " + objectNumber + "=" + drawObject.startY);
                    strDataList.add("STOP_X_OBJECT_ " + objectNumber + "=" + drawObject.stopX);
                    strDataList.add("STOP_Y_OBJECT_ " + objectNumber + "=" + drawObject.stopY);

                    strDataList.add("RADIUS_OBJECT_ " + objectNumber + "=" + drawObject.radius);

                    strDataList.add("PAINT_COLOR_ARGB_OBJECT_ " + objectNumber + "=" + String.format("%08X", drawObject.paint.getColor()));
                    strDataList.add("PAINT_STROKE_WIDTH_OBJECT_ " + objectNumber + "=" + drawObject.paint.getStrokeWidth());

                    Paint.Style paintStyle = drawObject.paint.getStyle();
                    if(paintStyle == Paint.Style.FILL)
                    {
                        strDataList.add("PAINT_STYLE_OBJECT_ " + objectNumber + "=FILL");
                    }
                    else if(paintStyle == Paint.Style.FILL_AND_STROKE)
                    {
                        strDataList.add("PAINT_STYLE_OBJECT_ " + objectNumber + "=FILL_AND_STROKE");
                    }
                    else if(paintStyle == Paint.Style.STROKE)
                    {
                        strDataList.add("PAINT_STYLE_OBJECT_ " + objectNumber + "=STROKE");
                    }
                    else
                    {
                        strDataList.add("PAINT_STYLE_OBJECT_ " + objectNumber + "=UNKNOWN");
                    }

                    objectNumber++;
                }
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("CLEAR_DRAW_OBJECTS"))
        {
            List<String> strDataList = new ArrayList<String>();

            if(mDrawObjectList != null || mDrawObjectList.size() > 0)
            {
                mDrawObjectList.clear();
                runOnUiThread(new InvalidateWindow());
            }

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
        strHelpList.add("ADD_DRAW_OBJECT - Add a draw object to the display. Multiple draw objects can be added by using a comma delimited list for each draw parameter.");
        strHelpList.add("  DRAW_OBJECT_TYPE - Required parameter. Type of object to draw: CIRCLE, LINE, POINT, RECTANGLE, or OVAL.");
        strHelpList.add("  START_X - Optional parameter. Start X Value for draw object in pixels or %. 0,0 is upper left corner of display. Negative indicates from opposite side.");
        strHelpList.add("  START_Y - Optional parameter. Start Y Value for draw object in pixels or %. 0,0 is upper left corner of display. Negative indicates from opposite side.");
        strHelpList.add("  STOP_X - Optional parameter. Stop X Value for draw object in pixels or %. 0,0 is upper left corner of display. Negative indicates from opposite side.");
        strHelpList.add("  STOP_Y - Optional parameter. Stop Y Value for draw object in pixels or %. 0,0 is upper left corner of display. Negative indicates from opposite side.");
        strHelpList.add("  RADIUS - Optional parameter. Radius, used for CIRCLE objects.");
        strHelpList.add("  PAINT_COLOR_ARGB - Optional parameter. Color to use in hex using format aarrggbb.");
        strHelpList.add("  PAINT_STROKE_WIDTH - Optional parameter. Width of paint stroke in pixels.");
        strHelpList.add("  PAINT_STYLE - Optional parameter. Paint style for draw object: FILL, FILL_AND_STROKE, or STROKE.");
        strHelpList.add("  CLEAR_DRAW_OBJECTS - Optional parameter. Set to TRUE or YES to clear all previously created draw objects before redrawing screen.");

        strHelpList.add("  ");
        strHelpList.add("GET_DRAW_OBJECTS - Get all of the current draw objects.");
        strHelpList.add("  NUMBER_OF_DRAW_OBJECTS - Number of objects to be drawn.");
        strHelpList.add("  DRAW_OBJECT_TYPE_OBJECT_XX - Type for draw object XX.");
        strHelpList.add("  START_X - Start X for draw object XX.");
        strHelpList.add("  START_Y - Start Y for draw object XX.");
        strHelpList.add("  STOP_X - Stop X for draw object XX.");
        strHelpList.add("  STOP_Y - Stop Y for draw object XX.");
        strHelpList.add("  RADIUS - Radius for draw object XX.");
        strHelpList.add("  PAINT_COLOR_ARGB - Paint Color for draw object XX.");
        strHelpList.add("  PAINT_STROKE_WIDTH - Paint stroke width for draw object XX.");
        strHelpList.add("  PAINT_STYLE - Paint style for draw object XX.");

        strHelpList.add("CLEAR_DRAW_OBJECTS - Clears all draw objects and blanks the screen.");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    public class DrawObject
    {
        public String drawObjectType;

        public float startX;
        public float startY;
        public float stopX;
        public float stopY;

        public float radius;

        public Paint paint;

        DrawObject()
        {
            drawObjectType = "";

            startX = 0;
            startY = 0;
            stopX = 0;
            stopY = 0;

            radius = 0;

            paint = new Paint();
        }
    }
}
