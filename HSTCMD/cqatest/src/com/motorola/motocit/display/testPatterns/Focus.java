/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.TestUtils;

public class Focus extends BlackPattern
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_Focus";
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        Focus_View focus_View = new Focus_View(this);
        setContentView(focus_View);
        if (mGestureListener != null)
        {
            focus_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Focus_View extends Pattern_View
    {

        public Focus_View(Context c)
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

            // draw gray background
            mStartBoxPaint.setARGB(255, 128, 128, 128);
            canvas.drawRect(originX, originY, width + originX, height + originY, mStartBoxPaint);

            int sizeFactor = 5;
            int MTFboxStartwidth;
            int MTFboxStartheight;
            int MTFboxEndwidth;
            int MTFboxEndheight;
            int MTFboxWidth = (width) / sizeFactor;
            int MTFboxHeight = (height) / sizeFactor;

            // Create UL,UR Box
            MTFboxStartwidth = (int) (0.5 * MTFboxWidth) + originX;
            MTFboxStartheight = (int) (0.5 * MTFboxHeight) + originY;
            MTFboxEndwidth = MTFboxStartwidth + MTFboxWidth;
            MTFboxEndheight = MTFboxStartheight + MTFboxHeight;
            createMTFBox(canvas, MTFboxStartwidth, MTFboxStartheight, MTFboxEndwidth, MTFboxEndheight, width, height);

            MTFboxStartwidth = (width - (int) (1.5 * MTFboxWidth)) + originX;
            MTFboxStartheight = (int) (0.5 * MTFboxHeight) + originY;
            MTFboxEndwidth = MTFboxStartwidth + MTFboxWidth;
            MTFboxEndheight = MTFboxStartheight + MTFboxHeight;
            createMTFBox(canvas, MTFboxStartwidth, MTFboxStartheight, MTFboxEndwidth, MTFboxEndheight, width, height);

            // Create LL,LR Box
            MTFboxStartwidth = (int) (0.5 * MTFboxWidth) + originX;
            MTFboxStartheight = (height - (int) (1.5 * MTFboxHeight)) + originY;
            MTFboxEndwidth = MTFboxStartwidth + MTFboxWidth;
            MTFboxEndheight = MTFboxStartheight + MTFboxHeight;
            createMTFBox(canvas, MTFboxStartwidth, MTFboxStartheight, MTFboxEndwidth, MTFboxEndheight, width, height);

            MTFboxStartwidth = (width - (int) (1.5 * MTFboxWidth)) + originX;
            MTFboxStartheight = (height - (int) (1.5 * MTFboxHeight)) + originY;
            MTFboxEndwidth = MTFboxStartwidth + MTFboxWidth;
            MTFboxEndheight = MTFboxStartheight + MTFboxHeight;
            createMTFBox(canvas, MTFboxStartwidth, MTFboxStartheight, MTFboxEndwidth, MTFboxEndheight, width, height);

            // Create Center Box
            MTFboxStartwidth = ((int) (width * 0.5) - (int) (0.5 * MTFboxWidth)) + originX;
            MTFboxStartheight = ((int) (height * 0.5) - (int) (0.5 * MTFboxHeight)) + originY;
            MTFboxEndwidth = MTFboxStartwidth + MTFboxWidth;
            MTFboxEndheight = MTFboxStartheight + MTFboxHeight;
            createMTFBox(canvas, MTFboxStartwidth, MTFboxStartheight, MTFboxEndwidth, MTFboxEndheight, width, height);
            if (bInvalidateViewOn)
            {
                invalidate();
            }

            if (sendPassPacket == true)
            {
                sendPassPacket = false;
                sendStartActivityPassed();
            }
        }
    }

    public void createMTFBox(Canvas canvas, int startwidth, int startheight, int endwidth, int endheight, int width, int height)
    {
        double centerheight = 0.5 * (endheight - startheight);
        double degree = 8;
        double adjacentlength;

        double lfcenterheight = 0.5 * height;
        double lfcenterwidth = 0.5 * width;

        Paint pathLinePaint = new Paint();

        Path pathA = new Path();
        Path pathB = new Path();

        adjacentlength = (0.5 * centerheight) / Math.tan(degree);

        // BoxA
        pathA.moveTo(startwidth, startheight);
        pathA.lineTo(endwidth, startheight);
        pathA.lineTo(endwidth, startheight + (int) centerheight + (int) adjacentlength);
        pathA.lineTo(startwidth, (startheight + (int) centerheight) - (int) adjacentlength);
        pathA.close();

        // BoxB
        pathB.moveTo(endwidth, startheight + (int) centerheight + (int) adjacentlength);
        pathB.lineTo(endwidth, endheight);
        pathB.lineTo(startwidth, endheight);
        pathB.lineTo(startwidth, (startheight + (int) centerheight) - (int) adjacentlength);
        pathB.close();

        if (((startwidth <= lfcenterwidth) && (startheight <= lfcenterheight)) || ((startwidth > lfcenterwidth) && (startheight <= lfcenterheight)))
        {
            pathLinePaint.setARGB(255, 255, 255, 255);
            canvas.drawPath(pathA, pathLinePaint);

            pathLinePaint.setARGB(255, 0, 0, 0);
            canvas.drawPath(pathB, pathLinePaint);
        }
        else if (((startwidth > lfcenterwidth) && (startheight > lfcenterheight)) || ((startwidth <= lfcenterwidth) && (startheight > lfcenterheight)))
        {
            pathLinePaint.setARGB(255, 0, 0, 0);
            canvas.drawPath(pathA, pathLinePaint);

            pathLinePaint.setARGB(255, 255, 255, 255);
            canvas.drawPath(pathB, pathLinePaint);
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

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Display Pattern - Focus:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - Focus:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - Focus:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Focus:  PASS" + "\r\n\r\n", MODE_APPEND);

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
