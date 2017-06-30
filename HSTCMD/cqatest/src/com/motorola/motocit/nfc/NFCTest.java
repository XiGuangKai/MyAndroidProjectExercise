/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.nfc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.INfcAdapterExtras;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

import com.nxp.nfc.NxpNfcAdapter;
import com.nxp.nfc.INxpNfcAdapterExtras;

public class NFCTest extends Test_Base
{
    private TextView nfcTagStatusTextView = null;
    private TextView nfcDetectedTagTextView = null;
    private TextView nfcMaxSizeTextView = null;
    private TextView nfcTagTypeTextView = null;
    private TextView nfcReadWriteTextView = null;
    private TextView nfcTnfTextView = null;

    private EditText mWriteTagEditText = null;

    private CheckBox mReadWriteCheckBox = null;
    private Boolean mReadWriteCheck = false;

    private NfcAdapter mNfcAdapter;
    private boolean mSawNFCIntent = false;
    private boolean isEnableForegroundDispatchSuccess = true;
    private boolean isDisableForegroundDispatchSuccess = true;

    private PendingIntent mNfcPendingIntent;

    private Boolean mOriginalNFCState;

    private Intent lastNfcIntent = null;

    private final Lock mNfcTagLock = new ReentrantLock();

    private final int STATE_OFF = 1;
    private final int STATE_TURNING_ON = 2;
    private final int STATE_ON = 3;
    private final int STATE_TURNING_OFF = 4;

    // There are two variants of the NfcAdapter.disable() function.
    // One that takes no arguments and one that takes a boolean.
    // main-dev-ics and main-dev-ics-qc don't take an argument.
    // While main-dev-ics-intel-bring-up takes a boolean argument
    private static volatile Method mNfcAdapterDisableNoArg = null;
    private static volatile Method mNfcAdapterDisableBoolArg = null;

    private static volatile Method mNfcAdapterSelectDefaultSecureElement = null;
    private static volatile Method mNfcAdapterSetDefaultSecureElementState = null;
    private static volatile Method mNfcAdapterGetDefaultSelectedSecureElement = null;

    private static Object mNfcSwpSwitchInstance = null;
    private static volatile Method mNfcSwpSwitchSetSWPSIM = null;
    private static volatile Method mNfcSwpSwitchGetSWPSIMStatus = null;

    private static ClassLoader mClassLoader = null;

    static
    {
        initializeNfcAdapterDisableMethods();
    }

    private class ParsedTagInfo
    {
        public String tagType = "NULL";
        public NdefMessage ndefMessage = null;
        public boolean isWritable = false;
        public int maxSize = -1;
        public String tnfType = "UNKNOWN";
        public short tnfTypeShort = -1;
        public String payloadData = "NULL";
        public Ndef ndef = null;
    }

    // Runnable to call enableForegroundDispatch on UI thread
    private class EnableForegroundDispatch implements Runnable
    {
        @Override
        public void run()
        {
            if (mNfcAdapter != null)
            {
                dbgLog(TAG, "Runnable() enableForegroundDispatch", 'i');
                isEnableForegroundDispatchSuccess = true;

                try
                {
                    mNfcAdapter.enableForegroundDispatch(NFCTest.this, mNfcPendingIntent, null, null);
                }
                catch (IllegalStateException e)
                {
                    dbgLog(TAG, "activity is not in resumed", 'e');
                    isEnableForegroundDispatchSuccess = false;
                }

                // notify calling thread
                synchronized (this)
                {
                    this.notify();
                }
            }
        }
    }

    // Runnable to call disableForegroundDispatch on UI thread
    private class DisableForegroundDispatch implements Runnable
    {
        @Override
        public void run()
        {
            if (mNfcAdapter != null)
            {
                dbgLog(TAG, "Runnable() disableForegroundDispatch", 'i');
                isDisableForegroundDispatchSuccess = true;

                try
                {
                    mNfcAdapter.disableForegroundDispatch(NFCTest.this);
                }
                catch (IllegalStateException e)
                {
                    dbgLog(TAG, "activity has already been paused", 'e');
                    isDisableForegroundDispatchSuccess = false;
                }

                // notify calling thread
                synchronized (this)
                {
                    this.notify();
                }
            }
        }
    }

    // function to init NfcAdapter Method variables
    private static void initializeNfcAdapterDisableMethods()
    {
        // try to get NfcAdapter.disable()
        try
        {
            if (mNfcAdapterDisableNoArg == null)
            {
                mNfcAdapterDisableNoArg = NfcAdapter.class.getMethod("disable");
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }

        // try to get NfcAdapter.disable(Boolean )
        try
        {
            if (mNfcAdapterDisableBoolArg == null)
            {
                mNfcAdapterDisableBoolArg = NfcAdapter.class.getMethod("disable", new Class[]
                        { boolean.class });
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }
    }

    // function to init NfcAdapter Method variables
    private static void initializeNfcAdapterSecureElementMethods()
    {

        // try to get NfcAdapter.selectDefaultSecureElement( String )
        try
        {
            if (mNfcAdapterSelectDefaultSecureElement == null)
            {
                mNfcAdapterSelectDefaultSecureElement = NfcAdapter.class.getMethod("selectDefaultSecureElement", new Class[] { String.class });
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }

        // try to get NfcAdapter.setDefaultSecureElementState( boolean )
        try
        {
            if (mNfcAdapterSetDefaultSecureElementState == null)
            {
                mNfcAdapterSetDefaultSecureElementState = NfcAdapter.class.getMethod("setDefaultSecureElementState", new Class[] { boolean.class });
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }

        // try to get NfcAdapter.getDefaultSelectedSecureElement( )
        try
        {
            if (mNfcAdapterGetDefaultSelectedSecureElement == null)
            {
                mNfcAdapterGetDefaultSelectedSecureElement = NfcAdapter.class.getMethod("getDefaultSelectedSecureElement");
            }
        }
        catch (NoSuchMethodException nsme)
        {
            // method does not exist
        }

    }

    // function to init NfcSwpSwitch Method variables
    private static void initializeNfcSwpSwitchMethods()
    {

        Class<?> classNfcSwpSwitch = null;
        try
        {
            classNfcSwpSwitch = mClassLoader.loadClass("android.nfc.NfcSwpSwitch");
            mNfcSwpSwitchInstance = classNfcSwpSwitch.newInstance();
        }
        catch (Exception e)
        {

        }

        if (classNfcSwpSwitch != null)
        {
            // try to get NfcSwpSwitch.setSWPSIM( Long )
            try
            {
                if (mNfcSwpSwitchSetSWPSIM == null)
                {
                    mNfcSwpSwitchSetSWPSIM = classNfcSwpSwitch.getMethod("setSWPSIM", new Class[] { long.class });
                }
            }
            catch (NoSuchMethodException nsme)
            {
                // method does not exist
            }

            // try to get NfcSwpSwitch.getSWPSIMStatus( )
            try
            {
                if (mNfcSwpSwitchGetSWPSIMStatus == null)
                {
                    mNfcSwpSwitchGetSWPSIMStatus = classNfcSwpSwitch.getMethod("getSWPSIMStatus");
                }
            }
            catch (NoSuchMethodException nsme)
            {
                // method does not exist
            }
        }
    }

    // Wrapper function to disable NfcAdapter.
    // It will first try to call the disable without any arg
    // and then it will try to call the disable with one boolean arg
    private boolean nfcAdapterDisable() throws CmdFailException
    {
        Object retValue;
        try
        {
            mNfcTagLock.lock();
            if (null != mNfcAdapterDisableNoArg)
            {
                try
                {
                    retValue = mNfcAdapterDisableNoArg.invoke(mNfcAdapter);
                }
                catch (Exception e)
                {
                    // throw fail exception
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to invoke NfcAdapter.disable(). %s", e.getMessage()));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else if (null != mNfcAdapterDisableBoolArg)
            {
                try
                {
                    retValue = mNfcAdapterDisableBoolArg.invoke(mNfcAdapter, true);
                }
                catch (Exception e)
                {
                    dbgLog(TAG, "nfcAdapterDisable() mNfcAdapterDisableBoolArg exception: " + e.getMessage(), 'i');

                    // throw fail exception
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(String.format("Failed to invoke NfcAdapter.disable(Boolean). %s", e.getMessage()));
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }
            else
            {
                // throw fail exception
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Could not locate function to disable NFC Adapter"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        finally
        {
            lastNfcIntent = null;
            mNfcTagLock.unlock();
        }

        return (Boolean) retValue;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "NFC_Test";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.nfc);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        nfcDetectedTagTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_detected_tag);
        nfcMaxSizeTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_max_size);
        nfcTagTypeTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_tag_type);
        nfcReadWriteTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_read_write);
        nfcTagStatusTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_tag_status);
        nfcTnfTextView = (TextView) findViewById(com.motorola.motocit.R.id.nfc_tnf);

        mWriteTagEditText = (EditText) findViewById(com.motorola.motocit.R.id.nfc_write_data);

        mReadWriteCheckBox = (CheckBox) findViewById(com.motorola.motocit.R.id.read_write_tag_checkbox);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(NFCTest.this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        if(mNfcAdapter != null)
        {
            mOriginalNFCState = mNfcAdapter.isEnabled();

            // Use Reflection to get SWP NFC Classes
            mClassLoader = this.getClassLoader();
            initializeNfcAdapterSecureElementMethods();

            // Use reflection to get SWP SIM Switch Classes
            initializeNfcSwpSwitchMethods();

            // only start the nfc adapter if UI start this activity
            if (wasActivityStartedByCommServer() == false)
            {
                if(!mNfcAdapter.isEnabled())
                {
                    try
                    {
                        mNfcAdapter.enable();
                    }
                    catch(Exception e)
                    {
                        Toast.makeText(getApplicationContext(), "Please activate NFC and press Back to return to the application!", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                }
            }
        }
        else
        {
            nfcTagStatusTextView.setText("NFC NOT SUPPORTED ON THIS DEVICE!");
        }

        mReadWriteCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                mReadWriteCheck = mReadWriteCheckBox.isChecked();
                if(mReadWriteCheck == true)
                {
                    mReadWriteCheckBox.setText("WRITE TAG");
                }
                else
                {
                    mReadWriteCheckBox.setText("READ TAG");
                }
            }
        });
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

            contentRecord("testresult.txt", "NFC Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "NFC Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (mSawNFCIntent == false)
        {
            sendStartActivityPassed();
        }

        if(mNfcAdapter != null)
        {
            dbgLog(TAG, "onResume() enableForegroundDispatch", 'i');
            mNfcAdapter.enableForegroundDispatch(NFCTest.this, mNfcPendingIntent, null, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        dbgLog(TAG, "onNewIntent()", 'i');

        Parcelable[] rawMsgs;

        String action = intent.getAction();
        if ((action != null)
                && (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED) || action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)
                        || action.equals(NfcAdapter.ACTION_TECH_DISCOVERED)))
        {
            try
            {
                mNfcTagLock.lock();

                // save intent for later
                lastNfcIntent = intent;
                dbgLog(TAG, "onNewIntent() save NDEF_DISCOVERED intent for later", 'i');

                mSawNFCIntent = true;

                nfcTagStatusTextView.setText("TAG FOUND!");

                mWriteTagEditText.setVisibility(View.VISIBLE);

                mReadWriteCheckBox.setVisibility(View.VISIBLE);

                rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

                Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                // keep klocwork happy
                if (null == detectedTag)
                {
                    return;
                }

                nfcDetectedTagTextView.setText(detectedTag.toString());

                Ndef ndef = Ndef.get(detectedTag);

                try
                {
                    if (ndef != null)
                    {
                        ndef.connect();

                        if (!ndef.isWritable())
                        {
                            nfcReadWriteTextView.setText("READ/WRITE = READ");
                        }
                        else
                        {
                            nfcReadWriteTextView.setText("READ/WRITE = WRITE");
                        }

                        nfcMaxSizeTextView.setText("Max Size = " + ndef.getMaxSize());
                        nfcTagTypeTextView.setText("TAG Type = " + ndef.getType());

                    }
                    else
                    {
                        // Card may need to be formatted
                        NdefFormatable ndefFormatable = NdefFormatable.get(detectedTag);
                        if (ndefFormatable != null)
                        {
                            ndefFormatable.connect();

                            String dataType = "T"; // Data Type 'T' for plain Text
                            // using RTD format
                            String dataToWrite = "en" + "BLANK CARD"; // en = English
                            byte[] dataTypeBytes = dataType.getBytes();

                            byte[] dataBytes = null;
                            try
                            {
                                dataBytes = dataToWrite.getBytes("UTF-8");
                            }
                            catch (UnsupportedEncodingException e1)
                            {
                                e1.printStackTrace();
                            }

                            byte[] payloadBytes = new byte[dataToWrite.getBytes().length + 1];

                            if (dataBytes != null)
                            {
                                // length of data language, i.e. en = 2 bytes
                                payloadBytes[0] = 0x0002;
                                for (int i = 1; i <= dataToWrite.getBytes().length; i++)
                                {
                                    payloadBytes[i] = dataBytes[i - 1];
                                }
                            }

                            NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, dataTypeBytes, new byte[]
                                    {}, payloadBytes);

                            NdefMessage ndefMessage = new NdefMessage(new NdefRecord[]
                                    { textRecord });

                            ndefFormatable.format(ndefMessage);

                            nfcTagTypeTextView.setText("TAG WAS FORMATTED WITH: BLANK CARD");

                            if (ndefFormatable.isConnected() == true)
                            {
                                ndefFormatable.close();
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    nfcMaxSizeTextView.setText("FAILED TO READ TAG");
                    lastNfcIntent = null;
                }

                if(mReadWriteCheck == false)
                {
                    NdefMessage[] msgs = null;

                    if (rawMsgs != null)
                    {
                        if (rawMsgs.length != 0)
                        {
                            msgs = new NdefMessage[rawMsgs.length];
                            for (int i = 0; i < rawMsgs.length; i++)
                            {
                                msgs[i] = (NdefMessage) rawMsgs[i];
                            }

                            String tagTNF;
                            if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_ABSOLUTE_URI)
                            {
                                tagTNF="TNF = ABSOLUTE_URI";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_EMPTY)
                            {
                                tagTNF="TNF = EMPTY";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_EXTERNAL_TYPE)
                            {
                                tagTNF="TNF = EXTERNAL_TYPE";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_MIME_MEDIA)
                            {
                                tagTNF="TNF = MIME_MEDIA";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_RESERVED)
                            {
                                tagTNF="TNF = RESERVED";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_UNCHANGED)
                            {
                                tagTNF="TNF = UNCHANGED";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_UNKNOWN)
                            {
                                tagTNF="TNF = UNKNOWN";
                            }
                            else if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_WELL_KNOWN)
                            {
                                tagTNF="TNF = WELL_KNOWN";
                            }
                            else
                            {
                                tagTNF="TNG = ERROR";
                            }

                            String tagData=null;

                            if(msgs[0].getRecords()[0].getTnf() == NdefRecord.TNF_WELL_KNOWN)
                            {
                                byte[] payload = msgs[0].getRecords()[0].getPayload();
                                String textEncoding = ((payload[0] & 0x0080) == 0) ? "UTF-8" : "UTF-16";
                                int languageCodeLength = payload[0] & 0x003F;
                                try {
                                    tagData = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }
                            else
                            {
                                tagData = new String(msgs[0].getRecords()[0].getPayload());
                            }


                            nfcTnfTextView.setText(tagTNF);
                            mWriteTagEditText.setText(tagData);
                            // Set text to black if the tag has data
                            mWriteTagEditText.setTextColor(0xff000000);
                        }
                        else
                        {
                            nfcTnfTextView.setText("");
                            mWriteTagEditText.setText("EMPTY_TAG!");
                            // Set text to red if the tag is empty
                            mWriteTagEditText.setTextColor(0xffff0000);
                        }
                    }
                }
                else
                {
                    String dataType = "T"; // Data Type 'T' for plain Text using RTD format
                    String dataToWrite = "en" + mWriteTagEditText.getText().toString();
                    byte[] dataTypeBytes = dataType.getBytes();

                    byte[] dataBytes = null;
                    try {
                        dataBytes = dataToWrite.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e1) {
                        e1.printStackTrace();
                    }

                    byte[] payloadBytes =  new byte[dataToWrite.getBytes().length+1];

                    if(dataBytes != null)
                    {
                        payloadBytes[0] = 0x0002;
                        for(int i=1 ; i <= dataToWrite.getBytes().length; i++)
                        {
                            payloadBytes[i]=dataBytes[i-1];
                        }
                    }


                    NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, dataTypeBytes,
                            new byte[] {}, payloadBytes);
                    NdefMessage ndefMessage = new NdefMessage(new NdefRecord[] {textRecord});

                    int size = ndefMessage.toByteArray().length;

                    if (ndef != null) {

                        if (!ndef.isWritable() ) {
                            mWriteTagEditText.setText("Tag is read-only.");
                        }
                        if (ndef.getMaxSize() < size) {
                            mWriteTagEditText.setText("Tag capacity is " + ndef.getMaxSize() + " bytes, message is " + size
                                    + " bytes.");
                        }

                        if(ndef.isWritable() && (size <= ndef.getMaxSize()))
                        {
                            try
                            {
                                ndef.writeNdefMessage(ndefMessage);
                                Toast.makeText(this, "Wrote message to pre-formatted tag.", Toast.LENGTH_SHORT).show();
                            }
                            catch (Exception e)
                            {
                                Toast.makeText(this, "FAILED TO WRITE TAG", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
                if ((ndef != null) && (ndef.isConnected() == true))
                {
                    try
                    {
                        ndef.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            finally
            {
                mNfcTagLock.unlock();
            }

        }
        else
        {
            mSawNFCIntent = false;
            super.onNewIntent(intent);
        }
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        if(mNfcAdapter != null)
        {
            dbgLog(TAG, "onPause() disableForegroundDispatch", 'i');
            mNfcAdapter.disableForegroundDispatch(NFCTest.this);
        }

        if (isFinishing())
        {
            if ((mNfcAdapter != null) && !mOriginalNFCState)
            {
                try
                {
                    nfcAdapterDisable();
                }
                catch (CmdFailException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    private ParsedTagInfo parseTag(Tag nfcTag) throws CmdFailException
    {
        ParsedTagInfo tagInfo = new ParsedTagInfo();
        try
        {
            mNfcTagLock.lock();

            Ndef ndef = Ndef.get(nfcTag);

            // it possible that if card is not formatted that ndef may be null.
            // See http://www.jessechen.net/blog/how-to-nfc-on-the-android-platform/
            if (ndef == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Ndef object from Tag is null"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            tagInfo.ndef = ndef;
            tagInfo.tagType = ndef.getType();
            tagInfo.isWritable = ndef.isWritable();
            tagInfo.maxSize = ndef.getMaxSize();

            try
            {
                ndef.connect();
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("ndef.connect() threw exception. " + e.getMessage()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                tagInfo.ndefMessage = ndef.getNdefMessage();
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("getNdefMessage() threw exception. " + e.getMessage()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            finally
            {
                try
                {
                    ndef.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }

            // make sure ndefMessage is null then set tnfType and payloadData to
            // nothing
            if (tagInfo.ndefMessage == null)
            {
                tagInfo.tnfType = "";
                tagInfo.payloadData = "";
            }
            else
            {

                // save TNF type (string)
                tagInfo.tnfTypeShort = tagInfo.ndefMessage.getRecords()[0].getTnf();

                // get TNF type (String)
                switch (tagInfo.ndefMessage.getRecords()[0].getTnf())
                {
                case NdefRecord.TNF_ABSOLUTE_URI:
                    tagInfo.tnfType = "ABSOLUTE_URI";
                    break;

                case NdefRecord.TNF_EMPTY:
                    tagInfo.tnfType = "EMPTY";
                    break;

                case NdefRecord.TNF_EXTERNAL_TYPE:
                    tagInfo.tnfType = "EXTERNAL_TYPE";
                    break;

                case NdefRecord.TNF_MIME_MEDIA:
                    tagInfo.tnfType = "MIME_MEDIA";
                    break;

                case NdefRecord.TNF_UNCHANGED:
                    tagInfo.tnfType = "UNCHANGED";
                    break;

                case NdefRecord.TNF_UNKNOWN:
                    tagInfo.tnfType = "UNKNOWN";
                    break;

                case NdefRecord.TNF_WELL_KNOWN:
                    tagInfo.tnfType = "WELL_KNOWN";
                    break;

                default:
                    tagInfo.tnfType = "UNKNOWN";
                    break;

                }

                // Get payload data
                String tagPayloadData;
                if (tagInfo.ndefMessage.getRecords()[0].getTnf() == NdefRecord.TNF_WELL_KNOWN)
                {
                    byte[] payload = tagInfo.ndefMessage.getRecords()[0].getPayload();
                    String textEncoding = ((payload[0] & 0x0080) == 0) ? "UTF-8" : "UTF-16";
                    int languageCodeLength = payload[0] & 0x003F;
                    try
                    {
                        tagPayloadData = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        tagPayloadData = "ERROR: UnsupportedEncodingException";
                        e.printStackTrace();
                    }
                }
                else
                {
                    tagPayloadData = new String(tagInfo.ndefMessage.getRecords()[0].getPayload());
                }

                tagInfo.payloadData = tagPayloadData;
            }
        }
        finally
        {
            mNfcTagLock.unlock();
        }

        return tagInfo;
    }

    private void WriteTagPayload(ParsedTagInfo parsedTagInfo, String payloadData) throws CmdFailException
    {
        try
        {
            mNfcTagLock.lock();
            // first check that the tag is writable
            if (parsedTagInfo.ndef.isWritable() == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Tag is not writable"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Right now only support TNF_WELL_KNOWN
            switch (parsedTagInfo.tnfTypeShort)
            {
            case NdefRecord.TNF_WELL_KNOWN:

                //int connectedTech = nfcTag.getConnectedTechnology();
                WriteTnfWellKnownPayloadData(parsedTagInfo, payloadData);
                break;

            default:
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Tag TNF %s not supported", parsedTagInfo.tnfType));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        finally
        {
            mNfcTagLock.unlock();
        }
    }

    void WriteTnfWellKnownPayloadData(ParsedTagInfo parsedTagInfo, String payloadData) throws CmdFailException
    {
        String dataType = "T"; // Data Type 'T' for plain Text using RTD format
        String dataToWrite = "en" + payloadData;
        byte[] dataTypeBytes = dataType.getBytes();

        byte[] dataBytes = null;
        try
        {
            dataBytes = dataToWrite.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e1)
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("getBytes returned UnsupportedEncodingException"));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        byte[] payloadBytes = new byte[dataToWrite.getBytes().length + 1];

        if (dataBytes != null)
        {
            payloadBytes[0] = 0x0002;
            for (int i = 1; i <= dataToWrite.getBytes().length; i++)
            {
                payloadBytes[i] = dataBytes[i - 1];
            }
        }

        try
        {
            mNfcTagLock.lock();
            NdefRecord textRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, dataTypeBytes, new byte[]
                    {}, payloadBytes);
            NdefMessage ndefMessage = new NdefMessage(new NdefRecord[]
                    { textRecord });

            int size = ndefMessage.toByteArray().length;

            if (size > parsedTagInfo.maxSize)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("payload size (%d) exceeds tag's max payload size (%d)", size, parsedTagInfo.maxSize));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                parsedTagInfo.ndef.connect();
                parsedTagInfo.ndef.writeNdefMessage(ndefMessage);
                parsedTagInfo.ndef.close();
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to write payload to Tag. Exception: " + e.getMessage()));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
        }
        finally
        {
            mNfcTagLock.unlock();
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("ENABLE_NFC"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Let's turn on NFC
            boolean enableStatus = false;
            try
            {
                enableStatus = mNfcAdapter.enable();
            }
            catch(Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to enable NFC Adapter"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (enableStatus == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to enable NFC"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_NFC"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Let's turn off NFC
            boolean disableStatus = false;
            try
            {
                // NOTE:  disable() will clear enableForegroundDispatch settings.
                //        Need to set enableForegroundDispatch again before enable()
                disableStatus = nfcAdapterDisable();
            }
            catch(Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to disable NFC Adapter"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (disableStatus == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Failed to disable NFC"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_NFC_STATE"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            List<String> strDataList = new ArrayList<String>();

            String nfcState;

            switch (mNfcAdapter.getAdapterState())
            {
            case STATE_ON:
                nfcState = "ON";
                break;
            case STATE_OFF:
                nfcState = "OFF";
                break;
            case STATE_TURNING_OFF:
                nfcState = "TURNING_OFF";
                break;
            case STATE_TURNING_ON:
                nfcState = "TURNING_ON";
                break;
            default:
                nfcState = "UNKNOWN";
                break;
            }

            strDataList.add("STATE=" + nfcState);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        else if (strRxCmd.equalsIgnoreCase("ENABLE_FOREGROUND_DISPATCH"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                // need to call enableForegroundDispatch again before
                // enabling NFC adapter to make sure the intent is
                // routed to this activity
                EnableForegroundDispatch enableForegroundDispatch = new EnableForegroundDispatch();

                synchronized (enableForegroundDispatch)
                {
                    runOnUiThread(enableForegroundDispatch);
                    enableForegroundDispatch.wait(); // unlocks myRunable while waiting
                }

            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' does not have permission to '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (!isEnableForegroundDispatchSuccess)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' is not in resumed state", TAG));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_FOREGROUND_DISPATCH"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                DisableForegroundDispatch disableForegroundDispatch = new DisableForegroundDispatch();

                synchronized (disableForegroundDispatch)
                {
                    runOnUiThread(disableForegroundDispatch);
                    disableForegroundDispatch.wait(); // unlocks myRunable while waiting
                }
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' does not have permission to '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (!isDisableForegroundDispatchSuccess)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' has already been paused", TAG));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }
            else
            {
                // Generate an exception to send data back to CommServer
                List<String> strReturnDataList = new ArrayList<String>();
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("READ_TAG"))
        {
            List<String> strDataList = new ArrayList<String>();

            if ((mNfcAdapter.getAdapterState() == STATE_ON) && (lastNfcIntent != null))
            {
                Tag nfcTag = lastNfcIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                if (null == nfcTag)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("getParcelableExtra for EXTRA_TAG returned NULL");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                ParsedTagInfo tagInfo = parseTag(nfcTag);

                strDataList.add("TAG_DETECTED=" + "YES");
                strDataList.add("TAG_TYPE=" + tagInfo.tagType);
                strDataList.add("WRITABLE=" + (tagInfo.isWritable ? "YES" : "NO"));
                strDataList.add("MAX_SIZE=" + tagInfo.maxSize);
                strDataList.add("TNF_TYPE=" + tagInfo.tnfType);
                strDataList.add("PAYLOAD_DATA=" + tagInfo.payloadData);
            }
            else
                // no tag avail
            {
                strDataList.add("TAG_DETECTED=" + "NO");
            }

            if (strDataList.isEmpty() == false)
            {
                CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
                sendInfoPacketToCommServer(infoPacket);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("WRITE_TAG"))
        {
            if ((mNfcAdapter.getAdapterState() != STATE_ON) || (lastNfcIntent == null))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("No NFC Tag detected"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // get tag object
            Tag nfcTag = lastNfcIntent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            if (null == nfcTag)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("getParcelableExtra for EXTRA_TAG returned NULL");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // make sure we have at least one key-value pair
            if (strRxCmdDataList.size() < 1)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' contains no data for command '%s'", TAG, strRxCmd));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            for (String keyValuePair: strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key   = splitResult[0];
                String value = splitResult[1];

                if (key.equalsIgnoreCase("PAYLOAD_DATA"))
                {
                    ParsedTagInfo parsedTagInfo = parseTag(nfcTag);

                    // force TNF type to WELL_KNOWN
                    parsedTagInfo.tnfTypeShort = NdefRecord.TNF_WELL_KNOWN;
                    parsedTagInfo.tnfType = "WELL_KNOWN";

                    WriteTagPayload(parsedTagInfo, value);
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("UNKNOWN key: " + key);
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_SECURE_ELEMENT_ID"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // For SIM Card NFC Secure Element use com.nxp.uicc.ID for SECURE_ID
            // For Embedded Secure Element use com.nxp.smart_mx.ID for SECURE_ID

            String secureId = null;
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("SECURE_ELEMENT_ID"))
                    {
                        secureId = value;
                    }
                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Key '%s' is not supported for command '%s'", key, strRxCmd));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
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


            try
            {
                mNfcAdapterSelectDefaultSecureElement.invoke(mNfcAdapter, secureId);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to set secure element", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SECURE_ELEMENT_ID"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            Object secureElement;
            try
            {
                secureElement = mNfcAdapterGetDefaultSelectedSecureElement.invoke(mNfcAdapter);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to get secure element", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("SECURE_ELEMENT_ID=" + secureElement.toString());

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("ENABLE_SECURE_ELEMENT_ID"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                mNfcAdapterSetDefaultSecureElementState.invoke(mNfcAdapter, true);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to enable secure element", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("DISABLE_SECURE_ELEMENT_ID"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            try
            {
                mNfcAdapterSetDefaultSecureElementState.invoke(mNfcAdapter, false);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to disable secure element", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_SWP_SIM"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            long swpSIM = 0;
            if (strRxCmdDataList.size() > 0)
            {
                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("SWP_SIM"))
                    {
                        swpSIM = Long.parseLong(value);
                    }
                    else
                    {
                        // Generate an exception to send FAIL result and mesg
                        // back to CommServer
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add(String.format("Key '%s' is not supported for command '%s'", key.toString(), strRxCmd));
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
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

            Boolean setSwpStatus;
            try
            {
                setSwpStatus = (Boolean) mNfcSwpSwitchSetSWPSIM.invoke(mNfcSwpSwitchInstance, swpSIM);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to set SWP SIM. Exception = %s", TAG, e.toString()));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            if (setSwpStatus == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to set SWP SIM", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SWP_SIM"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            Object swpSim;
            try
            {
                swpSim = mNfcSwpSwitchGetSWPSIMStatus.invoke(mNfcSwpSwitchInstance);
            }
            catch (Exception e)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to get SWP SIM", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("SWP_SIM=" + swpSim.toString());

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_DUAL_SWP_SIM_TEST_STATUS"))
        {
            // make sure this device has nfc adapter
            if (mNfcAdapter == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not supported on this device"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // make sure nfc is ON
            if (mNfcAdapter.getAdapterState() != STATE_ON)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("NFC is not enabled"));
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            int status = -1;

            dbgLog(TAG, "starting dual swp sim test", 'i');
            try
            {
                INxpNfcAdapterExtras mNxpNfcAdapterExtras = null;
                Context context = getApplicationContext();
                NfcAdapter nfcAdapter = NfcAdapter.getNfcAdapter(context);
                NxpNfcAdapter nxpNfcAdapter = NxpNfcAdapter.getNxpNfcAdapter(nfcAdapter);

                mNxpNfcAdapterExtras = nxpNfcAdapter.getNxpNfcAdapterExtrasInterface(nfcAdapter.getNfcAdapterExtrasInterface());
                // the dual SIM feature in NXP chip PN548 has been dropped and
                // new chip PN553 will implement this differently.
                // For now, just return the status -1
                //status = mNxpNfcAdapterExtras.getDualSwpTestStatus();
                dbgLog(TAG, "status=" + status, 'i');
            }
            catch (Exception e)
            {
                dbgLog(TAG, "Error happened. " + e.getMessage(), 'e');
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(String.format("Activity '%s' failed to get dual swp test status", TAG));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            String teststatus = "";
            if (status == 0)
            {
                teststatus = "DUALSWP_STATUS_BOTH_PASS";
            }
            else if (status == 1)
            {
                teststatus = "DUALSWP_STATUS_SIM_1_FAIL";
            }
            else if (status == 2)
            {
                teststatus = "DUALSWP_STATUS_SIM_2_FAIL";
            }
            else if (status == 3)
            {
                teststatus = "DUALSWP_STATUS_BOTH_FAIL";
            }
            else
            {
                teststatus = "DUALSWP_STATUS_SWITCH_FAIL";
            }

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("TEST_STATUS=" + teststatus);

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
        strHelpList.add("This function will read and write an NFC tag");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_NFC    - Enables NFC adapter");
        strHelpList.add("  DISABLE_NFC   - Disables NFC adapter");
        strHelpList.add("  GET_NFC_STATE - Returns the state of NFC adapter");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_FOREGROUND_DISPATCH  - Enable sending of tags to this activity when it's in the foreground");
        strHelpList.add("  DISABLE_FOREGROUND_DISPATCH - Disable sending of tags to this activity when it's in the foreground");
        strHelpList.add("  ");
        strHelpList.add("  READ_TAG      - Return information on most recently detected tag");
        strHelpList.add("    TAG_DETECTED - YES or NO");
        strHelpList.add("    TAG_TYPE     - NDEF tag type");
        strHelpList.add("    WRITABLE     - YES or NO");
        strHelpList.add("    MAX_SIZE     - Maximum NDEF message size in bytes");
        strHelpList.add("    TNF_TYPE     - NDEF TNF type");
        strHelpList.add("    PAYLOAD_DATA - NDEF payload data");
        strHelpList.add("  ");
        strHelpList.add("  WRITE_TAG      - Write specified payload data to tag");
        strHelpList.add("    PAYLOAD_DATA - NDEF payload data to write");
        strHelpList.add("  ");
        strHelpList.add("  SET_SECURE_ELEMENT_ID      - Set the default secure element ID");
        strHelpList.add("    SECURE_ELEMENT_ID - Element ID to set as default");
        strHelpList.add("  ");
        strHelpList.add("  GET_SECURE_ELEMENT_ID      - Get the default secure element ID");
        strHelpList.add("  ");
        strHelpList.add("  ENABLE_SECURE_ELEMENT_ID   - Enable the default secure element ID");
        strHelpList.add("  ");
        strHelpList.add("  DISABLE_SECURE_ELEMENT_ID  - Disable the default secure element ID");
        strHelpList.add("  ");
        strHelpList.add("  SET_SWP_SIM      - Set the SIM card to use for secure element");
        strHelpList.add("    SWP_SIM - ID of SIM Card to select");
        strHelpList.add("  ");
        strHelpList.add("  GET_SWP_SIM      - Get SIM Card currently in use for secure element");
        strHelpList.add("  ");
        strHelpList.add("  GET_DUAL_SWP_SIM_TEST_STATUS      - Get dual swp sim test status");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "NFC Test:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "NFC Test:  PASS" + "\r\n\r\n", MODE_APPEND);

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
