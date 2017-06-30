/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.motorola.motocit.alt.altautocycle.util.Constant;
import com.motorola.motocit.alt.altautocycle.util.FileTools;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;
import com.motorola.motocit.Test_Main;

import java.io.FileOutputStream;
import java.io.InputStream;

public class AltMainActivity extends Test_Base
{
    private Button btnStart;
    private Button btnStop;
    private Button btnVerify;
    private Button btnOnline;
    private Button btnBringup;
    private Button btnBringupVerify;
    private SharedPreferences settings;
    private boolean isSetAlarm;
    // true means normal start, false means verify function start.
    private boolean startFlag;
    private boolean mBringupCycle;

    private boolean isTestRuning = false;
    private boolean isStopTestSuccess = false;
    private boolean hasFailure = false;

    private boolean isPermissionAllowed = false;
    private boolean isPermissionAllowedForCamera = false;
    private boolean isPermissionAllowedForAccount = false;
    private String[] permissions = { "android.permission.CAMERA", "android.permission.GET_ACCOUNTS" };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "AltMainActivity";
        super.onCreate(savedInstanceState);
        dbgLog(TAG, "onCreate", 'i');
        init();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        dbgLog(TAG, "onResume", 'i');
        // sendStartActivityPassed();

        checkPermission();

        if ((isPermissionAllowedForCamera && isPermissionAllowedForAccount) || isPermissionAllowed)
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
            isPermissionAllowedForCamera = true;
            isPermissionAllowedForAccount = true;
        }
        else
        {
            dbgLog(TAG, "checking permission", 'i');
            // check permissions on M release
            ArrayList<String> requestPermissions = new ArrayList();

            if (checkSelfPermission(permissions[0]) != PackageManager.PERMISSION_GRANTED)
            {
                isPermissionAllowedForCamera = false;
                requestPermissions.add(permissions[0]);
            }
            else
            {
                isPermissionAllowedForCamera = true;
            }

            if (checkSelfPermission(permissions[1]) != PackageManager.PERMISSION_GRANTED)
            {
                isPermissionAllowedForAccount = false;
                requestPermissions.add(permissions[1]);
            }
            else
            {
                isPermissionAllowedForAccount = true;
            }

            if (!requestPermissions.isEmpty())
            {
                // Permission has not been granted and must be
                // requested.
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
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                {
                    if (permissions[i] == Manifest.permission.CAMERA)
                    {
                        isPermissionAllowedForCamera = true;
                        dbgLog(TAG, "isPermissionAllowedForCamera=true", 'i');
                    }

                    if (permissions[i] == Manifest.permission.GET_ACCOUNTS)
                    {
                        isPermissionAllowedForAccount = true;
                        dbgLog(TAG, "isPermissionAllowedForAudio=true", 'i');
                    }
                }
                else
                {
                    dbgLog(TAG, "deny permission", 'i');
                    finish();
                }
            }
        }
    }

    private void init()
    {
        Constant.setAutoCycleItems();

        setContentView(com.motorola.motocit.R.layout.alt_main_layout);
        setTitle("Auto Cycle Test");
        btnStart = (Button) this.findViewById(com.motorola.motocit.R.id.main_button_start);
        btnVerify = (Button) this.findViewById(com.motorola.motocit.R.id.main_button_verify);
        btnBringup = (Button) this.findViewById(com.motorola.motocit.R.id.bringup_button_id);
        btnBringupVerify = (Button) this.findViewById(com.motorola.motocit.R.id.bringup_verify_button_id);
        btnOnline = (Button) this.findViewById(com.motorola.motocit.R.id.main_button_online);

        btnStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderStart = new AlertDialog.Builder(AltMainActivity.this);

                builderStart.setTitle("Confirmation");
                builderStart.setMessage("Continue Auto Cycle Test?");

                builderStart.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        prepareStartTest("AUTO_CYCLE");
                    }

                });

                builderStart.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }

                });

                AlertDialog alertSeq = builderStart.create();
                alertSeq.show();
            }
        });

        btnVerify.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderVerify = new AlertDialog.Builder(AltMainActivity.this);

                builderVerify.setTitle("Confirmation");
                builderVerify.setMessage("Continue Mini Cycle Test?");

                builderVerify.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        prepareStartTest("MINI_CYCLE");
                    }
                });

                builderVerify.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }

                });

                AlertDialog alertSeq = builderVerify.create();
                alertSeq.show();
            }
        });

        btnBringup.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderBringup = new AlertDialog.Builder(AltMainActivity.this);

                builderBringup.setTitle("Confirmation");
                builderBringup.setMessage("Continue Bring Up Auto Cycle Test?");

                builderBringup.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        prepareStartTest("BRINGUP_AUTO_CYCLE");
                    }

                });

                builderBringup.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }

                });

                AlertDialog alertSeq = builderBringup.create();
                alertSeq.show();
            }
        });

        btnBringupVerify.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                AlertDialog.Builder builderBringupVerify = new AlertDialog.Builder(AltMainActivity.this);

                builderBringupVerify.setTitle("Confirmation");
                builderBringupVerify.setMessage("Continue Bring Up Mini Cycle Test?");

                builderBringupVerify.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                        prepareStartTest("BRINGUP_MINI_CYCLE");
                    }

                });

                builderBringupVerify.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();
                    }

                });

                AlertDialog alertSeq = builderBringupVerify.create();
                alertSeq.show();
            }
        });

        btnOnline.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                prepareStartTest("SET_ONLINE_MODE");
            }
        });

        setBootmodeInfo();
        setVersionInfo();
        prepareConfigFile();
    }

    private void setBootmodeInfo()
    {
        String bootmode = SystemProperties.get("ro.bootmode", "unknown");
    }

    private void setVersionInfo()
    {
        try
        {
            PackageManager pkManager = this.getPackageManager();
            PackageInfo pkInfo = pkManager.getPackageInfo(this.getPackageName(), 0);
            String versionName = pkInfo.versionName;
            versionName = versionName != null ? versionName : "1.0";
        }
        catch (Exception e)
        {}
    }

    private void prepareStartTest(final String testname)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                hasFailure = false;
                boolean isOnlineModeTest = false;

                if (!isFactoryMode())
                {
                    TestUtils.dbgLog(TAG, "Test mode error. Should be in Factory Mode", 'e');
                    alertMessage(getResources().getString(com.motorola.motocit.R.string.factory_mode_error),
                            getResources().getString(com.motorola.motocit.R.string.factory_mode_alert));
                    hasFailure = true;
                }

                // checkPermission();

                if (testname.contentEquals("AUTO_CYCLE"))
                {
                    TestUtils.dbgLog(TAG, "Prepare AUTO_CYCLE test", 'i');
                    startFlag = true;
                    TestUtils.dbgLog(TAG, "startFlag=true", 'i');
                    btnStart.setEnabled(false);
                    String feedback = checkEnvironment();
                    if (feedback.equals("OK"))
                    {
                        // Always start from the beginning if click the
                        // button
                        clearPreferences();
                        setBringupCycle(false);
                    }
                    else if (feedback.equals("GPS"))
                    {
                        btnStart.setEnabled(true);
                        enableGPS("Press continue button to enable GPS function");
                    }
                    else
                    {
                        hasFailure = true;
                        alertMessage(null, feedback);
                    }
                }
                else if (testname.contentEquals("MINI_CYCLE"))
                {
                    TestUtils.dbgLog(TAG, "Prepare MINI_CYCLE test", 'i');
                    startFlag = false;
                    btnVerify.setEnabled(false);
                    TestUtils.dbgLog(TAG, "startFlag=false", 'i');

                    // Always start from the beginning if click the button
                    clearPreferences();
                    setBringupCycle(false);
                }
                else if (testname.contentEquals("BRINGUP_AUTO_CYCLE"))
                {
                    TestUtils.dbgLog(TAG, "Prepare BRINGUP_AUTO_CYCLE test", 'i');
                    startFlag = true;
                    btnBringup.setEnabled(false);

                    // Always start from the beginning if click the button
                    clearPreferences();
                    setBringupCycle(true);
                }
                else if (testname.contentEquals("BRINGUP_MINI_CYCLE"))
                {
                    TestUtils.dbgLog(TAG, "Prepare BRINGUP_MINI_CYCLE test", 'i');
                    startFlag = false;
                    btnBringupVerify.setEnabled(false);

                    // Always start from the beginning if click the button
                    clearPreferences();
                    setBringupCycle(true);
                }
                else if (testname.contentEquals("SET_ONLINE_MODE"))
                {
                    isOnlineModeTest = true;
                }
                else
                {
                    hasFailure = true;
                    TestUtils.dbgLog(TAG, "Unsupported test", 'e');
                }

                if (!hasFailure)
                {
                    if (isOnlineModeTest)
                    {
                        TestUtils.dbgLog(TAG, "Prepare SET_ONLINE_MODE test", 'i');
                        Intent i = new Intent();
                        TestUtils.dbgLog(TAG, "Set class for RunModemOnlineActivity", 'i');
                        i.setClass(AltMainActivity.this, com.motorola.motocit.alt.altautocycle.RunModemOnlineActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                    else
                    {
                        startTest();
                    }
                }
            }
        });

    }

    public static boolean isFactoryMode()
    {
        String bootmode = SystemProperties.get("ro.bootmode", "unknown");
        if ("factory".equalsIgnoreCase(bootmode) || "mot-factory".equalsIgnoreCase(bootmode))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    private void enableGPS(String msg)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder = builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id)
            {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, 0);
                dialog.cancel();
                btnStart.setEnabled(true);
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void alertMessage(String title, String msg)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (title != null && title.isEmpty() == false)
        {
            builder.setTitle(title);
        }
        builder = builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int id)
            {
                dialog.cancel();
                finish();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private String checkEnvironment()
    {
        String feedback = "";
        try
        {
            // check SD card folder
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            {
                File file = new File(Constant.SD_PATH);
                if (!file.exists())
                {
                    if (!file.mkdirs())
                    {
                        feedback = "Create folder " + Constant.SD_PATH + " fail";
                    }
                    else
                    {
                        feedback = "OK";
                    }
                }
                else
                {
                    feedback = "OK";
                }
            }
            else
            {
                feedback = "Memory card is unusable";
            }
            // check GPS status
            if (Constant.CHECK_GPS_FUNCTION)
            {
                if (feedback.equals("OK"))
                {
                    LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                    if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    {
                        feedback = "GPS";
                    }
                }
            }
        }
        catch (Exception e)
        {
            feedback = e.getMessage();
        }
        return feedback;
    }

    private void startTest()
    {
        TestUtils.dbgLog(TAG, "In startTest", 'i');
        isTestRuning = false;
        if (startFlag)
        {
            TestUtils.dbgLog(TAG, "Start Auto Cycle Test", 'i');
            TestUtils.dbgLog(TAG, "CYCLE_INTERVAL_TIME=" + Constant.CYCLE_INTERVAL_TIME, 'i');
            setAlarm(Constant.CYCLE_INTERVAL_TIME + 30 * 1000);
        }
        else
        {
            TestUtils.dbgLog(TAG, "Start Bringup Test", 'i');
            TestUtils.dbgLog(TAG, "VERIFY_CYCLE_INTERVAL_TIME=" + Constant.VERIFY_CYCLE_INTERVAL_TIME, 'i');
            setAlarm(Constant.VERIFY_CYCLE_INTERVAL_TIME + 5 * 1000);
        }

        if (isSetAlarm)
        {
            initApplication();
            isTestRuning = true;
            // this.finish();
        }
    }

    private void setAlarm(int waitTime)
    {
        this.isSetAlarm = false;
        try
        {
            Intent intent = new Intent(this, com.motorola.motocit.alt.altautocycle.AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000, waitTime, pendingIntent);
            this.isSetAlarm = true;
        }
        catch (Exception e)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException e1)
            {}
            finally
            {
                startTest();
            }
        }
    }

    private void cancelAlarm()
    {
        try
        {
            AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(this, com.motorola.motocit.alt.altautocycle.AlarmReceiver.class), 0));
            alarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(this, com.motorola.motocit.alt.altautocycle.AlarmVibratorELPannelReceiver.class), 0));
        }
        catch (Exception e)
        {}
    }

    private void initApplication()
    {
        try
        {
            TestUtils.dbgLog(TAG, "init app", 'i');
            SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd");
            this.settings = this.getSharedPreferences("altautocycle", 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("current_step", 0);
            editor.putString("log_file", Constant.SD_PATH + "autocycle" + sf.format(new Date()) + ".log");
            editor.putBoolean("start_flag", this.startFlag);
            editor.commit();
        }
        catch (Exception e)
        {}
    }

    private void clearPreferences()
    {
        settings = this.getSharedPreferences("altautocycle", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.commit();
    }

    private void setBringupCycle(boolean bringup)
    {
        mBringupCycle = bringup;
        settings = this.getSharedPreferences("altautocycle", 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("bringup_cycle", bringup);
        editor.commit();
    }

    private void prepareConfigFile()
    {
        File file1 = new File("/sdcard/alt_autocycle/alt_audio_volume.cfg");
        if (file1.exists())
        {
            return;
        }
        InputStream in = null;
        FileOutputStream out = null;
        int rawID = com.motorola.motocit.R.raw.alt_audio_volume;
        try
        {
            in = getResources().openRawResource(rawID);
            out = new FileOutputStream(file1);
            byte[] buffer = new byte[1024];
            while (true)
            {
                int ins = in.read(buffer);
                if (ins == -1)
                {
                    out.flush();
                    out.close();
                    out = null;
                }
                else
                {
                    out.write(buffer, 0, ins);
                }
            }
        }
        catch (Exception e)
        {}
        finally
        {
            try
            {
                if (in != null)
                {
                    in.close();
                    in = null;
                }
                if (out != null)
                {
                    out.close();
                    out = null;
                }
            }
            catch (Exception e)
            {}
        }
        return;
    }

    private void stopTest()
    {
        isStopTestSuccess = true;
        isTestRuning = false;
        // create broadcast to stop alt test base activity
        TestUtils.dbgLog(TAG, "Stop test - Sending broadcast to stop alt test", 'i');
        Intent altTestBaseIntent = new Intent(ALTBaseActivity.ACTION_ALT_TEST_BASE_FINISH);
        sendBroadcast(altTestBaseIntent);
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("START_TEST"))
        {
            if (isTestRuning)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("ALT test is in process, stop it before start new test");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);

            }
            else
            {
                if (strRxCmdDataList.size() > 0)
                {
                    List<String> strReturnDataList = new ArrayList<String>();

                    for (int i = 0; i < strRxCmdDataList.size(); i++)
                    {
                        if (strRxCmdDataList.get(i).toUpperCase().contains("TESTNAME"))
                        {
                            List<String> strDataList = new ArrayList<String>();
                            String testname = null;
                            testname = strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1);
                            dbgLog(TAG, "Test name passed in " + testname, 'i');

                            prepareStartTest(testname);

                            if (!hasFailure)
                            {
                                strDataList.add("START_TEST=PASS");
                            }
                            else
                            {
                                strDataList.add("START_TEST=FAILED");
                            }

                            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                            sendInfoPacketToCommServer(infoPacket);

                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN: " + strRxCmdDataList.get(i));
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to
                    // CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_TEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();
            stopTest();

            // set timeout 10s
            long startTime = System.currentTimeMillis();
            while (!isStopTestSuccess)
            {
                dbgLog(TAG, "Stopping test now", 'i');

                if ((System.currentTimeMillis() - startTime) > 10000)
                {
                    dbgLog(TAG, "Time out to stop test", 'e');
                    isStopTestSuccess = false;
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                    isStopTestSuccess = false;
                }
            }

            if (isStopTestSuccess)
            {
                strDataList.add("STOP_TEST=PASS");
            }
            else
            {
                strDataList.add("STOP_TEST=FAILED");
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CURRENT_TEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();
            if (!isTestRuning)
            {
                // Return command PASS, set running test to NULL in returned
                // data
                strDataList.add("RUNNING_TEST=NULL");
                strDataList.add("AUTO_CYCLE_TEST=FALSE");
                strDataList.add("MINI_CYCLE_TEST=FALSE");
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                boolean hasError = false;
                boolean isAutoCycleTest = false;
                int curStep = -1;

                try
                {
                    settings = this.getSharedPreferences("altautocycle", 0);
                    if (settings != null)
                    {
                        curStep = settings.getInt("current_step", 0);
                        isAutoCycleTest = settings.getBoolean("start_flag", true);
                        TestUtils.dbgLog("TAG", "GET_CURRENT_TEST. cur_step" + curStep, 'i');
                    }
                    else
                    {
                        hasError = true;
                        TestUtils.dbgLog("TAG", "Can not get current test step", 'e');
                    }
                }
                catch (Exception e)
                {
                    hasError = true;
                }

                if (!hasError && (curStep > -1))
                {
                    strDataList.add("RUNNING_TEST=" + Constant.AUTO_CYCLE_ITEMS[curStep - 1]);
                    if (isAutoCycleTest)
                    {
                        strDataList.add("AUTO_CYCLE_TEST=TRUE");
                        strDataList.add("MINI_CYCLE_TEST=FALSE");
                    }
                    else
                    {
                        strDataList.add("AUTO_CYCLE_TEST=FALSE");
                        strDataList.add("MINI_CYCLE_TEST=TRUE");
                    }
                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                    sendInfoPacketToCommServer(infoPacket);

                    // Generate an exception to send data back to CommServer
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("FAILED TO GET RUNNING TEST");
                    TestUtils.dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("SET_TIME_SETTINGS"))
        {
            if (isTestRuning)
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("ALT test is in process, stop it before setting test time");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);

            }
            else
            {
                if (strRxCmdDataList.size() > 0)
                {
                    List<String> strReturnDataList = new ArrayList<String>();

                    for (String keyValuePair : strRxCmdDataList)
                    {
                        String splitResult[] = splitKeyValuePair(keyValuePair);
                        String key = splitResult[0];
                        String value = splitResult[1];

                        if (key.equalsIgnoreCase("CYCLE_WAITING_TIME"))
                        {
                            Constant.CYCLE_WAITING_TIME = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("CYCLE_INTERVAL_TIME"))
                        {
                            Constant.CYCLE_INTERVAL_TIME = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("CYCLE_RUNNING_LOG"))
                        {
                            Constant.CYCLE_RUNNING_LOG = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("VERIFY_CYCLE_WAITING_TIME"))
                        {
                            Constant.VERIFY_CYCLE_WAITING_TIME = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("VERIFY_CYCLE_INTERVAL_TIME"))
                        {
                            Constant.VERIFY_CYCLE_INTERVAL_TIME = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("VERIFY_CYCLE_RUNNING_LOG"))
                        {
                            Constant.VERIFY_CYCLE_RUNNING_LOG = Integer.parseInt(value);
                        }
                        else if (key.equalsIgnoreCase("ELPANNEL_INTERVAL_TIME"))
                        {
                            Constant.ELPANNEL_INTERVAL_TIME = Integer.parseInt(value);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN key: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }

                    // Generate an exception to send data back to CommServer
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    // Generate an exception to send FAIL result and mesg back
                    // to
                    // CommServer
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_TIME_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();
            List<String> strReturnDataList = new ArrayList<String>();

            strDataList.add("CYCLE_WAITING_TIME=" + Constant.CYCLE_WAITING_TIME);
            strDataList.add("CYCLE_INTERVAL_TIME=" + Constant.CYCLE_INTERVAL_TIME);
            strDataList.add("CYCLE_RUNNING_LOG=" + Constant.CYCLE_RUNNING_LOG);
            strDataList.add("VERIFY_CYCLE_WAITING_TIME=" + Constant.VERIFY_CYCLE_WAITING_TIME);
            strDataList.add("VERIFY_CYCLE_INTERVAL_TIME=" + Constant.VERIFY_CYCLE_INTERVAL_TIME);
            strDataList.add("VERIFY_CYCLE_RUNNING_LOG=" + Constant.VERIFY_CYCLE_RUNNING_LOG);
            strDataList.add("ELPANNEL_INTERVAL_TIME=" + Constant.ELPANNEL_INTERVAL_TIME);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

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
        strHelpList.add("This function is to start/stop ALT function test");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("START_TEST - start specific ALT function test");
        strHelpList.add("  ");
        strHelpList.add("STOP_Test - stop the ALT test");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        return true;
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

    private void logResults(String passFail)
    {}
}
