/*
 * Copyright (c) 2013 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.personalization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class Personalization extends Test_Base
{
    private ListView          lv;
    private ArrayList<String> list = new ArrayList<String>();
    private static final String PS_DIR    = "/customize";

    private static final String JSON_FILE = "personalization.json";

    private int wallpaper_num = 0;
    private int ringtones_num = 0;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Personalization";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.personalization);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        lv = (ListView)findViewById(com.motorola.motocit.R.id.mPsList);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                com.motorola.motocit.R.layout.personalization_item,
                com.motorola.motocit.R.id.PsInfo,
                getPsData());
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(itemclick);

    }

    OnItemClickListener itemclick = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            String select_file = null;
            String launch_file = null;
            String file_type;

            try
            {
                String SelectedItem = parent.getItemAtPosition(position).toString();

                dbgLog(TAG, "Item selected:" + SelectedItem, 'd');

                if (SelectedItem.contains("."))
                {
                    /* retrieve filename from selected line */
                    if (SelectedItem.contains("default") == true)
                    {
                        select_file = SelectedItem.substring(SelectedItem.indexOf(":") + 1);
                    }
                    else
                    {
                        select_file = SelectedItem.substring(SelectedItem.indexOf(")") + 2);
                    }

                    /* check if full path */
                    if (select_file.startsWith("/") == false)
                    {
                        launch_file = String.format("%s/%s", PS_DIR, select_file);
                    }
                    else
                    {
                        launch_file = select_file;
                    }

                    dbgLog(TAG, "selected file:" + select_file, 'd');
                    dbgLog(TAG, "launch file:" + launch_file, 'd');

                    file_type = select_file.substring(select_file.indexOf(".") + 1);
                    dbgLog(TAG, "file type:" + file_type, 'd');

                    ps_playfile(launch_file, file_type);
                }

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    };

    private void ps_playfile(String filename, String filetype)
    {
        File   file   = new File(filename);
        Intent intent = new Intent();
        String type   = null;

        if (filetype.equalsIgnoreCase("mp3"))
        {
            type = "audio/*";
        }
        else if (filetype.equalsIgnoreCase("jpg"))
        {
            type = "image/*";
        }
        else
        {
            dbgLog(TAG, "unsupported file type:", 'e');
        }


        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), type);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }


    /*parse json file and extract entries of wallpaper,ringstone,pp. */
    private ArrayList<String>   getPsData()
    {
        String       my_jsonfile  = null;
        StringBuffer stringBuffer = new StringBuffer();
        String       line         = null;

        dbgLog(TAG, "getPsData ....", 'd');

        try
        {
            my_jsonfile = String.format("%s/%s", PS_DIR, JSON_FILE);
            BufferedReader br = new BufferedReader(new FileReader(new File(my_jsonfile)));

            while ((line = br.readLine()) != null)
            {
                stringBuffer.append(line);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        dbgLog(TAG, "Retrieve data from json file ...", 'd');
        try
        {
            JSONObject jsonObject = new JSONObject(stringBuffer.toString());

            if (jsonObject.isNull("personalization") == true)
            {
                list.add("personalization: no any info");
                dbgLog(TAG, "personalization: no any info", 'd');
            }
            else
            {
                JSONObject personalization = jsonObject.getJSONObject("personalization");

                if (personalization.isNull("pp") != true)
                {
                    /* retrieve pp info */
                    JSONObject pp = personalization.getJSONObject("pp");

                    get_pp_info(pp);
                }
                else
                {
                    list.add("pp: no info");
                    dbgLog(TAG, "pp: no info", 'd');
                }

                if (personalization.isNull("dp") != true)
                {
                    JSONObject dp = personalization.getJSONObject("dp");

                    /* retrieve wallpaper info */
                    get_wallpaper_info(dp);

                    /* retrieve ringtones info */
                    get_ringtones_info(dp);

                }
                else
                {
                    list.add("dp: no info");
                    dbgLog(TAG, "dp: no info", 'd');
                }
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();

        }
        return list;
    }

    private void get_ringtones_info(JSONObject dp)
    {
        String dp_default = null;
        String file       = null;

        /* retrieve ringtones info */
        try
        {
            if (dp.isNull("ringtones") != true)
            {
                JSONArray ringtones = dp.getJSONArray("ringtones");

                list.add("Ringtones:");
                for (int j = 0; j < ringtones.length(); j++)
                {
                    if (ringtones.getJSONObject(j).isNull("default") != true)
                    {
                        dp_default = ringtones.getJSONObject(j).getString("default");
                    }
                    if (ringtones.getJSONObject(j).isNull("file") != true)
                    {
                        file = ringtones.getJSONObject(j).getString("file");
                    }

                    if ((file != null) && (file.startsWith("/") == false))
                    {
                        file = String.format("%s/%s", PS_DIR, file);
                    }
                    if ((dp_default != null) && dp_default.equalsIgnoreCase("true"))
                    {
                        list.add((j + 1) + ") " + "default:" + file);
                        ringtones_num++;
                    }
                    else
                    {
                        list.add((j + 1) + ") " + file);
                        ringtones_num++;
                    }

                    dbgLog(TAG, "ringtones info", 'd');
                    dbgLog(TAG, dp_default, 'd');
                    dbgLog(TAG, file, 'd');
                }
            }
            else
            {
                list.add("Ringtones: no info");
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
    }

    private void get_wallpaper_info(JSONObject dp)
    {
        String dp_default = null;
        String file       = null;

        try
        {
            if (dp.isNull("wallpaper") != true)
            {
                /* retrieve wallpaper info */
                JSONArray wallpaper = dp.getJSONArray("wallpaper");

                list.add("WallPaper:");

                for (int i = 0; i < wallpaper.length(); i++)
                {
                    if (wallpaper.getJSONObject(i).isNull("default") != true)
                    {
                        dp_default = wallpaper.getJSONObject(i).getString("default");
                    }
                    if (wallpaper.getJSONObject(i).isNull("file") != true)
                    {
                        file = wallpaper.getJSONObject(i).getString("file");
                    }

                    if ((file != null) && (file.startsWith("/") == false))
                    {
                        file = String.format("%s/%s", PS_DIR, file);
                    }

                    if ((dp_default != null) && (dp_default.equalsIgnoreCase("true")))
                    {
                        list.add((i + 1) + ") " + "default:" + file);
                        wallpaper_num++;
                    }
                    else
                    {
                        list.add((i + 1) + ") " + file);
                        wallpaper_num++;
                    }

                    dbgLog(TAG, "wallpaper info", 'd');
                    dbgLog(TAG, dp_default, 'd');
                    dbgLog(TAG, file, 'd');
                }
            }
            else
            {
                list.add("Wallpaper: no info");
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

    }

    private void get_pp_info(JSONObject pp)
    {
        String frontClr  = null;
        String backClr   = null;
        String backEgvn  = null;
        String egvnLine  = null;
        String egvnGreet = null;

        list.add("Physical personalization:");
        try
        {
            if (pp.isNull("frontClr") != true)
            {
                frontClr = pp.getString("frontClr");
            }
            list.add(" frontClr:" + " " + frontClr);

            if (pp.isNull("backClr") != true)
            {
                backClr = pp.getString("backClr");
            }
            list.add(" backClr:" + " " + backClr);

            if (pp.isNull("backEgvn") != true)
            {
                backEgvn = pp.getString("backEgvn");
            }
            list.add(" backEgvn:" + " " + backEgvn);

            if (pp.isNull("egvnLine") != true)
            {
                egvnLine = pp.getString(egvnLine);
            }
            list.add(" egvnLine:" + " " + egvnLine);

            if (pp.isNull("egvnGreet") != true)
            {
                egvnGreet = pp.getString("egvnGreet");
            }
            list.add(" egvnGreet:" + " " + egvnGreet);

            dbgLog(TAG, "frontClr: " + frontClr, 'd');
            dbgLog(TAG, "backClr: " + backClr, 'd');
            dbgLog(TAG, "backEgvn: " + backEgvn, 'd');
            dbgLog(TAG, "egvnLine: " + egvnLine, 'd');
            dbgLog(TAG, "egvnGreet: " + egvnGreet, 'd');

        }
        catch (JSONException e)
        {
            e.printStackTrace();

        }

    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        /* When running from CommServer normally ignore KeyDown event */
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Personalization Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Personalization Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private ArrayList<String>   RetrieveFilebyIndex(int start, int loop, boolean IfVerifyExistense, String type)
    {
        String file = null;
        String line = null;

        ArrayList<String> subList = new ArrayList<String>();

        if (loop > 0)
        {

            for (int i = start; i < (start + loop); i++)
            {
                line = list.get(i);
                if (line.contains("default") == true)
                {
                    file = line.substring(line.indexOf(":") + 1);
                }
                else
                {
                    file = line.substring(line.indexOf(")") + 2);
                }

                /* add full path */
                if ((file.startsWith("/") == false))
                {
                    file = String.format("%s/%s", PS_DIR, file);
                }
                if (IfVerifyExistense == false)
                {
                    subList.add(file);
                }
                else /* only if file doesn't exist, add file to list */
                {
                    File new_file = new File(file);

                    if (new_file.exists() == false)
                    {
                        dbgLog(TAG, "file:" + file + "doesn't exist", 'd');
                        subList.add(file);
                    }
                }
                dbgLog(TAG, "file:" + file, 'd');
            }
        }
        /*
    else if(
    {
        subList.add(String.format("No " + type + " file info"));
    }
         */
        return subList;
    }


    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        int wp_start = -1;
        int rt_start = -1;

        if (wallpaper_num > 0)
        {
            wp_start = list.indexOf("WallPaper:") + 1;
        }
        if (ringtones_num > 0)
        {
            rt_start = list.indexOf("Ringtones:") + 1;
        }
        dbgLog(TAG, "wp_start: " + wp_start + " rt_start: " + rt_start, 'd');

        /* dbgLog(TAG, "strRxCmdDataList.size() = " + strRxCmdDataList.size(), 'd'); */
        /* dbgLog(TAG, "strRxCmdDataList.get(0)" + strRxCmdDataList.get(0), 'd'); */

        if (strRxCmd.equalsIgnoreCase("GET_FILE_INFO"))
        {
            dbgLog(TAG, "GET_FILE_INFO", 'd');
            if (strRxCmdDataList.size() < 1)
            {
                String statusMsg = "Missing GET FILE INFO input parameter";

                dbgLog(TAG, statusMsg, 'd');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Missing GET FILE INFO input parameter");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                if (strRxCmdDataList.get(0).equalsIgnoreCase("ALL"))
                {
                    dbgLog(TAG, "Get ALL file", 'd');
                    List<String> strDataList = new ArrayList<String>();

                    strDataList.addAll(RetrieveFilebyIndex(wp_start, wallpaper_num, false, "WALLPAPER"));

                    strDataList.addAll(RetrieveFilebyIndex(rt_start, ringtones_num, false, "RINGTONE"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("WALLPAPER"))
                {
                    dbgLog(TAG, "Get WALLPAPER file", 'd');
                    List<String> strDataList = new ArrayList<String>();
                    strDataList.addAll(RetrieveFilebyIndex(wp_start, wallpaper_num, false, "WALLPAPER"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("RINGTONE"))
                {
                    dbgLog(TAG, "Get RINGTONE file", 'd');
                    List<String> strDataList = new ArrayList<String>();

                    strDataList.addAll(RetrieveFilebyIndex(rt_start, ringtones_num, false, "RINGTONE"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("INVALID GET FILE INFO input parameter");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("VERIFY_FILE_EXISTENSE"))
        {
            dbgLog(TAG, "VERIFY_FILE_EXISTENSE", 'd');
            if (strRxCmdDataList.size() < 1)
            {
                String statusMsg = "Missing VERIFY_FILE_EXISTENSE input parameter";

                dbgLog(TAG, statusMsg, 'd');

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Missing VERIFY_FILE_EXISTENSE input parameter");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                if (strRxCmdDataList.get(0).equalsIgnoreCase("ALL"))
                {
                    dbgLog(TAG, "Verify ALL file", 'd');
                    List<String> strDataList = new ArrayList<String>();

                    strDataList.addAll(RetrieveFilebyIndex(wp_start, wallpaper_num, true, "WALLPAPER"));

                    strDataList.addAll(RetrieveFilebyIndex(rt_start, ringtones_num, true, "RINGTONE"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("WALLPAPER"))
                {
                    dbgLog(TAG, "Verify WALLPAPER file", 'd');
                    List<String> strDataList = new ArrayList<String>();
                    strDataList.addAll(RetrieveFilebyIndex(wp_start, wallpaper_num, true, "WALLPAPER"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                else if (strRxCmdDataList.get(0).equalsIgnoreCase("RINGTONE"))
                {
                    dbgLog(TAG, "Verify RINGTONE file", 'd');
                    List<String> strDataList = new ArrayList<String>();

                    strDataList.addAll(RetrieveFilebyIndex(rt_start, ringtones_num, true, "RINGTONE"));

                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else if (strRxCmdDataList.get(0).contains("FILENAME") && (strRxCmdDataList.size() > 1))
                {

                    List<String> strDataList = new ArrayList<String>();

                    StringBuffer file_sb  = new StringBuffer(strRxCmdDataList.get(1));

                    for(int i=2; i< strRxCmdDataList.size(); i++)
                    {
                        file_sb.append(" ");
                        file_sb.append(strRxCmdDataList.get(i));

                    }

                    dbgLog(TAG, "Verify specified file existence:" + file_sb.toString(), 'd');
                    File new_file = new File(file_sb.toString());

                    if (new_file.exists() == true)
                    {
                        strDataList.add("ture");
                    }
                    else
                    {
                        strDataList.add("false");
                    }
                    CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);

                    sendInfoPacketToCommServer(infoPacket);

                    /* Generate an exception to send data back to CommServer */
                    List<String> strReturnDataList = new ArrayList<String>();
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("INVALID VERIFY FILE EXISTENSE parameter");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("DIGITAL_APK_CLASS_NAME"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                String strDigitalAPK = strRxCmdDataList.get(0);
                List<String> strDataList = new ArrayList<String>();

                dbgLog(TAG,"Digital Personalization APK is  " + strDigitalAPK, 'd');

                PackageManager pm = getPackageManager();
                ComponentName name = new ComponentName("com.motorola.digitalpersonalization", strDigitalAPK);

                dbgLog(TAG, "Component Name is " + name.toString(), 'd');


                int state = pm.getComponentEnabledSetting(name);

                if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                {
                    strDataList.add("disabled");
                }
                else if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                {
                    strDataList.add("enabled");
                }

                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);

                /* Generate an exception to send data back to CommServer */
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        else
        {
            /* Generate an exception to send FAIL result and mesg back to */
            /* CommServer */
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
        strHelpList.add("This function retrieve personalization info");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("GET_FILE_INFO ALL - Get a list of all filenames with absolute path,including Wallpaper,Ringtones.");
        strHelpList.add("  ");
        strHelpList.add("GET_FILE_INFO WALlPAPER - Get a list of WALLPAPER filenames with absolute path.");
        strHelpList.add("  ");
        strHelpList.add("GET_FILE_INFO RINGTONE - Get a list of RINGTONE filenames with absolute path.");
        strHelpList.add("  ");
        strHelpList.add("VERIFY_FILE_EXISTENSE ALL - Verify if all files really exists under file system.");
        strHelpList.add("  ");
        strHelpList.add("VERIFY_FILE_EXISTENSE WALlPAPER - Verify if all WALlPAPER files really exists under file system.");
        strHelpList.add("  ");
        strHelpList.add("VERIFY_FILE_EXISTENSE RINGTONE - Verify if all RINGTONE files really exists under file system.");
        strHelpList.add("  ");
        strHelpList.add("VERIFY_FILE_EXISTENSE filename -  Verify if the specified file really exists under file system.");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);

        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Personalization Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Personalization Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
}
