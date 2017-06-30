/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Toast;

public class Test_Main extends Test_Base
{

    private WifiManager mWifiManager = null;
    private boolean isWiFiOffDefault = false;

    private static Method mSettingsPutInt = null;
    private static Method mSettingsGetInt = null;
    private static Class<?> mSettingsClass = null;
    private static final String mDisable_sound = "sound_effects_enabled";
    private Integer mDefault_sound_effect = 0;
    private String filePassedIn = "cqatest_cfg";
    private boolean isALTConfigSelected = false;
    private boolean isFactoryConfigSelected = false;
    private RadioButton factoryConfig;
    private RadioButton altConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Test_Main";
        super.onCreate(savedInstanceState);

        checkWiFiDefaultState(); // Get wifi default state

        // Setup Display Settings
        TestUtils.setCQASettingsFromConfig();
        adjustViewDisplayArea(com.motorola.motocit.R.layout.main_test);

        Button menuModeButton = (Button) findViewById(com.motorola.motocit.R.id.menumode_button);
        Button sequenceModeButton = (Button) findViewById(com.motorola.motocit.R.id.sequencemode_button);
        Button modelAssemblyButton = (Button) findViewById(com.motorola.motocit.R.id.modelassemblymode_button);
        factoryConfig = (RadioButton) findViewById(com.motorola.motocit.R.id.config_factory);
        altConfig = (RadioButton) findViewById(com.motorola.motocit.R.id.config_alt);

        this.setTitle("CQATest");

        // use cqatest_cfg by default
        factoryConfig.setChecked(true);
        altConfig.setChecked(false);

        if (TestUtils.isDaliSportHW())
        {
            TestUtils.setSequenceFileInUse(filePassedIn + "_DaliSport");
            TestUtils.setfactoryOrAltConfigFileInUse(filePassedIn + "_DaliSport");
        }
        else
        {
            TestUtils.setSequenceFileInUse(filePassedIn);
            TestUtils.setfactoryOrAltConfigFileInUse(filePassedIn);
        }

        dbgLog(TAG, "onCreate, config_file_name:" + TestUtils.getSequenceFileInUse(), 'i');

        disableTouchSound();

        menuModeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                contentRecord("mode.txt", "Menu", MODE_WORLD_WRITEABLE);
                Intent myIntent = new Intent(Test_Main.this, com.motorola.motocit.Test_MenuMode.class);
                Test_Main.this.startActivity(myIntent);
            }
        });

        sequenceModeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderSeq = new AlertDialog.Builder(Test_Main.this);

                builderSeq.setTitle("Confirmation");
                builderSeq.setMessage("Continue Sequential Test?");

                builderSeq.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int which)
                   {
                       contentRecord("mode.txt", "Seq", MODE_WORLD_WRITEABLE);
                       Intent myIntent = new Intent(Test_Main.this, com.motorola.motocit.Test_SequenceMode.class);
                       Test_Main.this.startActivity(myIntent);

                       dialog.dismiss();
                   }

                });

                builderSeq.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int which)
                   {
                       dialog.dismiss();
                   }

                });

                AlertDialog alertSeq = builderSeq.create();
                alertSeq.show();
            }
        });

        modelAssemblyButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderAssembly = new AlertDialog.Builder(Test_Main.this);
                builderAssembly.setTitle("Confirmation");
                builderAssembly.setMessage("Continue Assembly Test?");

                builderAssembly.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int which)
                   {
                       contentRecord("mode.txt", "Seq", MODE_WORLD_WRITEABLE);
                       Intent myIntent = new Intent(Test_Main.this, com.motorola.motocit.Test_ModelAssemblyMode.class);
                       Test_Main.this.startActivity(myIntent);

                       dialog.dismiss();
                   }

                });

                builderAssembly.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int which)
                   {
                       dialog.dismiss();
                   }

                });

                AlertDialog alertAssembly = builderAssembly.create();
                alertAssembly.show();
            }
        });

        factoryConfig.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                isFactoryConfigSelected = factoryConfig.isChecked();

                if (isFactoryConfigSelected)
                {
                    filePassedIn = "cqatest_cfg";
                    if (TestUtils.isDaliSportHW())
                    {
                        filePassedIn = filePassedIn + "_DaliSport";
                    }
                    dbgLog(TAG, "config_file_name:" + filePassedIn, 'i');
                    factoryConfig.setChecked(true);
                    altConfig.setChecked(false);
                    TestUtils.setfactoryOrAltConfigFileInUse(filePassedIn);
                    dbgLog(TAG, "use factory config file", 'i');
                }
            }
        });

        altConfig.setOnCheckedChangeListener(new RadioButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                isALTConfigSelected = altConfig.isChecked();

                if (isALTConfigSelected)
                {
                    filePassedIn = "cqatest_alt_cfg";
                    if (TestUtils.isDaliSportHW())
                    {
                        filePassedIn = filePassedIn + "_DaliSport";
                    }
                    dbgLog(TAG, "config_file_name:" + filePassedIn, 'i');
                    if (TestUtils.isConfigFileExist(filePassedIn))
                    {
                        altConfig.setChecked(true);
                        factoryConfig.setChecked(false);
                        dbgLog(TAG, "use alt config file", 'i');
                    }
                    else
                    {
                        // If there is no alt config, use default config file instead
                        filePassedIn = "cqatest_cfg";
                        altConfig.setChecked(false);
                        factoryConfig.setChecked(true);
                        Toast.makeText(Test_Main.this, "No ALT Config, use default config file instead", Toast.LENGTH_SHORT).show();
                        dbgLog(TAG, "no alt config file", 'i');
                    }
                    TestUtils.setfactoryOrAltConfigFileInUse(filePassedIn);
                }
            }
        });

    }
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        enableSystemUiHider(false);
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
        Context context = Test_Main.this;
        PackageManager pm = context.getPackageManager();
        ComponentName comp = new ComponentName(context, Test_Main.class);
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
                        ContentResolver.class, String.class, int.class});

                mSettingsPutInt = mSettingsClass.getMethod("putInt", new Class[] {
                        ContentResolver.class, String.class, int.class});
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
                mDefault_sound_effect = (Integer)mSettingsGetInt.invoke(mSettingsClass, getContentResolver(),
                        mDisable_sound, 0);
                dbgLog(TAG, "getInt for default sound effect value " + mDefault_sound_effect, 'i');
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "fail to invoke getInt", 'i');
        }

        if ((mSettingsPutInt != null)  && (mDefault_sound_effect == 1))
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

        strHelpList.add("Test Main");
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
