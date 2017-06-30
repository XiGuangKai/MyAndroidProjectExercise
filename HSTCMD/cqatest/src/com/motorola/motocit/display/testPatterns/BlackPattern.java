/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class BlackPattern extends Test_Base
{
    protected boolean sendPassPacket = false;
    protected boolean bInvalidateViewOn = false;
    protected int DisplayWidth;
    protected int DisplayHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Need to check if TAG is already set
        if (TAG == null)
        {
            TAG = "TestPattern_Black";
        }
        super.onCreate(savedInstanceState);

        Pattern_View pattern_View = new Pattern_View(this);
        setContentView(pattern_View);
        if (mGestureListener != null)
        {
            pattern_View.setOnTouchListener(mGestureListener);
        }
        /* Force Button/Softkey brightness to maximum */
        setButtonBacklightBrightness(100);
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

        // set true because flicker test will call onDraw
        // over and over and we don't want to send PassPacket
        // each time
        sendPassPacket = true;

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

    public class Pattern_View extends View
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

        public Pattern_View(Context c)
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
            mStartBoxPaint.setARGB(255, 0, 0, 0);
            mPathLinePaint = new Paint();
            mPathLinePaint.setAntiAlias(false);
            mPathLinePaint.setARGB(255, 255, 255, 255);
            mPathLinePaint.setStyle(Paint.Style.STROKE);
            mPathLinePaint.setStrokeWidth(mLineSize);

            Pattern_View.this.setKeepScreenOn(true);
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
            // draw black background
            setRequestedOrientation(1);
            canvas.drawRect(originX, originY, width + originX, height + originY, mStartBoxPaint);
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

        @Override
        public void onSizeChanged(int w, int h, int oldw, int oldh)
        {
            super.onSizeChanged(w, h, oldw, oldh);
            DisplayWidth = w;
            DisplayHeight = h;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int action = event.getAction();
        Context context = getApplicationContext();
        CharSequence textEnabled = "Flicker Enabled";
        CharSequence textDisabled = "Flicker Disabled";
        int duration = Toast.LENGTH_SHORT;

        dbgLog(TAG, "onTouchEvent hiding navigation bar", 'i');
        this.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        if ((action == MotionEvent.ACTION_UP) && !wasActivityStartedByCommServer())
        {

            bInvalidateViewOn = !bInvalidateViewOn;
            if (bInvalidateViewOn)
            {
                Toast toast = Toast.makeText(context, textEnabled, duration);
                toast.show();
            }
            else
            {
                Toast toast = Toast.makeText(context, textDisabled, duration);
                toast.show();
            }
            this.getWindow().getDecorView().invalidate();
        }
        return true;

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

            contentRecord("testresult.txt", "Display Pattern - Black:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - Black:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
            // Handle Common Test Display commands first
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

    protected boolean handleCommonDisplayTestSpecificActions() throws CmdFailException, CmdPassException
    {
        boolean actionFound = false;

        // Valid APK commands
        if (strRxCmd.equalsIgnoreCase("SET_DISPLAY_SETTINGS"))
        {
            actionFound = true;

            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("FLICKER"))
                    {
                        int nFlickerSetting = Integer.parseInt(value);
                        if (nFlickerSetting >= 1)
                        {
                            bInvalidateViewOn = true;
                        }
                        else
                        {
                            bInvalidateViewOn = false;
                        }

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_X_LEFT_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(Integer.parseInt(value), TestUtils.getDisplayXRightOffset(), TestUtils.getDisplayYTopOffset(),
                                TestUtils.getDisplayYBottomOffset());

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_X_RIGHT_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(TestUtils.getDisplayXLeftOffset(), Integer.parseInt(value), TestUtils.getDisplayYTopOffset(),
                                TestUtils.getDisplayYBottomOffset());

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_Y_TOP_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(TestUtils.getDisplayXLeftOffset(), TestUtils.getDisplayXRightOffset(), Integer.parseInt(value),
                                TestUtils.getDisplayYBottomOffset());

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_Y_BOTTOM_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(TestUtils.getDisplayXLeftOffset(), TestUtils.getDisplayXRightOffset(),
                                TestUtils.getDisplayYTopOffset(), Integer.parseInt(value));

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_X_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(Integer.parseInt(value), Integer.parseInt(value), TestUtils.getDisplayYTopOffset(),
                                TestUtils.getDisplayYBottomOffset());

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_Y_OFFSET"))
                    {
                        TestUtils.setDisplayOffsets(TestUtils.getDisplayXLeftOffset(), TestUtils.getDisplayXRightOffset(), Integer.parseInt(value),
                                Integer.parseInt(value));

                        InvalidateWindow invalidateWindow = new InvalidateWindow();
                        runOnUiThread(invalidateWindow);
                    }
                    else if (key.equalsIgnoreCase("DISPLAY_BACKLIGHT_BRIGHTNESS"))
                    {
                        int brightness = Integer.parseInt(value);

                        if ((brightness < 0) || (brightness > 100))
                        {
                            strReturnDataList.add("Invalid brightness value: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            setBacklightBrightness(brightness);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_DISPLAY_SETTINGS"))
        {
            actionFound = true;

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("FLICKER_SETTING_ON=" + bInvalidateViewOn);
            strDataList.add("DISPLAY_WIDTH=" + DisplayWidth);
            strDataList.add("DISPLAY_HEIGHT=" + DisplayHeight);
            strDataList.add("DISPLAY_X_LEFT_OFFSET=" + TestUtils.getDisplayXLeftOffset());
            strDataList.add("DISPLAY_X_RIGHT_OFFSET=" + TestUtils.getDisplayXRightOffset());
            strDataList.add("DISPLAY_Y_TOP_OFFSET=" + TestUtils.getDisplayYTopOffset());
            strDataList.add("DISPLAY_Y_BOTTOM_OFFSET=" + TestUtils.getDisplayYBottomOffset());

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        return actionFound;
    }

    @Override
    protected void printHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.addAll(getCommonDisplayHelp());

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    protected List<String> getCommonDisplayHelp()
    {
        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("The TestPattern activities display various images that can demonstrate diplay failures");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("SET_DISPLAY_SETTINGS - Change display settings for FLICKER");
        strHelpList.add("  FLICKER: 1 = Flicker Turn On, 0 = Flicker Turn Off");
        strHelpList.add("  DISPLAY_X_OFFSET: X Offset for display patterns used for X Left and Right");
        strHelpList.add("  DISPLAY_Y_OFFSET: Y Offset for display patterns used for Y Top and Bottom");
        strHelpList.add("  DISPLAY_X_LEFT_OFFSET: X Left Offset for display patterns");
        strHelpList.add("  DISPLAY_X_RIGHT_OFFSET: X Right Offset for display patterns");
        strHelpList.add("  DISPLAY_Y_TOP_OFFSET: Y Top Offset for display patterns");
        strHelpList.add("  DISPLAY_Y_BOTTOM_OFFSET: Y Bottom Offset for display patterns");
        strHelpList.add("  DISPLAY_BACKLIGHT_BRIGHTNESS: value for backlight brightness. Range 0-100");
        strHelpList.add("  ");
        strHelpList.add("GET_DISPLAY_SETTINGS - returns the Display settings");
        strHelpList.add("  FLICKER_SETTING_ON: 1 = Flicker Turned On, 0 = Flicker Turned Off");
        strHelpList.add("  DISPLAY_WIDTH");
        strHelpList.add("  DISPLAY_HEIGHT");
        strHelpList.add("  DISPLAY_X_OFFSET: X Offset for display patterns");
        strHelpList.add("  DISPLAY_Y_OFFSET: Y Offset for display patterns");

        return strHelpList;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Display Pattern - Black:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        contentRecord("testresult.txt", "Display Pattern - Black:  PASS" + "\r\n\r\n", MODE_APPEND);

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

    private void setBacklightBrightness(final float brightness)
    {
        Thread setLight = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ContentResolver resolver = getContentResolver();
                            Settings.System.putInt(resolver, "screen_brightness_mode", 0);

                            Window window = getWindow();
                            if (null != window)
                            {
                                WindowManager.LayoutParams lp = window.getAttributes();
                                lp.screenBrightness = (float) (1.0f * brightness * 0.01);
                                window.setAttributes(lp);
                            }
                        }
                    });
                }
                finally
                {
                }
            };
        };
        setLight.start();
    }

    /* Set Button/Softkey brightness 0 - 100 */
    private void setButtonBacklightBrightness(final float brightness)
    {
        Thread setLight = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ContentResolver resolver = getContentResolver();
                            Settings.System.putInt(resolver, "screen_brightness_mode", 0);

                            Window window = getWindow();
                            if (null != window)
                            {
                                dbgLog(TAG, "Update button brightness to " + brightness, 'i');
                                WindowManager.LayoutParams lp = window.getAttributes();
                                lp.buttonBrightness = (float) (1.0f * brightness * 0.01);
                                window.setAttributes(lp);
                            }
                        }
                    });
                }
                finally
                {
                }
            };
        };
        setLight.start();
    }
}
