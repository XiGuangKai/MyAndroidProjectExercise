/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.headset;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.AudioSystem;
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
import com.motorola.motocit.key.Keys;

public class HeadsetInfo extends Test_Base
{
    private TextView mHeadsetDetectTextView;
    private TextView mHeadsetTypeTextView;
    private TextView mHeadsetAddressTextView;
    private TextView mHeadsetMicTextView;
    private TextView mHeadsetKeyTextView;

    private boolean recordKeys = false;
    private final Lock lockKeyTestList = new ReentrantLock();
    private Vector<String> keyTestList = new Vector<String>();

    private AudioManager mAudioManager;
    private ComponentName mMediaButtonReceiver;

    private final String HEADSET_PLUGGED = "PLUGGED";
    private final String HEADSET_UNPLUGGED = "UNPLUGGED";

    private final String HEADSET_ADDRESS_UNPLUGGED = "No Device";
    private final String HEADSET_TYPE_UNPLUGGED = "No Device";

    private final String HEADSET_HAS_MIC_YES = "YES";
    private final String HEADSET_HAS_MIC_NO = "NO";

    private boolean isHeadsetTypeSupported = false;
    private boolean isHeadsetAddressSupported = false;

    private BroadcastReceiver headsetReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (null == action)
            {
                return;
            }

            dbgLog(TAG, "headsetReceiver Intent Action: " + action, 'i');

            dbgLog(TAG, "headsetReceiver Intent: " + intent.toString(), 'i');
            Bundle extras = intent.getExtras();
            Set<String> extrasSet = extras.keySet();

            for (String s : extrasSet)
            {
                dbgLog(TAG, "headsetReceiver Intent Extras: " + s, 'i');
                String intentValue = intent.getStringExtra(s);
                int intentInt = intent.getIntExtra(s, -999);
                dbgLog(TAG, "headsetReceiver Intent Extra Value: " + intentValue, 'i');
                dbgLog(TAG, "headsetReceiver Intent Extra Int: " + intentInt, 'i');
            }

            if (action.equals(Intent.ACTION_HEADSET_PLUG))
            {
                handleActionHeadsetPlug(context, intent);
            }
            else if (action.equals(Intent.ACTION_MEDIA_BUTTON))
            {
                handleActionMediaButton(context, intent);
            }
        }
    };

    public class MediaButtonReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            if ((null != keyEvent) && (null != mHeadsetKeyTextView))
            {
                try
                {
                    String keyString = Keys.keyCodeToStr(keyEvent.getKeyCode());

                    // see if up or down or multiple action
                    String keyAction = "UNKNOWN";
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN)
                    {
                        keyAction = "DOWN";
                    }
                    else if (keyEvent.getAction() == KeyEvent.ACTION_UP)
                    {
                        keyAction = "UP";
                    }
                    else if (keyEvent.getAction() == KeyEvent.ACTION_MULTIPLE)
                    {
                        keyAction = "MULTIPLE";
                    }

                    String msg = String.format("HEADSET::MediaButtonReceiver Received keycode=%d, keyname=%s, action=%s", keyEvent.getKeyCode(), keyString, keyAction);
                    TestUtils.dbgLog("MediaButtonReceiver", msg, 'v');

                    mHeadsetKeyTextView.setText("Headset Key Press Status: " + keyString + " pressed " + keyAction);
                }
                catch (Exception e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    };

    private void handleActionHeadsetPlug(Context context, Intent intent)
    {
        // get plug state
        String headsetPluggedState = getHeadsetPlugState(intent);
        mHeadsetDetectTextView.setText("Headset Detect Status: " + headsetPluggedState);

        // get type
        String headsetType = getHeadsetType(intent);
        if (headsetType.toLowerCase().contains("unknown") && !isHeadsetTypeSupported)
        {
            mHeadsetTypeTextView.setVisibility(View.INVISIBLE);
        }
        else
        {
            mHeadsetTypeTextView.setVisibility(View.VISIBLE);
            mHeadsetTypeTextView.setText("Headset Type: " + headsetType);
        }

        // get plug address
        String headsetAddress = getHeadsetAddress(intent);
        if (headsetAddress.toLowerCase().contains("unknown") && !isHeadsetAddressSupported)
        {
            mHeadsetAddressTextView.setVisibility(View.INVISIBLE);
        }
        else
        {
            mHeadsetAddressTextView.setVisibility(View.VISIBLE);
            mHeadsetAddressTextView.setText("Headset Address: " + headsetAddress);
        }

        // get has mic or not
        String headsetHasMic = getHeadsetHasMic(intent);
        mHeadsetMicTextView.setText("Headset Has Microphone: " + headsetHasMic);
    }

    private void handleActionMediaButton(Context context, Intent intent)
    {
        KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

        if (null != keyEvent)
        {
            try
            {
                String keyString = Keys.keyCodeToStr(keyEvent.getKeyCode());

                // see if up or down or multiple action
                String keyAction = "UNKNOWN";
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN)
                {
                    keyAction = "DOWN";
                }
                else if (keyEvent.getAction() == KeyEvent.ACTION_UP)
                {
                    keyAction = "UP";
                }
                else if (keyEvent.getAction() == KeyEvent.ACTION_MULTIPLE)
                {
                    keyAction = "MULTIPLE";
                }

                mHeadsetKeyTextView.setText("Headset Key Press Status: " + keyString + "pressed " + keyAction);
                String msg = String.format("HEADSET::handleActionMediaButton Received keycode=%d, keyname=%s, action=%s", keyEvent.getKeyCode(), keyString, keyAction);
                TestUtils.dbgLog("handleActionMediaButton", msg, 'v');

            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private String getHeadsetPlugState(Intent intent)
    {
        int state = intent.getIntExtra("state", 0);

        String pluggedState;

        if (1 == state)
        {
            pluggedState = HEADSET_PLUGGED;
        }
        else
        {
            pluggedState = HEADSET_UNPLUGGED;
        }

        return pluggedState;
    }

    private String getHeadsetType(Intent intent)
    {
        int state = intent.getIntExtra("state", 0);

        String type = "";

        if (1 == state)
        {
            type = intent.getStringExtra("name");
            if (null == type)
            {
                // Try portName, added in Marshmallow
                type = intent.getStringExtra("portName");

                if (null == type)
                {
                    type = "";
                }
                else
                {
                    if (TextUtils.isEmpty(type))
                    {
                        isHeadsetTypeSupported = false;
                    }
                    else
                    {
                        isHeadsetTypeSupported = true;
                    }
                }
            }
            else
            {
                isHeadsetTypeSupported = true;
            }
        }

        if ((null == type) || (TextUtils.isEmpty(type)))
        {
            type = "UNKNOWN";
        }

        return type;
    }

    private String getHeadsetAddress(Intent intent)
    {
        int state = intent.getIntExtra("state", 0);

        String address = "";

        if (1 == state)
        {
            address = intent.getStringExtra("address");
            if (null == address)
            {
                address = "";
            }
            else
            {
                if (TextUtils.isEmpty(address))
                {
                    isHeadsetAddressSupported = false;
                }
                else
                {
                    isHeadsetAddressSupported = true;
                }
            }
        }

        if ((null == address) || (TextUtils.isEmpty(address)))
        {
            address = "UNKNOWN";
        }

        return address;
    }

    private String getHeadsetHasMic(Intent intent)
    {
        int hasMicInt = intent.getIntExtra("microphone", 0);

        // Oddly microphone will be set to 1 if no device is connected
        // so get state and if not connected set mic to NO
        int state = intent.getIntExtra("state", 0);

        String hasMicString;

        if ((1 == hasMicInt) && (1 == state))
        {
            hasMicString = HEADSET_HAS_MIC_YES;
        }
        else
        {
            hasMicString = HEADSET_HAS_MIC_NO;
        }

        return hasMicString;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "HeadsetInfo";
        super.onCreate(savedInstanceState);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiver = new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.headsetinfo);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        // Set initial headset detect text
        mHeadsetDetectTextView = (TextView) findViewById(com.motorola.motocit.R.id.headset_detect_info);
        mHeadsetDetectTextView.setTextColor(Color.WHITE);
        mHeadsetDetectTextView.setText("Headset Detect Status: NA");

        // Set initial headset type text
        mHeadsetTypeTextView = (TextView) findViewById(com.motorola.motocit.R.id.headset_type_info);
        mHeadsetTypeTextView.setTextColor(Color.WHITE);
        mHeadsetTypeTextView.setText("Headset Type: NA");
        mHeadsetTypeTextView.setVisibility(View.INVISIBLE);

        // Set initial headset address text
        mHeadsetAddressTextView = (TextView) findViewById(com.motorola.motocit.R.id.headset_address_info);
        mHeadsetAddressTextView.setTextColor(Color.WHITE);
        mHeadsetAddressTextView.setText("Headset Address: NA");
        mHeadsetAddressTextView.setVisibility(View.INVISIBLE);

        // Set initial headset microphone present text
        mHeadsetMicTextView = (TextView) findViewById(com.motorola.motocit.R.id.headset_mic_info);
        mHeadsetMicTextView.setTextColor(Color.WHITE);
        mHeadsetMicTextView.setText("Headset Has Microphone: NA");

        // Set initial headset key press text
        mHeadsetKeyTextView = (TextView) findViewById(com.motorola.motocit.R.id.headset_key_info);
        mHeadsetKeyTextView.setTextColor(Color.WHITE);
        mHeadsetKeyTextView.setText("Headset Key Press Status: NA");
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        IntentFilter headsetFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        // headsetFilter.addAction(Intent.ACTION_MEDIA_BUTTON);
        // headsetFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        registerReceiver(headsetReceiver, headsetFilter);

        mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiver);

        sendStartActivityPassed();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        this.unregisterReceiver(this.headsetReceiver);

        mAudioManager.unregisterMediaButtonEventReceiver(mMediaButtonReceiver);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        dbgLog(TAG, "onKeyDown() saw " + keyCode, 'i');

        if ((wasActivityStartedByCommServer() == false) && TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && (ev.getRepeatCount() != 0))
            {
                contentRecord("testresult.txt", "HeadsetInfo Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
            else if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) && (ev.getRepeatCount() != 0))
            {
                contentRecord("testresult.txt", "HeadsetInfo Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

                systemExitWrapper(0);
            }
        }

        if ((recordKeys == true) && (keyCode != KeyEvent.KEYCODE_POWER))
        {
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

        try
        {
            String keyString = Keys.keyCodeToStr(keyCode);
            mHeadsetKeyTextView.setText("Headset Key Press Status: " + keyString + " pressed");
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
        }

        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev)
    {
        dbgLog(TAG, "onKeyUp() saws " + keyCode, 'i');

        if ((recordKeys == true) && (keyCode != KeyEvent.KEYCODE_POWER))
        {
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

        try
        {
            String keyString = Keys.keyCodeToStr(keyCode);
            mHeadsetKeyTextView.setText("Headset Key Press Status: " + keyString + " released");
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
        }

        return true;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
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
        else if (strRxCmd.equalsIgnoreCase("GET_HEADSET_STATE"))
        {
            Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_HEADSET_PLUG));

            List<String> strDataList = new ArrayList<String>();

            if (intent != null)
            {
                strDataList.add("PLUG_STATE=" + getHeadsetPlugState(intent));
                strDataList.add("HEADSET_TYPE=" + getHeadsetType(intent));
                strDataList.add("HEADSET_ADDRESS=" + getHeadsetAddress(intent));
                strDataList.add("HEADSET_HAS_MIC=" + getHeadsetHasMic(intent));
            }
            else
            {
                // if intent is null then no headset has been
                // plugged inserted since the phone has started up.
                // Assume the no headset state

                strDataList.add("PLUG_STATE=" + HEADSET_UNPLUGGED);
                strDataList.add("HEADSET_TYPE=" + HEADSET_TYPE_UNPLUGGED);
                strDataList.add("HEADSET_ADDRESS=" + HEADSET_ADDRESS_UNPLUGGED);
                strDataList.add("HEADSET_HAS_MIC=" + HEADSET_HAS_MIC_NO);
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("FAKE_HEADSET_STATE"))
        {
            AudioManager audioManager = (AudioManager) HeadsetInfo.this.getSystemService(Context.AUDIO_SERVICE);

            List<String> strReturnDataList = new ArrayList<String>();

            String headsetName = "Headset";
            String headsetAddress = "";
            int headsetIntent = AudioSystem.DEVICE_OUT_WIRED_HEADSET;
            int headsetState = 0;
            boolean headsetStateDefined = false;

            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("HEADSET_TYPE"))
                    {
                        headsetName = value;
                    }
                    else if (key.equalsIgnoreCase("HEADSET_ADDRESS"))
                    {
                        headsetAddress = value;
                    }
                    else if (key.equalsIgnoreCase("HEADSET_INTENT"))
                    {
                        if (value.equalsIgnoreCase("HEADSET") || value.equalsIgnoreCase("WIRED_HEADSET"))
                        {
                            headsetIntent = AudioSystem.DEVICE_OUT_WIRED_HEADSET;
                        }
                        else if (value.equalsIgnoreCase("HEADPHONE") || value.equalsIgnoreCase("WIRED_HEADPHONE"))
                        {
                            headsetIntent = AudioSystem.DEVICE_OUT_WIRED_HEADPHONE;
                        }
                        else if (value.equalsIgnoreCase("LINE"))
                        {
                            headsetIntent = AudioSystem.DEVICE_OUT_LINE;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN INTENT: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("HEADSET_STATE"))
                    {
                        if (value.equalsIgnoreCase("PLUGGED"))
                        {
                            headsetStateDefined = true;
                            headsetState = 1;
                        }
                        else if (value.equalsIgnoreCase("UNPLUGGED"))
                        {
                            headsetStateDefined = true;
                            headsetState = 0;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN STATE: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }

                }

                if (headsetStateDefined == true)
                {
                    Method setWiredDeviceConnectionStateMethod = null;
                    if (setWiredDeviceConnectionStateMethod == null)
                    {
                        try
                        {
                            setWiredDeviceConnectionStateMethod = AudioManager.class.getMethod("setWiredDeviceConnectionState", new Class[] {
                                    int.class, int.class, String.class });

                            dbgLog(TAG, "setWiredDeviceConnectionState(int, int, String) Method Found", 'i');

                            setWiredDeviceConnectionStateMethod.invoke(audioManager, headsetIntent, headsetState, headsetName);

                            dbgLog(TAG, "setWiredDeviceConnectionState Invoke complete", 'i');
                        }
                        catch (Exception ex)
                        {
                            // method does not exist
                            setWiredDeviceConnectionStateMethod = null;
                        }

                        if (setWiredDeviceConnectionStateMethod == null)
                        {
                            try
                            {
                                setWiredDeviceConnectionStateMethod = AudioManager.class.getMethod("setWiredDeviceConnectionState", new Class[] {
                                        int.class, int.class, String.class, String.class });

                                dbgLog(TAG, "setWiredDeviceConnectionState(int, int, String, String) Method Found", 'i');

                                setWiredDeviceConnectionStateMethod.invoke(audioManager, headsetIntent, headsetState, headsetAddress, headsetName);

                                dbgLog(TAG, "setWiredDeviceConnectionState Invoke complete", 'i');
                            }
                            catch (Exception ex)
                            {
                                // method does not exist
                                dbgLog(TAG, "setWiredDeviceConnectionState Failed", 'e');
                                dbgLog(TAG, ex.toString(), 'e');
                                strReturnDataList.add("setWiredDeviceConnectionState FAILED");
                                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                            }
                        }
                    }
                }
                else
                {
                    strReturnDataList.add("HEADSET_STATE IS UNDEFINED");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
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
        strHelpList.add("This function returns the different SW Versions");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  GET_HEADSET_STATE   - returns the following results:");
        strHelpList.add("     PLUG_STATE      - if headset is plugged in");
        strHelpList.add("     HEADSET_TYPE    - Name of headset as reported by android");
        strHelpList.add("     HEADSET_ADDRESS - Address of headset as reported by android");
        strHelpList.add("     HEADSET_HAS_MIC - YES or NO");
        strHelpList.add("  FAKE_HEADSET_STATE   - takes the following parameters:");
        strHelpList.add("     HEADSET_STATE      - PLUGGED or UNPLUGGED");
        strHelpList.add("     HEADSET_TYPE    - Name of headset as reported by android");
        strHelpList.add("     HEADSET_ADDRESS - Address of headset as reported by android");
        strHelpList.add("     HEADSET_INTENT - HEADSET, WIRED_HEADSET, HEADPHONE, WIRED_HEADPHONE, or LINE");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "HeadsetInfo Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "HeadsetInfo Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

        systemExitWrapper(0);
        return true;
    }
}
