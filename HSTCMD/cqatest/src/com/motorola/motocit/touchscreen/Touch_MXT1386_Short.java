/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.touchscreen;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;

public class Touch_MXT1386_Short extends Touch_Base
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Touch_MXT1386_Short";
        passMessage = "Touch_MXT1386_Short passed";
        failMessage = "Touch_MXT1386_Short failed";
        super.onCreate(savedInstanceState);
        setContentView(new TS_MXT1386_Short_View(this));
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, true);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    public class TS_MXT1386_Short_View extends Touch_View
    {
        private float mViewHeight;
        private float mViewWidth;
        private boolean mFirstDraw = true;

        public TS_MXT1386_Short_View(Context c)
        {
            super(c);
            // Set the line size. For MXT224 devices, this is set at 1/4 the
            // shorter of the sides
            mPathLinePaint.setStrokeWidth(mLineSize);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            // Set the following variable to determine line width. Bigger number
            // = smaller line
            int LineWidthDivisor = 6;
            if (mFirstDraw)
            {
                testStatus = TEST_STATUS_RUNNING;
                mBottom = mHeaderBottom;
                mViewHeight = getTouchHeight();
                mViewWidth = getTouchWidth();
                mLineSize = (getTouchWidth() < getTouchHeight() ? getTouchWidth() / LineWidthDivisor : getTouchHeight() / LineWidthDivisor);
                mFirstDraw = false;

                // Add Points
                // Point 1 is 0,0 (top left)
                addVertex((float) 0.0, (float) 0.0, mViewHeight, mViewWidth, mLineSize, mBottom);
                // Point 2 is 1000,333.3 (1/3 down Right)
                addVertex((float) 1000.0, (float) 333.3, mViewHeight, mViewWidth, mLineSize, mBottom);
                // Point 3 is 0,666.6 (2/3 down Left)
                addVertex((float) 0.0, (float) 666.6, mViewHeight, mViewWidth, mLineSize, mBottom);
                // Point 4 is 1000,1000 (bottom Right)
                addVertex((float) 1000.0, (float) 1000.0, mViewHeight, mViewWidth, mLineSize, mBottom);
                // Point 5 is 0,1000 (bottom Left)
                // addVertex((float)0.0,(float)1000.0,mViewHeight,mViewWidth,mLineSize,bottom);
                // Point 6 is 1000,666.6 (2/3 down Right)
                // addVertex((float)1000.0,(float)666.6,mViewHeight,mViewWidth,mLineSize,bottom);
                // Point 7 is 0,333.3 (1/3 down Left)
                // addVertex((float)0.0,(float)333.3,mViewHeight,mViewWidth,mLineSize,bottom);
                // Point 8 is 1000,0 (top Right)
                // addVertex((float)1000.0,(float)0.0,mViewHeight,mViewWidth,mLineSize,bottom);
                // Point 9 is 0,0 (top left)
                // addVertex((float)0.0,(float)0.0,mViewHeight,mViewWidth,mLineSize,bottom);
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
                contentRecord("testresult.txt", "Touch Screen - Touch_MXT1386_SHORT:  PASS" + "\r\n\r\n", MODE_APPEND);

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
                    // the test failed
                    CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "PASS", TAG, e.strReturnDataList);
                    sendUnsolicitedPacketToCommServer(unsolicitedPacket);
                    testStatus = TEST_STATUS_PASS;

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
                    // - if not then close the activity else continue to display the result screen
                    if (!wasActivityStartedByCommServer())
                    {
                        finish();
                    }                }
            }

            drawTouchPath(canvas, mVelocity);
            drawCrossHairs(canvas);
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

            contentRecord("testresult.txt", "Touch Screen - Touch_MXT1386_SHORT:  FAILED" + "\r\n\r\n", MODE_APPEND);

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


}
