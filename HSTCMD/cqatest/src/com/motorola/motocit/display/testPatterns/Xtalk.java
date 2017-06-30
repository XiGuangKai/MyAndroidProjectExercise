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
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.TestUtils;

public class Xtalk extends BlackPattern
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_Xtalk";
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        Xtalk_View xtalk_View = new Xtalk_View(this);
        setContentView(xtalk_View);
        if (mGestureListener != null)
        {
            xtalk_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Xtalk_View extends Pattern_View
    {

        public Xtalk_View(Context c)
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

            int startheight = 0;
            int nBarWidthStart[] =
                { 0, 0, 0, 0, 0 };
            int nBarWidthEnd[] =
                { 0, 0, 0, 0, 0 };
            int nBarHeightStart[] =
                { 0, 0, 0, 0, 0 };
            int nBarHeightEnd[] =
                { 0, 0, 0, 0, 0 };

            double barWidth = 0;
            double totalWidthOfAllBars = 0;
            double totalWidthNotBars = 0;
            double pixelsBetweenBars = 0;
            double barHeight = 0;
            double barWidthRatio = 0.025;
            double[] barHeightRatio =
                { 0.31, 0.43, 0.55, 0.67, 0.79 };

            Rect r = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            // draw black background
            canvas.drawRect(originX, originY, width + originX, height + originY, mStartBoxPaint);

            // draw white lines around border
            canvas.drawLine(originX, originY, width + originX, originY, mPathLinePaint);
            canvas.drawLine(width + originX, originY, width + originX, height + originY, mPathLinePaint);
            canvas.drawLine(width + originX, height + originY, originX, height + originY, mPathLinePaint);
            canvas.drawLine(originX, height + originY, originX, originY, mPathLinePaint);

            // Given Bar Width in Percentage of total width, find the start and
            // end points of each bar
            barWidth = barWidthRatio * (width - 2);
            totalWidthOfAllBars = 5 * barWidth;
            totalWidthNotBars = (width - 2) - totalWidthOfAllBars;
            pixelsBetweenBars = totalWidthNotBars / 6.0; // 6 because of space
            // before and after
            // each bar

            if ((barWidth < 1) || (pixelsBetweenBars < 1))
            {
                // image too small to display a xtalk pattern correctly
            }
            else
            {
                for (int cnt = 0; cnt < 5; cnt++)
                {
                    nBarWidthStart[cnt] = (int) (((cnt + 1) * pixelsBetweenBars) + (cnt * barWidth) + 1);
                    nBarWidthEnd[cnt] = (int) (((cnt + 1) * pixelsBetweenBars) + ((cnt + 1) * barWidth) + 1);

                    barHeight = barHeightRatio[cnt] * (height - startheight - 2);

                    nBarHeightStart[cnt] = (int) (height - barHeight - 1);
                    nBarHeightEnd[cnt] = (height - 1);

                    if ((barHeight < 1) || (nBarHeightStart[cnt] < 0))
                    {
                        // image too small to display a xtalk pattern correctly
                    }
                    else
                    {
                        r.set(nBarWidthStart[cnt] + originX, nBarHeightStart[cnt] + originY, nBarWidthEnd[cnt] + originX, nBarHeightEnd[cnt]
                                + originY);

                        canvas.drawRect(r, mPathLinePaint);
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

            contentRecord("testresult.txt", "Display Pattern - Xtalk:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - Xtalk:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - Xtalk:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Xtalk:  PASS" + "\r\n\r\n", MODE_APPEND);

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
