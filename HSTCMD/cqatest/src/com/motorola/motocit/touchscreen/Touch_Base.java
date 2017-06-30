/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.touchscreen;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public abstract class Touch_Base extends Test_Base
{
    protected final String TEST_STATUS_PASS = "PASS";
    protected final String TEST_STATUS_FAIL = "FAIL";
    protected final String TEST_STATUS_RUNNING = "RUNNING";
    protected String testStatus = TEST_STATUS_FAIL;
    protected Handler mHandlerInTouch = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        isTouchScreenTest = true;
        this.getWindow();
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = this.getWindow(); // keep klocwork happy
        if (null != window)
        {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
    }

    private final Runnable mHiderInTouch = new Runnable()
    {
        @Override public void run()
        {
            dbgLog(TAG, "In Touch Base mHiderInTouch handler", 'i');
            Window window = getWindow();
            if ( wasActivityStartedByCommServer() )
            {
                dbgLog(TAG, "In Touch Base mHiderInTouch handler,show navigation keys", 'i');
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        dbgLog(TAG, "onWindowFocusChanged - Touch_Base: Focus = " + hasFocus, 'i');
        dbgLog(TAG, "onWindowFocusChanged - Touch_Base: setting flag to true", 'i');
        isTouchScreenTest = true;
        Window window = this.getWindow();
        if (hasFocus && wasActivityStartedByCommServer())
        {
            dbgLog(TAG, "onWindowFocusChanged - Touch_Base: in IF condition, show keys", 'i');
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            mHandlerInTouch.postDelayed(mHiderInTouch,500);
            mHandlerInTouch.postDelayed(mHiderInTouch,1000);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        isTouchScreenTest = true;
        sendStartActivityPassed();
    }

    @Override
    protected void onPause() {
        dbgLog(TAG, "onPause() called", 'i');

        super.onPause();
        isTouchScreenTest = false;
    }

    @Override
    protected void onDestroy() {
        dbgLog(TAG, "onDestroy() called", 'i');

        super.onDestroy();
        isTouchScreenTest = false;
    }

    public class Touch_View extends View
    {
        protected final ArrayList<Float> mXs = new ArrayList<Float>();
        protected final ArrayList<Float> mYs = new ArrayList<Float>();
        protected final ArrayList<Float> mXVertices = new ArrayList<Float>();
        protected final ArrayList<Float> mYVertices = new ArrayList<Float>();
        protected final FontMetricsInt mTextMetrics = new FontMetricsInt();
        protected final Paint mTextPaint;
        protected final Paint mTextBackgroundPaint;
        protected final Paint mTextLevelPaint;
        protected final Paint mPaint;
        protected final Paint mTargetPaint;
        protected final Paint mStartBoxPaint;
        protected final Paint mPathLinePaint;
        protected int mLineSize;
        protected int mHeaderBottom;
        protected int mCurSegment;
        protected int mSegmentStatus;
        protected int mBottom;
        protected int mCurX;
        protected int mCurY;
        protected float mCurPressure;
        protected float mCurSize;
        protected int mCurWidth;
        protected boolean mCurDown;
        protected VelocityTracker mVelocity;

        public Touch_View(Context c)
        {
            super(c);
            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(10);
            mTextPaint.setARGB(255, 0, 0, 0);
            mTextBackgroundPaint = new Paint();
            mTextBackgroundPaint.setAntiAlias(false);
            mTextBackgroundPaint.setARGB(128, 255, 255, 255);
            mTextLevelPaint = new Paint();
            mTextLevelPaint.setAntiAlias(false);
            mTextLevelPaint.setARGB(192, 255, 0, 0);
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setARGB(255, 255, 255, 255);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(2);

            mTargetPaint = new Paint();
            mTargetPaint.setAntiAlias(false);
            mTargetPaint.setARGB(192, 0, 0, 255);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(1);
            mStartBoxPaint = new Paint();
            mStartBoxPaint.setAntiAlias(false);
            mStartBoxPaint.setARGB(255, 0, 255, 0);
            mPathLinePaint = new Paint();
            mPathLinePaint.setAntiAlias(true);
            mPathLinePaint.setARGB(255, 255, 255, 255);
            mPathLinePaint.setStyle(Paint.Style.STROKE);
            mPathLinePaint.setStrokeWidth(mLineSize);
            dbgLog(TAG, "Touch View Created", 'd');
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            mTextPaint.getFontMetricsInt(mTextMetrics);
            mHeaderBottom = TestUtils.getDisplayYTopOffset() + (-mTextMetrics.ascent + mTextMetrics.descent + 2);
            dbgLog("foo", "Metrics: ascent=" + mTextMetrics.ascent + " descent=" + mTextMetrics.descent + " leading=" + mTextMetrics.leading
                    + " top=" + mTextMetrics.top + " bottom=" + mTextMetrics.bottom, 'i');
        }

        public void drawStats(Canvas canvas, int CurX, int CurY, float CurPressure, float CurSize, VelocityTracker CurVelocity)
        {
            int w = getTouchWidth() / 5;
            int base = TestUtils.getDisplayYTopOffset() + (-mTextMetrics.ascent + 1);
            int bottom = mHeaderBottom;
            mPathLinePaint.setStrokeWidth(mLineSize);
            dbgLog(TAG, "Width=" + getWidth() + " Touch Width=" + getTouchWidth(), 'i');
            dbgLog(TAG, "TopLeft=" + TestUtils.getDisplayXLeftOffset() + "," + TestUtils.getDisplayYTopOffset() + " BottomRight" + (w - 1) + ","
                    + bottom, 'i');

            canvas.drawRect(TestUtils.getDisplayXLeftOffset(), TestUtils.getDisplayYTopOffset(), (TestUtils.getDisplayXLeftOffset() + w) - 1, bottom,
                    mTextBackgroundPaint);
            canvas.drawText("X: " + CurX, TestUtils.getDisplayXLeftOffset() + 1, base, mTextPaint);
            dbgLog(TAG, "X: " + CurX, 'i');

            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + w, TestUtils.getDisplayYTopOffset(),
                    (TestUtils.getDisplayXLeftOffset() + (w * 2)) - 1, bottom, mTextBackgroundPaint);
            canvas.drawText("Y: " + CurY, TestUtils.getDisplayXLeftOffset() + 1 + w, base, mTextPaint);
            dbgLog(TAG, "Y: " + CurY, 'i' );

            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + (w * 2), TestUtils.getDisplayYTopOffset(),
                    (TestUtils.getDisplayXLeftOffset() + (w * 3)) - 1, bottom, mTextBackgroundPaint);
            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + (w * 2), TestUtils.getDisplayYTopOffset(),
                    (TestUtils.getDisplayXLeftOffset() + ((w * 2) + (CurPressure * w))) - 1, bottom,
                    mTextLevelPaint);
            canvas.drawText("Pres: " + CurPressure, TestUtils.getDisplayXLeftOffset() + 1 + (w * 2), base, mTextPaint);
            dbgLog(TAG, "CurrentPressure: " + CurPressure, 'i');

            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + (w * 3), TestUtils.getDisplayYTopOffset(),
                    (TestUtils.getDisplayXLeftOffset() + (w * 4)) - 1, bottom, mTextBackgroundPaint);
            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + (w * 3), TestUtils.getDisplayYTopOffset(),
                    (TestUtils.getDisplayXLeftOffset() + ((w * 3) + (CurSize * w))) - 1, bottom,
                    mTextLevelPaint);
            canvas.drawText("Size: " + CurSize, TestUtils.getDisplayXLeftOffset() + 1 + (w * 3), base, mTextPaint);

            canvas.drawRect(TestUtils.getDisplayXLeftOffset() + (w * 4), TestUtils.getDisplayYTopOffset(), TestUtils.getDisplayXLeftOffset()
                    + getTouchWidth(), bottom,
                    mTextBackgroundPaint);

            int velocity = CurVelocity == null ? 0 : (int) (CurVelocity.getYVelocity() * 1000);
            canvas.drawText("yVel: " + velocity, TestUtils.getDisplayXLeftOffset() + 1 + (w * 4), base, mTextPaint);
            dbgLog(TAG, "yVel: " + velocity, 'i');
            return;
        }

        public boolean addVertex(float xV, float yV, float ViewHeight, float ViewWidth, int lineSize, float header)
        {
            // Points are based on a 0-1000 range for X and Y. this is to allow
            // different screen sizes
            if ((xV >= 0.0) && (xV <= 1000.0) && (yV >= 0.0) && (yV <= 1000.0))
            {
                // x is Width, y is Height
                xV = Math.round((xV / 1000) * ViewWidth);
                yV = Math.round((yV / 1000) * ViewHeight);

                xV = xV + TestUtils.getDisplayXLeftOffset();
                yV = yV + TestUtils.getDisplayYTopOffset();

                dbgLog(TAG, "Vertex Before Correction " + xV + " " + yV, 'd');

                if ((xV - TestUtils.getDisplayXLeftOffset()) < (lineSize / 2))
                {
                    xV = Math.round((lineSize / 2) + TestUtils.getDisplayXLeftOffset());
                }

                if ((yV - TestUtils.getDisplayYTopOffset()) < ((lineSize / 2) + header))
                {
                    yV = Math.round((lineSize / 2) + header);
                }
                if ((xV - TestUtils.getDisplayXRightOffset()) > (ViewWidth - (lineSize / 2)))
                {
                    xV = Math.round((ViewWidth - (lineSize / 2)) + TestUtils.getDisplayXLeftOffset());
                }
                if ((yV - TestUtils.getDisplayYBottomOffset()) > (ViewHeight - (lineSize / 2)))
                {
                    yV = Math.round((ViewHeight - (lineSize / 2)) + TestUtils.getDisplayYBottomOffset());
                }




                mXVertices.add(xV);
                mYVertices.add(yV);
                dbgLog(TAG, "Vertex Added at " + xV + " " + yV, 'd');
                return true;
            }

            new AlertDialog.Builder(mContext).setTitle("Invalid Vertex Entered").setPositiveButton("ok", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int whichButton)
                {

                    // Do nothing... just warning about bad code
                }
            }).setNegativeButton("cancel", new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int whichButton)
                {

                    // Do nothing... just warning about bad code
                }
            }).create();
            return false;
        }

        public boolean drawLines(Canvas canvas)
        {
            if (mCurDown)
            {
                canvas.drawColor(0xffffffff);
            }
            if ((mSegmentStatus == -1) && mCurDown)
            {
                mStartBoxPaint.setARGB(255, 255, 0, 0);
                canvas.drawRect(TestUtils.getDisplayXLeftOffset(), mBottom + 1, TestUtils.getDisplayXLeftOffset() + getTouchWidth(),
                        TestUtils.getDisplayYBottomOffset() + getTouchHeight(), mStartBoxPaint);
                mStartBoxPaint.setARGB(255, 255, 255, 255);
                canvas.drawCircle(mXVertices.get(0), mYVertices.get(0), mLineSize / 2, mStartBoxPaint);
            }
            if (mSegmentStatus == 2)
            {
                mStartBoxPaint.setARGB(255, 0, 255, 0);
                canvas.drawRect(TestUtils.getDisplayXLeftOffset(), mBottom + 1, TestUtils.getDisplayXLeftOffset() + getTouchWidth(),
                        TestUtils.getDisplayYBottomOffset() + getTouchHeight(), mStartBoxPaint);
            }
            else if (!mCurDown)
            {
                mStartBoxPaint.setARGB(255, 255, 255, 255);
                mCurSegment = 0;
                canvas.drawCircle(mXVertices.get(0), mYVertices.get(0), mLineSize / 2, mStartBoxPaint);
            }

            if (mCurDown && (mSegmentStatus >= 0) && (mSegmentStatus < 2))
            {
                for (int i = 0; i < (mCurSegment + 1); i++)
                {
                    if (i < (mXVertices.size() - 1))
                    {
                        // set color
                        setPaintColor(i + 1, mCurSegment, mStartBoxPaint);
                        setPaintColor(i + 1, mCurSegment, mPathLinePaint);
                        // draw line
                        canvas.drawLine(mXVertices.get(i), mYVertices.get(i), mXVertices.get(i + 1), mYVertices.get(i + 1), mPathLinePaint);
                        // draw endpoints
                        canvas.drawCircle(mXVertices.get(i), mYVertices.get(i), mLineSize / 2, mStartBoxPaint);
                        canvas.drawCircle(mXVertices.get(i + 1), mYVertices.get(i + 1), mLineSize / 2, mStartBoxPaint);

                    }
                }
            }
            return true;
        }

        protected boolean drawTouchPath(Canvas canvas, VelocityTracker Velocity)
        {
            final int N = mXs.size();
            float lastX = 0, lastY = 0;
            mPaint.setARGB(255, 0, 255, 255);
            for (int i = 0; i < N; i++)
            {
                float x = mXs.get(i);
                float y = mYs.get(i);
                if (i > 0)
                {
                    canvas.drawLine(lastX, lastY, x, y, mTargetPaint);
                    canvas.drawPoint(lastX, lastY, mPaint);
                }
                lastX = x;
                lastY = y;
            }
            if (Velocity != null)
            {
                mPaint.setARGB(255, 255, 0, 0);
                float xVel = Velocity.getXVelocity() * (1000 / 60);
                float yVel = Velocity.getYVelocity() * (1000 / 60);
                canvas.drawLine(lastX, lastY, lastX + xVel, lastY + yVel, mPaint);
            }
            else
            {
                canvas.drawPoint(lastX, lastY, mPaint);
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event)
        {
            float lfDist;
            float lfSlope;
            float lfIntercept;
            int action = event.getAction();

            if ((mXVertices.size() > 0)
                    && ((action == MotionEvent.ACTION_DOWN) || (action == MotionEvent.ACTION_UP) || (action == MotionEvent.ACTION_MOVE)
                        || (action == MotionEvent.ACTION_POINTER_DOWN) || (action == MotionEvent.ACTION_POINTER_UP)))
            {
                if (((action == MotionEvent.ACTION_DOWN) && (mSegmentStatus < 2)) || (action == MotionEvent.ACTION_POINTER_DOWN))
                {
                    mXs.clear();
                    mYs.clear();
                    mVelocity = VelocityTracker.obtain();
                    mCurSegment = 0;
                    dbgLog(TAG, "Touchscreen Pressed", 'd');
                }

                if (((action == MotionEvent.ACTION_UP) && (mSegmentStatus < 2)) || (action == MotionEvent.ACTION_POINTER_UP))
                {
                    mXs.clear();
                    mYs.clear();
                    mVelocity = VelocityTracker.obtain();
                    mCurSegment = 0;
                    dbgLog(TAG, "Touchscreen Released", 'd');
                }
                if (mVelocity != null)
                {
                    mVelocity.addMovement(event);
                    mVelocity.computeCurrentVelocity(1);
                }

                final int N = event.getHistorySize();
                for (int i = 0; i < N; i++)
                {
                    mXs.add(event.getHistoricalX(i));
                    mYs.add(event.getHistoricalY(i));
                }
                mXs.add(event.getX());
                mYs.add(event.getY());
                mCurDown = ((action == MotionEvent.ACTION_DOWN) || (action == MotionEvent.ACTION_MOVE) && (action != MotionEvent.ACTION_POINTER_DOWN));
                mCurX = (int) event.getX();
                mCurY = (int) event.getY();
                mCurPressure = event.getPressure();
                mCurSize = event.getSize();
                mCurWidth = (int) (mCurSize * (getTouchWidth() / 3));

                if (mCurDown && (mSegmentStatus != 2))
                {
                    // if current segment = 0, check the starting dot
                    if (mCurSegment == 0)
                    {
                        lfDist = (float) Math.sqrt(Math.pow(Math.abs(mXVertices.get(0) - mCurX), 2)
                                + Math.pow(Math.abs(mYVertices.get(0) - mCurY), 2));
                        if (lfDist <= (mLineSize / 2))
                        {
                            mCurSegment++;
                            mSegmentStatus = 0;
                        }
                        else
                        {
                            mSegmentStatus = -1;
                        }
                    }
                    if ((mCurSegment > 0) && (mSegmentStatus >= 0))
                    {
                        // Check the current segment
                        if (0 != (mXVertices.get(mCurSegment) - mXVertices.get(mCurSegment - 1)))
                        {
                            lfSlope = (mYVertices.get(mCurSegment) - mYVertices.get(mCurSegment - 1))
                                    / (mXVertices.get(mCurSegment) - mXVertices.get(mCurSegment - 1));
                            // y = mx + b
                            // b = y - mx
                            lfIntercept = mYVertices.get(mCurSegment) - (lfSlope * mXVertices.get(mCurSegment));
                            lfDist = getDist(lfSlope, lfIntercept, mCurX, mCurY);
                        }
                        else
                            // Vertical line... check distance in X
                        {
                            lfDist = Math.abs(mCurX - mXVertices.get(mCurSegment));
                        }
                        if (lfDist <= (mLineSize / 2))
                        {
                            mSegmentStatus = 0;
                        }
                        else
                        {
                            if ((mCurY > mBottom) && (mCurY <= getTouchHeight()))
                            {
                                mSegmentStatus = -1;
                            }
                        }
                    }
                    if ((mCurSegment > 0) && (mSegmentStatus >= 0) && (mCurSegment < (mXVertices.size() - 1)))
                    {
                        // Check the next segment
                        if (0 != (mXVertices.get(mCurSegment + 1) - mXVertices.get(mCurSegment)))
                        {
                            lfSlope = (mYVertices.get(mCurSegment + 1) - mYVertices.get(mCurSegment))
                                    / (mXVertices.get(mCurSegment + 1) - mXVertices.get(mCurSegment));
                            // y = mx + b
                            // b = y - mx
                            lfIntercept = mYVertices.get(mCurSegment + 1) - (lfSlope * mXVertices.get(mCurSegment + 1));
                            lfDist = getDist(lfSlope, lfIntercept, mCurX, mCurY);
                        }
                        else
                            // Vertical line... check distance in X
                        {
                            lfDist = Math.abs(mCurX - mXVertices.get(mCurSegment + 1));
                        }
                        if (lfDist <= (mLineSize / 2))
                        {
                            mSegmentStatus = 0;
                            mCurSegment++;
                        }
                    }
                    // Check if in final point
                    if ((mCurSegment == (mXVertices.size() - 1)) && (mSegmentStatus >= 0))
                    {
                        lfDist = (float) Math.sqrt(Math.pow(Math.abs(mXVertices.get(mCurSegment) - mCurX), 2)
                                + Math.pow(Math.abs(mYVertices.get(mCurSegment) - mCurY), 2));
                        if (lfDist <= (mLineSize / 2))
                        {
                            mSegmentStatus = 2;
                        }
                    }
                }

                if (mCurSegment == mXVertices.size())
                {
                    mSegmentStatus = 2;
                    mCurSegment++;
                }
                if (mSegmentStatus == -1)
                {
                    mCurSegment = 0;
                }
            }
            else
            {
                dbgLog(TAG, "Action not defined for this Event Type " + action, 'd');
            }
            invalidate();
            return true;
        }

        public boolean drawCrossHairs(Canvas canvas)
        {
            if (mCurDown)
            {
                canvas.drawLine(0, mCurY, getTouchWidth(), mCurY, mTargetPaint);
                canvas.drawLine(mCurX, 0, mCurX, getTouchHeight(), mTargetPaint);
                int pressureLevel = (int) (mCurPressure * 255);
                mPaint.setARGB(255, pressureLevel, 128, 255 - pressureLevel);
                canvas.drawPoint(mCurX, mCurY, mPaint);
                canvas.drawCircle(mCurX, mCurY, mCurWidth, mPaint);
            }
            return true;
        }

        public int getTouchWidth()
        {
            return (getWidth() - TestUtils.getDisplayXLeftOffset() - TestUtils.getDisplayXRightOffset());
        }

        public int getTouchHeight()
        {
            return (getHeight() - TestUtils.getDisplayYBottomOffset() - TestUtils.getDisplayYTopOffset());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) && (event.getRepeatCount() == 0))
        {

            try
            {
                testFailed();
            }
            catch (CmdFailException e)
            {
                // This command response was not triggered by the computer so
                // create unsolicited packet back to CommServer reporting the
                // test failed
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.addAll(e.strErrMsgList);

                CommServerDataPacket unsolicitedPacket = new CommServerDataPacket(0, "FAIL", TAG, strErrMsgList);
                sendUnsolicitedPacketToCommServer(unsolicitedPacket);
            }

            dbgLog(TAG, "Volume Down Key Pressed, previous message should be used to fail test", 'd');

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            finish();

            // do something on back.
            return true;
        }
        else
        {
            dbgLog(TAG, "Other Key Pressed, nothing should happen", 'd');
        }
        return true;
    }

    @Override
    public void onBackPressed()
    {
        // do something on back.
        dbgLog(TAG, "Overriding Back Key to do nothing", 'd');
        return;
    }

    public float getDist(float lSlope, float lCenterIntercept, int lCurrentX, int lCurrentY)
    {
        return (float) (Math.abs(lCurrentY - (lSlope * lCurrentX) - lCenterIntercept) / Math.sqrt(Math.pow(lSlope, 2) + 1));
    }

    public void setPaintColor(int nSegment, int nCurSegment, Paint PaintObject)
    {
        if (nCurSegment < nSegment)
        {
            PaintObject.setARGB(255, 255, 255, 0);
        }
        if (nCurSegment == nSegment)
        {
            PaintObject.setARGB(255, 255, 255, 0);
        }
        if (nCurSegment > nSegment)
        {
            PaintObject.setARGB(255, 0, 255, 0);
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("GET_TOUCH_RESULT"))
        {
            List<String> strDataList = new ArrayList<String>();
            strDataList.add(String.format("TOUCH_RESULT=" + testStatus));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            List<String> strReturnDataList = new ArrayList<String>();
            // Generate an exception to send data back to CommServer
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
            // Generate an exception to send FAIL result and mesg back to
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

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This activity tests a touchscreen on a phone by having the operator trace a line as it creates an");
        strHelpList.add("hourglass pattern on the screen.  Volume Down to fail the test.");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_TOUCH_RESULT - returns the TOUCH_RESULT value");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
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
        return true;
    }

}
