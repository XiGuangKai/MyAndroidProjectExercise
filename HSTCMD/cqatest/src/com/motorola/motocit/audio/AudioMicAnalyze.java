/*
 * Copyright (c) 2014-2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;
import com.motorola.motocit.dft.DFT;

public class AudioMicAnalyze extends Test_Base
{
    private boolean isRecording = false;
    private boolean isAnalyze = false;

    private final int AUDIO_SOURCE_DEFAULT = 0;
    // Path needed for low power audio, from MediaRecorder.java
    private static final int AUDIO_SOURCE_MOT_VR_SOURCE = 11;

    private final static String MIC_KEY = "mic_path";
    private final static String PRIMARY_MIC_PATH = "primary";
    private final static String SECONDARY_MIC_PATH = "secondary";
    private final static String TERTIARY_MIC_PATH = "tertiary";
    private final static String HEADSET_MIC_PATH = "headset";
    private final static String MIC_4_PATH = "mic4";
    private final static String MIC_5_PATH = "mic5";
    private final static String MIC_PATH_NONE = "none";

    private static final String[] audiosource_spinner = { "DEFAULT MIC", "CAMCORDER MIC",
            "PRIMARY MIC", "SECONDARY MIC",
            "TERTIARY MIC", "HEADSET MIC",
            "PRIMARY MIC LOW PWR",
            "SECONDARY MIC LOW PWR",
            "TERTIARY MIC LOW PWR",
            "HEADSET MIC LOW PWR",
            "MIC 4", "MIC 4 LOW PWR",
            "MIC 5", "MIC 5 LOW PWR" };

    private ArrayList<String> ignored_audio_source;
    private AudioManager audioManager;
    private AudioRecord audioRec;

    private int defaultAudioSourceType = MediaRecorder.AudioSource.MIC;

    private TextView loopback_prompt_ui;
    private TextView mMicFrequency;
    private TextView mMicAmplitude;
    private Spinner spinnerMicSelect;
    private ArrayAdapter<String> adapterLoopback;

    private final int dftSampleSize = 2048;
    short[] mFftBuffer = new short[dftSampleSize];
    int mFftBufferPosition = 0;
    private final int PRECISION = 2;
    private final int SAMPLE_RATE = 8000;


    private final Lock lockDFTData = new ReentrantLock();

    DFT mDFT = new DFT();
    double mFftAmplitude = 0;
    double mFftMaxFrequency = 0;

    private boolean isPermissionAllowed = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_MicAnalyze";

        super.onCreate(savedInstanceState);
        dbgLog(TAG, "onCreate", 'i');

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_loopback);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        dbgLog(TAG, "finished Adjust View", 'i');

    }

    private void setSelectedMicPath(String audioMicSelect)
    {
        String keyPair = MIC_KEY + "=" + audioMicSelect;
        dbgLog(TAG, "Setting selected mic with keyPair '" + keyPair + "'", 'd');
        audioManager.setParameters(keyPair);
    }

    private void loopBackInit()
    {
        if (isAnalyze)
        {
            stopAnalyze();
        }

        audioManager = (AudioManager) AudioMicAnalyze.this.getSystemService(Context.AUDIO_SERVICE);
    }

    private void startAnalyze(final int audioSourceType)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                dbgLog(TAG, "Run in loopbackButtonListener thread", 'd');

                isRecording = true;

                isAnalyze = true;

                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                int buffersize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);

                short[] mBuffer = new short[buffersize];

                dbgLog(TAG, "mBuffer: " + mBuffer, 'd');
                dbgLog(TAG, "mBuffer Size: " + buffersize, 'd');
                dbgLog(TAG, "Setting audioRec...", 'd');

                AudioRecord audioRec = new AudioRecord(audioSourceType, SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, buffersize);
                dbgLog(TAG, "Setting audioTrack...", 'd');

                audioRec.startRecording();
                dbgLog(TAG, "Recording Started...", 'd');

                while (isRecording)
                {
                    audioRec.read(mBuffer, 0, buffersize);
                    for (int bufferLocation = 0; bufferLocation < buffersize; bufferLocation++)
                    {
                        if (mFftBufferPosition < dftSampleSize)
                        {
                            mFftBuffer[mFftBufferPosition] = mBuffer[bufferLocation];
                            mFftBufferPosition++;
                        }
                    }
                    if (mFftBufferPosition >= dftSampleSize)
                    {
                        mDFT.newFftTransform(mFftBuffer, SAMPLE_RATE, "HANNING");
                        try
                        {
                            lockDFTData.lock();
                            UpdateUiTextView updateFreqUi = new UpdateUiTextView(mMicFrequency, Color.WHITE, "Frequency: "
                                    + Double.toString(TestUtils.round(mDFT.maxPowerFrequency(), PRECISION)));
                            runOnUiThread(updateFreqUi);


                            mFftMaxFrequency = mDFT.maxPowerFrequency();
                            // Convert amplitude to dB Full Scale
                            mFftAmplitude = 10 * Math.log(mDFT.maxAmplitude() / 32767);

                            UpdateUiTextView updateAmplUi = new UpdateUiTextView(mMicAmplitude, Color.WHITE, "Amplitude: "
                                    + Double.toString(TestUtils.round(mFftAmplitude, PRECISION)));
                            runOnUiThread(updateAmplUi);
                        }
                        catch (Exception e)
                        {
                            dbgLog(TAG, "EXCEPTION WRITING FREQ/AMPL: " + e.toString(), 'd');
                        }
                        finally
                        {
                            lockDFTData.unlock();
                        }

                        mFftBufferPosition = 0;
                    }

                }

                audioRec.stop();
                audioRec.release();
                dbgLog(TAG, "Stop Recording = " + audioRec.getRecordingState(), 'd');
            }
        }).start();
    }

    private void stopAnalyze()
    {
        // Fix ANR issue in sequential test here
        if (audioManager == null)
        {
            return;
        }

        // Always set mic select back to none
        audioManager.setParameters(MIC_KEY + "=" + MIC_PATH_NONE);

        isRecording = false;
        isAnalyze = false;
        dbgLog(TAG, "isRecording = " + isRecording, 'd');
        try
        {
            Thread.sleep(1000);
        }
        catch (Exception e)
        {
            dbgLog(TAG, "sleep exceptions..." + e, 'd');
        }
        if (audioRec != null)
        {
            audioRec = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        if (1001 == requestCode)
        {
            if (grantResults.length > 0)
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    isPermissionAllowed = true;
                }
                else
                {
                    isPermissionAllowed = false;
                    finish();
                }
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        dbgLog(TAG, "onResume", 'i');

        if (Build.VERSION.SDK_INT < 23)
        {
            // set to true to ignore the permission check
            isPermissionAllowed = true;
        }
        else
        {
            // check permissions on M release
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            {
                // Permission has not been granted and must be requested.
                requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1001);
                return;
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            loopback_prompt_ui = (TextView) findViewById(com.motorola.motocit.R.id.mic_loopback_prompt);

            dbgLog(TAG, "Finding amplitude and frequency Views", 'i');
            mMicFrequency = (TextView) findViewById(com.motorola.motocit.R.id.mic_loopback_frequency);
            mMicAmplitude = (TextView) findViewById(com.motorola.motocit.R.id.mic_loopback_amplitude);

            dbgLog(TAG, "Finding spinner Views", 'i');
            spinnerMicSelect = (Spinner) findViewById(com.motorola.motocit.R.id.audiosource);

            dbgLog(TAG, "finished Finding Views", 'i');

            adapterLoopback = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);

            ignored_audio_source = TestUtils.getIgnoredMicFromConfig();

            for (int i = 0; i < audiosource_spinner.length; i++)
            {
                boolean isIgnored = false;

                for (String temp : ignored_audio_source)
                {
                    if (audiosource_spinner[i].toString().equals(temp.toString()))
                    {
                        dbgLog(TAG, "Found one mic ignored: " + temp.toString(), 'i');
                        isIgnored = true;
                    }
                }

                if (!isIgnored)
                {
                    adapterLoopback.add(audiosource_spinner[i]);
                }
            }

            adapterLoopback.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            spinnerMicSelect.setAdapter(adapterLoopback);
            spinnerMicSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener()
            {
                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
                {
                    String micName = spinnerMicSelect.getSelectedItem().toString();
                    loopback_prompt_ui.setText("Now using mic " + micName);
                    if (micName.equalsIgnoreCase("DEFAULT MIC"))
                    {
                        dbgLog(TAG, "Selected default mic from UI", 'i');
                        loopBackInit();
                        startAnalyze(MediaRecorder.AudioSource.MIC);
                    }
                    else if (micName.equalsIgnoreCase("CAMCORDER MIC"))
                    {
                        dbgLog(TAG, "Selected camcorder mic from UI", 'i');
                        loopBackInit();
                        startAnalyze(MediaRecorder.AudioSource.CAMCORDER);
                    }
                    else if (micName.equalsIgnoreCase("PRIMARY MIC"))
                    {
                        dbgLog(TAG, "Selected primary mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(PRIMARY_MIC_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("SECONDARY MIC"))
                    {
                        dbgLog(TAG, "Selected secondary mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(SECONDARY_MIC_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("TERTIARY MIC"))
                    {
                        dbgLog(TAG, "Selected tertiary mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(TERTIARY_MIC_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("HEADSET MIC"))
                    {
                        dbgLog(TAG, "Selected headset mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(HEADSET_MIC_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("PRIMARY MIC LOW PWR"))
                    {
                        dbgLog(TAG, "Selected primary low pwr mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(PRIMARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else if (micName.equalsIgnoreCase("SECONDARY MIC LOW PWR"))
                    {
                        dbgLog(TAG, "Selected secondary low pwr mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(SECONDARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else if (micName.equalsIgnoreCase("TERTIARY MIC LOW PWR"))
                    {
                        dbgLog(TAG, "Selected tertiary low pwr mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(TERTIARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else if (micName.equalsIgnoreCase("HEADSET MIC LOW PWR"))
                    {
                        dbgLog(TAG, "Selected headset low pwr mic from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(HEADSET_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else if (micName.equalsIgnoreCase("MIC 4"))
                    {
                        dbgLog(TAG, "Selected mic 4 from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(MIC_4_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("MIC 4 LOW PWR"))
                    {
                        dbgLog(TAG, "Selected mic 4 low pwr from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(MIC_4_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else if (micName.equalsIgnoreCase("MIC 5"))
                    {
                        dbgLog(TAG, "Selected mic 5 from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(MIC_5_PATH);
                        startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                    }
                    else if (micName.equalsIgnoreCase("MIC 5 LOW PWR"))
                    {
                        dbgLog(TAG, "Selected mic 5 low pwr from UI", 'i');
                        loopBackInit();
                        setSelectedMicPath(MIC_5_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());
                        startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                    }
                    else
                    {
                        dbgLog(TAG, "Error of mic selection", 'e');
                    }

                    arg0.setVisibility(View.VISIBLE);
                }

                public void onNothingSelected(AdapterView<?> arg0)
                {

                }
            });

            sendStartActivityPassed();

            /*
             * If activity was pasued by HOME key, re-launch audio loopback,
             * start
             * loopback
             * from default mic
             */
            if (isAnalyze == false && (wasActivityStartedByCommServer() == false))
            {
                spinnerMicSelect.setSelection(AUDIO_SOURCE_DEFAULT);
                loopback_prompt_ui.setText("Now using Mic " + audiosource_spinner[0]);
                loopBackInit();
            }
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Audio test");
        }
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
            contentRecord("testresult.txt", "Audio - Mic Analyze:  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            stopAnalyze();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Audio - Mic Analyze:  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            stopAnalyze();

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
                stopAnalyze();
                finish();
            }
        }

        return true;
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (isAnalyze && (wasActivityStartedByCommServer() == false))
        {
            stopAnalyze();
        }
    }

    @Override
    public void onDestroy()
    {
        dbgLog(TAG, "onDestroy", 'd');

        super.onDestroy();

        if (isAnalyze)
        {
            stopAnalyze();
        }
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("STOP_ANALYZE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if (isAnalyze)
            {
                stopAnalyze();
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("START_ANALYZE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            if (isAnalyze)
            {
                strReturnDataList.add("Mic Loopback is in progress, stop loopback at first");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                String audioSourceType = null;

                if (strRxCmdDataList.size() == 0)
                {
                    loopBackInit();
                    startAnalyze(defaultAudioSourceType); // If no audio_source
                    // assigned, use
                    // default type.
                    // Generate an exception to send data back to CommServer
                    throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else if (strRxCmdDataList.size() > 1)
                {
                    strReturnDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else
                {
                    if (strRxCmdDataList.get(0).toUpperCase(Locale.US).contains("AUDIO_SOURCE"))
                    {
                        audioSourceType = strRxCmdDataList.get(0).substring(strRxCmdDataList.get(0).indexOf("=") + 1);
                        if (audioSourceType.equalsIgnoreCase("CAMCORDER"))
                        {
                            loopBackInit();
                            startAnalyze(MediaRecorder.AudioSource.CAMCORDER);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("DEFAULT") || audioSourceType.equalsIgnoreCase("MIC"))
                        {
                            loopBackInit();
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("PRIMARY"))
                        {
                            loopBackInit();
                            setSelectedMicPath(PRIMARY_MIC_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("SECONDARY"))
                        {
                            loopBackInit();
                            setSelectedMicPath(SECONDARY_MIC_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("TERTIARY"))
                        {
                            loopBackInit();
                            setSelectedMicPath(TERTIARY_MIC_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("HEADSET"))
                        {
                            loopBackInit();
                            setSelectedMicPath(HEADSET_MIC_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("PRIMARY_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(PRIMARY_MIC_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("SECONDARY_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(SECONDARY_MIC_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("TERTIARY_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(TERTIARY_MIC_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("HEADSET_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(HEADSET_MIC_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("MIC_4"))
                        {
                            loopBackInit();
                            setSelectedMicPath(MIC_4_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("MIC4_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(MIC_4_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("MIC_5"))
                        {
                            loopBackInit();
                            setSelectedMicPath(MIC_5_PATH);
                            startAnalyze(MediaRecorder.AudioSource.DEFAULT);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else if (audioSourceType.equalsIgnoreCase("MIC5_LOW_PWR"))
                        {
                            loopBackInit();
                            setSelectedMicPath(MIC_5_PATH);
                            AudioUtils.setDspAudioPath(getApplicationContext());
                            startAnalyze(AUDIO_SOURCE_MOT_VR_SOURCE);
                            // Generate an exception to send data back to
                            // CommServer
                            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN TYPE: " + strRxCmdDataList.get(0));
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN PARAMETER: " + strRxCmdDataList.get(0));
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_AUDIO_DATA"))
        {
            List<String> strDataList = new ArrayList<String>();

            try
            {
                lockDFTData.lock();

                strDataList.add(String.format("MAX_FREQUENCY=" + mFftMaxFrequency));
                strDataList.add(String.format("MAX_AMPLITUDE=" + mFftAmplitude));
            }
            catch (Exception e)
            {

            }
            finally
            {
                lockDFTData.unlock();
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
        strHelpList.add("This function will set the device to loopback audio");
        strHelpList.add("");
        strHelpList.add("STOP_ANALYZE  - Stop mic analyze");
        strHelpList.add("");
        strHelpList.add("START_ANALYZE- If no parameter after action START_ANALYZE, device will use default mic as AudioSource");
        strHelpList.add("               - AUDIO_SOURCE=CAMCORDER, use TOP/BACK mic");
        strHelpList.add("               - AUDIO_SOURCE=DEFAULT, use default mic");
        strHelpList.add("               - AUDIO_SOURCE=PRIMARY, use primary mic");
        strHelpList.add("               - AUDIO_SOURCE=SECONDARY, use secondary mic");
        strHelpList.add("               - AUDIO_SOURCE=TERTIARY, use teriary mic");
        strHelpList.add("               - AUDIO_SOURCE=HEADSET, use headset mic");
        strHelpList.add("               - AUDIO_SOURCE=PRIMARY_LOW_PWR, use primary low pwr mic");
        strHelpList.add("               - AUDIO_SOURCE=SECONDARY_LOW_PWR, use secondary low pwr mic");
        strHelpList.add("               - AUDIO_SOURCE=TERTIARY_LOW_PWR, use tertiary low pwr mic");
        strHelpList.add("               - AUDIO_SOURCE=HEADSET_LOW_PWR, use headset low pwr mic");
        strHelpList.add("               - AUDIO_SOURCE=MIC_4, use mic 4 mic");
        strHelpList.add("               - AUDIO_SOURCE=MIC_4_LOW_PWR, use mic 4 low pwr mic");
        strHelpList.add("               - AUDIO_SOURCE=MIC_5, use mic 5 mic");
        strHelpList.add("               - AUDIO_SOURCE=MIC_5_LOW_PWR, use mic 5 low pwr mic");
        strHelpList.add("GET_AUDIO_DATA - Gets information on any connected Mods");
        strHelpList.add("    MAX_FREQUENCY - Max frequency");
        strHelpList.add("    MAX_AMPLITUDE - Max amplitude of max frequency");
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
        contentRecord("testresult.txt", "Audio - Mic Analyze:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        stopAnalyze();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Audio - Mic Analyze:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        stopAnalyze();

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
            stopAnalyze();
            finish();
        }
        return true;
    }
}
