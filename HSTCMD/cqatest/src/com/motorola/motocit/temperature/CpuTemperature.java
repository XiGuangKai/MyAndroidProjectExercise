/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.temperature;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CpuTemperature extends Test_Base
{
    boolean activityStartedFromCommServer = false;

    private TextView CpuTemperatureText;

    public static final String FILE_OMAP_FULL_CAP = "/sys/class/power_supply/battery/charge_full_design";
    private static final String HWMON_PATH = "/sys/class/hwmon";
    private static final String THERMAL_PATH = "/sys/class/thermal";

    private String temperatureCpuFile = "";

    private static int iTempCpu = 0;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "CPU_TEMPERATURE";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.cpu_temperature);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        CpuTemperatureText = (TextView) findViewById(com.motorola.motocit.R.id.cpu_temperature);

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        readTemperatureData();
        CpuTemperatureText.setText("CPU Temperature = " + iTempCpu);

        sendStartActivityPassed();
    }

    private void readTemperatureData()
    {
        // get CPU temperature
        // from kernel team, check for tmp108, then for msm_therm, then for
        // xo_therm_pu2
        // bt3: get additional temperature sensor data
        if (temperatureCpuFile.equals(""))
        {
            temperatureCpuFile = locateTmp108Path();
            if (temperatureCpuFile == null)
            {
                temperatureCpuFile = locateAdcPaths();
            }
            if (temperatureCpuFile == null)
            {
                temperatureCpuFile = locateThermalPaths();
            }
        }

        if (temperatureCpuFile != null)
        {
            dbgLog(TAG, "read cpu temperature file=" + temperatureCpuFile, 'i');
            String tempTempStr = getStringValueFromSysfs(temperatureCpuFile);
            if (tempTempStr != null)
            {
                if (tempTempStr.contains("Result"))
                {   // format like this: Result:26 Raw:82fe
                    String[] strLineParts = tempTempStr.trim().split(" ");
                    iTempCpu = Integer.valueOf(strLineParts[0].split(":")[1]);
                }
                else
                {   // format like this: 27437
                    iTempCpu = (Integer.valueOf(tempTempStr)) / 1000;
                }
            }
            else
            {
                iTempCpu = 0;
            }
        }
        else
        {
            dbgLog(TAG, "temperature CPU file is null", 'e');
        }

        dbgLog(TAG, "cpu temperature=" + iTempCpu, 'i');
    }

    // temperature readings
    private String locateTmp108Path()
    {
        int i = 0;
        File file;
        String path = new String();

        File hwmon = new File(HWMON_PATH);
        if(!hwmon.exists()){
            return null;
        }

        int numFiles = hwmon.listFiles().length;

        while (i < numFiles)
        {
            try
            {
                // search /sys/class/hwmon at first
                path = HWMON_PATH + "/hwmon" + String.valueOf(i) + "/";
                file = new File(path + "name");
                if (file.exists() == false)
                {
                    i++;
                    continue;
                }

                String name = getStringValueFromSysfs(path + "name");
                if (name.startsWith("tmp108") == true)
                {
                    return path + "temp1_input";
                }
            }
            catch (Exception e)
            {
                dbgLog(TAG, "locateTmp108Path(): Unable to find tmp108 path\n", 'e');
            }
            i++;
        }

        if (TextUtils.isEmpty(path))
        {
            // if path is empty, search /sys/class/hwmon/device
            i = 0;
            while (i < numFiles)
            {
                try
                {
                    path = HWMON_PATH + "/hwmon" + String.valueOf(i) + "/device/";
                    file = new File(path + "name");
                    if (file.exists() == false)
                    {
                        i++;
                        continue;
                    }

                    String name = getStringValueFromSysfs(path + "name");
                    if (name.startsWith("tmp108") == true)
                    {
                        return path + "temp1_input";
                    }

                }
                catch (Exception e)
                {
                    dbgLog(TAG, "locateTmp108Path(): Unable to find tmp108 path\n", 'e');
                }
                i++;
            }
        }

        if (i >= numFiles)
        {
            dbgLog(TAG, "locateTmp108Path(): Error while searching for tmp108 path\n", 'e');
        }
        return null;
    }

    private String locateAdcPaths()
    {
        int i = 0;
        File file;
        String path = new String();
        String name = new String();
        File hwmon = new File(HWMON_PATH);
        if(!hwmon.exists()){
            return null;
        }

        int numFiles = hwmon.listFiles().length;
        while (i < numFiles)
        {
            try
            {
                path = HWMON_PATH + "/hwmon" + String.valueOf(i) + "/device/";
                file = new File(path + "msm_therm");
                if (file.exists() == true)
                {
                    return (path + "msm_therm");
                }
                else
                {
                    file = new File(path + "xo_therm_pu2");
                    if (file.exists() == true)
                    {
                        return (path + "xo_therm_pu2");
                    }
                }
            }
            finally
            {}
            i++;
        }
        return null;
    }

    private String locateThermalPaths() {
        File thermal = new File(HWMON_PATH);
        if(!thermal.exists()){
            return null;
        }

        int numFiles = thermal.listFiles().length;
        for(int i=0; i < numFiles; i++){
            try {
                String path = THERMAL_PATH + "/thermal_zone" + String.valueOf(i) + "/";
                File file = new File(path + "type");
                if (!file.exists()) {
                    continue;
                }
                String name = getStringValueFromSysfs(path + "type");
                if (name.endsWith("cpu")){ // mtktscpu
                    return path + "temp";
                }
             }
            catch (Exception e)
            {
                dbgLog(TAG, "locateThermalPaths(): Unable to find mtktscpu path\n", 'e');
            }
         }

        return null;
    }

    public String getStringValueFromSysfs(String fromFile)
    {
        BufferedReader in = null;
        try
        {
            in = new BufferedReader(new FileReader(fromFile), 128);
            String line = in.readLine();
            if (line != null)
            {
                return line.trim();
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Error reading from file " + fromFile + " : " + e.getMessage(), 'e');
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                    in = null;
                }
                catch (IOException e)
                {
                    dbgLog(TAG, "Error closing reader for sysfs battery info file: " + e.getMessage(), 'e');
                    in = null;
                }
            }
        }
        return null;
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("GET_TEMPERATURE"))
        {
            List<String> strDataList = new ArrayList<String>();

            readTemperatureData();

            UpdateUiTextView updateUiCpuTemperature = new UpdateUiTextView(CpuTemperatureText, Color.WHITE, "CPU Temperature = " + String.valueOf(iTempCpu));
            runOnUiThread(updateUiCpuTemperature);

            strDataList.add(String.format("CPU_TEMPERATURE=" + iTempCpu));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
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
        strHelpList.add("This function will read the CPU Temperature");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_TEMPERATURE - Returns CPU Temperature in degrees Celsius");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP))
        {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            {
                contentRecord("testresult.txt", "CPU Temperature Test: PASS" + "\r\n", MODE_APPEND);

                logResults(TEST_PASS);
            }
            else
            {
                contentRecord("testresult.txt", "CPU Temperature Test: FAILED" + "\r\n", MODE_APPEND);

                logResults(TEST_FAIL);
            }

            contentRecord("testresult.txt", "CPU Temperature: " + String.valueOf(iTempCpu) + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "CPU Temperature Test: FAILED" + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "CPU Temperature: " + String.valueOf(iTempCpu) + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_FAIL);

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
        contentRecord("testresult.txt", "CPU Temperature Test: PASS" + "\r\n", MODE_APPEND);
        contentRecord("testresult.txt", "CPU Temperature: " + String.valueOf(iTempCpu) + "\r\n\r\n", MODE_APPEND);

        logResults(TEST_PASS);

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

    private void logResults(String passFail)
    {
        List<String> testResultName = new ArrayList<String>();
        List<String> testResultValues = new ArrayList<String>();
        testResultName.add("CPU_TEMPERATURE");

        testResultValues.add(String.valueOf(iTempCpu));

        logTestResults(TAG, passFail, testResultName, testResultValues);
    }
}
