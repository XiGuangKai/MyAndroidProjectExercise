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

public class Greenblocks extends BlackPattern
{
    /**
     * @see android.app.Activity#onCreate(Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_Greenblocks";
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        Greenblocks_View greenblocks_View = new Greenblocks_View(this);
        setContentView(greenblocks_View);
        if (mGestureListener != null)
        {
            greenblocks_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Greenblocks_View extends Pattern_View
    {

        public Greenblocks_View(Context c)
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

            int valuered = 0;
            int valueblue = 0;
            int valuegreen = -1;

            Rect r = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            for (int rows = 0; rows < 16; rows++)
            {
                for (int cols = 0; cols < 16; cols++)
                {
                    valuegreen++;

                    mPathLinePaint.setARGB(255, valuered, valuegreen, valueblue);

                    r.set(((cols * width) / 16) + originX, ((rows * height) / 16) + originY, (((cols + 1) * width) / 16) + originX,
                            (((rows + 1) * height) / 16) + originY);

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
            contentRecord("testresult.txt", "Display Pattern - Green Blocks:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            contentRecord("testresult.txt", "Display Pattern - Green Blocks:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - Green Blocks:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Green Blocks:  PASS" + "\r\n\r\n", MODE_APPEND);

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
