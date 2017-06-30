/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.mmc.MMC;

public class Test_Result extends Test_Base
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Test_Result";
        super.onCreate(savedInstanceState);

        adjustViewDisplayArea(com.motorola.motocit.R.layout.test_result);

        TextView textTestResult = (TextView) findViewById(com.motorola.motocit.R.id.test_result_txt);
        textTestResult.setMovementMethod(ScrollingMovementMethod.getInstance());


        // if Odm device, copy test result file to external sdcard
        if (TestUtils.isOdmDevice())
        {
            MMC sdcard = new MMC();
            String sdcardPath = sdcard.getExternalStorageMountPath();

            if (sdcardPath == null)
            {
                Toast.makeText(this, "EXTERNAL SD CARD IS NOT SUPPORTED!", Toast.LENGTH_SHORT).show();
            }
            else if (searchSDcard(sdcardPath) == false)
            {
                Toast.makeText(this, "EXTERNAL SD CARD IS NOT MOUNTED!", Toast.LENGTH_SHORT).show();
            }
            else
            {
                OutputStream out = null;

                try
                {
                    out = new BufferedOutputStream(new FileOutputStream(sdcardPath + "/testresult.txt"));

                    out.write((contentRead("testresult.txt")).getBytes());

                }
                catch (FileNotFoundException e)
                {
                    e.printStackTrace();

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    try
                    {
                        if (null != out)
                        {
                            out.close();
                            out = null;
                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
        else
        {
            MMC sdcard = new MMC();
            String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();

            Log.e("CQATest", "TEST_RESULT SD CARD PATH: " + sdcardPath);

            if (sdcardPath == null)
            {
                Toast.makeText(this, "EXTERNAL SD CARD IS NOT SUPPORTED!", Toast.LENGTH_SHORT).show();
            }

            else
            {
                OutputStream out = null;

                try
                {

                    out = new BufferedOutputStream(new FileOutputStream(sdcardPath + "/testresult.txt"));

                    out.write((contentRead("testresult.txt")).getBytes());
                }
                catch (FileNotFoundException e)
                {
                    e.printStackTrace();

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                finally
                {
                    try
                    {
                        if (null != out)
                        {
                            out.close();
                            out = null;
                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        String tempText = contentRead("testresult.txt");

        String tempDocInfo = tempText;
        String keyword = "FAILED";
        int keywordIndex = tempDocInfo.indexOf(keyword);

        SpannableStringBuilder style = new SpannableStringBuilder(tempText);
        while (keywordIndex != -1)
        {
            style.setSpan(new BackgroundColorSpan(Color.RED), keywordIndex, keywordIndex + keyword.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int tempkeywordTempIndex = keywordIndex + keyword.length();
            tempDocInfo = tempText.substring(tempkeywordTempIndex, tempText.length());
            keywordIndex = tempDocInfo.indexOf(keyword);
            if (keywordIndex != -1)
            {
                keywordIndex = keywordIndex + tempkeywordTempIndex;
            }
        }

        textTestResult.setText(style);

        textTestResult.setLongClickable(true);

        textTestResult.setOnLongClickListener(new OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                onBackPressed();
                return true;
            }
        });
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

        strHelpList.add("Test Result");
        strHelpList.add("");
        strHelpList.add("This activity brings up the Test Result menu");
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
