/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle;

import java.io.IOException;
import java.util.LinkedList;

import com.motorola.motocit.TestUtils;
import com.motorola.motocit.alt.altautocycle.util.TcmdSocket;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;

public class RunModemOnlineActivity extends Activity
{

    private static final String TAG = "RunModemOnlineActivity";

    private static final String MODEM_ONLINE_BATCH = "/system/etc/motorola/12m/batch/Autocycle_Online_Dmss.bat";

    private static final int BATCH_STATE_STARTED = 1000;
    private static final int BATCH_STATE_COMPLETED = 1001;

    private LinkedList<String> mLines = new LinkedList<String>();

    private ArrayAdapter<String> mAdapter = null;

    private RunBatchTask mBatchTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        TestUtils.dbgLog(TAG, "onCreate", 'i');
        init();
        mHandler.sendEmptyMessage(BATCH_STATE_STARTED);
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

    void init()
    {
        this.setTitle("Modem Online/DMSS");
        setContentView(com.motorola.motocit.R.layout.alt_batch_result);
        ListView list_view = (ListView) findViewById(com.motorola.motocit.R.id.batch_result);
        mAdapter = new ArrayAdapter<String>(this, com.motorola.motocit.R.layout.alt_batch_result_text, mLines);
        // Assign adapter to list_view
        list_view.setAdapter(mAdapter);
        list_view.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case BATCH_STATE_STARTED:
                    mBatchTask = new RunBatchTask();
                    mBatchTask.execute(RunBatchTask.MODEM_ONLINE);
                    break;
                case BATCH_STATE_COMPLETED:
                    if (mBatchTask != null)
                    {
                        mBatchTask.cancel(true);
                        mBatchTask = null;
                    }
                    break;
            }
        }
    };

    public class RunBatchTask extends AsyncTask<Integer, String, Integer>
    {
        protected static final int INTERRUPTED = -1;
        protected static final int MODEM_ONLINE = 0;

        @Override
        protected Integer doInBackground(Integer...batchType)
        {
            int response = INTERRUPTED;
            try
            {
                int testType = batchType[0].intValue();
                switch (testType)
                {
                    case MODEM_ONLINE:
                        doModemOnline();
                        response = MODEM_ONLINE;
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
                    TestUtils.dbgLog(TAG, "onPostExecute: RunBatchTask Interrupted", 'i');
                    break;
                case MODEM_ONLINE:
                    mHandler.sendEmptyMessage(BATCH_STATE_COMPLETED);
                    break;
                default:
                    break;
            }
        }

        private void doModemOnline() throws InterruptedException
        {
            TcmdSocket tcmd_socket = null;
            String response = null;
            try
            {
                tcmd_socket = new TcmdSocket();
                waitForMs(2000);
                publishProgress("Starting modem online...");
                startTest(MODEM_ONLINE_BATCH, tcmd_socket);
                response = waitForResponse(tcmd_socket);
                publishProgress("modem online: " + response);
            }
            catch (InterruptedException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                TestUtils.dbgLog(TAG, "doModemOnline Exception - " + e.getMessage() + " " + Log.getStackTraceString(e), 'e');
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
            TestUtils.dbgLog(TAG, "batchData = " + batchData, 'i');

            try
            {
                socket.send(TcmdSocket.TCMD_BATCH_OPCODE + batchData + "0000");
            }
            catch (Exception e)
            {
                TestUtils.dbgLog(TAG, "startTest Exception - " + e.getMessage() + " " + Log.getStackTraceString(e), 'e');
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
                    TestUtils.dbgLog(TAG, "Response - " + parsedResponse.getParsedResponse() + ", bytesProcessed - " + parsedResponse.getBytesProcessed(), 'i');
                    int bytesLeft = responseData.getDataLength();
                    int currentByte = 0;
                    while (bytesLeft > 0)
                    {
                        int resp_data_length = 256 * (response[currentByte + 10] & 0xFF) + (response[currentByte + 11] & 0xFF);
                        TestUtils.dbgLog(TAG, "Response Data Length - " + resp_data_length, 'i');

                        // Get response flag bit
                        if (!responseFlag(response[currentByte + 0]))
                        {
                            TestUtils.dbgLog(TAG, "Response - Corrupted", 'e');
                            error = "FAIL: Response Corrupted!";
                            exitLoop = true;
                            break;
                        }
                        else
                        {
                            if (resp_data_length == 2)
                            {
                                TestUtils.dbgLog(TAG, "Solicited Response DATA: " + String.format("%02X %02X", response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1]), 'i');
                                if (((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH] & 0xFF) * 256 + (response[currentByte
                                        + TcmdSocket.TCMD_HEADER_LENGTH + 1] & 0xFF)) == 0)
                                {
                                    TestUtils.dbgLog(TAG, "Batch file started successfully!", 'i');
                                }
                                else
                                {
                                    TestUtils.dbgLog(TAG, "FAILED to start batch file! Skipping.", 'e');
                                    error = "FAIL: FAILED to start batch file! Skipping.";
                                    exitLoop = true;
                                    break;
                                }
                            }
                            else if (resp_data_length >= 4)
                            {
                                TestUtils.dbgLog(TAG, "DATA = " + String.format("%02X %02X %02X %02X", response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1], response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2],
                                        response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3]), 'i');
                                if (((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH] & 0xFF) * 256 + (response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 1] & 0xFF)) == 32768)
                                {
                                    TestUtils.dbgLog(TAG, "Batch File execution complete. Response code = " + String.format("%02X %02X", response[currentByte
                                            + TcmdSocket.TCMD_HEADER_LENGTH + 2], response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3]), 'i');
                                    if ((response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2] & 0xFF) * 256 + (response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 3] & 0xFF) != 0)
                                    {
                                        error = "FAIL: Batch Response Code = " + String.format("%02X %02X", response[currentByte + TcmdSocket.TCMD_HEADER_LENGTH + 2], response[currentByte
                                                + TcmdSocket.TCMD_HEADER_LENGTH + 3]);
                                    }
                                    exitLoop = true;
                                    break;
                                }
                            }
                            bytesLeft = bytesLeft - (TcmdSocket.TCMD_HEADER_LENGTH + resp_data_length);
                            currentByte = currentByte + TcmdSocket.TCMD_HEADER_LENGTH + resp_data_length;
                            TestUtils.dbgLog(TAG, "Bytes left - " + bytesLeft + ", Current Byte - " + currentByte, 'i');
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    throw e;
                }
                catch (Exception e)
                {
                    TestUtils.dbgLog(TAG, "Exception waitForResponse - " + e.getMessage() + " " + Log.getStackTraceString(e), 'e');
                }
                if (exitLoop)
                {
                    TestUtils.dbgLog(TAG, "Exit for loop", 'i');
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
