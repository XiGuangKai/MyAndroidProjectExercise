/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.LocalActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AppMainActivity extends Test_Base
{

    private ViewPager mPager;
    private List<View> listViews;
    private ImageView cursor;
    private TextView tabNameCQA, tabNameAlt;
    private int offset = 0;
    private int currIndex = 0;
    private int bmpW;
    private LocalActivityManager manager = null;
    private Context context = null;

    private WifiManager mWifiManager = null;
    private boolean isWiFiOffDefault = false;

    private static Method mSettingsPutInt = null;
    private static Method mSettingsGetInt = null;
    private static Class<?> mSettingsClass = null;
    private static final String mDisable_sound = "sound_effects_enabled";
    private Integer mDefault_sound_effect = 0;

    private boolean isPermissionAllowed = false;
    private boolean isPermissionAllowedForCamera = false;
    private boolean isPermissionAllowedForAccount = false;
    private String[] permissions = { "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_PHONE_STATE", "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.BODY_SENSORS", "com.motorola.mod.permission.RAW_PROTOCOL" };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "App_Main_Activity";
        super.onCreate(savedInstanceState);

        checkWiFiDefaultState();
        setContentView(com.motorola.motocit.R.layout.main_layout_tabview);
        context = getApplicationContext();
        manager = new LocalActivityManager(this, true);
        manager.dispatchCreate(savedInstanceState);
        disableTouchSound();
        InitImageView();
        InitTextView();
        InitViewPager();
    }

    private void InitTextView()
    {
        tabNameCQA = (TextView) findViewById(com.motorola.motocit.R.id.tabname_cqa);
        tabNameAlt = (TextView) findViewById(com.motorola.motocit.R.id.tabname_alttest);

        tabNameCQA.setOnClickListener(new MyOnClickListener(0));
        tabNameAlt.setOnClickListener(new MyOnClickListener(1));
    }

    private void InitViewPager()
    {
        LayoutInflater mInflater = getLayoutInflater();
        mPager = (ViewPager) findViewById(com.motorola.motocit.R.id.vPager);
        final ArrayList<View> list = new ArrayList<View>();

        // add CQA main menu
        Intent intentCQATest = new Intent(context, com.motorola.motocit.Test_Main.class);
        list.add(getView("com.motorola.motocit.R.layout.main_test", intentCQATest));

        if ((Build.VERSION.SDK_INT >= 23) && TestUtils.isUserdebugEngBuild() && TestUtils.isMotDevice())
        {
            // add ALT main menu only it's Motorola products and sw is
            // userdebug/eng build of M or afterwards.
            Intent intentAlt = new Intent(context, com.motorola.motocit.alt.altautocycle.AltMainActivity.class);
            list.add(getView("com.motorola.motocit.R.layout.alt_main_layout", intentAlt));
        }
        else
        {
            Intent intentAltBlank = new Intent(context, com.motorola.motocit.AltBlankActivity.class);
            list.add(getView("com.motorola.motocit.R.layout.alt_no_permission_layout", intentAltBlank));
        }

        mPager.setAdapter(new MyPagerAdapter(list));
        mPager.setCurrentItem(0);
        mPager.setOnPageChangeListener(new MyOnPageChangeListener());
    }

    private View getView(String id, Intent intent)
    {
        return manager.startActivity(id, intent).getDecorView();
    }

    private void InitImageView()
    {
        cursor = (ImageView) findViewById(com.motorola.motocit.R.id.cursor);
        bmpW = BitmapFactory.decodeResource(getResources(), com.motorola.motocit.R.drawable.a).getWidth();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenW = dm.widthPixels;
        offset = (screenW / 2 - bmpW) / 2;
        Matrix matrix = new Matrix();
        matrix.postTranslate(offset, 0);
        cursor.setImageMatrix(matrix);
    }

    public class MyPagerAdapter extends PagerAdapter
    {
        public List<View> mListViews;

        public MyPagerAdapter(List<View> mListViews){
            this.mListViews = mListViews;
        }

        @Override
        public void destroyItem(View arg0, int arg1, Object arg2)
        {
            ((ViewPager) arg0).removeView(mListViews.get(arg1));
        }

        @Override
        public void finishUpdate(View arg0)
        {}

        @Override
        public int getCount()
        {
            return mListViews.size();
        }

        @Override
        public Object instantiateItem(View arg0, int arg1)
        {
            ((ViewPager) arg0).addView(mListViews.get(arg1), 0);
            return mListViews.get(arg1);
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1)
        {
            return arg0 == (arg1);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1)
        {}

        @Override
        public Parcelable saveState()
        {
            return null;
        }

        @Override
        public void startUpdate(View arg0)
        {}
    }

    public class MyOnClickListener implements View.OnClickListener
    {
        private int index = 0;

        public MyOnClickListener(int i){
            index = i;
        }

        @Override
        public void onClick(View v)
        {
            mPager.setCurrentItem(index);
        }
    };

    public class MyOnPageChangeListener implements OnPageChangeListener
    {

        int one = offset * 2 + bmpW;
        int two = one * 2;

        @Override
        public void onPageSelected(int arg0)
        {
            Animation animation = null;
            switch (arg0)
            {
                case 0:
                    if (currIndex == 1)
                    {
                        animation = new TranslateAnimation(one, 0, 0, 0);
                    }
                    else if (currIndex == 2)
                    {
                        animation = new TranslateAnimation(two, 0, 0, 0);
                    }
                    break;
                case 1:
                    if (currIndex == 0)
                    {
                        animation = new TranslateAnimation(offset, one, 0, 0);
                    }
                    else if (currIndex == 2)
                    {
                        animation = new TranslateAnimation(two, one, 0, 0);
                    }
                    break;
                case 2:
                    if (currIndex == 0)
                    {
                        animation = new TranslateAnimation(offset, two, 0, 0);
                    }
                    else if (currIndex == 1)
                    {
                        animation = new TranslateAnimation(one, two, 0, 0);
                    }
                    break;
            }
            currIndex = arg0;
            animation.setFillAfter(true);
            animation.setDuration(300);
            cursor.startAnimation(animation);
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2)
        {}

        @Override
        public void onPageScrollStateChanged(int arg0)
        {}
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        enableSystemUiHider(false);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        checkPermission();

        // if ((isPermissionAllowedForCamera && isPermissionAllowedForAccount)
        // || isPermissionAllowed)
        if (isPermissionAllowed)
        {
            sendStartActivityPassed();
        }
        else
        {
            dbgLog(TAG, "no permission granted to run test", 'e');
            sendStartActivityFailed("No Permission Granted to Camera test");
        }

    }

    private void checkPermission()
    {

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            dbgLog(TAG, "checking permission", 'i');
            // check permissions on M release
            ArrayList<String> requestPermissions = new ArrayList();

            for (String perm : permissions)
            {

                try
                {
                    getPackageManager().getPermissionInfo(perm, 0);
                    if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED)
                    {
                        requestPermissions.add(perm);
                    }
                }
                catch (Exception e)
                {
                    //Permission does not exist
                    dbgLog(TAG, perm + " does not exist", 'i');
                }

            }

            if (!requestPermissions.isEmpty())
            {
                // Permission has not been granted and must be requested.
                String[] params = requestPermissions.toArray(new String[requestPermissions.size()]);
                dbgLog(TAG, "requesting permissions", 'i');
                requestPermissions(params, 1001);
                return;
            }
            else
            {
                isPermissionAllowed = true;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (1001 == requestCode)
        {
            for (int i = 0; i < permissions.length; i++)
            {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                {
                    dbgLog(TAG, "permission denied", 'i');
                    finish();
                }
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev)
    {
        dbgLog(TAG, "onKeyUp() saw " + keyCode, 'i');

        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            systemExitWrapper(0);
        }

        return true;
    }

    @Override
    protected void onDestroy()
    {
        Context context = AppMainActivity.this;
        PackageManager pm = context.getPackageManager();
        ComponentName comp = new ComponentName(context, com.motorola.motocit.AppMainActivity.class);
        String bootmode = SystemProperties.get("ro.bootmode", "normal");

        if ((pm != null) && (comp != null))
        {
            int enabled = pm.getComponentEnabledSetting(comp);

            if ((enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) && !TestUtils.isFactoryCableBoot() && !bootmode.equals("bp-tools"))
            {
                dbgLog(TAG, "Neither factory mode nor bp-tools mode and current window is visible, will hide it.", 'd');
                pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }

        super.onDestroy();
        dbgLog(TAG, "onDestroy", 'd');
        restoreWiFiState(isWiFiOffDefault); // Restore wifi default state
        restoreTouchSound();
    }

    private void restoreWiFiState(boolean isOffByDefault)
    {
        if (isOffByDefault)
        {
            if (mWifiManager.isWifiEnabled())
            {

                mWifiManager.setWifiEnabled(false);
            }
        }
        else
        {
            if (!mWifiManager.isWifiEnabled())
            {
                mWifiManager.setWifiEnabled(true);
            }
        }
    }

    private void checkWiFiDefaultState()
    {
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (!mWifiManager.isWifiEnabled())
        {
            isWiFiOffDefault = true;
        }
    }

    private void disableTouchSound()
    {
        String settingsProvider = "android.provider.Settings$System";

        try
        {
            mSettingsClass = Class.forName(settingsProvider);
            if (mSettingsClass != null)
            {

                mSettingsGetInt = mSettingsClass.getMethod("getInt", new Class[] {
                        ContentResolver.class, String.class, int.class });

                mSettingsPutInt = mSettingsClass.getMethod("putInt", new Class[] {
                        ContentResolver.class, String.class, int.class });
            }
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }

        try
        {
            if (mSettingsGetInt != null)
            {
                mDefault_sound_effect = (Integer) mSettingsGetInt.invoke(mSettingsClass, getContentResolver(),
                        mDisable_sound, 0);
                dbgLog(TAG, "getInt for default sound effect value " + mDefault_sound_effect, 'i');
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "fail to invoke getInt", 'i');
        }

        if ((mSettingsPutInt != null) && (mDefault_sound_effect == 1))
        {
            try
            {
                mSettingsPutInt.invoke(mSettingsClass, getContentResolver(),
                        mDisable_sound, 0);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "fail to invoke putInt", 'i');
            }
        }

    }

    private void restoreTouchSound()
    {
        if (mDefault_sound_effect == 1)
        {
            try
            {
                mSettingsPutInt.invoke(mSettingsClass, getContentResolver(),
                        mDisable_sound, 1);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "fail to re-enable touch sound", 'i');
            }
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("NO_VALID_COMMANDS"))
        {

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

        strHelpList.add("App Main Activity");
        strHelpList.add("");
        strHelpList.add("This activity brings up the Main Test menu");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        return false;
    }

    @Override
    public boolean onSwipeLeft()
    {
        return false;
    }

    @Override
    public boolean onSwipeUp()
    {
        return false;
    }

    @Override
    public boolean onSwipeDown()
    {
        return false;
    }
}
