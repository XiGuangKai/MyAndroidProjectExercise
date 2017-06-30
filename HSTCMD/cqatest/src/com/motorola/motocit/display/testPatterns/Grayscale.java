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
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.TestUtils;

public class Grayscale extends BlackPattern
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_Grayscale";
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        Grayscale_View grayscale_View = new Grayscale_View(this);
        setContentView(grayscale_View);
        if (mGestureListener != null)
        {
            grayscale_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Grayscale_View extends Pattern_View
    {

        public Grayscale_View(Context c)
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

            int value = 0;
            double bitsPerRow = 0;

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            bitsPerRow = 255.0 / (height - 1);
            for (int rows = 0; rows < height; rows++)
            {
                value = (int) ((rows) * bitsPerRow);
                mPathLinePaint.setARGB(255, value, value, value);
                canvas.drawLine(originX, rows + originY, width + originX, rows + originY, mPathLinePaint);
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

            contentRecord("testresult.txt", "Display Pattern - GrayScale:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - GrayScale:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - GrayScale:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - GrayScale:  PASS" + "\r\n\r\n", MODE_APPEND);

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
