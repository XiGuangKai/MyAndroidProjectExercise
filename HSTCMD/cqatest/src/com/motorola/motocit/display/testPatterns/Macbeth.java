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

public class Macbeth extends BlackPattern{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TAG = "TestPattern_Macbeth";
        super.onCreate(savedInstanceState);
        setRequestedOrientation(1);

        Macbeth_View macbeth_View = new Macbeth_View(this);
        setContentView(macbeth_View);
        if (mGestureListener != null)
        {
            macbeth_View.setOnTouchListener(mGestureListener);
        }
    }

    public class Macbeth_View extends Pattern_View {

        public Macbeth_View(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {

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
            int valuegreen = 0;

            Rect r = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            for(int rows = 0; rows < 6; rows++)
            {
                for(int cols = 0; cols < 4; cols++)
                {
                    if(cols == 0)
                    {
                        if(rows == 5)
                        {
                            valuered = 115;
                            valuegreen = 82;
                            valueblue = 68;
                        }
                        if(rows == 4)
                        {
                            valuered = 194;
                            valuegreen = 150;
                            valueblue = 130;
                        }
                        if(rows == 3)
                        {
                            valuered = 98;
                            valuegreen = 122;
                            valueblue = 157;
                        }
                        if(rows == 2)
                        {
                            valuered = 87;
                            valuegreen = 108;
                            valueblue = 67;
                        }
                        if(rows == 1)
                        {
                            valuered = 133;
                            valuegreen = 128;
                            valueblue = 177;
                        }
                        if(rows == 0)
                        {
                            valuered = 103;
                            valuegreen = 189;
                            valueblue = 170;
                        }
                    }
                    else if(cols == 1)
                    {
                        if(rows == 5)
                        {
                            valuered = 214;
                            valuegreen = 126;
                            valueblue = 44;
                        }
                        if(rows == 4)
                        {
                            valuered = 80;
                            valuegreen = 91;
                            valueblue = 166;
                        }
                        if(rows == 3)
                        {
                            valuered = 193;
                            valuegreen = 90;
                            valueblue = 99;
                        }
                        if(rows == 2)
                        {
                            valuered = 94;
                            valuegreen = 60;
                            valueblue = 108;
                        }
                        if(rows == 1)
                        {
                            valuered = 157;
                            valuegreen = 188;
                            valueblue = 64;
                        }
                        if(rows == 0)
                        {
                            valuered = 224;
                            valuegreen = 163;
                            valueblue = 46;
                        }
                    }
                    else if(cols == 2)
                    {
                        if(rows == 5)
                        {
                            valuered = 56;
                            valuegreen = 61;
                            valueblue = 150;
                        }
                        if(rows == 4)
                        {
                            valuered = 70;
                            valuegreen = 148;
                            valueblue = 73;
                        }
                        if(rows == 3)
                        {
                            valuered = 175;
                            valuegreen = 54;
                            valueblue = 60;
                        }
                        if(rows == 2)
                        {
                            valuered = 231;
                            valuegreen = 199;
                            valueblue = 31;
                        }
                        if(rows == 1)
                        {
                            valuered = 187;
                            valuegreen = 86;
                            valueblue = 149;
                        }
                        if(rows == 0)
                        {
                            valuered = 8;
                            valuegreen = 133;
                            valueblue = 161;
                        }
                    }
                    else if(cols == 3)
                    {
                        if(rows == 5)
                        {
                            valuered = 243;
                            valuegreen = 243;
                            valueblue = 242;
                        }
                        if(rows == 4)
                        {
                            valuered = 200;
                            valuegreen = 200;
                            valueblue = 200;
                        }
                        if(rows == 3)
                        {
                            valuered = 160;
                            valuegreen = 160;
                            valueblue = 160;
                        }
                        if(rows == 2)
                        {
                            valuered = 122;
                            valuegreen = 122;
                            valueblue = 121;
                        }
                        if(rows == 1)
                        {
                            valuered = 85;
                            valuegreen = 85;
                            valueblue = 85;
                        }
                        if(rows == 0)
                        {
                            valuered = 52;
                            valuegreen = 52;
                            valueblue = 52;
                        }
                    }

                    mPathLinePaint.setARGB(255, valuered, valuegreen, valueblue);

                    r.set(((cols * width) / 4) + originX, ((rows * height) / 6) + originY, (((cols + 1) * width) / 4) + originX,
                            (((rows + 1) * height) / 6) + originY);

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
            contentRecord("testresult.txt", "Display Pattern - Macbeth:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            contentRecord("testresult.txt", "Display Pattern - Macbeth:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - Macbeth:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Macbeth:  PASS" + "\r\n\r\n", MODE_APPEND);

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
