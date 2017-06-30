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

public class Dark extends BlackPattern
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_Color_Dark";
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        Dark_View dark_View = new Dark_View(this);
        setContentView(dark_View);
        if (mGestureListener != null)
        {
            dark_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Dark_View extends Pattern_View
    {

        public Dark_View(Context c)
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

            int value1 = 8;
            int value2 = 4;
            int valueRed = 0;
            int valueBlue = 0;
            int valueGreen = 0;

            Rect r = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            for (int rows = 0; rows < 3; rows++)
            {
                for (int cols = 0; cols < 2; cols++)
                {
                    valueRed = 0;
                    valueBlue = 0;
                    valueGreen = 0;

                    if (cols == 0)
                    {
                        if (rows == 0)
                        {
                            valueRed = value1;
                        }
                        if (rows == 1)
                        {
                            valueGreen = value2;
                        }
                        if (rows == 2)
                        {
                            valueBlue = value1;
                        }
                    }
                    else
                    {
                        if (rows == 0)
                        {
                            valueRed = value2;
                        }
                        if (rows == 1)
                        {
                            valueGreen = value1;
                        }
                        if (rows == 2)
                        {
                            valueBlue = value2;
                        }
                    }

                    mPathLinePaint.setARGB(255, valueRed, valueGreen, valueBlue);

                    r.set(((cols * width) / 2) + originX, ((rows * height) / 3) + originY, (((cols + 1) * width) / 2) + originX,
                            (((rows + 1) * height) / 3) + originY);

                    canvas.drawRect(r, mPathLinePaint);
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

            contentRecord("testresult.txt", "Display Pattern - Dark:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - Dark:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - Dark:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Dark:  PASS" + "\r\n\r\n", MODE_APPEND);

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
