/*
 * Copyright (c) 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.fingerprint;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
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

import com.fingerprints.service.FingerprintSensorTest;
import com.fingerprints.service.FingerprintSensorTest.FingerprintSensorTestListener;
import com.fingerprints.service.IFingerprintSensorTest;
import com.fingerprints.service.IFingerprintSensorTestListener;
import com.fingerprints.service.IFingerprintService;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class FingerPrintTest extends Test_Base implements FingerprintSensorTestListener
{

    static final String SERVICE_NAME = "sensor_test";

    private final int FPC_SELFTEST_PASSED = 0;
    private final int FPC_SELFTEST_FAILED = 1;
    private final int FPC_SELFTEST_FAILED_POWER_WAKEUP = 2;
    private final int FPC_SELFTEST_FAILED_SENSOR_RESET = 3;
    private final int FPC_SELFTEST_FAILED_READ_HWID = 4;
    private final int FPC_SELFTEST_FAILED_CAPTURE_IMAGE = 5;
    private final int FPC_SELFTEST_FAILED_IRQ = 6;
    private final int FPC_SELFTEST_FAILED_SENSOR_COULD_NOT_BE_REACHED = 16;

    private final int FPC_CHECKERBOARD_PASSED = 0;
    private final int FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR = 1;
    private final int FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR = 2;
    private final int FPC_CHECKERBOARD_DEAD_PIXELS = 4;
    private final int FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT = 8;
    private int FPC_IMAGE_QUALITY_TEST_MIN_ITERATIONS = 100;

    private String selftestStr = "";
    private String checkerboardtestStr = "";
    private String imagequalitytestStr = "";
    private int imagequalitytestcount = 0;
    private boolean imagequalitytestpassed = true;

    private FingerprintSensorTestListener mSensorTestListener = new FingerprintSensorTestListener()
    {

        @Override
        public void onSelfTestResult(final int result)
        {
            dbgLog(TAG, "Received selfTest result = " + result, 'i');
            isTestResultReceived = true;
            if (result == FPC_SELFTEST_PASSED)
            {
                selftestStr = "FPC_SELFTEST_PASSED";
            }
            else
            {
		switch(result)
		{
		case FPC_SELFTEST_FAILED:
 		    selftestStr = "FPC_SELFTEST_GENERIC_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_POWER_WAKEUP:
 		    selftestStr = "FPC_SELFTEST_POWER_WAKEUP_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_SENSOR_RESET:
 		    selftestStr = "FPC_SELFTEST_SENSOR_RESET_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_READ_HWID:
 		    selftestStr = "FPC_SELFTEST_READ_HWID_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_CAPTURE_IMAGE:
 		    selftestStr = "FPC_SELFTEST_CAPTURE_IMAGE_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_IRQ:
 		    selftestStr = "FPC_SELFTEST_IRQ_FAILED";
		    break;
		case FPC_SELFTEST_FAILED_SENSOR_COULD_NOT_BE_REACHED:
 		    selftestStr = "FPC_SELFTEST_SENOSR_COULD_NOT_BE_REACHED_FAILED";
		    break;
		}
            }
            addMessage("SelfTestResult=" + selftestStr);
        }

        @Override
        public void onCheckerboardTestResult(final int result)
        {
            dbgLog(TAG, "Received CheckerboardTest result = " + result, 'i');
            isTestResultReceived = true;
            String str = "";
            switch (result)
            {
                case FPC_CHECKERBOARD_PASSED:
                    checkerboardtestStr = "FPC_CHECKERBOARD_PASSED";
                    break;
                case FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR:
                    checkerboardtestStr = "FPC_CHECKERBOARD_TYPE1_MEDIAN_ERROR";
                    break;

                case FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR:
                    checkerboardtestStr = "FPC_CHECKERBOARD_TYPE2_MEDIAN_ERROR";
                    break;

                case FPC_CHECKERBOARD_DEAD_PIXELS:
                    checkerboardtestStr = "FPC_CHECKERBOARD_DEAD_PIXELS";
                    break;

                case FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT:
                    checkerboardtestStr = "FPC_CHECKERBOARD_DEAD_PIXELS_FINGER_DETECT";
                    break;

                default:
                    checkerboardtestStr = "FPC_CHECKERBOARD_UNKNOWN_ERROR";
            }
            addMessage("CheckerBoardTestResult=" + checkerboardtestStr);
        }

        @Override
        public void onImagequalityTestResult(int result)
        {
            dbgLog(TAG, "Received fingerprint finger detection result = " + result, 'i');
            imagequalitytestcount++;
            if (result == 0)
            {
                imagequalitytestpassed = false;
            }
            if (imagequalitytestcount == FPC_IMAGE_QUALITY_TEST_MIN_ITERATIONS)
            {
                isTestResultReceived = true;
                if (imagequalitytestpassed)
                {
                    imagequalitytestStr = "FPC_FINGER_PRESENT";
                    addMessage("FingerDetectionTestResult=" + "FPC_FINGER_PRESENT");
                }
                else
                {
                    imagequalitytestStr = "FPC_FINGER_ABSENT";
                    addMessage("FingerDetectionTestResult=" + "FPC_FINGER_ABSENT");
                }

                mBtnStartImageQualityTest.setEnabled(true);
            }
            else
            {
                // run test again
                if (sTest != null)
                {
                    sTest.imagequalityTest(mSensorTestListener);
                }

            }
        }

	@Override
	public void onImagecapacitanceTestResult(int result){};
	@Override
	public void onImageresetpixelTestResult(int result){};
	@Override
	public void onAfdcalibrationTestResult(int result){};
	@Override
	public void onAfdcalibrationrubberstampTestResult(int result){};
	@Override
	public void onAfdrubberstampTestResult(int result){};

    };

    private Button mBtnStartSelfTest; // self test
    private Button mBtnStartCheckerBoardTest; // checker board test
    private Button mBtnStartImageQualityTest;
    private static long FPS_TEST_TIMEOUT_MSECS = 10000;

    private boolean isTestResultReceived = false;

    private FingerprintSensorTest sTest;
    private ArrayAdapter<String> mAdapter;
    private ArrayList<String> mMsgList = new ArrayList<String>();

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Sensor_FingerPrintTest";
        dbgLog(TAG, "onCreate()", 'i');

        super.onCreate(savedInstanceState);

        if (!TestUtils.hasFingerprintSensor())
        {
            Toast.makeText(FingerPrintTest.this, "The device does NOT support FingerPrint", Toast.LENGTH_SHORT).show();
            FingerPrintTest.this.finish();
        }

        setContentView(com.motorola.motocit.R.layout.fingerprint_test_layout);

        mAdapter = new ArrayAdapter<String>(FingerPrintTest.this, com.motorola.motocit.R.layout.fingerprint_list_item, mMsgList);
        ListView list = (ListView) findViewById(com.motorola.motocit.R.id.list);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        list.setAdapter(mAdapter);

        // Get the views from layout
        mBtnStartSelfTest = (Button) findViewById(com.motorola.motocit.R.id.selftest);
        mBtnStartSelfTest.setOnClickListener(onStartSelfTestClick);

        mBtnStartCheckerBoardTest = (Button) findViewById(com.motorola.motocit.R.id.checkerboardtest);
        mBtnStartCheckerBoardTest.setOnClickListener(onStartCheckerBoardTestClick);

        mBtnStartImageQualityTest = (Button) findViewById(com.motorola.motocit.R.id.imagequalitytest);
        mBtnStartImageQualityTest.setOnClickListener(onStartImageQualityTestClick);

        if (Build.DEVICE.toLowerCase().contains("nash")) {
            dbgLog(TAG, "Set FPC_IMAGE_QUALITY_TEST_MIN_ITERATIONS to 10 for Golden Eagle temporarily due to IKSWN-23693", 'i');
            FPC_IMAGE_QUALITY_TEST_MIN_ITERATIONS = 10;
        }
    }

    protected void onResume()
    {
        super.onResume();
        try
        {
            dbgLog(TAG, "onResume, creating sensor object", 'i');
            sTest = new FingerprintSensorTest();
        }
        catch (Exception e)
        {
            addMessage("Failed to create sensor object");
            e.printStackTrace();
        }
        sendStartActivityPassed();

    }

    protected void onPause()
    {
        super.onPause();
        dbgLog(TAG, "onPause()", 'i');
        if (sTest != null)
        {
            sTest = null;
        }
    }

    protected void onDestroy()
    {
        super.onDestroy();
        dbgLog(TAG, "onDestroy()", 'i');
    }

    private View.OnClickListener onStartSelfTestClick = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
            isTestResultReceived = false;
            clearMessages();
            if (sTest != null)
            {
                dbgLog(TAG, "starting selfTest", 'i');
                sTest.selfTest(mSensorTestListener);
            }
        }
    };

    private View.OnClickListener onStartCheckerBoardTestClick = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
            isTestResultReceived = false;
            clearMessages();
            if (sTest != null)
            {
                dbgLog(TAG, "starting Checker Board Test", 'i');
                sTest.checkerboardTest(mSensorTestListener);
            }
        }
    };

    private View.OnClickListener onStartImageQualityTestClick = new View.OnClickListener() {
        @Override
        public void onClick(View v)
        {
            imagequalitytestcount = 0;
            imagequalitytestpassed = true;
            isTestResultReceived = false;
            clearMessages();
            mBtnStartImageQualityTest.setEnabled(false);
            if (sTest != null)
            {
                dbgLog(TAG, "starting FingerDetectionTest", 'i');
                sTest.imagequalityTest(mSensorTestListener);
            }
        }
    };

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
    public void onCheckerboardTestResult(int result)
    {
        dbgLog(TAG, "Received CheckerboardTest result = " + result, 'i');
        addMessage("CheckerBoardTestResult=" + result);

    }

    @Override
    public void onSelfTestResult(int result)
    {
        dbgLog(TAG, "Received selfTest result = " + result, 'i');
        addMessage("SelfTestResult=" + result);

    }

    @Override
    public void onImagequalityTestResult(int arg0)
    {

    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("START_FPS_SELFTEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            isTestResultReceived = false;
            clearMessages();
            if (!TestUtils.hasFingerprintSensor())
            {
                selftestStr = "FPC_TEST_HW_NOT_EXIST";
            }
            else
            {
                if (sTest != null)
                {
                    dbgLog(TAG, "starting selfTest", 'i');
                    sTest.selfTest(mSensorTestListener);
                }
                else
                {
                    selftestStr = "FPC_TEST_FAILED_OBJECT";
                }
            }

            // Start the FPS test, timeout is 10s by default
            long startTime = System.currentTimeMillis();
            while (!isTestResultReceived)
            {
                dbgLog(TAG, "Starting self test", 'v');

                if ((System.currentTimeMillis() - startTime) > FPS_TEST_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Time out to start FingerPrint test", 'e');
                    selftestStr = "FPC_TEST_TIME_OUT";
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            strDataList.add("TEST_RESULT=" + selftestStr);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_FPS_CHECKERBOARDTEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            isTestResultReceived = false;
            clearMessages();
            if (!TestUtils.hasFingerprintSensor())
            {
                checkerboardtestStr = "FPC_TEST_HW_NOT_EXIST";
            }
            else
            {
                if (sTest != null)
                {
                    dbgLog(TAG, "starting checkerboard test", 'i');
                    sTest.checkerboardTest(mSensorTestListener);
                }
                else
                {
                    checkerboardtestStr = "FPC_TEST_FAILED_OBJECT";
                }
            }

            // Start the FPS test, timeout is 10s by default
            long startTime = System.currentTimeMillis();
            while (!isTestResultReceived)
            {
                dbgLog(TAG, "Starting checkerboard test", 'v');

                if ((System.currentTimeMillis() - startTime) > FPS_TEST_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Time out to start FingerPrint test", 'e');
                    checkerboardtestStr = "FPC_TEST_TIME_OUT";
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            strDataList.add("TEST_RESULT=" + checkerboardtestStr);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_FPS_FINGERDETECTIONTEST"))
        {
            List<String> strDataList = new ArrayList<String>();
            isTestResultReceived = false;
            clearMessages();
            if (!TestUtils.hasFingerprintSensor())
            {
                imagequalitytestStr = "FPC_TEST_HW_NOT_EXIST";
            }
            else
            {
                if (sTest != null)
                {
                    dbgLog(TAG, "starting finger detection test", 'i');
                    imagequalitytestcount = 0;
                    imagequalitytestpassed = true;
                    sTest.imagequalityTest(mSensorTestListener);
                }
                else
                {
                    imagequalitytestStr = "FPC_TEST_FAILED_OBJECT";
                }
            }

            // Start the FPS test, timeout is 10s by default
            long startTime = System.currentTimeMillis();
            while (!isTestResultReceived)
            {
                dbgLog(TAG, "Runing finger detection test", 'v');

                if ((System.currentTimeMillis() - startTime) > FPS_TEST_TIMEOUT_MSECS)
                {
                    dbgLog(TAG, "Time out to start FingerPrint test", 'e');
                    imagequalitytestStr = "FPC_TEST_TIME_OUT";
                    break;
                }

                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            strDataList.add("TEST_RESULT=" + imagequalitytestStr);

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
        strHelpList.add("START_FPS_SELFTEST - self test");
        strHelpList.add("");
        strHelpList.add("START_FPS_CHECKERBOARDTEST - checker board test");
        strHelpList.add("");
        strHelpList.add("START_FPS_FINGERDETECTIONTEST - finger detection test");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Sensor - FingerPrintTest:  FAILED" + "\r\n\r\n", MODE_APPEND);

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
        contentRecord("testresult.txt", "Sensor - FingerPrintTest:  PASS" + "\r\n\r\n", MODE_APPEND);

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

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) || (keyCode == KeyEvent.KEYCODE_VOLUME_UP))
        {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            {
                contentRecord("testresult.txt", "Sensor - FingerPrintTest:  PASS" + "\r\n\r\n", MODE_APPEND);
            }
            else
            {
                contentRecord("testresult.txt", "Sensor - FingerPrintTest:  FAILED" + "\r\n\r\n", MODE_APPEND);
            }

            contentRecord("testresult.txt", "SelfTestResult: " + selftestStr + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "CheckerBoardTestResult: " + checkerboardtestStr + "\r\n", MODE_APPEND);
            contentRecord("testresult.txt", "FingerDetectionTestResult: " + imagequalitytestStr + "\r\n", MODE_APPEND);

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
    public void onImagecapacitanceTestResult(int result){};
    @Override
    public void onImageresetpixelTestResult(int result){};
    @Override
    public void onAfdcalibrationTestResult(int result){};
    @Override
    public void onAfdcalibrationrubberstampTestResult(int result){};
    @Override
    public void onAfdrubberstampTestResult(int result){};
}
