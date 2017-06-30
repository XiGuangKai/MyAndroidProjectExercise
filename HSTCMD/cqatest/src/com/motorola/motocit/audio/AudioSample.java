/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.Test_Base;

public class AudioSample extends Test_Base
{
    private TextView audioSampleRateView;
    private TextView audioSampleLengthView;
    private TextView audioSampleDiscardLengthView;
    private TextView audioSampleSourceView;
    private TextView audioSampleMicSelectView;
    private TextView audioSampleFormatView;
    private TextView audioSampleTimeoutView;
    private TextView audioSampleStatusView;

    private final static String MIC_KEY = "mic_path";
    private final static String PRIMARY_MIC_PATH = "primary";
    private final static String SECONDARY_MIC_PATH = "secondary";
    private final static String TERTIARY_MIC_PATH = "tertiary";
    private final static String HEADSET_MIC_PATH = "headset";
    private final static String MIC_4_PATH = "mic4";
    private final static String MIC_5_PATH = "mic5";
    private final static String MIC_PATH_NONE = "none";

    // Path needed for low power audio, from MediaRecorder.java
    private static final int AUDIO_SOURCE_MOT_VR_SOURCE = 11;

    // settings that can be changed by user
    private int sampleRate = 8000;
    private int sampleLengthMs = 20;
    private int audioCaptureTimeoutMs = 5000;
    private int audioSampleDiscardTimeMs = 0;
    private int audioInputSource = AudioSource.DEFAULT;
    private int audioInputFormat = AudioFormat.CHANNEL_IN_MONO;
    private String audioMicSelect = MIC_PATH_NONE;
    private int mNumberOfChannelsSampled = 0;

    private AudioManager mAudioManager;

    private boolean mIsAudioSampleServiceBound = false;

    private final static int GET_CURRENT_SAMPLE_TIMEOUT_MS = 10000;
    private final static int DEFAULT_SAMPLE_BUFFER_DEPTH = 1;

    public class AudioSampleSettings
    {
        public String micKey = MIC_KEY;
        public String audioMicSelect = MIC_PATH_NONE;
        public int sampleRate = 8000;
        public int sampleLengthMs = 20;
        public int audioCaptureTimeoutMs = 5000;
        public int audioSampleDiscardTimeMs = 0;
        public int audioInputSource = AudioSource.DEFAULT;
        public int audioInputFormat = AudioFormat.CHANNEL_IN_MONO;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_Sample";
        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_sample);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        audioSampleRateView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_rate);
        audioSampleLengthView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_length);
        audioSampleDiscardLengthView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_discard_length);
        audioSampleSourceView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_source);
        audioSampleMicSelectView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_mic_select);
        audioSampleFormatView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_format);
        audioSampleTimeoutView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_timeout);
        audioSampleStatusView = (TextView) findViewById(com.motorola.motocit.R.id.audio_sample_status);

        UpdateUiTextView updateUiSampleRate = new UpdateUiTextView(audioSampleRateView, Color.WHITE, Integer.toString(sampleRate));
        updateUiSampleRate.run();

        UpdateUiTextView updateUiSampleLength = new UpdateUiTextView(audioSampleLengthView, Color.WHITE, Integer.toString(sampleLengthMs));
        updateUiSampleLength.run();

        UpdateUiTextView updateUiSampleSource = new UpdateUiTextView(audioSampleSourceView, Color.WHITE, getAudioInputSource());
        updateUiSampleSource.run();

        UpdateUiTextView updateUiSampleMicSelect = new UpdateUiTextView(audioSampleMicSelectView, Color.WHITE, audioMicSelect);
        updateUiSampleMicSelect.run();

        UpdateUiTextView updateUiSampleFormat = new UpdateUiTextView(audioSampleFormatView, Color.WHITE, getAudioInputFormat());
        updateUiSampleFormat.run();

        UpdateUiTextView updateUiSampleTimeout = new UpdateUiTextView(audioSampleTimeoutView, Color.WHITE, Integer.toString(audioCaptureTimeoutMs));
        updateUiSampleTimeout.run();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (null == mAudioManager)
        {
            sendStartActivityFailed("Could not get reference to AudioManager");
        }
        else
        {
            sendStartActivityPassed();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    public void onDestroy()
    {
        // set mic select back to none
        if (null != mAudioManager)
        {
            mAudioManager.setParameters(MIC_KEY + "=" + MIC_PATH_NONE);
        }

        super.onDestroy();
        unbindAudioSampleService();
    }

    private boolean audioCapture16BitPCM(int timeoutMs, StringBuffer sbStatusMesg,
            short[] sampleBufferLeft, short[] sampleBufferRight,
            int inputSampleRate, int inputSource, int inputFormat)
    {
        dbgLog(TAG, "Start audioCapture16BitPCM", 'd');

        // okay to change thread priority because
        // handleTestSpecificActions() is called by Test_Base in a new thread.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        AudioRecord recorder = null;
        boolean sampleStatus = false;
        boolean firstSample = false;

        try
        {
            String keyPair = MIC_KEY + "=" + audioMicSelect;
            dbgLog(TAG, "Setting mic select with keyPair '" + keyPair + "'", 'd');

            mAudioManager.setParameters(keyPair);

            int audioRecordBufferSize = AudioRecord.getMinBufferSize(inputSampleRate, inputFormat, AudioFormat.ENCODING_PCM_16BIT);

            recorder = new AudioRecord(inputSource, inputSampleRate, inputFormat, AudioFormat.ENCODING_PCM_16BIT, audioRecordBufferSize);

            // Report back the number of channels
            mNumberOfChannelsSampled = recorder.getChannelCount();

            if ((firstSample == false) && (audioInputSource == AUDIO_SOURCE_MOT_VR_SOURCE))
            {
                try
                {
                    Thread.sleep(1000, 0);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                AudioUtils.setDspAudioPath(getApplicationContext());
                firstSample = true;
            }

            recorder.startRecording();

            // until we fill sampleBuffer
            short[] tempBuffer = new short[audioRecordBufferSize];

            int totalShortsRead = 0;

            long startTime = System.currentTimeMillis();

            while (sampleBufferLeft.length > totalShortsRead)
            {
                // see if hit timeout
                if ((System.currentTimeMillis() - startTime) > timeoutMs)
                {
                    dbgLog(TAG, "audioCapture16BitPCM() timeout reached", 'd');
                    throw new Exception("audioCapture16BitPCM() timeout reached. Timeout (ms) is " + timeoutMs);
                }

                int shortsRead = recorder.read(tempBuffer, 0, tempBuffer.length);

                if (shortsRead < 0)
                {
                    throw new Exception("AudioRecord.read() returned error " + shortsRead);
                }

                dbgLog(TAG, "audioCapture16BitPCM read " + shortsRead + " shorts", 'd');

                // append to sampleBuffer
                for (int i = 0; (i < shortsRead) && (sampleBufferLeft.length > totalShortsRead); i++)
                {
                    sampleBufferLeft[totalShortsRead] = tempBuffer[i];

                    if (mNumberOfChannelsSampled == 2)
                    {
                        i++;
                        sampleBufferRight[totalShortsRead] = tempBuffer[i];
                    }

                    totalShortsRead++;
                }
            }

            recorder.stop();
            recorder.release();

            sampleStatus = true;
            sbStatusMesg.append(String.format("AudioCapture success"));
        }
        catch (Exception e)
        {
            sampleStatus = false;
            sbStatusMesg.append(String.format("AudioCapture failed. %s", e.getMessage()));

            if (recorder != null)
            {
                recorder.stop();
                recorder.release();
            }
        }

        return sampleStatus;
    }

    private void setAudioInputSource(String source) throws CmdFailException
    {
        if (source.equalsIgnoreCase("CAMCORDER"))
        {
            audioInputSource = AudioSource.CAMCORDER;
        }
        else if (source.equalsIgnoreCase("DEFAULT"))
        {
            audioInputSource = AudioSource.DEFAULT;
        }
        else if (source.equalsIgnoreCase("MIC"))
        {
            audioInputSource = AudioSource.MIC;
        }
        else if (source.equalsIgnoreCase("VOICE_CALL"))
        {
            audioInputSource = AudioSource.VOICE_CALL;
        }
        else if (source.equalsIgnoreCase("VOICE_COMMUNICATION"))
        {
            audioInputSource = AudioSource.VOICE_COMMUNICATION;
        }
        else if (source.equalsIgnoreCase("VOICE_DOWNLINK"))
        {
            audioInputSource = AudioSource.VOICE_DOWNLINK;
        }
        else if (source.equalsIgnoreCase("VOICE_RECOGNITION"))
        {
            audioInputSource = AudioSource.VOICE_RECOGNITION;
        }
        else if (source.equalsIgnoreCase("VOICE_UPLINK"))
        {
            audioInputSource = AudioSource.VOICE_UPLINK;
        }
        else if (source.equalsIgnoreCase("MIC_LOW_PWR"))
        {
            audioInputSource = AUDIO_SOURCE_MOT_VR_SOURCE;
        }
        else
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unrecognized INPUT_SOURCE setting: " + source);
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        return;
    }

    private String getAudioInputSource()
    {
        String source = "UNKNOWN";

        switch (audioInputSource)
        {
        case AudioSource.CAMCORDER:
            source = "CAMCORDER";
            break;

        case AudioSource.DEFAULT:
            source = "DEFAULT";
            break;

        case AudioSource.MIC:
            source = "MIC";
            break;

        case AudioSource.VOICE_CALL:
            source = "VOICE_CALL";
            break;

        case AudioSource.VOICE_COMMUNICATION:
            source = "VOICE_COMMUNICATION";
            break;

        case AudioSource.VOICE_DOWNLINK:
            source = "VOICE_DOWNLINK";
            break;

        case AudioSource.VOICE_RECOGNITION:
            source = "VOICE_RECOGNITION";
            break;

        case AudioSource.VOICE_UPLINK:
            source = "VOICE_UPLINK";
            break;

        case AUDIO_SOURCE_MOT_VR_SOURCE:
            source = "MIC_LOW_PWR";
            break;

        default:
            source = "UNKNOWN";
        }
        return source;
    }

    private void setAudioInputFormat(String format) throws CmdFailException
    {
        if (format.equalsIgnoreCase("CHANNEL_IN_BACK"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_BACK;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_BACK_PROCESSED"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_BACK_PROCESSED;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_DEFAULT"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_DEFAULT;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_FRONT"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_FRONT;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_FRONT_PROCESSED"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_FRONT_PROCESSED;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_LEFT"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_LEFT;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_LEFT_PROCESSED"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_LEFT_PROCESSED;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_MONO"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_MONO;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_PRESSURE"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_PRESSURE;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_RIGHT"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_RIGHT;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_RIGHT_PROCESSED"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_RIGHT_PROCESSED;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_STEREO"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_STEREO;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_VOICE_DNLINK"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_VOICE_DNLINK;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_VOICE_UPLINK"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_VOICE_UPLINK;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_X_AXIS"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_X_AXIS;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_Y_AXIS"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_Y_AXIS;
        }
        else if (format.equalsIgnoreCase("CHANNEL_IN_Z_AXIS"))
        {
            audioInputFormat = AudioFormat.CHANNEL_IN_Z_AXIS;
        }
        else
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unrecognized INPUT_FORMAT setting: " + format);
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }

        return;
    }

    private String getAudioInputFormat()
    {
        String format = "UNKNOWN";

        switch (audioInputFormat)
        {
        case AudioFormat.CHANNEL_IN_BACK:
            format = "CHANNEL_IN_BACK";
            break;

        case AudioFormat.CHANNEL_IN_BACK_PROCESSED:
            format = "CHANNEL_IN_BACK_PROCESSED";
            break;

        case AudioFormat.CHANNEL_IN_DEFAULT:
            format = "CHANNEL_IN_DEFAULT";
            break;

            // CHANNEL_IN_FRONT has the same constant value as CHANNEL_IN_MONO
            // case AudioFormat.CHANNEL_IN_FRONT:
            //    format = "CHANNEL_IN_FRONT";
            //    break;
            //

        case AudioFormat.CHANNEL_IN_FRONT_PROCESSED:
            format = "CHANNEL_IN_FRONT_PROCESSED";
            break;

        case AudioFormat.CHANNEL_IN_LEFT:
            format = "CHANNEL_IN_LEFT";
            break;

        case AudioFormat.CHANNEL_IN_LEFT_PROCESSED:
            format = "CHANNEL_IN_LEFT_PROCESSED";
            break;

        case AudioFormat.CHANNEL_IN_MONO:
            format = "CHANNEL_IN_MONO";
            break;

        case AudioFormat.CHANNEL_IN_PRESSURE:
            format = "CHANNEL_IN_PRESSURE";
            break;

        case AudioFormat.CHANNEL_IN_RIGHT:
            format = "CHANNEL_IN_RIGHT";
            break;

        case AudioFormat.CHANNEL_IN_RIGHT_PROCESSED:
            format = "CHANNEL_IN_RIGHT_PROCESSED";
            break;

        case AudioFormat.CHANNEL_IN_STEREO:
            format = "CHANNEL_IN_STEREO";
            break;

        case AudioFormat.CHANNEL_IN_VOICE_DNLINK:
            format = "CHANNEL_IN_VOICE_DNLINK";
            break;

        case AudioFormat.CHANNEL_IN_VOICE_UPLINK:
            format = "CHANNEL_IN_VOICE_UPLINK";
            break;

        case AudioFormat.CHANNEL_IN_X_AXIS:
            format = "CHANNEL_IN_X_AXIS";
            break;

        case AudioFormat.CHANNEL_IN_Y_AXIS:
            format = "CHANNEL_IN_Y_AXIS";
            break;

        case AudioFormat.CHANNEL_IN_Z_AXIS:
            format = "CHANNEL_IN_Z_AXIS";
            break;

        default:
            format = "UNKNOWN";
        }

        return format;
    }

    private void setMicSelect(String micSelect) throws CmdFailException
    {
        if (micSelect.equalsIgnoreCase(PRIMARY_MIC_PATH))
        {
            audioMicSelect = PRIMARY_MIC_PATH;
        }
        else if (micSelect.equalsIgnoreCase(SECONDARY_MIC_PATH))
        {
            audioMicSelect = SECONDARY_MIC_PATH;
        }
        else if (micSelect.equalsIgnoreCase(TERTIARY_MIC_PATH))
        {
            audioMicSelect = TERTIARY_MIC_PATH;
        }
        else if (micSelect.equalsIgnoreCase(HEADSET_MIC_PATH))
        {
            audioMicSelect = HEADSET_MIC_PATH;
        }
        else if (micSelect.equalsIgnoreCase(MIC_4_PATH))
        {
            audioMicSelect = MIC_4_PATH;
        }
        else if (micSelect.equalsIgnoreCase(MIC_5_PATH))
        {
            audioMicSelect = MIC_5_PATH;
        }
        else if (micSelect.equalsIgnoreCase(MIC_PATH_NONE))
        {
            audioMicSelect = MIC_PATH_NONE;
        }
        else
        {
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add("Unrecognized MIC_SELECT setting: " + micSelect);
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
        return;
    }

    protected AudioSampleService mAudioSampleService;

    private ServiceConnection mAudioSampleConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mAudioSampleService = ((AudioSampleService.LocalBinder) service).getService();
            mIsAudioSampleServiceBound = true;

            // Tell the user about this for our demo.
            dbgLog(TAG, "Connected to AudioSampleService", 'i');
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mAudioSampleService = null;
            dbgLog(TAG, "Disconnected from AudioSampleService", 'i');

        }
    };

    void doBindAudioSampleService()
    {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        // bindService(new Intent(Test_Base.this,
        // CommServer.class), mConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(getApplicationContext(), AudioSampleService.class), mAudioSampleConnection, Context.BIND_AUTO_CREATE);

        // wait for new service to be successfully bound
        while (mIsAudioSampleServiceBound == false)
        {
            try
            {
                Thread.sleep(1);
            }
            catch (InterruptedException ignore)
            {

            }
        }
    }

    void unbindAudioSampleService()
    {
        if (mIsAudioSampleServiceBound == true)
        {
            dbgLog(TAG, "OnDestroy() unbindService(mAudioSampleConnection)", 'i');
            this.unbindService(mAudioSampleConnection);

            mIsAudioSampleServiceBound = false;
        }
    }

    private boolean isAudioSampleServiceRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        // keep klocwork happy
        if (null == manager)
        {
            dbgLog(TAG, "isAudioSampleServiceRunning() Could not retrieve ActivityManager", 'e');
            return false;
        }

        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        if (null == runningServices)
        {
            dbgLog(TAG, "isAudioSampleServiceRunning() Could not retrieve list of running services", 'e');
            return false;
        }

        String ServiceName = this.getPackageName() + ".audio.AudioSampleService";
        dbgLog(TAG, "isAudioSampleServiceRunning() looking for " + ServiceName, 'i');

        for (RunningServiceInfo service : runningServices)
        {
            // keep klocwork happy
            if (null == service)
            {
                continue;
            }

            dbgLog(TAG, "isAudioSampleServiceRunning() check service named " + service.service.getClassName(), 'i');

            if (ServiceName.equals(service.service.getClassName()))
            {
                dbgLog(TAG, "isAudioSampleServiceRunning() returned true", 'i');
                return true;
            }
        }
        dbgLog(TAG, "isAudioSampleServiceRunning() returned false", 'i');
        return false;
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        boolean captureStatus;
        if (strRxCmd.equalsIgnoreCase("SET_SAMPLE_SETTINGS"))
        {
            // if list has no data .. return NACK
            if (strRxCmdDataList.size() == 0)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("No key=value pairs sent for SET cmd");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                // Keys that are in common to both mono and stereo tones
                if (key.equalsIgnoreCase("SAMPLE_RATE_HZ"))
                {
                    sampleRate = Integer.parseInt(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleRateView, Color.WHITE, Integer.toString(sampleRate));
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("SAMPLE_LENGTH_MS"))
                {
                    sampleLengthMs = Integer.parseInt(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleLengthView, Color.WHITE, Integer.toString(sampleLengthMs));
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("SAMPLE_DISCARD_TIME_MS"))
                {
                    audioSampleDiscardTimeMs = Integer.parseInt(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleDiscardLengthView, Color.WHITE, Integer.toString(audioSampleDiscardTimeMs));
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("CAPTURE_TIMEOUT_MS"))
                {
                    audioCaptureTimeoutMs = Integer.parseInt(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleTimeoutView, Color.WHITE, Integer.toString(audioCaptureTimeoutMs));
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("INPUT_SOURCE"))
                {
                    setAudioInputSource(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleSourceView, Color.WHITE, getAudioInputSource());
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("INPUT_FORMAT"))
                {
                    setAudioInputFormat(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleFormatView, Color.WHITE, getAudioInputFormat());
                    runOnUiThread(updateUi);
                }
                else if (key.equalsIgnoreCase("MIC_SELECT"))
                {
                    setMicSelect(value);

                    // update UI
                    UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleMicSelectView, Color.WHITE, audioMicSelect);
                    runOnUiThread(updateUi);
                }
                else
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("UNKNOWN key " + key);
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }
            } // end for (String keyValuePair: strRxCmdDataList)

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_SAMPLE_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("SAMPLE_RATE_HZ=" + sampleRate);
            strDataList.add("SAMPLE_LENGTH_MS=" + sampleLengthMs);
            strDataList.add("SAMPLE_DISCARD_TIME_MS=" + audioSampleDiscardTimeMs);
            strDataList.add("CAPTURE_TIMEOUT_MS=" + audioCaptureTimeoutMs);
            strDataList.add("INPUT_SOURCE=" + getAudioInputSource());
            strDataList.add("MIC_SELECT=" + audioMicSelect);
            strDataList.add("INPUT_FORMAT=" + getAudioInputFormat());

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("SAMPLE_AUDIO"))
        {
            int numSamples = (int) (sampleRate * ((audioSampleDiscardTimeMs + sampleLengthMs) / 1000.0));
            short[] sampleBufferLeft = new short[numSamples];
            short[] sampleBufferRight = new short[numSamples];


            StringBuffer sbStatusMesg = new StringBuffer();
            captureStatus = audioCapture16BitPCM(audioCaptureTimeoutMs, sbStatusMesg, sampleBufferLeft, sampleBufferRight, sampleRate,
                    audioInputSource, audioInputFormat);

            String statusMesg = sbStatusMesg.toString();

            if (captureStatus == false)
            {
                // update UI
                UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleStatusView, Color.RED, statusMesg);
                runOnUiThread(updateUi);

                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(statusMesg);
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            List<String> strDataList = new ArrayList<String>();

            strDataList.add("NUMBER_OF_CHANNELS=" + mNumberOfChannelsSampled);

            // retrieve data and send back to PC
            StringBuffer sBuffer = new StringBuffer();
            if (mNumberOfChannelsSampled == 2)
            {
                sBuffer.append("LEFT_DATA=");
            }
            else
            {
                sBuffer.append("DATA=");
            }

            int discardDataEnd = (int) (sampleRate * ((audioSampleDiscardTimeMs) / 1000.0));

            for (int i = (discardDataEnd); i < sampleBufferLeft.length; i++)
            {
                sBuffer.append(sampleBufferLeft[i]);

                // only append "," if not last element
                if ((i + 1) < sampleBufferLeft.length)
                {
                    sBuffer.append(",");
                }
            }

            String returnDataLeft = sBuffer.toString();

            strDataList.add(returnDataLeft);

            if (mNumberOfChannelsSampled == 2)
            {
                // retrieve Right data and send back to PC
                StringBuffer sBufferRight = new StringBuffer();
                String returnDataRight = null;

                sBufferRight.append("RIGHT_DATA=");

                for (int i = (discardDataEnd); i < sampleBufferRight.length; i++)
                {
                    sBufferRight.append(sampleBufferRight[i]);

                    // only append "," if not last element
                    if ((i + 1) < sampleBufferRight.length)
                    {
                        sBufferRight.append(",");
                    }
                }

                returnDataRight = sBufferRight.toString();

                strDataList.add(returnDataRight);
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // update UI
            UpdateUiTextView updateUi = new UpdateUiTextView(audioSampleStatusView, Color.WHITE, statusMesg);
            runOnUiThread(updateUi);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_SAMPLING_SERVICE"))
        {
            // if service is not running start it
            if (isAudioSampleServiceRunning() == false)
            {
                startService(new Intent(getApplicationContext(), AudioSampleService.class));
            }

            doBindAudioSampleService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_SAMPLING_SERVICE"))
        {
            if (isAudioSampleServiceRunning() == true)
            {
                // if some how service is running and NOT bound
                // then bind to the service so we can tell it to stop sampling
                if (mIsAudioSampleServiceBound == false)
                {
                    doBindAudioSampleService();
                }

                // make sure sample service was bound successfully
                if (mAudioSampleService == null)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add("mAudioSampleService was not bound successfully");
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // stop any current sampling
                StringBuffer sbStatusMesg = new StringBuffer();
                if (mAudioSampleService.stopSampling(sbStatusMesg) == false)
                {
                    List<String> strErrMsgList = new ArrayList<String>();
                    strErrMsgList.add(sbStatusMesg.toString());
                    dbgLog(TAG, strErrMsgList.get(0), 'i');
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                }

                // Stop sample service
                stopService(new Intent(getApplicationContext(), AudioSampleService.class));
            }
            unbindAudioSampleService();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("START_SAMPLING"))
        {
            // if service not running or no bound then fail
            if ((mIsAudioSampleServiceBound == false) || (isAudioSampleServiceRunning() == false))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Sample service is not running or has not been bound");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // get any options
            int bufferDepth = DEFAULT_SAMPLE_BUFFER_DEPTH;

            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                if (key.equalsIgnoreCase("SAMPLE_BUFFER_DEPTH"))
                {
                    bufferDepth = Integer.parseInt(value);
                }
            }

            // set sampling settings
            AudioSampleSettings sampleSettings = new AudioSampleSettings();
            sampleSettings.micKey = MIC_KEY;
            sampleSettings.audioMicSelect = audioMicSelect;
            sampleSettings.sampleRate = sampleRate;
            sampleSettings.sampleLengthMs = sampleLengthMs;
            sampleSettings.audioCaptureTimeoutMs = audioCaptureTimeoutMs;
            sampleSettings.audioSampleDiscardTimeMs = audioSampleDiscardTimeMs;
            sampleSettings.audioInputSource = audioInputSource;
            sampleSettings.audioInputFormat = audioInputFormat;

            // make sure sample service was bound successfully
            if (mAudioSampleService == null)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("mAudioSampleService was not bound successfully");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // start sampling
            StringBuffer sbStatusMesg = new StringBuffer();
            if (mAudioSampleService.startSampling(sampleSettings, bufferDepth, sbStatusMesg) == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(sbStatusMesg.toString());
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_SAMPLING"))
        {
            // if service not ruuning or no bound then fail
            if ((mIsAudioSampleServiceBound == false) || (isAudioSampleServiceRunning() == false))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Sample service is not running or has not been bound");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // stop any current sampling
            StringBuffer sbStatusMesg = new StringBuffer();
            if (mAudioSampleService.stopSampling(sbStatusMesg) == false)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add(sbStatusMesg.toString());
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_BUFFERED_SAMPLE"))
        {
            if ((mIsAudioSampleServiceBound == false) || (isAudioSampleServiceRunning() == false))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Sample service is not running or has not been bound");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            Object[] sampleObject = mAudioSampleService.getBufferedSample();

            List<String> strDataList = getAudioSampleFromService(sampleObject, mAudioSampleService.getNumberOfChannelsSampled());

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);
            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CURRENT_SAMPLE"))
        {
            if ((mIsAudioSampleServiceBound == false) || (isAudioSampleServiceRunning() == false))
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("Sample service is not running or has not been bound");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            int timeoutMs = GET_CURRENT_SAMPLE_TIMEOUT_MS;

            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                if (key.equalsIgnoreCase("TIMEOUT_MS"))
                {
                    timeoutMs = Integer.parseInt(value);
                }
            }

            Object[] sampleObject = mAudioSampleService.getCurrentSample(timeoutMs);

            List<String> strDataList = getAudioSampleFromService(sampleObject, mAudioSampleService.getNumberOfChannelsSampled());

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
            // Generate an exception to send FAIL result and mesg back to CommServer
            List<String> strErrMsgList = new ArrayList<String>();
            strErrMsgList.add(String.format("Activity '%s' does not recognize command '%s'", TAG, strRxCmd));
            dbgLog(TAG, strErrMsgList.get(0), 'i');
            throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
        }
    }

    private List<String> getAudioSampleFromService(Object[] sampleObject, int numberOfChannels)
    {
        List<String> strDataList = new ArrayList<String>();

        if (sampleObject != null)
        {
            short[] sampleBufferLeft = (short[]) sampleObject[0];

            strDataList.add("DATA_AVAIL=YES");

            strDataList.add("NUMBER_OF_CHANNELS=" + numberOfChannels);

            // retrieve data and send back to PC
            StringBuffer sBufferLeft = new StringBuffer();
            if (numberOfChannels == 2)
            {
                sBufferLeft.append("LEFT_DATA=");
            }
            else
            {
                sBufferLeft.append("DATA=");
            }

            for (int i = 0; i < sampleBufferLeft.length; i++)
            {
                sBufferLeft.append(sampleBufferLeft[i]);

                // only append "," if not last element
                if ((i + 1) < sampleBufferLeft.length)
                {
                    sBufferLeft.append(",");
                }
            }

            strDataList.add(sBufferLeft.toString());

            if ((numberOfChannels == 2))
            {
                // retrieve Right data and send back to PC
                StringBuffer sBufferRight = new StringBuffer();

                short[] sampleBufferRight = (short[]) sampleObject[1];

                sBufferRight.append("RIGHT_DATA=");

                for (int i = 0; i < sampleBufferRight.length; i++)
                {
                    sBufferRight.append(sampleBufferRight[i]);

                    // only append "," if not last element
                    if ((i + 1) < sampleBufferRight.length)
                    {
                        sBufferRight.append(",");
                    }
                }

                strDataList.add(sBufferRight.toString());
            }
        }
        else
        {
            strDataList.add("DATA_AVAIL=NO");
        }

        return strDataList;
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
        strHelpList.add("  SET_SAMPLE_SETTINGS - Sets audio sample settting. Valid settings below:");
        strHelpList.add("    SAMPLE_RATE_HZ   - sample rate. default 8000");
        strHelpList.add("    SAMPLE_LENGTH_MS - sample length. default 20");
        strHelpList.add("    SAMPLE_DISCARD_TIME_MS - sample data to discard in mS. default 0");
        strHelpList.add("    CAPTURE_TIMEOUT_MS - capture timeout in mS. default 5000");
        strHelpList.add("    INPUT_SOURCE     - audio input source. default is DEFAULT");
        strHelpList.add("    INPUT_FORMAT     - audio input format rate. default is CHANNEL_IN_MONO");
        strHelpList.add("    MIC_SELECT       - Mot specific override for mic selection.");
        strHelpList.add("                       Only valid when INPUT_SOURCE is DEFAULT.");
        strHelpList.add("                       Valid values are: primary, secondary, tertiary, headset");
        strHelpList.add("                       default is none");
        strHelpList.add("  ");
        strHelpList.add("  GET_SAMPLE_SETTINGS - Gets current audio sample settings");
        strHelpList.add("  ");
        strHelpList.add("  SAMPLE_AUDIO - Samples audio and returns data under DATA key");
        strHelpList.add("                 This is single shot sample. For multiple back to back samples see sample service commands");
        strHelpList.add("  ");
        strHelpList.add("  ### Sample Service Commands ###");
        strHelpList.add("  The sample service provides a method to efficiently take multiple audio samples "
                + "without having to create a new record object for each sample.");
        strHelpList.add("  ");
        strHelpList.add("  START_SAMPLING_SERVICE - starts the sampling service");
        strHelpList.add("  STOP_SAMPLING_SERVICE  - stops the sampling service");
        strHelpList.add("  START_SAMPLING - instructs sampling service to start sampling with settings from SET_SAMPLE_SETTINGS");
        strHelpList.add("    SAMPLE_BUFFER_DEPTH - number of completed samples to buffer. Default 1, min value 1");
        strHelpList.add("  STOP_SAMPLING  - instructs sampling service to stop sampling");
        strHelpList.add("  GET_BUFFERED_SAMPLE - returns the oldest buffered sample from the sample service if available");
        strHelpList.add("                        If a sample is available the DATA_AVAIL key will be set to YES and "
                + "sample data will be available under the DATA key");
        strHelpList.add("  GET_CURRENT_SAMPLE - clears the buffered samples in the sample service and returns a newly captured sample");
        strHelpList.add("                       If a sample is available the DATA_AVAIL key will be set to YES and "
                + "sample data will be available under the DATA key");
        strHelpList.add("    TIMEOUT_MS - max time in millisecs to wait for sample to be taken");

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
