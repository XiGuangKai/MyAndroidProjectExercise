/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
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

public class BrightLine extends BlackPattern {

    private static final String TAG = "TestPattern_Color_BrightLine";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        BrightLineView brightLineView = new BrightLineView(this);
        setContentView(brightLineView);

        if (mGestureListener != null) {
            brightLineView.setOnTouchListener(mGestureListener);
        }
    }

    public class BrightLineView extends Pattern_View {

        public BrightLineView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // if activity is ending don't redraw
            if (isActivityEnding()) {
                return;
            }

            int originX = TestUtils.getDisplayXLeftOffset();
            int originY = TestUtils.getDisplayYTopOffset();

            int width = getWidth() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());

            Rect rectangle = new Rect();

            mPathLinePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            // First Line = RED
            // Second Line = GREEN
            // Third Line = BLUE

            int top = -1;
            int bottom = -1;

            // Create arrays to define each color.
            int colorRed[] = {255, 0, 0};
            int colorGreen[] = {0, 255, 0};
            int colorBlue[] = {0, 0, 255};

            for (int rows = 0; rows < 3; rows++) {
                mPathLinePaint.setARGB(255, colorRed[rows], colorGreen[rows], colorBlue[rows]);

                // Calculate top/bottom coordinates of each rectangle.
                top = originY + ((rows * height) / 3);
                bottom = originY + (((rows + 1) * height) / 3);

                rectangle.set(originX, top, width, bottom);

                canvas.drawRect(rectangle, mPathLinePaint);
            }

            if (bInvalidateViewOn) {
                invalidate();
            }

            if (sendPassPacket == true) {
                sendPassPacket = false;
                sendStartActivityPassed();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        // Ignore Key events if the activity was started through CommServer.
        if (wasActivityStartedByCommServer()) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            contentRecord("testresult.txt", "Display Pattern - BrightLine:  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);

            try {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            contentRecord("testresult.txt", "Display Pattern - BrightLine:  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);

            try {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (modeCheck("Seq")) {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();
                return false;
            }
            else {
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    public boolean onSwipeRight() {
        contentRecord("testresult.txt", "Display Pattern - BrightLine:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

        try {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft() {

        contentRecord("testresult.txt", "Display Pattern - BrightLine:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

        try {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeUp() {
        return true;
    }

    @Override
    public boolean onSwipeDown() {
        if (modeCheck("Seq")) {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();
            return false;
        }
        else {
            systemExitWrapper(0);
        }
        return true;
    }
}