/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.mods;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Toast;

import com.motorola.mod.ModManager;

import java.util.List;

import com.motorola.mod.ModDevice;
import com.motorola.mod.ModDisplay;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModProtocol.Protocol;
import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class ProjectorActivity extends Test_Base
{

    private ModManager mModManager;

    Handler mHandler = null;
    ModsProjectorFragment mModsProjectorFragment = null;
    ModsTestPatternFragment mModsTestPatternFragment = null;
    FragmentManager mFragmentManager = null;

    private BroadcastReceiver mModBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_ATTACH.equals(action))
            {
                dbgLog(TAG, "Mod attached", 'i');
                mModsProjectorFragment.updateStatus(true);
            }
            if (ModManager.ACTION_MOD_DETACH.equals(action))
            {
                dbgLog(TAG, "Mod detached", 'i');
                mModsProjectorFragment.updateStatus(false);
            }
        }
    };

    @Override
    public void onCreate(Bundle bundle)
    {
        TAG = "MotoMods_Projector";
        super.onCreate(bundle);

        mModManager = MotoMods.getModManager();
        dbgLog(TAG, "mModManager=" + mModManager, 'i');

        IntentFilter filter = new IntentFilter();
        filter.addAction(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        registerReceiver(mModBroadcastReceiver, filter);
        mFragmentManager = getFragmentManager();
        mModsProjectorFragment = new ModsProjectorFragment();
        mModsTestPatternFragment = new ModsTestPatternFragment();
        mFragmentManager.beginTransaction()
                .replace(android.R.id.content, mModsProjectorFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(mModBroadcastReceiver);
    }

    class ModsProjectorFragment extends PreferenceFragment
    {
        Handler mDisplayHandler = null;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            dbgLog(TAG, "ModsProjectorFragment: onCreate", 'i');

            addPreferencesFromResource(com.motorola.motocit.R.layout.mods_projector);
            mDisplayHandler = new Handler();
            setupDisplayTurnOnOff();
            setupShowTestPattern();
            updateStatus(true);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState)
        {
            dbgLog(TAG, "ModsProjectorFragment: onCreateView", 'i');
            setNormalScreen();
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        private void setupDisplayTurnOnOff()
        {
            SwitchPreference preference = (SwitchPreference) findPreference(getString(com.motorola.motocit.R.string.pref_title_display_turn_on_off));
            preference.setEnabled(isDisplayAvailable());
            preference.setChecked(getDisplayState());
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    Boolean value = (Boolean) newValue;
                    if (value)
                    {
                        setDisplayState(ModDisplay.STATE_ON);
                    }
                    else
                    {
                        setDisplayState(ModDisplay.STATE_OFF);
                    }
                    return true;
                }
            });
        }

        private void setupShowTestPattern()
        {
            Preference preference = (Preference) findPreference(getString(com.motorola.motocit.R.string.pref_title_show_test_pattern));
            preference.setEnabled(isDisplayAvailable());
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    mFragmentManager.beginTransaction()
                            .replace(android.R.id.content, mModsTestPatternFragment)
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
            });
        }

        public void updateStatus(boolean attached)
        {
            Preference preference = findPreference(getString(com.motorola.motocit.R.string.pref_title_status));
            preference.setSummary((attached) ? getString(com.motorola.motocit.R.string.mod_attach) : getString(com.motorola.motocit.R.string.mod_detach));

            Preference display = findPreference(getString(com.motorola.motocit.R.string.pref_title_display_turn_on_off));
            display.setEnabled(isDisplayAvailable());
        }

        private boolean isDisplayAvailable()
        {
            if (mModManager != null)
            {
                return (mModManager.getClassManager(Protocol.MODS_DISPLAY) != null);
            }
            else
            {
                return false;
            }
        }

        private void setDisplayState(final int state)
        {
            mDisplayHandler.post(new Runnable() {
                public void run()
                {
                    ModDisplay display = null;
                    String result = "failure";
                    if (mModManager != null)
                    {
                        display = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                        if (display != null && display.setModDisplayState(state))
                        {
                            result = "success";
                            dbgLog(TAG, "Display state written with success", 'i');
                        }
                        else
                        {
                            dbgLog(TAG, "Display state written with failure", 'i');
                        }
                    }
                    Toast.makeText(ProjectorActivity.this, "Display state changed  " + result, Toast.LENGTH_SHORT).show();
                }
            });
        }

        private boolean getDisplayState()
        {
            ModDisplay display = null;
            if (mModManager != null)
            {
                display = (ModDisplay) mModManager.getClassManager(Protocol.MODS_DISPLAY);
                if (display != null && display.getModDisplayState() == ModDisplay.STATE_ON)
                {
                    return true;
                }
            }
            return false;
        }
    }

    public class TestPatternView extends View
    {
        protected final Paint mPathLinePaint;

        public TestPatternView(Context c)
        {
            super(c);
            mPathLinePaint = new Paint();
            mPathLinePaint.setAntiAlias(false);
            mPathLinePaint.setARGB(255, 255, 255, 255);
            mPathLinePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            dbgLog(TAG, "HdmiNineBars_View: onDraw", 'i');
            // if activity is ending don't redraw
            if (isActivityEnding())
            {
                return;
            }

            // NOTE: X and Y values are reversed since this is a landscape
            // pattern!
            int originY = 0 + TestUtils.getDisplayXLeftOffset();
            int originX = 0 + TestUtils.getDisplayYTopOffset();
            int width = getWidth() - 1 - (TestUtils.getDisplayYTopOffset() + TestUtils.getDisplayYBottomOffset());
            int height = getHeight() - 1 - (TestUtils.getDisplayXLeftOffset() + TestUtils.getDisplayXRightOffset());

            int VertDivs = 0, HorDivs = 9;

            // if Divs set to 0, use that as flag to divs equal to display
            // resolution
            if (VertDivs == 0)
            {
                VertDivs = height;
            }
            if (HorDivs == 0)
            {
                HorDivs = width;
            }

            float VertRatio = (float) height / VertDivs;
            float HorRatio = (float) width / HorDivs;
            float[] hsv =
            { 0, 0, 0 }; // array to hold HSV values; Hue, Saturation, Value
            Rect r = new Rect();

            // cycle thru grid incrementally changing HSV
            for (int rows = 0; rows < VertDivs; rows++)
            {
                for (int cols = 0; cols < HorDivs; cols++)
                {
                    hsv[0] = (((float) cols / HorDivs) * 360); // Hue 0 to 360
                    // one half of vertical pattern has fixed Value and varies
                    // Sat
                    if (rows < (VertDivs / 2))
                    {
                        hsv[1] = ((2 * (float) rows) / VertDivs); // Saturation
                        hsv[2] = (float) 1.0; // Value fixed to 1.0
                    }
                    // other half of vertical pattern has fixed Sat and varies
                    // Value
                    else
                    {
                        hsv[1] = (float) 1.0; // Saturation fixed to 1.0
                        hsv[2] = (float) 2.0 - ((2 * (float) rows) / VertDivs); // Value
                    }

                    // Define color and draw cell in grid
                    mPathLinePaint.setColor(Color.HSVToColor(255, hsv));
                    r.set((int) (HorRatio * cols) + originX, (int) (VertRatio * rows) + originY, (int) (HorRatio * (cols + 1)) + originX,
                            (int) (VertRatio * (rows + 1)) + originY);
                    canvas.drawRect(r, mPathLinePaint);
                }
            }
        }
    }

    class ModsTestPatternFragment extends Fragment
    {
        View mView = null;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            setFullScreen();

            if (mView == null)
            {
                mView = new TestPatternView(ProjectorActivity.this);
            }
            mView.setVisibility(View.VISIBLE);
            mView.setOnTouchListener(new OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    toggleNavigationBar();
                    return false;
                }

            });
            setContentView(mView);

            dbgLog(TAG, "ModsTestPatternFragment: onCreate", 'i');
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onDestroy()
        {
            super.onDestroy();
            if (mView != null)
            {
                mView.setVisibility(View.GONE);
            }
            dbgLog(TAG, "ModsTestPatternFragment: onDestroy", 'i');
        }
    }

    private void setFullScreen()
    {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ActionBar action = getActionBar();
        action.hide();

        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    private void setNormalScreen()
    {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ActionBar action = getActionBar();
        action.show();

        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        newUiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
        newUiOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    private void toggleNavigationBar()
    {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this,
                        getString(com.motorola.motocit.R.string.mode_notice),
                        Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                dbgLog(TAG, "mFragmentManager.getBackStackEntryCount()=" + mFragmentManager.getBackStackEntryCount(), 'i');
                if (mFragmentManager.getBackStackEntryCount() > 1)
                {
                    mFragmentManager.popBackStackImmediate();
                    mFragmentManager.beginTransaction()
                            .remove(mModsTestPatternFragment)
                            .commit();
                }
                else
                {
                    systemExitWrapper(0);
                }
            }
        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException,
            CmdPassException
    {}

    @Override
    protected void printHelp()
    {}

    @Override
    protected boolean onSwipeRight()
    {
        return false;
    }

    @Override
    protected boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    protected boolean onSwipeDown()
    {
        return false;
    }

    @Override
    protected boolean onSwipeUp()
    {
        return false;
    }
}
