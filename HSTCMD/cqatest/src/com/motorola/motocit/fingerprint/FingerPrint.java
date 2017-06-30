/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.fingerprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import com.synaptics.fingerprint.Fingerprint;
import com.synaptics.fingerprint.FingerprintEvent;
import com.synaptics.fingerprint.SensorTest;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

/**
 * Sensor Test tool
 *
 * Sensor tests are performed based on Script, Options and Data log options.
 * This Activity provides the UI to choose the above options.
 * Unit Id is an optional field and is used only to include in data log.
 *
 * request() API of Validity library is used to perform sensor test. This API takes two arguments.
 * 1st arugment is the command, VCS_REQUEST_COMMAND_SENSOR_TEST is for sensor test.
 * 2nd argument is SensorTest object, which contains the fields- scriptId, options, dataLogOpt and unitId.
 *
 */
public class FingerPrint extends Test_Base implements Fingerprint.EventListener {

    private Button mBtnStart; //Start Button
    private Spinner mSpnrScriptId; //Spinner for Test Scripts
    private Spinner mSpnrOptions; //Spinner for test options
    private Spinner mSpnrDataLogOptions; //Spinner for data log options
    private EditText mEtUnitId; //to input Unit Id number, that is included in the datalog.

    private LinkedHashMap<String, Integer> mMapOptions = new LinkedHashMap<String, Integer>(); //Map option labels to option flag codes
    private LinkedHashMap<String, Integer> mMapDataLogOptions = new LinkedHashMap<String, Integer>(); //Map data log option labels and data log flags
    private boolean[] mSelectedOptions; //Holds the test option indexes that are selected in spinner
    private boolean[] mSelectedDataLogOptions; //Holds the data log options indexes that are selected in spinner

    private int mScriptId; //Holds the selected script id
    private int mOptions; //Holds the selected options (from options spinner)
    private int mDataLogOptions; //Holds the selected data log options (from data log spinner)
    private int mUnitId; //Holds the unit Id if entered

    private Fingerprint mFingerprint;
    private Thread mThread;
    private Semaphore mLock = new Semaphore(1);
    private ArrayAdapter<String> mAdapter;
    private ArrayList<String> mMsgList = new ArrayList<String>();

    private boolean isTestPass = false;
    private static long FPS_TEST_TIMEOUT_MSECS = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_FingerPrint";
        dbgLog(TAG, "onCreate()", 'i');

        super.onCreate(savedInstanceState);

        if (!hasFingerprintSensor())
        {
            Toast.makeText(FingerPrint.this, "The device does NOT support FingerPrint", Toast.LENGTH_SHORT).show();
            FingerPrint.this.finish();
        }

        setContentView(com.motorola.motocit.R.layout.fingerprint_layout);

        //Get the views from layout
        mSpnrScriptId = (Spinner) findViewById(com.motorola.motocit.R.id.script_id);
        mSpnrOptions = (Spinner) findViewById(com.motorola.motocit.R.id.options);
        mSpnrDataLogOptions = (Spinner) findViewById(com.motorola.motocit.R.id.data_log_options);
        mEtUnitId = (EditText) findViewById(com.motorola.motocit.R.id.unit_id);
        mBtnStart = (Button) findViewById(com.motorola.motocit.R.id.start_stop);
        mBtnStart.setOnClickListener(onStartBtnClick);

        mAdapter = new ArrayAdapter<String>(FingerPrint.this, com.motorola.motocit.R.layout.fingerprint_list_item, mMsgList);
        ListView list = (ListView) findViewById(com.motorola.motocit.R.id.list);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        list.setAdapter(mAdapter);

        populateUi();

    }

    protected void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    protected void onPause()
    {
        super.onPause();
        dbgLog(TAG, "onPause()", 'i');
    }

    protected void onDestroy()
    {
        super.onDestroy();
        dbgLog(TAG, "onDestroy()", 'i');

        if (mFingerprint == null)
        {
            return;
        }

        try
        {
            mLock.acquire();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        finally
        {
            mFingerprint.cleanUp();
            mFingerprint = null;
            mLock.release();
        }
    }

    private View.OnClickListener onStartBtnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
            startSensorTest();
        }
    };

    private boolean hasFingerprintSensor()
    {
        return SystemProperties.getInt("ro.mot.hw.fingerprint", 0) == 1;
    }

    /**
     * Spans a thread, reads the user input from UI, prepares the SensorTest object with chosen
     * options and invokes the request() API to start the sensor test.
     */
    private void startSensorTest()
    {
        dbgLog(TAG, "startSensorTest()", 'i');

        mThread = new Thread(new Runnable() {
            public void run()
            {
                if (mLock.tryAcquire())
                {

                    clearMessages();

                    readUserInput();

                    //Prepare object to pass thru API
                    SensorTest sTest = new SensorTest();
                    sTest.scriptId = mScriptId;
                    sTest.options = mOptions;
                    sTest.dataLogOpt = mDataLogOptions;
                    sTest.unitId = mUnitId;
                    dbgLog(TAG, "SensorTest[" + sTest.scriptId + ", " + sTest.options + ", " + sTest.dataLogOpt + ", " + sTest.unitId + "]", 'i');

                    //Disable start button to avoid multiple taps
                    enableStartBtn(false);

                    // load library and register callback
                    if (mFingerprint == null)
                    {
                        mFingerprint = new Fingerprint(FingerPrint.this, FingerPrint.this);
                    }

                    //Call request API
                    int result = mFingerprint.request(Fingerprint.VCS_REQUEST_COMMAND_SENSOR_TEST, sTest);
                    if (result == Fingerprint.VCS_RESULT_OK)
                    {
                        addMessage("FingerPrint Test Success");
                        dbgLog(TAG, "FingerPrint test success", 'i');
                        isTestPass = true;
                    }
                    else
                    {
                        addMessage("FingerPrint Test Failed");
                        dbgLog(TAG, "FingerPrint test failed", 'i');
                    }

                    enableStartBtn(true);
                    mLock.release();
                }
            }
        });
        mThread.start();
    }

    /**
     * Callback method
     */
    public void onEvent(FingerprintEvent event)
    {
        if (event != null)
        {
            dbgLog(TAG, "onEvent(): Event Id = " + event.eventId, 'i');

            String statusMessage = "";
            switch (event.eventId)
            {
            //Event Id
                case Fingerprint.EVT_SNSR_TEST_SCRIPT_START:
                    statusMessage = "Script started";
                    break;
                case Fingerprint.EVT_SNSR_TEST_SECTION_START:
                    statusMessage = "Section started";
                    break;
                case Fingerprint.EVT_SNSR_TEST_SECTION_END:
                    statusMessage = "Section completed";
                    if (event.eventData != null && event.eventData instanceof Integer)
                    {
                        statusMessage += getResultString((Integer) event.eventData);
                    }
                    break;
                case Fingerprint.EVT_SNSR_TEST_SCRIPT_END:
                    statusMessage = "Script completed";
                    statusMessage += getResultString((Integer) event.eventData);
                    break;
                case Fingerprint.EVT_SNSR_TEST_RESET_AFTER_TEST_RES:
                    statusMessage = "Test completed, sensor has been reset";
                    break;
            }

            if (!statusMessage.equals(""))
            {
                addMessage(statusMessage);
            }
        }
    }

    /**
     * Returns message for the supplied result code.
     */
    private String getResultString(int binResult)
    {
        dbgLog(TAG, "binResult=" + binResult, 'i');
        String result = "";
        switch (binResult)
        {
            case Fingerprint.VCS_SNSR_TEST_PASS_BIN:
                result = "Test Passed";
                break;
            case Fingerprint.VCS_SNSR_TEST_CODE_FAIL_BIN:
                result = "Internal Failure";
                break;
            case Fingerprint.VCS_SNSR_TEST_USER_STOP_FAIL_BIN:
                result = "User Stopped Testing";
                break;
            case Fingerprint.VCS_SNSR_TEST_NO_TEST_SCRIPT_FAIL_BIN:
                result = "No Test Script";
                break;
            case Fingerprint.VCS_SNSR_TEST_NO_CALLBACK_FAIL_BIN:
                result = "No Callback Function";
                break;
            case Fingerprint.VCS_SNSR_TEST_INFO_CHCK_FAIL_BIN:
                result = "Test Setup Data Failure";
                break;
            default:
                result = "Test Failed";
                break;
        }
        if (!result.equals(""))
        {
            result = " [" + result + "]";
        }
        return result;
    }

    /**
     * Fill the spinners with options and data log option flags and select default flags.
     */
    private void populateUi()
    {
        //Fill a map with test options
        mMapOptions.put("Stop on fail", Fingerprint.VCS_SNSR_TEST_STOP_ON_FAIL_FLAG);
        mMapOptions.put("Script start stop", Fingerprint.VCS_SNSR_TEST_SEND_CB_SCRIPT_START_STOP_FLAG);
        mMapOptions.put("Section start stop", Fingerprint.VCS_SNSR_TEST_SEND_CB_SECT_START_STOP_FLAG);
        ArrayAdapter<String> optionsArray = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mMapOptions.keySet().toArray(new String[0]));
        mSpnrOptions.setAdapter(optionsArray);
        mSpnrOptions.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    //Prompt a dialog to choose the desired options
                    showOptions();
                }
                return true;
            }
        });
        mSelectedOptions = new boolean[mMapOptions.size()];
        Arrays.fill(mSelectedOptions, Boolean.FALSE);
        mSelectedOptions[2] = true; //default Fingerprint.VCS_SNSR_TEST_SEND_CB_SCRIPT_START_STOP_FLAG
        mSpnrOptions.setSelection(2);

        //Fill map with data log options
        mMapDataLogOptions.put("Tests", Fingerprint.VCS_SNSR_TEST_DATALOG_TESTS_FLAG);
        mMapDataLogOptions.put("Data only", Fingerprint.VCS_SNSR_TEST_DATALOG_DATA_ONLY_TESTS_FLAG);
        mMapDataLogOptions.put("Sensor info", Fingerprint.VCS_SNSR_TEST_DATALOG_SENSOR_INFO_FLAG);
        mMapDataLogOptions.put("Include bin desc", Fingerprint.VCS_SNSR_TEST_DATALOG_INC_BIN_DESC_FLAG);
        mMapDataLogOptions.put("Include test list", Fingerprint.VCS_SNSR_TEST_DATALOG_INC_TEST_LIST_FLAG);
        mMapDataLogOptions.put("Include notes", Fingerprint.VCS_SNSR_TEST_DATALOG_INC_NOTES_FLAG);
        mMapDataLogOptions.put("Include script db list", Fingerprint.VCS_SNSR_TEST_DATALOG_INC_SCRIPT_DB_LIST_FLAG);
        mMapDataLogOptions.put("File create", Fingerprint.VCS_SNSR_TEST_DATALOG_FILE_CREATE_FLAG);
        mMapDataLogOptions.put("File append", Fingerprint.VCS_SNSR_TEST_DATALOG_FILE_APPEND_FLAG);

        //set default flags
        mSelectedDataLogOptions = new boolean[mMapDataLogOptions.size()];
        Arrays.fill(mSelectedDataLogOptions, Boolean.FALSE);
        mSelectedDataLogOptions[0] = true; //default Fingerprint.VCS_SNSR_TEST_DATALOG_TESTS_FLAG
        mSelectedDataLogOptions[7] = true; //Fingerprint.VCS_SNSR_TEST_DATALOG_FILE_CREATE_FLAG

        ArrayAdapter<String> dataOptionsArray = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mMapDataLogOptions.keySet().toArray(new String[0]));
        mSpnrDataLogOptions.setAdapter(dataOptionsArray);
        mSpnrDataLogOptions.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    //Prompt a dialog to choose the desired data log options
                    showDatalogOptions();
                }
                return true;
            }
        });

        mSpnrScriptId.setSelection(0);
    }

    /**
     * Show a dialog with all test options. Each option is associated with a checkbox.
     * Default and previousely selected options are marked as checked when dialog is shown.
     * The selected options are updated to an array; this array is used to enable the option flags
     * in the input argument object which is passed to request() API.
     */
    private void showOptions()
    {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Options");
        builder.setMultiChoiceItems(mMapOptions.keySet().toArray(new String[0]), mSelectedOptions, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked)
            {
                mSelectedOptions[which] = isChecked;
                if (isChecked)
                    mSpnrOptions.setSelection(which);
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Show a dialog with data log options. Each option is associated with a checkbox.
     * Default and previousely selected options are marked as checked when dialog is shown.
     * The selected options are updated to an array; this array is used to enable the 
     * data log option flags in the input argument object which is passed to request() API.
     */
    private void showDatalogOptions()
    {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Data log options");
        builder.setMultiChoiceItems(mMapDataLogOptions.keySet().toArray(new String[0]), mSelectedDataLogOptions, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked)
            {
                mSelectedDataLogOptions[which] = isChecked;
                if (isChecked)
                    mSpnrDataLogOptions.setSelection(which);
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Get the user input to member variables.
     */
    private void readUserInput()
    {

        //------Script Id for 57B sensor--------
        mScriptId = Fingerprint.VCS_SNSR_TEST_SPI57B_ID367_A4_NOFLASH_DEVICEINFO_SCRIPT_ID; //Get sensor device information

        //------Get selected Options flags from UI------
        List<Integer> optionsValueList = new ArrayList<Integer>(mMapOptions.values());
        mOptions = 0;
        for (int i = 0; i < mSelectedOptions.length; i++)
        {
            if (mSelectedOptions[i])
            {
                Integer value = optionsValueList.get(i);
                mOptions |= value;
            }
        }

        //-----Get selected Datalog Options flags from UI-----
        List<Integer> dataOptionsValueList = new ArrayList<Integer>(mMapDataLogOptions.values());
        mDataLogOptions = 0;
        for (int i = 0; i < mSelectedDataLogOptions.length; i++)
        {
            if (mSelectedDataLogOptions[i])
            {
                Integer value = dataOptionsValueList.get(i);
                mDataLogOptions |= value;
            }
        }

        //Get the supplied Unit Id from UI
        String unitId = mEtUnitId.getText().toString();
        if (unitId != null && unitId.trim().length() > 0)
        {
            mUnitId = Integer.parseInt(unitId);
        }
    }

    /**
     * Enable or disables Start button
     */
    private void enableStartBtn(final boolean enabled)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                mBtnStart.setEnabled(enabled);
            }
        });
    }

    /**
     * Prompts a toast message with supplied string
     */
    private void showResult(final String msg)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                Toast.makeText(FingerPrint.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Add message to the list view to show in UI
     */
    private void addMessage(final String msg)
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                mMsgList.add(msg);
                mAdapter.notifyDataSetChanged();
                dbgLog(TAG, "Msg:" + msg, 'i');
            }
        });
    }

    /**
     * Remove all messages from the list view
     */
    private void clearMessages()
    {
        runOnUiThread(new Runnable() {
            public void run()
            {
                mMsgList.clear();
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("START_FPS_TEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            if (!hasFingerprintSensor())
            {
                strDataList.add("TEST_RESULT=FPS_NOT_EXIST");
            }
            else
            {
                startSensorTest();
            }

            // Start the FPS test, timeout is 10s by default
            long startTime = System.currentTimeMillis();
            while (!isTestPass)
            {
                dbgLog(TAG, "Starting the FPS test", 'v');

                if ((System.currentTimeMillis() - startTime) > FPS_TEST_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Time out to start FingerPrint test", 'e');
                    isTestPass = false;
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    isTestPass = false;
                }
            }

            if (isTestPass)
            {
                strDataList.add("TEST_RESULT=FPS_PASS");
            }
            else
            {
                strDataList.add("TEST_RESULT=FPS_FAILED");
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
        strHelpList.add("This function will test FingerPrint sensor");
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
        contentRecord("testresult.txt", "Sensor - FingerPrint:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Sensor - FingerPrint:  PASS" + "\r\n\r\n", MODE_APPEND);

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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if (wasActivityStartedByCommServer() == true)
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Sensor - FingerPrint:  PASS" + "\r\n\r\n", MODE_APPEND);

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

            contentRecord("testresult.txt", "Sensor - FingerPrint:  FAILED" + "\r\n\r\n", MODE_APPEND);

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

}
