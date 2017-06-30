/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class DisplayFile extends Test_Base {
    private ImageView displayImageView = null;
    private String imageFileName = null;
    protected boolean bInvalidateViewOn = false;
    private boolean isTestPass = false;
    private static long DISPLAY_TEST_TIMEOUT_MSECS = 5000;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "TestPattern_DisplayFile";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.displayfile);

        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        displayImageView = (ImageView) findViewById(com.motorola.motocit.R.id.imagedisplay);

    }

    @Override
    public void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private void displayImageFile(String imageFileName)
    {
        float start_time = System.currentTimeMillis();

        final Bitmap bitmap = getLoacalBitmap("/mnt/sdcard/" + imageFileName);

        float end_time = System.currentTimeMillis();
        dbgLog(TAG, "time consume:" + (end_time - start_time), 'i');

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (bitmap != null)
                {
                    displayImageView.setImageBitmap(bitmap);
                    InvalidateWindow invalidateWindow = new InvalidateWindow();
                    runOnUiThread(invalidateWindow);
                    isTestPass = true;
                }
                else
                {
                    dbgLog(TAG, "image file is NULL", 'e');
                    isTestPass = false;
                }
            }
        });
    }

    private static Bitmap getLoacalBitmap(String url)
    {
        try
        {
            FileInputStream fis = new FileInputStream(url);
            return BitmapFactory.decodeStream(fis);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private class InvalidateWindow implements Runnable {
        @Override
        public void run()
        {
            getWindow().getDecorView().invalidate();
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
        // Hide soft keys on ice cream sandwich
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        {
            this.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        if (action == MotionEvent.ACTION_UP)
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

            contentRecord("testresult.txt", "Display Pattern - DisplayImageFile:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Display Pattern - DisplayImageFile:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("DisplayImageFile"))
        {

            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (int i = 0; i < strRxCmdDataList.size(); i++)
                {
                    if (strRxCmdDataList.get(i).toUpperCase().contains("FILENAME"))
                    {
                        List<String> strDataList = new ArrayList<String>();
                        imageFileName = strRxCmdDataList.get(i).substring(strRxCmdDataList.get(i).indexOf("=") + 1);
                        dbgLog(TAG, "image file name = " + imageFileName, 'i');

                        isTestPass = false;
                        displayImageFile(imageFileName);

                        // set display image timeout 10s
                        long startTime = System.currentTimeMillis();
                        while (!isTestPass)
                        {
                            dbgLog(TAG, "Displaying image", 'v');

                            if ((System.currentTimeMillis() - startTime) > DISPLAY_TEST_TIMEOUT_MSECS)
                            {
                                dbgLog(TAG, "Time out to display image", 'e');
                                isTestPass = false;
                                break;
                            }

                            try
                            {
                                Thread.sleep(50);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                                isTestPass = false;
                            }
                        }

                        if (isTestPass)
                        {
                            strDataList.add("DISPLAY_IMAGE_RESULT=PASS");
                        }
                        else
                        {
                            strDataList.add("DISPLAY_IMAGE_RESULT=FAILED");
                        }

                        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                        sendInfoPacketToCommServer(infoPacket);

                        // Generate an exception to send data back to CommServer
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
                // Generate an exception to send FAIL result and mesg back to
                // CommServer
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

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
        strHelpList.add("This function displays the image file");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("DisplayImageFile - Display the image file which stored in device folder");
        strHelpList.add("  ");
        strHelpList.add("  FILENAME - The file name which would be displayed.");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Display Pattern - DisplayImageFile:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Display Pattern - DisplayImageFile:  PASS" + "\r\n\r\n", MODE_APPEND);

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
