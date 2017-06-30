/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.key;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
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

public class KeyTest extends Test_Base
{
    private TextView keyTestTextView;
    private boolean recordKeys = false;

    private final Lock lockKeyPressedList = new ReentrantLock();
    private final Lock lockKeyReleasedList = new ReentrantLock();
    private final Lock lockKeyTestList = new ReentrantLock();

    private Vector<Integer> keyPressedList = new Vector<Integer>();
    private Vector<Integer> keyReleasedList = new Vector<Integer>();
    private Vector<String> keyTestList = new Vector<String>();
    private HomeReceiver receiverHome = new HomeReceiver();
    private IntentFilter filterHome = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

    private KeysToTest[] keysToTest;
    private boolean KeyTestSettingsFromConfigFileInUse = false;

    boolean powerKeyHold = false;

    @Override
    public void onCreate(Bundle icicle)
    {
        TAG = "KeyTest";
        super.onCreate(icicle);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.key_keytest);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        setText("No Key is pressed");

        // register ScreenOffOnReceiver
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        this.registerReceiver(ScreenOffOnReceiver, intentFilter);
        this.registerReceiver(receiverHome, filterHome);

        KeyTestSettingsFromConfigFileInUse = getKeyTestSettingsFromConfig();
    }

    protected class HomeReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            {
                String reason = intent.getStringExtra("reason");
                if (reason != null)
                {
                    if (reason.equals("homekey"))
                    {
                        try
                        {
                            setText(Keys.keyCodeToStr(KeyEvent.KEYCODE_HOME) + "  Pressed and Released");
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        if (wasActivityStartedByCommServer())
                        {
                            // Add to key to output list
                            if (recordKeys == true)
                            {
                                // update keyPressedList
                                lockKeyPressedList.lock();
                                keyPressedList.add(KeyEvent.KEYCODE_HOME);
                                lockKeyPressedList.unlock();

                                // update keyReleasedList
                                lockKeyReleasedList.lock();
                                keyReleasedList.add(KeyEvent.KEYCODE_HOME);
                                lockKeyReleasedList.unlock();

                                lockKeyTestList.lock();
                                keyTestList.add(String.format("%02X00", KeyEvent.KEYCODE_HOME));
                                keyTestList.add(String.format("%02X01", KeyEvent.KEYCODE_HOME));
                                lockKeyTestList.unlock();
                            }
                        }

                        validateExpectedKeys(KeyEvent.KEYCODE_HOME, "PRESSEDANDRELEASED");

                    }
                    else if (reason.equals("recentapps"))
                    {
                        try
                        {
                            setText(Keys.keyCodeToStr(KeyEvent.KEYCODE_APP_SWITCH) + " Pressed and Released");
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        if (wasActivityStartedByCommServer())
                        {
                            // Add to key to output list
                            if (recordKeys == true)
                            {
                                // update keyPressedList
                                lockKeyPressedList.lock();
                                keyPressedList.add(KeyEvent.KEYCODE_APP_SWITCH);
                                lockKeyPressedList.unlock();

                                // update keyReleasedList
                                lockKeyReleasedList.lock();
                                keyReleasedList.add(KeyEvent.KEYCODE_APP_SWITCH);
                                lockKeyReleasedList.unlock();

                                lockKeyTestList.lock();
                                keyTestList.add(String.format("%02X00", KeyEvent.KEYCODE_APP_SWITCH));
                                keyTestList.add(String.format("%02X01", KeyEvent.KEYCODE_APP_SWITCH));
                                lockKeyTestList.unlock();
                            }
                        }

                        validateExpectedKeys(KeyEvent.KEYCODE_APP_SWITCH, "PRESSEDANDRELEASED");
                    }
                }
            }
        }
    }

    protected BroadcastReceiver ScreenOffOnReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // make klocwork happy
            String action = intent.getAction();
            if (null == action)
            {
                return;
            }

            dbgLog(TAG, "ScreenOffOnReceiver Intent Action: " + action, 'i');

            if (action.equals(Intent.ACTION_SCREEN_OFF))
            {
                dbgLog(TAG, "Screen_off event", 'i');
                // wake up screen
                PowerManager pm;
                pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl;
                wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "My Tag");
                wl.acquire();
                wl.release();

                try
                {
                    setText(Keys.keyCodeToStr(KeyEvent.KEYCODE_POWER) + "  Pressed and Released");
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                if (wasActivityStartedByCommServer())
                {
                    // Add to key to output list
                    if (recordKeys == true)
                    {
                        // update keyPressedList
                        lockKeyPressedList.lock();
                        keyPressedList.add(KeyEvent.KEYCODE_POWER);
                        lockKeyPressedList.unlock();

                        // update keyReleasedList
                        lockKeyReleasedList.lock();
                        keyReleasedList.add(KeyEvent.KEYCODE_POWER);
                        lockKeyReleasedList.unlock();

                        lockKeyTestList.lock();
                        keyTestList.add(String.format("%02X00", KeyEvent.KEYCODE_POWER));
                        keyTestList.add(String.format("%02X01", KeyEvent.KEYCODE_POWER));
                        lockKeyTestList.unlock();
                    }
                }

                validateExpectedKeys(KeyEvent.KEYCODE_POWER, "PRESSEDANDRELEASED");
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        boolean startedByCommServer = wasActivityStartedByCommServer();
        dbgLog(TAG, "On key Down Called", 'i');

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && (ev.getRepeatCount() != 0) && (startedByCommServer == false)
                && (KeyTestSettingsFromConfigFileInUse == false) && TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            contentRecord("testresult.txt", "KEY Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
        else if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) && (ev.getRepeatCount() != 0) && (startedByCommServer == false)
                && (KeyTestSettingsFromConfigFileInUse == false) && TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            contentRecord("testresult.txt", "KEY Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        else if ((keyCode == KeyEvent.KEYCODE_BACK) && (startedByCommServer == false)
                && TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
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

        try
        {
            dbgLog(TAG, "Got Keycode onKeyDown: " + keyCode, 'i');
            if ((keyCode == KeyEvent.KEYCODE_POWER) && (ev.getRepeatCount() != 0))
            {
                powerKeyHold = true;
                setText(Keys.keyCodeToStr(keyCode) + "  Pressed and Hold");
            }
            else
            {
                setText(Keys.keyCodeToStr(keyCode) + "  Pressed");
            }
            if ((recordKeys == true) && (keyCode != KeyEvent.KEYCODE_POWER))
            {
                try
                {
                    lockKeyPressedList.lock();
                    keyPressedList.add(keyCode);
                }
                finally
                {
                    lockKeyPressedList.unlock();
                }

                try
                {
                    lockKeyTestList.lock();
                    keyTestList.add(String.format("%02X00", keyCode));
                }
                finally
                {
                    lockKeyTestList.unlock();
                }
            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Failed to map keycode to string " + e.getLocalizedMessage(), 'e');
        }

        validateExpectedKeys(keyCode, "PRESSED");

        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev)
    {
        dbgLog(TAG, "On key Up Called", 'i');
        try
        {
            dbgLog(TAG, "Got Keycode onKeyUp: " + keyCode, 'i');
            if ((keyCode == KeyEvent.KEYCODE_POWER) && (powerKeyHold == true))
            {
                dbgLog(TAG, "Ignore Power key release event", 'i');
            }
            else
            {
                setText(Keys.keyCodeToStr(keyCode) + "  Released");
            }

            if ((recordKeys == true) && (keyCode != KeyEvent.KEYCODE_POWER))
            {
                try
                {
                    lockKeyReleasedList.lock();
                    keyReleasedList.add(keyCode);
                }
                finally
                {
                    lockKeyReleasedList.unlock();
                }

                try
                {
                    lockKeyTestList.lock();
                    keyTestList.add(String.format("%02X01", keyCode));
                }
                finally
                {
                    lockKeyTestList.unlock();
                }

            }
        }
        catch (Exception e)
        {
            dbgLog(TAG, "Failed to map keycode to string " + e.getLocalizedMessage(), 'e');
        }

        validateExpectedKeys(keyCode, "RELEASED");

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        dbgLog(TAG, "On configuration Called", 'i');
    }

    private void setText(String text)
    {
        keyTestTextView = (TextView) findViewById(com.motorola.motocit.R.id.keytest_1);

        keyTestTextView.setTextColor(Color.GREEN);
        keyTestTextView.setTextSize(20f);
        keyTestTextView.setText(text);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        dbgLog(TAG, "On Resume", 'i');
        // //If the application resumes, then the app switch key was not pressed
        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        dbgLog(TAG, "On Pause Called", 'i');
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        dbgLog(TAG, "On Stop Called", 'i');
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        dbgLog(TAG, "onWindowFocusChanged: ", 'i');
        if ((hasFocus == true) && (powerKeyHold == true))
        {
            powerKeyHold = false;
            try
            {
                setText(Keys.keyCodeToStr(KeyEvent.KEYCODE_POWER) + "  Released");
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Failed to map keycode to string " + e.getLocalizedMessage(), 'e');
            }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        dbgLog(TAG, "On Destroy Called", 'i');

        this.unregisterReceiver(ScreenOffOnReceiver);
        ScreenOffOnReceiver = null;
        this.unregisterReceiver(receiverHome);
        receiverHome = null;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        // Change Output Directory
        if (strRxCmd.equalsIgnoreCase("START_RECORD"))
        {
            recordKeys = true;

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_RECORD"))
        {
            recordKeys = false;

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_KEYS"))
        {
            List<String> strDataList = new ArrayList<String>();

            // add pressed keys to results
            try
            {
                lockKeyPressedList.lock();
                strDataList.add("KEY_PRESSED=" + TextUtils.join(",", keyPressedList));
                keyPressedList.clear();
            }
            finally
            {
                lockKeyPressedList.unlock();
            }

            // add released keys to results
            try
            {
                lockKeyReleasedList.lock();
                strDataList.add("KEY_RELEASED=" + TextUtils.join(",", keyReleasedList));
                keyReleasedList.clear();
            }
            finally
            {
                lockKeyReleasedList.unlock();
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_KEYS_FOR_TEST"))
        {
            List<String> strDataList = new ArrayList<String>();

            // add pressed keys to results
            try
            {
                lockKeyTestList.lock();
                strDataList.add("KEYS=" + TextUtils.join(",", keyTestList));
                keyTestList.clear();
            }
            finally
            {
                lockKeyTestList.unlock();
            }

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
        strHelpList.add("This function will return the state of keys");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("START_RECORD - Begins recording key presses");
        strHelpList.add("  ");
        strHelpList.add("STOP_RECORD - Stops recording key presses");
        strHelpList.add("  ");
        strHelpList.add("GET_KEYS - Get all buffered pressed/released keys. Will clear buffer after returning data");
        strHelpList.add("  ");
        strHelpList
        .add("GET_KEYS_FOR_TEST - Get all buffered pressed/released keys in a format that emulates the KEY_TEST test command format. Will clear buffer after returning data");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        if (KeyTestSettingsFromConfigFileInUse == false)
        {
            contentRecord("testresult.txt", "KEY Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

        return false;
    }

    @Override
    public boolean onSwipeLeft()
    {
        if (KeyTestSettingsFromConfigFileInUse == false)
        {
            contentRecord("testresult.txt", "KEY Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

        return false;
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

    public void validateExpectedKeys(int keyCode, String pressedOrReleased)
    {
        if (KeyTestSettingsFromConfigFileInUse == true)
        {
            dbgLog(TAG, "KeyTestSettingsFromConfigFile =  " + KeyTestSettingsFromConfigFileInUse, 'i');

            boolean expectedKeysCompleted = true;

            int NumberOfKeysToTest = keysToTest.length;
            for (int i = 0; i < NumberOfKeysToTest; i++)
            {
                if (keyCode == keysToTest[i].keycode)
                {
                    if (pressedOrReleased.toUpperCase(Locale.US).contains("PRESSED"))
                    {
                        keysToTest[i].keyPressed = true;
                    }
                    if (pressedOrReleased.toUpperCase(Locale.US).contains("RELEASED"))
                    {
                        keysToTest[i].keyReleased = true;
                    }
                }
                if ((keysToTest[i].keyPressed == false) || (keysToTest[i].keyReleased == false))
                {
                    expectedKeysCompleted = false;
                }
            }
            if (expectedKeysCompleted)
            {
                contentRecord("testresult.txt", "KeysTest:  PASS" + "\r\n\r\n", MODE_APPEND);

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
        }
    }

    public boolean getKeyTestSettingsFromConfig()
    {
        boolean result = false;
        String SequenceFileInUse = TestUtils.getSequenceFileInUse();
        File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
        String config_file = null;

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else if (file_system_sdcard.exists())
        {
            config_file = file_system_sdcard.toString();
        }
        else
        {
            dbgLog(TAG, "!! CANN'T FIND KEYS PARAMETERS IN CONFIG FILE", 'd');
        }

        if ((config_file != null) && (SequenceFileInUse != null))
        {
            try
            {
                BufferedReader breader = new BufferedReader(new FileReader(config_file));
                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    if (line.toUpperCase().contains("<KEYTEST_SETTINGS>") == true)
                    {
                        result = true;
                        break;
                    }
                }

                if (null != line)
                {
                    dbgLog(TAG, "Settings: " + line, 'd');
                    String[] fields = line.split(",");
                    for (String field : fields)
                    {
                        if (field.contains("KEYS_TO_TEST"))
                        {
                            String[] tokens = field.split("=");
                            String[] keys = tokens[1].split(":");
                            int NumberOfKeysToTest = keys.length;
                            dbgLog(TAG, "Number Of Keys = " + NumberOfKeysToTest, 'd');
                            keysToTest = new KeysToTest[NumberOfKeysToTest];

                            for (int i = 0; i < NumberOfKeysToTest; i++)
                            {
                                keysToTest[i] = new KeysToTest();
                                keysToTest[i].keycode = Integer.parseInt(keys[i]);
                            }
                        }
                    }
                }

                breader.close();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "!!! Some exception in parsing KeyTest settings=" + e.toString(), 'd');
            }
        }

        return result;
    }

    private class KeysToTest
    {
        public int keycode;
        public boolean keyPressed;
        public boolean keyReleased;

        KeysToTest()
        {
            keycode = 0;
            keyPressed = false;
            keyReleased = false;
        }
    }
}
