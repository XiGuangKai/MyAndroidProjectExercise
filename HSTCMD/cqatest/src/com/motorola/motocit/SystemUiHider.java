/*
 * This code is based off android-ui-utils project located at:
 * http://code.google.com/p/android-ui-utils/source/browse/design-preview/android/src/com/google/android/apps/proofer/SystemUiHider.java?r=be24d98e6e36c6b2ab651088e6c6380132883a0b
 *
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Motorola Revision history (newest first):
 *
 *    Date           CR           Author                   Description
 * 2012/07/12   IKHSS7-44164    Ken Moy - wkm039       Redraw screen when soft keys are hidden
 * 2012/07/11   IKHSS7-43391    Ken Moy - wkm039       Use Build.VERSION_CODES.ICE_CREAM_SANDWICH
 * 2012/05/22   IKHSS7-34342    Ken Moy - wkm039       Add code from above link and customized for motocit environment
 */

package com.motorola.motocit;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;

public class SystemUiHider
{
    private View mView;
    private boolean mEnabled = true;
    private boolean mInvalidateScreenWhenHidden = false;
    private Handler mHandler = new Handler();

    public SystemUiHider(View view)
    {
        mView = view;
    }

    public void enable(boolean enable)
    {
        mEnabled = enable;

        // set initial visibility
        if (mEnabled)
        {
            mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        else
        {
            mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    public void invalidateScreenWhenHidden(boolean enable)
    {
        mInvalidateScreenWhenHidden = enable;
    }

    private final Runnable mHider = new Runnable()
    {
        @Override public void run()
        {
            Log.i("CQATest", "In SystemUiHider mHider handler, hide navigation keys" );
            mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            mView.invalidate();
         }
    };

    public void setup()
    {

        mView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        mView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
        {
            @Override
            public void onSystemUiVisibilityChange(int visibility)
            {
                if (mEnabled && ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)==0))
                {
                    Log.i("CQATest", "onSystemUiVisibilityChange hiding navigation keys");
                    mHandler.postDelayed(mHider,3000);
                }

                if (mInvalidateScreenWhenHidden && (visibility == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION))
                {
                    // I have noticed that when virtual soft keys are hidden the screen is not
                    // always properly redrawn (Xtalk pattern).  Need to trigger a redraw of the screen.
                    // The catch is that the setOnSystemUiVisibilityChangeListener callback is called
                    // before the slide animation for hiding the soft keys is complete and if I call
                    // the invalidate while the animation is occuring the screen is still not redrawn
                    // properly.  I have not found a way to determine when the animation is complete
                    // so instead I have to invalidate the screen every 200 ms for 1 sec because on
                    // average the slide animation takes 500 ms to complete.

                    Log.i("CQATest", "onSystemUiVisibilityChange mInvalidateScreenWhenHidden=true");
                    mView.invalidate();
                    mView.postInvalidateDelayed(200);
                    mView.postInvalidateDelayed(400);
                    mView.postInvalidateDelayed(600);
                    mView.postInvalidateDelayed(800);
                    mView.postInvalidateDelayed(1000);
                }
            }
        });
    }
}
