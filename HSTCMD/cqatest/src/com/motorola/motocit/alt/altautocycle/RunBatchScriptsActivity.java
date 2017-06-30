/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.io.IOException;
import java.util.LinkedList;
import android.os.SystemProperties;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.BPBands;
import com.motorola.motocit.alt.altautocycle.util.TcmdSocket;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.PowerManager;
import android.os.SystemClock;

public class RunBatchScriptsActivity extends ALTBaseActivity
{

    private static final int MAX_LINES = 1000;

    private static final int BATCH_STATE_START_CALLED = 1000;
    private static final int BATCH_STATE_LED_TEST_COMPLETE = 1001;
    private static final int BATCH_STATE_IRRC_TEST_COMPLETE = 1002;
    private static final int BATCH_STATE_HALLEFFECT_TEST_COMPLETE = 1003;
    private static final int BATCH_STATE_BAND_TEST_COMPLETE = 1004;

    // Functional Batch Files
    private static final String LED_TEST_BATCH = "/system/etc/motorola/12m/batch/Autocycle_LED.bat";
    private static final String IRRC_TEST_BATCH = "/system/etc/motorola/12m/batch/Autocycle_IrRC.bat";
    private static final String HALLEFFECT_TEST_BATCH = "/system/etc/motorola/12m/batch/Autocycle_HallEffect.bat";

    // CDMA Batch Files
    private static final String CDMA_BC0_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_CDMA_BC0.bat";
    private static final String CDMA_BC1_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_CDMA_BC1.bat";
    private static final String CDMA_BC10_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_CDMA_BC10.bat";
    private static final String CDMA_BC15_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_CDMA_BC15.bat";
    private static final String CDMA_BC0_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_CDMA_BC0.bat";
    private static final String CDMA_BC1_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_CDMA_BC1.bat";
    private static final String CDMA_BC10_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_CDMA_BC10.bat";
    private static final String CDMA_BC15_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_CDMA_BC15.bat";

    // GSM Batch Files
    private static final String GSM_850_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_GSM850.bat";
    private static final String GSM_900_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_GSM900.bat";
    private static final String GSM_1800_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_GSM1800.bat";
    private static final String GSM_1900_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_GSM1900.bat";
    private static final String GSM_850_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_GSM850.bat";
    private static final String GSM_900_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_GSM900.bat";
    private static final String GSM_1800_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_GSM1800.bat";
    private static final String GSM_1900_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_GSM1900.bat";

    // WCDMA Batch Files
    private static final String WCDMA_800_JAPAN_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA800_Japan.bat";
    private static final String WCDMA_850_JAPAN_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA850_Japan.bat";
    private static final String WCDMA_850_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA850.bat";
    private static final String WCDMA_900_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA900.bat";
    private static final String WCDMA_1700_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA1700.bat";
    private static final String WCDMA_1900_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA1900.bat";
    private static final String WCDMA_2100_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_WCDMA2100.bat";
    private static final String WCDMA_800_JAPAN_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA800_Japan.bat";
    private static final String WCDMA_850_JAPAN_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA850_Japan.bat";
    private static final String WCDMA_850_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA850.bat";
    private static final String WCDMA_900_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA900.bat";
    private static final String WCDMA_1700_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA1700.bat";
    private static final String WCDMA_1900_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA1900.bat";
    private static final String WCDMA_2100_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_WCDMA2100.bat";

    // TD_SCDMA Batch Files
    private static final String TD_SCDMA_B34_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_TD_SCDMA_B34.bat";
    private static final String TD_SCDMA_B39_TX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Tx_TD_SCDMA_B39.bat";
    private static final String TD_SCDMA_B34_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_TD_SCDMA_B34.bat";
    private static final String TD_SCDMA_B39_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_Rx_TD_SCDMA_B39.bat";

    // LTE Batch Files
    private static final String LTE_B1_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B1.bat";
    private static final String LTE_B2_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B2.bat";
    private static final String LTE_B3_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B3.bat";
    private static final String LTE_B4_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B4.bat";
    private static final String LTE_B5_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B5.bat";
    private static final String LTE_B6_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B6.bat";
    private static final String LTE_B7_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B7.bat";
    private static final String LTE_B8_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B8.bat";
    private static final String LTE_B9_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B9.bat";
    private static final String LTE_B10_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B10.bat";
    private static final String LTE_B11_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B11.bat";
    private static final String LTE_B12_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B12.bat";
    private static final String LTE_B13_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B13.bat";
    private static final String LTE_B14_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B14.bat";
    private static final String LTE_B17_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B17.bat";
    private static final String LTE_B19_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B19.bat";
    private static final String LTE_B20_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B20.bat";
    private static final String LTE_B25_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B25.bat";
    private static final String LTE_B26_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B26.bat";
    private static final String LTE_B28_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B28.bat";
    private static final String LTE_B29_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B29.bat";
    private static final String LTE_B30_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B30.bat";
    private static final String LTE_B38_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B38.bat";
    private static final String LTE_B39_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B39.bat";
    private static final String LTE_B40_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B40.bat";
    private static final String LTE_B41_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B41.bat";
    private static final String LTE_B66_TX_RX_BATCH = "/system/etc/motorola/12m/batch/Autocycle_BP_LTE_B66.bat";

    private LinkedList<String> mLines = new LinkedList<String>();

    private ArrayAdapter<String> mAdapter = null;

    private RunBatchTask mBatchTask = null;
    private NotificationManager mNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        this.TAG = RunBatchScriptsActivity.class.getSimpleName();
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy()
    {
        if (mBatchTask != null)
        {
            mBatchTask.cancel(true);
            mBatchTask = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    void release()
    {}

    // Turn screen off
    public void turnScreenOff()
    {
        PowerManager pm =
                (PowerManager) getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

    // Turn screen on
    public void turnScreenOn()
    {
        PowerManager pm =
                (PowerManager) getSystemService(Context.POWER_SERVICE);
        pm.wakeUp(SystemClock.uptimeMillis());
    }

    protected void lightNotificationLED()
    {
        Notification notification = new Notification();
        notification.ledARGB = 0xffffffff;
        notification.ledOnMS = 300;
        notification.ledOffMS = 100;

        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        mNotificationManager.cancelAll();
        mNotificationManager.notify(0, notification);
        turnScreenOff();
    }

    @Override
    void init()
    {
        this.TAG = "RunBatchScriptsActivity";

        this.setTitle("Batch Scripts Test");
        setContentView(com.motorola.motocit.R.layout.alt_batch_result);

        ListView list_view = (ListView) findViewById(com.motorola.motocit.R.id.batch_result);

        mAdapter = new ArrayAdapter<String>(this, com.motorola.motocit.R.layout.alt_batch_result_text,
                mLines);

        // Assign adapter to list_view
        list_view.setAdapter(mAdapter);
        list_view.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    void start()
    {
        if (!this.isRunning)
        {
            this.isRunning = true;
            mHandler.sendEmptyMessage(BATCH_STATE_START_CALLED);
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case BATCH_STATE_START_CALLED:
                    mBatchTask = new RunBatchTask();
                    mBatchTask.execute(RunBatchTask.LED_TEST);
                    break;
                case BATCH_STATE_LED_TEST_COMPLETE:
                    mBatchTask = new RunBatchTask();
                    mBatchTask.execute(RunBatchTask.IRRC_TEST);
                    break;
                case BATCH_STATE_IRRC_TEST_COMPLETE:
                    mBatchTask = new RunBatchTask();
                    mBatchTask.execute(RunBatchTask.HALLEFFECT_TEST);
                    break;
                case BATCH_STATE_HALLEFFECT_TEST_COMPLETE:
                    mBatchTask = new RunBatchTask();
                    mBatchTask.execute(RunBatchTask.BAND_TEST);
                    break;
                case BATCH_STATE_BAND_TEST_COMPLETE:
                    TestUtils.dbgLog(TAG, "Reached the last test item, rebooting unit", 'i');
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    pm.reboot(null);
                    break;
                default:
                    break;
            }
        }
    };

    public class RunBatchTask extends AsyncTask<Integer, String, Integer> // <Params,
                                                                          // Progress,
                                                                          // Result>
    {
        protected static final int INTERRUPTED = -1;
        protected static final int LED_TEST = 0;
        protected static final int IRRC_TEST = 1;
        protected static final int HALLEFFECT_TEST = 2;
        protected static final int BAND_TEST = 3;

        @Override
        protected Integer doInBackground(Integer...batchType)
        {
            int response = INTERRUPTED;
            try
            {
                int testType = batchType[0].intValue();

                switch (testType)
                {
                    case LED_TEST:
                        doLedTest();
                        response = LED_TEST;
                        break;
                    case IRRC_TEST:
                        doIrRCTest();
                        response = IRRC_TEST;
                        break;
                    case HALLEFFECT_TEST:
                        doHallEffectTest();
                        response = HALLEFFECT_TEST;
                        break;
                    case BAND_TEST:
                        doBandTest();
                        response = BAND_TEST;
                        break;
                    default:
                        break;
                }
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, "RunBatchTask was interrupted");
                response = INTERRUPTED;
            }
            return response;
        }

        @Override
        protected void onProgressUpdate(String...progress)
        {
            for (String s : progress)
            {
                ListView listView = (ListView) findViewById(com.motorola.motocit.R.id.batch_result);

                if (mLines.size() > MAX_LINES)
                    mLines.removeFirst();

                mLines.addLast(s);

                ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
            }
            super.onProgressUpdate(progress);
        }

        @Override
        protected void onPostExecute(Integer result)
        {
            int res = result.intValue();
            switch (res)
            {
                case INTERRUPTED:
                    // Do nothing
                    Log.d(TAG, "onPostExecute: RunBatchTask Interrupted");
                    break;
                case LED_TEST:
                    mHandler.sendEmptyMessage(BATCH_STATE_LED_TEST_COMPLETE);
                    break;
                case IRRC_TEST:
                    mHandler.sendEmptyMessage(BATCH_STATE_IRRC_TEST_COMPLETE);
                    break;
                case HALLEFFECT_TEST:
                    mHandler.sendEmptyMessage(BATCH_STATE_HALLEFFECT_TEST_COMPLETE);
                    break;
                case BAND_TEST:
                    // For Golden Eagle, run bands test in BandTestActivity
                    if (Build.DEVICE.toLowerCase().contains("nash") == false) {
                        mHandler.sendEmptyMessage(BATCH_STATE_BAND_TEST_COMPLETE);
                    }
                    break;
                default:
                    break;
            }
        }

        private void doLedTest() throws InterruptedException
        {
            TcmdSocket tcmd_socket = null;
            String result = null;
            String product = SystemProperties.get("ro.hw.device", "unknow");
            publishProgress("LED Test: " + product);
/*
            if (product.equals("Styx") || "surnia".equals(product))
            {
                publishProgress("LED Test: " + product + " batch do not run,run the apk led testing");
                lightNotificationLED();
                for (int j = 0; j < 10; j++)
                    waitForMs(20000);
                turnScreenOn();
                mNotificationManager.cancelAll();
                return;
            }
*/
            try
            {
                tcmd_socket = new TcmdSocket();
                waitForMs(2000);
                Log.d(TAG, "Starting LED Test");
                startTest(LED_TEST_BATCH, tcmd_socket);
                publishProgress("LED Test: START");
                result = waitForResponse(tcmd_socket);
                publishProgress("LED Test: " + result);
                logWriter(TAG, "doLedTest, result=" + result);
            }
            catch (InterruptedException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                Log.e(TAG, "doLedTest Exception - " + e.getMessage() + " "
                        + Log.getStackTraceString(e));
            }
            finally
            {
                if (tcmd_socket != null)
                {
                    try
                    {
                        tcmd_socket.close();
                        tcmd_socket = null;
                    }
                    catch (IOException e)
                    {

                    }
                }
            }

        }

        private void doIrRCTest() throws InterruptedException
        {
            TcmdSocket tcmd_socket = null;
            String result = null;
            String product = SystemProperties.get("ro.hw.device", "unknow");
            if (product.contains("otus") || product.contains("surnia") ||
                product.contains("osprey") || product.toLowerCase().contains("athene") ||
                product.toLowerCase().contains("harpia") || product.toLowerCase().contains("albus") ||
                product.toLowerCase().contains("nash") || product.toLowerCase().contains("owens") ||
                product.toLowerCase().contains("perry") || product.toLowerCase().contains("montana") ||
                product.toLowerCase().contains("sanders"))
            {
                publishProgress("IrRC Test: " + product + " batch do not run");
                return;
            }

            try
            {
                tcmd_socket = new TcmdSocket();
                waitForMs(2000);
                Log.d(TAG, "Starting IrRC Test");
                startTest(IRRC_TEST_BATCH, tcmd_socket);
                publishProgress("IrRC Test: START");
                result = waitForResponse(tcmd_socket);
                publishProgress("IrRC Test: " + result);
                logWriter(TAG, "doIrRCTest, result=" + result);
            }
            catch (InterruptedException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                Log.e(TAG, "doIrRCTest Exception - " + e.getMessage() + " "
                        + Log.getStackTraceString(e));
            }
            finally
            {
                if (tcmd_socket != null)
                {
                    try
                    {
                        tcmd_socket.close();
                        tcmd_socket = null;
                    }
                    catch (IOException e)
                    {}
                }
            }
        }

        private void doHallEffectTest() throws InterruptedException
        {
            TcmdSocket tcmd_socket = null;
            String result = null;
            String product = SystemProperties.get("ro.hw.device", "unknow");
            if (product.contains("otus") || product.contains("surnia") ||
                product.toLowerCase().contains("athene") || product.toLowerCase().contains("harpia") ||
                product.toLowerCase().contains("albus") || product.toLowerCase().contains("nash") ||
                product.toLowerCase().contains("owens") || product.toLowerCase().contains("perry") ||
                product.toLowerCase().contains("montana") || product.toLowerCase().contains("sanders"))
            {
                publishProgress("Hall Test: " + product + " batch do not run");
                return;
            }

            try
            {
                tcmd_socket = new TcmdSocket();
                waitForMs(2000);
                Log.d(TAG, "Starting Hall Effect Test");
                startTest(HALLEFFECT_TEST_BATCH, tcmd_socket);
                publishProgress("Hall Effect Test: START");
                result = waitForResponse(tcmd_socket);
                publishProgress("Hall Effect Test: " + result);
                logWriter(TAG, "doHallEffectTest, result=" + result);
            }
            catch (InterruptedException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                Log.e(TAG, "doHallEffectTest Exception - " + e.getMessage() + " "
                        + Log.getStackTraceString(e));
            }
            finally
            {
                if (tcmd_socket != null)
                {
                    try
                    {
                        tcmd_socket.close();
                        tcmd_socket = null;
                    }
                    catch (IOException e)
                    {}
                }
            }
        }

        private void doBandTest() throws InterruptedException
        {
            // For Golden Eagle, run bands test in BandTestActivity
            if (Build.DEVICE.toLowerCase().contains("nash"))
            {
                startActivity(new Intent(RunBatchScriptsActivity.this.getApplicationContext(), BandTestActivity.class));
                finish();
                return;
            }

            TcmdSocket tcmd_socket = null;
            try
            {
                BPBands bpBands = BPBands.getInstance(RunBatchScriptsActivity.this.getApplicationContext());
                String result = null;
                Log.d(TAG, "Testing supported bands");
                tcmd_socket = new TcmdSocket();

                waitForMs(5000);

                boolean exitRFMode = false;

                // CDMA
                if (bpBands.isCDMABC0())
                {
                    Log.d(TAG, "Testing CDMA BC0 TX");
                    startTest(CDMA_BC0_TX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC0 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC0 TX: " + result);
                    logWriter(TAG, "CDMA BC0 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing CDMA BC0 RX");
                    startTest(CDMA_BC0_RX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC0 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC0 RX: " + result);
                    logWriter(TAG, "CDMA BC0 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isCDMABC1())
                {
                    Log.d(TAG, "Testing CDMA BC1 TX");
                    startTest(CDMA_BC1_TX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC1 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC1 TX: " + result);
                    logWriter(TAG, "CDMA BC1 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing CDMA BC1 RX");
                    startTest(CDMA_BC1_RX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC1 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC1 RX: " + result);
                    logWriter(TAG, "CDMA BC1 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isCDMABC10())
                {
                    Log.d(TAG, "Testing CDMA BC10 TX");
                    startTest(CDMA_BC10_TX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC10 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC10 TX: " + result);
                    logWriter(TAG, "CDMA BC10 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing CDMA BC10 RX");
                    startTest(CDMA_BC10_RX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC10 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC10 RX: " + result);
                    logWriter(TAG, "CDMA BC10 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isCDMABC15())
                {
                    Log.d(TAG, "Testing CDMA BC15 TX");
                    startTest(CDMA_BC15_TX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC15 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC15 TX: " + result);
                    logWriter(TAG, "CDMA BC15 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing CDMA BC15 RX");
                    startTest(CDMA_BC15_RX_BATCH, tcmd_socket);
                    publishProgress("CDMA BC15 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("CDMA BC15 RX: " + result);
                    logWriter(TAG, "CDMA BC15 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }

                if (exitRFMode) {
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }

                // GSM
                if (bpBands.isGSM850())
                {
                    Log.d(TAG, "Testing GSM 850 TX");
                    startTest(GSM_850_TX_BATCH, tcmd_socket);
                    publishProgress("GSM 850 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 850 TX: " + result);
                    logWriter(TAG, "GSM 850 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing GSM 850 RX");
                    startTest(GSM_850_RX_BATCH, tcmd_socket);
                    publishProgress("GSM 850 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 850 RX: " + result);
                    logWriter(TAG, "GSM 850 RX, result=" + result);
                    waitForMs(1000);

                    // GSM850 on Vector ROW/China has diversity
                    // Need call FTM_RF_MODE_EXIT after GSM850
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }
                if (bpBands.isGSM900())
                {
                    Log.d(TAG, "Testing GSM 900 TX");
                    startTest(GSM_900_TX_BATCH, tcmd_socket);
                    publishProgress("GSM 900 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 900 TX: " + result);
                    logWriter(TAG, "GSM 900 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing GSM 900 RX");
                    startTest(GSM_900_RX_BATCH, tcmd_socket);
                    publishProgress("GSM 900 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 900 RX: " + result);
                    logWriter(TAG, "GSM900 RX, result=" + result);
                    waitForMs(1000);

                    // GSM900 on Vector/Vertex/Affinity China has diversity
                    // Need call FTM_RF_MODE_EXIT after GSM900
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }
                if (bpBands.isGSM1800())
                {
                    Log.d(TAG, "Testing GSM 1800 TX");
                    startTest(GSM_1800_TX_BATCH, tcmd_socket);
                    publishProgress("GSM 1800 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 1800 TX: " + result);
                    logWriter(TAG, "GSM 1800 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing GSM 1800 RX");
                    startTest(GSM_1800_RX_BATCH, tcmd_socket);
                    publishProgress("GSM 1800 RX: START");

                    waitForResponse(tcmd_socket);
                    publishProgress("GSM 1800 RX: " + result);
                    logWriter(TAG, "GSM 1800 RX, result=" + result);
                    waitForMs(1000);

                    // GSM1800 on Vector/Vertex/Affinity China has diversity
                    // Need call FTM_RF_MODE_EXIT after GSM1800
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }
                if (bpBands.isGSM1900())
                {
                    Log.d(TAG, "Testing GSM 1900 TX");
                    startTest(GSM_1900_TX_BATCH, tcmd_socket);
                    publishProgress("GSM 1900 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 1900 TX: " + result);
                    logWriter(TAG, "GSM 1900 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing GSM 1900 RX");
                    startTest(GSM_1900_RX_BATCH, tcmd_socket);
                    publishProgress("GSM 1900 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("GSM 1900 RX: " + result);
                    logWriter(TAG, "GSM 1900 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }

                if (exitRFMode) {
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }

                // WCDMA
                if (bpBands.isWCDMA800Japan())
                {
                    Log.d(TAG, "Testing WCDMA 800 Japan TX");
                    startTest(WCDMA_800_JAPAN_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 800 Japan TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 800 Japan TX: " + result);
                    logWriter(TAG, "WCDMA 800 Japan TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 800 Japan RX");
                    startTest(WCDMA_800_JAPAN_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 800 Japan RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 800 Japan RX: " + result);
                    logWriter(TAG, "WCDMA 800 Japan RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA850Japan())
                {
                    Log.d(TAG, "Testing WCDMA 850 Japan TX");
                    startTest(WCDMA_850_JAPAN_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 850 Japan TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 850 Japan TX: " + result);
                    logWriter(TAG, "WCDMA 850 Japan TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 850 Japan RX");
                    startTest(WCDMA_850_JAPAN_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 850 Japan RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 850 Japan RX: " + result);
                    logWriter(TAG, "WCDMA 850 Japan RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA850())
                {
                    Log.d(TAG, "Testing WCDMA 850 TX");
                    startTest(WCDMA_850_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 850 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 850 TX: " + result);
                    logWriter(TAG, "WCDMA 850 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 850 RX");
                    startTest(WCDMA_850_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 850 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 850 RX: " + result);
                    logWriter(TAG, "WCDMA 850 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA900())
                {
                    Log.d(TAG, "Testing WCDMA 900 TX");
                    startTest(WCDMA_900_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 900 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 900 TX: " + result);
                    logWriter(TAG, "WCDMA 900 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 900 RX");
                    startTest(WCDMA_900_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 900 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 900 RX: " + result);
                    logWriter(TAG, "WCDMA 900 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA1700())
                {
                    Log.d(TAG, "Testing WCDMA 1700 TX");
                    startTest(WCDMA_1700_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 1700 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 1700 TX: " + result);
                    logWriter(TAG, "WCDMA 1700 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 1700 RX");
                    startTest(WCDMA_1700_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 1700 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 1700 RX: " + result);
                    logWriter(TAG, "WCDMA 1700 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA1900())
                {
                    Log.d(TAG, "Testing WCDMA 1900 TX");
                    startTest(WCDMA_1900_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 1900 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 1900 TX: " + result);
                    logWriter(TAG, "WCDMA 1900 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 1900 RX");
                    startTest(WCDMA_1900_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 1900 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 1900 RX: " + result);
                    logWriter(TAG, "WCDMA 1900 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isWCDMA2100())
                {
                    Log.d(TAG, "Testing WCDMA 2100 TX");
                    startTest(WCDMA_2100_TX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 2100 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 2100 TX: " + result);
                    logWriter(TAG, "WCDMA 2100 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing WCDMA 2100 RX");
                    startTest(WCDMA_2100_RX_BATCH, tcmd_socket);
                    publishProgress("WCDMA 2100 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("WCDMA 2100 RX: " + result);
                    logWriter(TAG, "WCDMA 2100 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }

                if (exitRFMode) {
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }

                // TD_SCDMA
                if (bpBands.isTDSCDMAB34())
                {
                    Log.d(TAG, "Testing TD_SCDMA B34 TX");
                    startTest(TD_SCDMA_B34_TX_BATCH, tcmd_socket);
                    publishProgress("TD_SCDMA B34 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("TD_SCDMA B34 TX: " + result);
                    logWriter(TAG, "TD_SCDMA B34 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing TD_SCDMA B34 RX");
                    startTest(TD_SCDMA_B34_RX_BATCH, tcmd_socket);
                    publishProgress("TD_SCDMA B34 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("TD_SCDMA B34 RX: " + result);
                    logWriter(TAG, "TD_SCDMA B34 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }
                if (bpBands.isTDSCDMAB39())
                {
                    Log.d(TAG, "Testing TD_SCDMA B39 TX");
                    startTest(TD_SCDMA_B39_TX_BATCH, tcmd_socket);
                    publishProgress("TD_SCDMA B39 TX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("TD_SCDMA B39 TX: " + result);
                    logWriter(TAG, "TD_SCDMA B39 TX, result=" + result);
                    waitForMs(1000);

                    Log.d(TAG, "Testing TD_SCDMA B39 RX");
                    startTest(TD_SCDMA_B39_RX_BATCH, tcmd_socket);
                    publishProgress("TD_SCDMA B39 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("TD_SCDMA B39 RX: " + result);
                    logWriter(TAG, "TD_SCDMA B39 RX, result=" + result);
                    waitForMs(1000);

                    exitRFMode = true;
                }

                if (exitRFMode) {
                    exitRFMode(tcmd_socket);
                    exitRFMode = false;
                    waitForMs(1000);
                }

                // LTE
                if (bpBands.isLTEB1())
                {
                    Log.d(TAG, "Testing LTE B1 TX RX");
                    startTest(LTE_B1_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B1 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B1 TX RX: " + result);
                    logWriter(TAG, "LTE B1 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB2())
                {
                    Log.d(TAG, "Testing LTE B2 TX RX");
                    startTest(LTE_B2_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B2 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B2 TX RX: " + result);
                    logWriter(TAG, "LTE B2 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB3())
                {
                    Log.d(TAG, "Testing LTE B3 TX RX");
                    startTest(LTE_B3_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B3 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B3 TX RX: " + result);
                    logWriter(TAG, "LTE B3 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB4())
                {
                    Log.d(TAG, "Testing LTE B4 TX RX");
                    startTest(LTE_B4_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B4 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B4 TX RX: " + result);
                    logWriter(TAG, "LTE B4 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB5())
                {
                    Log.d(TAG, "Testing LTE B5 TX RX");
                    startTest(LTE_B5_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B5 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B5 TX RX: " + result);
                    logWriter(TAG, "LTE B5 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB6())
                {
                    Log.d(TAG, "Testing LTE B6 TX RX");
                    startTest(LTE_B6_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B6 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B6 TX RX: " + result);
                    logWriter(TAG, "LTE B6 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB7())
                {
                    Log.d(TAG, "Testing LTE B7 TX RX");
                    startTest(LTE_B7_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B7 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B7 TX RX: " + result);
                    logWriter(TAG, "LTE B7 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB8())
                {
                    Log.d(TAG, "Testing LTE B8 TX RX");
                    startTest(LTE_B8_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B8 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B8 TX RX: " + result);
                    logWriter(TAG, "LTE B8 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB9())
                {
                    Log.d(TAG, "Testing LTE B9 TX RX");
                    startTest(LTE_B9_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B9 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B9 TX RX: " + result);
                    logWriter(TAG, "LTE B9 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB10())
                {
                    Log.d(TAG, "Testing LTE B10 TX RX");
                    startTest(LTE_B10_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B10 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B10 TX RX: " + result);
                    logWriter(TAG, "LTE B10 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB11())
                {
                    Log.d(TAG, "Testing LTE B11 TX RX");
                    startTest(LTE_B11_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B11 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B11 TX RX: " + result);
                    logWriter(TAG, "LTE B11 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB12())
                {
                    Log.d(TAG, "Testing LTE B12 TX RX");
                    startTest(LTE_B12_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B12 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B12 TX RX: " + result);
                    logWriter(TAG, "LTE B12 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB13())
                {
                    Log.d(TAG, "Testing LTE B13 TX RX");
                    startTest(LTE_B13_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B13 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B13 TX RX: " + result);
                    logWriter(TAG, "LTE B13 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB14())
                {
                    Log.d(TAG, "Testing LTE B14 TX RX");
                    startTest(LTE_B14_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B14 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B14 TX RX: " + result);
                    logWriter(TAG, "LTE B14 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB17())
                {
                    Log.d(TAG, "Testing LTE B17 TX RX");
                    startTest(LTE_B17_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B17 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B17 TX RX: " + result);
                    logWriter(TAG, "LTE B17 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB19())
                {
                    Log.d(TAG, "Testing LTE B19 TX RX");
                    startTest(LTE_B19_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B19 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B19 TX RX: " + result);
                    logWriter(TAG, "LTE B19 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB20())
                {
                    Log.d(TAG, "Testing LTE B20 TX RX");
                    startTest(LTE_B20_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B20 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B20 TX RX: " + result);
                    logWriter(TAG, "LTE B20 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB25())
                {
                    Log.d(TAG, "Testing LTE B25 TX RX");
                    startTest(LTE_B25_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B25 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B25 TX RX: " + result);
                    logWriter(TAG, "LTE B25 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB26())
                {
                    Log.d(TAG, "Testing LTE B26 TX RX");
                    startTest(LTE_B26_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B26 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B26 TX RX: " + result);
                    logWriter(TAG, "LTE B26 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB28())
                {
                    Log.d(TAG, "Testing LTE B28 TX RX");
                    startTest(LTE_B28_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B28 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B28 TX RX: " + result);
                    logWriter(TAG, "LTE B28 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB29())
                {
                    Log.d(TAG, "Testing LTE B29 RX");
                    startTest(LTE_B29_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B29 RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B29 RX: " + result);
                    logWriter(TAG, "LTE B29 RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB30())
                {
                    Log.d(TAG, "Testing LTE B30 TX RX");
                    startTest(LTE_B30_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B30 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B30 TX RX: " + result);
                    logWriter(TAG, "LTE B30 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB38())
                {
                    Log.d(TAG, "Testing LTE B38 TX RX");
                    startTest(LTE_B38_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B38 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B38 TX RX: " + result);
                    logWriter(TAG, "LTE B38 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB39())
                {
                    Log.d(TAG, "Testing LTE B39 TX RX");
                    startTest(LTE_B39_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B39 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B39 TX RX: " + result);
                    logWriter(TAG, "LTE B39 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB40())
                {
                    Log.d(TAG, "Testing LTE B40 TX RX");
                    startTest(LTE_B40_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B40 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B40 TX RX: " + result);
                    logWriter(TAG, "LTE B40 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB41())
                {
                    Log.d(TAG, "Testing LTE B41 TX RX");
                    startTest(LTE_B41_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B41 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B41 TX RX: " + result);
                    logWriter(TAG, "LTE B41 TX RX, result=" + result);
                    waitForMs(1000);
                }
                if (bpBands.isLTEB66())
                {
                    Log.d(TAG, "Testing LTE B66 TX RX");
                    startTest(LTE_B66_TX_RX_BATCH, tcmd_socket);
                    publishProgress("LTE B66 TX RX: START");
                    result = waitForResponse(tcmd_socket);
                    publishProgress("LTE B66 TX RX: " + result);
                    logWriter(TAG, "LTE B66 TX RX, result=" + result);
                    waitForMs(1000);
                }
                Log.d(TAG, "Done Testing supported bands");
                publishProgress("BANDS TEST COMPLETED");
                logWriter("************************************************** ALT auto cycle test", "COMPLETED");
            }
            catch (InterruptedException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                Log.e(TAG, "testSupportedBands Exception - " + e.getMessage()
                        + " " + Log.getStackTraceString(e));
            }
            finally
            {
                if (tcmd_socket != null)
                {
                    try
                    {
                        tcmd_socket.close();
                        tcmd_socket = null;
                    }
                    catch (IOException e)
                    {

                    }
                }
            }

            // wait for 5 second before reboot, so it is possible to check last
            // band test status
            waitForMs(5000);
        }

        private void startTest(String fileName, TcmdSocket socket)
        {
            String batchData = new String(TcmdSocket.TCMD_BATCHDATA_PREFIX);
            byte[] filePath = fileName.getBytes();
            for (int i = 0; i < fileName.length(); i++)
            {
                batchData += String.format("%04X", filePath[i]);
            }
            Log.d(TAG, "batchData = " + batchData);

            try
            {
                socket.send(TcmdSocket.TCMD_BATCH_OPCODE + batchData + "0000");
            }
            catch (Exception e)
            {
                Log.e(TAG, "startTest Exception - " + e.getMessage() + " "
                        + Log.getStackTraceString(e));
            }
        }

        // execute FTM_RF_MODE_EXIT at the end of each RAT technology,
        // to avoid BP panic when switch to another RAT technology, between 2 WTR
        private void exitRFMode(TcmdSocket socket) {
            Log.d(TAG, "send FTM_RF_MODE_EXIT");
            try {
                // FTM_RF_MODE_EXIT
                socket.send("0FF0044B0B1400660200000C00");
            } catch (Exception e) {
                Log.e(TAG, "FTM_RF_MODE_EXIT Exception - " + e.getMessage() + " "
                        + Log.getStackTraceString(e));
            }
        }

        private boolean responseFlag(byte data)
        {
            if ((data & 0x80) == 0x80)
            {
                return true;
            }
            else
            {
                return false;
            }
        }

        private String waitForResponse(TcmdSocket socket)
                throws InterruptedException
        {
            String error = "SUCCESS";
            for (;;)
            {
                boolean exitLoop = false;
                try
                {
                    Thread.sleep(500);

                    TcmdSocket.TcmdResponseData responseData = socket.receive();
                    if (responseData == null)
                    {
                        Log.e(TAG, "Response null");
                        break;
                    }
                    byte[] response = responseData.getData();
                    TcmdSocket.TcmdParsedResponse parsedResponse = TcmdSocket
                            .parseTcmdResponse(response);
                    Log.d(TAG, "Response - " + parsedResponse.getParsedResponse() +
                            ", bytesProcessed - " + parsedResponse.getBytesProcessed());
                    int bytesLeft = responseData.getDataLength();
                    int currentByte = 0;
                    while (bytesLeft > 0)
                    {
                        int resp_data_length = 256 * (response[currentByte + 10] & 0xFF)
                                + (response[currentByte + 11] & 0xFF);
                        Log.d(TAG, "Response Data Length - " + resp_data_length);

                        if (!responseFlag(response[currentByte + 0]))
                        {// Get response flag bit
                            Log.e(TAG, "Response - Corrupted");
                            error = "FAIL: Response Corrupted!";
                            exitLoop = true;
                            break;
                        }
                        else
                        {
                            if (resp_data_length == 2)
                            {
                                Log.d(TAG, "Solicited Response DATA: " + String.format(
                                        "%02X %02X",
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1]));
                                if (((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH] & 0xFF) * 256 + (response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1] & 0xFF)) == 0)
                                {
                                    Log.d(TAG, "Batch file started successfully!");
                                }
                                else
                                {
                                    Log.e(TAG, "FAILED to start batch file! Skipping.");
                                    error = "FAIL: FAILED to start batch file! Skipping.";
                                    exitLoop = true;
                                    break;
                                }
                            }
                            else if (resp_data_length >= 4)
                            {
                                Log.d(TAG, "DATA = " + String.format(
                                        "%02X %02X %02X %02X",
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3]));
                                if (((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH] & 0xFF) * 256 + (response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1] & 0xFF)) == 32768)
                                {
                                    Log.d(TAG, "Batch File execution complete. Response code = " + String.format(
                                            "%02X %02X",
                                            response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2],
                                            response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3]));
                                    if ((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2] & 0xFF) * 256
                                            + (response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3] & 0xFF) != 0)
                                    {
                                        error = "FAIL: Batch Response Code = "
                                                + String.format(
                                                        "%02X %02X",
                                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2],
                                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3]);
                                    }
                                    exitLoop = true;
                                    break;
                                }
                                /*
                                 * if ((response[currentByte +
                                 * TCMD_HEADER_LENGTH] == 128) &&
                                 * (response[currentByte + TCMD_HEADER_LENGTH +
                                 * 1] == 0)) { Log.i(TAG,
                                 * "Batch File execution complete. Response code = "
                                 * +
                                 * String.format("%02X %02X",
                                 * response[currentByte + TCMD_HEADER_LENGTH +
                                 * 2],
                                 * response[currentByte + TCMD_HEADER_LENGTH +
                                 * 3])); break; }
                                 */
                            }
                            bytesLeft = bytesLeft - (TcmdSocket.TCMD_HEADER_LENGTH + resp_data_length);
                            currentByte = currentByte + TcmdSocket.TCMD_HEADER_LENGTH + resp_data_length;
                            Log.d(TAG, "Bytes left - " + bytesLeft + ", Current Byte - " + currentByte);
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Exception waitForResponse - " + e.getMessage()
                            + " " + Log.getStackTraceString(e));
                }
                if (exitLoop)
                {
                    Log.d(TAG, "Exit for loop");
                    break;
                }
            }
            return error;
        }

        private void waitForMs(long millis) throws InterruptedException
        {
            try
            {
                Thread.sleep(millis);
            }
            catch (InterruptedException e)
            {
                throw e;
            }
        }
    }
}
