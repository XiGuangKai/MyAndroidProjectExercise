/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AudioToneGen extends Test_Base
{
    private AudioManager audioManager;
    private AudioTrack audioTrack;

    private MediaPlayer mediaPlayer = null;

    private int maxCallVolume;
    private int maxMediaVolume;

    private double mAudioToneLength = 6;
    private int mAudioToneSampleRate = 44100;
    private double mAudioToneStartFreq = 100.0;
    private double mAudioToneStopFreq = 1000.0;
    private List<Double> mFreqList = new ArrayList<Double>();
    private String mMultiFreqType = "SIMULTANEOUS";
    private List<Double> mFreqAmplitudeList = new ArrayList<Double>();
    private List<Double> mFreqPhaseShiftList = new ArrayList<Double>();
    private List<Double> mLeftFreqList = new ArrayList<Double>();
    private List<Double> mLeftFreqAmplitudeList = new ArrayList<Double>();
    private List<Double> mLeftFreqPhaseShiftList = new ArrayList<Double>();
    private List<Double> mRightFreqList = new ArrayList<Double>();
    private List<Double> mRightFreqAmplitudeList = new ArrayList<Double>();
    private List<Double> mRightFreqPhaseShiftList = new ArrayList<Double>();

    private int mAudioToneVolume = 400;
    private int mAudioToneNumberOfLoops = 0;
    private boolean mAudioToneLogScale = true;

    // stereo variables
    private double mLeftAudioToneStartFreq = 100.0;
    private double mLeftAudioToneStopFreq = 1000.0;
    private int mLeftAudioToneVolume = 400;
    private boolean mLeftAudioToneLogScale = true;
    private boolean mLeftMute = false;

    private double mRightAudioToneStartFreq = 100.0;
    private double mRightAudioToneStopFreq = 1000.0;
    private int mRightAudioToneVolume = 400;
    private boolean mRightAudioToneLogScale = true;
    private boolean mRightMute = false;

    // multi tone
    private int mNumberOfFreqs = 2;
    private int mLeftNumberOfFreqs = 2;
    private int mRightNumberOfFreqs = 2;

    private int initAlarmVolume;
    private int initDTMFVolume;
    private int initMusicVolume;
    private int initNotificationVolume;
    private int initRingVolume;
    private int initSystemVolume;
    private int initVoiceCallVolume;

    private int mStreamType = AudioManager.STREAM_MUSIC;

    private int mMediaFileID = com.motorola.motocit.R.raw.mediasweep_mmi;

    private Button playButton;

    private EditText toneLengthEditbox = null;
    private EditText sampleRateEditbox = null;
    private EditText startFreqEditbox = null;
    private EditText stopFreqEditbox = null;
    private EditText volumeEditbox = null;

    private CheckBox linearOrLogCheckBox;
    private CheckBox loopCheckBox;
    private CheckBox loudspeakerEarpieceCheckbox;

    private static final int DEVICE_OUT_FM_TX = 0x1000000;
    private static final int DEVICE_OUT_PROXY = 0x2000000;

    private AudioSettings audioParam = new AudioSettings();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_ToneGen";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_tonegenerator);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }

        playButton = (Button) findViewById(com.motorola.motocit.R.id.play_audio_tone);

        toneLengthEditbox = (EditText) findViewById(com.motorola.motocit.R.id.toneLengthTextBox);
        sampleRateEditbox = (EditText) findViewById(com.motorola.motocit.R.id.sampleRateTextBox);
        startFreqEditbox = (EditText) findViewById(com.motorola.motocit.R.id.startFreqTextBox);
        stopFreqEditbox = (EditText) findViewById(com.motorola.motocit.R.id.stopFreqTextBox);
        volumeEditbox = (EditText) findViewById(com.motorola.motocit.R.id.volumeTextBox);

        AudioFunctions.getAudioSettingsFromConfig(audioParam);

        toneLengthEditbox.setText(Double.toString(audioParam.m_length));
        sampleRateEditbox.setText(Integer.toString(audioParam.m_sample_rate));
        startFreqEditbox.setText(Integer.toString(audioParam.m_start_freq));
        stopFreqEditbox.setText(Integer.toString(audioParam.m_end_freq));
        volumeEditbox.setText(Integer.toString(audioParam.m_volume));

        linearOrLogCheckBox = (CheckBox) findViewById(com.motorola.motocit.R.id.linearOrLogCheckbox);
        loopCheckBox = (CheckBox) findViewById(com.motorola.motocit.R.id.loopCheckbox);
        loudspeakerEarpieceCheckbox = (CheckBox) findViewById(com.motorola.motocit.R.id.loudspeakerEarpieceCheckbox);

        // Route audio to speaker
        audioManager = (AudioManager) AudioToneGen.this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
        dbgLog(TAG, "SetSpeakerOn = True", 'i');

        initAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        initDTMFVolume = audioManager.getStreamVolume(AudioManager.STREAM_DTMF);
        initMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        initNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        initRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        initSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        initVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);

        maxCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        audioManager.setMode(AudioManager.MODE_NORMAL);

        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        playButton.setOnClickListener(playMediaFileListener);

        linearOrLogCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (linearOrLogCheckBox.isChecked())
                {
                    linearOrLogCheckBox.setText("Logrithmic");
                }
                else
                {
                    linearOrLogCheckBox.setText("Linear");
                }
            }
        });

        loudspeakerEarpieceCheckbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
        {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (loudspeakerEarpieceCheckbox.isChecked())
                {
                    audioManager.setSpeakerphoneOn(false);
                    dbgLog(TAG, "SetSpeakerOn = False", 'i');
                    // Record current in-call volume, will restore it
                    // onStop
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVolume, 0);
                    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);
                    setVolumeControlStream(AudioManager.STREAM_MUSIC);

                    audioManager.setMode(AudioManager.MODE_IN_CALL);

                    loudspeakerEarpieceCheckbox.setText("Earpiece");
                }
                else
                {
                    audioManager.setSpeakerphoneOn(true);
                    dbgLog(TAG, "SetSpeakerOn = True", 'i');
                    // Record current in-call volume, will restore it
                    // onStop
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);
                    setVolumeControlStream(AudioManager.STREAM_MUSIC);

                    audioManager.setMode(AudioManager.MODE_NORMAL);

                    loudspeakerEarpieceCheckbox.setText("Loudspeaker");
                }

            }
        });

    }

    private OnClickListener playMediaFileListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            double toneLength = Double.parseDouble(toneLengthEditbox.getText().toString());

            toneLength = AudioFunctions.validateToneLength(toneLength);
            toneLengthEditbox.setText(Double.toString(toneLength));

            int sampleRate = Integer.parseInt(sampleRateEditbox.getText().toString());
            sampleRate = AudioFunctions.validateSampleRate(sampleRate);
            sampleRateEditbox.setText(Integer.toString(sampleRate));

            double stopFreq = Double.parseDouble(stopFreqEditbox.getText().toString());
            stopFreq = AudioFunctions.validateFreq(stopFreq);
            stopFreqEditbox.setText(Double.toString(stopFreq));

            double startFreq = Double.parseDouble(startFreqEditbox.getText().toString());
            startFreq = AudioFunctions.validateFreq(startFreq);
            startFreqEditbox.setText(Double.toString(startFreq));

            int volume = Integer.parseInt(volumeEditbox.getText().toString());
            volume = AudioFunctions.validateVolume(volume);
            volumeEditbox.setText(Integer.toString(volume));

            int numberTargetSamples = (int) (toneLength * sampleRate);

            byte tone[];

            if (linearOrLogCheckBox.isChecked())
            {
                // Logarathimic
                tone = AudioFunctions.createTone(toneLength, sampleRate, startFreq, stopFreq, volume, true);
            }
            else
            {
                // Linear
                tone = AudioFunctions.createTone(toneLength, sampleRate, startFreq, stopFreq, volume, false);
            }

            stopAudioToneGen();
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 2 * numberTargetSamples, AudioTrack.MODE_STATIC);
            audioTrack.write(tone, 0, 2 * numberTargetSamples);

            if (loopCheckBox.isChecked())
            {
                audioTrack.setLoopPoints(0, numberTargetSamples, -1);
            }

            audioTrack.play();

        }
    };

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        stopAudioToneGen();
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, initAlarmVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, initDTMFVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initMusicVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, initNotificationVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, initRingVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, initSystemVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, initVoiceCallVolume, 0);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        sendStartActivityPassed();
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("SET_AUDIO_TONE_SETTINGS"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    // Keys that are in common to both mono and stereo tones
                    if (key.equalsIgnoreCase("TONE_LENGTH"))
                    {
                        mAudioToneLength = Double.parseDouble(value);
                        mAudioToneLength = AudioFunctions.validateToneLength(mAudioToneLength);
                    }
                    else if (key.equalsIgnoreCase("SAMPLE_RATE"))
                    {
                        mAudioToneSampleRate = Integer.parseInt(value);
                        mAudioToneSampleRate = AudioFunctions.validateSampleRate(mAudioToneSampleRate);
                    }
                    else if (key.equalsIgnoreCase("NUMBER_OF_LOOPS"))
                    {
                        mAudioToneNumberOfLoops = Integer.parseInt(value);
                    }

                    // Mono specific keys
                    else if (key.equalsIgnoreCase("START_FREQ"))
                    {
                        mAudioToneStartFreq = Double.parseDouble(value);
                        mAudioToneStartFreq = AudioFunctions.validateFreq(mAudioToneStartFreq);
                    }
                    else if (key.equalsIgnoreCase("STOP_FREQ"))
                    {
                        mAudioToneStopFreq = Double.parseDouble(value);
                        mAudioToneStopFreq = AudioFunctions.validateFreq(mAudioToneStopFreq);
                    }
                    else if (key.equalsIgnoreCase("FREQ_LIST"))
                    {
                        mFreqList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mFreqList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("MULTI_FREQ_TYPE"))
                    {
                        if ((!value.equalsIgnoreCase("SIMULTANEOUS")) && (!value.equalsIgnoreCase("STEPPED")))
                        {
                            strReturnDataList.add("MULTI_FREQ_TYPE " + value + " not Supported.");
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                        mMultiFreqType = value;
                    }
                    else if (key.equalsIgnoreCase("FREQ_AMPLITUDE_LIST"))
                    {
                        mFreqAmplitudeList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mFreqAmplitudeList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("FREQ_PHASE_SHIFT_LIST"))
                    {
                        mFreqPhaseShiftList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mFreqPhaseShiftList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    // multi tone
                    else if (key.equalsIgnoreCase("NUMBER_OF_FREQS"))
                    {
                        mNumberOfFreqs = Integer.parseInt(value);
                    }
                    else if (key.equalsIgnoreCase("VOLUME"))
                    {
                        mAudioToneVolume = Integer.parseInt(value);
                        mAudioToneVolume = AudioFunctions.validateVolume(mAudioToneVolume);
                    }
                    else if (key.equalsIgnoreCase("LOG_SCALE"))
                    {
                        mAudioToneLogScale = (Integer.parseInt(value) == 0) ? false : true;
                    }

                    // Stereo specific keys
                    else if (key.equalsIgnoreCase("LEFT_START_FREQ"))
                    {
                        mLeftAudioToneStartFreq = Double.parseDouble(value);
                        mLeftAudioToneStartFreq = AudioFunctions.validateFreq(mLeftAudioToneStartFreq);
                    }
                    else if (key.equalsIgnoreCase("RIGHT_START_FREQ"))
                    {
                        mRightAudioToneStartFreq = Double.parseDouble(value);
                        mRightAudioToneStartFreq = AudioFunctions.validateFreq(mRightAudioToneStartFreq);
                    }
                    else if (key.equalsIgnoreCase("LEFT_STOP_FREQ"))
                    {
                        mLeftAudioToneStopFreq = Double.parseDouble(value);
                        mLeftAudioToneStopFreq = AudioFunctions.validateFreq(mLeftAudioToneStopFreq);
                    }
                    else if (key.equalsIgnoreCase("RIGHT_STOP_FREQ"))
                    {
                        mRightAudioToneStopFreq = Double.parseDouble(value);
                        mRightAudioToneStopFreq = AudioFunctions.validateFreq(mRightAudioToneStopFreq);
                    }
                    else if (key.equalsIgnoreCase("RIGHT_FREQ_LIST"))
                    {
                        mRightFreqList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mRightFreqList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("RIGHT_FREQ_AMPLITUDE_LIST"))
                    {
                        mRightFreqAmplitudeList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mRightFreqAmplitudeList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("RIGHT_FREQ_PHASE_SHIFT_LIST"))
                    {
                        mRightFreqPhaseShiftList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mRightFreqPhaseShiftList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("LEFT_FREQ_LIST"))
                    {
                        mLeftFreqList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mLeftFreqList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("LEFT_FREQ_AMPLITUDE_LIST"))
                    {
                        mLeftFreqAmplitudeList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mLeftFreqAmplitudeList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("LEFT_FREQ_PHASE_SHIFT_LIST"))
                    {
                        mLeftFreqPhaseShiftList.clear();

                        String splitValue[] = value.split(",");

                        for (int i = 0; i < splitValue.length; i++)
                        {
                            mLeftFreqPhaseShiftList.add(Double.parseDouble(splitValue[i]));
                        }
                    }
                    else if (key.equalsIgnoreCase("LEFT_NUMBER_OF_FREQS"))
                    {
                        mLeftNumberOfFreqs = Integer.parseInt(value);
                    }
                    else if (key.equalsIgnoreCase("RIGHT_NUMBER_OF_FREQS"))
                    {
                        mRightNumberOfFreqs = Integer.parseInt(value);
                    }
                    else if (key.equalsIgnoreCase("LEFT_VOLUME"))
                    {
                        mLeftAudioToneVolume = Integer.parseInt(value);
                        mLeftAudioToneVolume = AudioFunctions.validateVolume(mLeftAudioToneVolume);
                    }
                    else if (key.equalsIgnoreCase("RIGHT_VOLUME"))
                    {
                        mRightAudioToneVolume = Integer.parseInt(value);
                        mRightAudioToneVolume = AudioFunctions.validateVolume(mRightAudioToneVolume);
                    }
                    else if (key.equalsIgnoreCase("LEFT_LOG_SCALE"))
                    {
                        mLeftAudioToneLogScale = (Integer.parseInt(value) == 0) ? false : true;
                    }
                    else if (key.equalsIgnoreCase("RIGHT_LOG_SCALE"))
                    {
                        mRightAudioToneLogScale = (Integer.parseInt(value) == 0) ? false : true;
                    }
                    else if (key.equalsIgnoreCase("LEFT_MUTE"))
                    {
                        mLeftMute = (Integer.parseInt(value) == 1) ? true : false;
                    }
                    else if (key.equalsIgnoreCase("RIGHT_MUTE"))
                    {
                        mRightMute = (Integer.parseInt(value) == 1) ? true : false;
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN key: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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
        else if (strRxCmd.equalsIgnoreCase("GET_AUDIO_TONE_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("TONE_LENGTH=" + mAudioToneLength);
            strDataList.add("SAMPLE_RATE=" + mAudioToneSampleRate);
            strDataList.add("NUMBER_OF_LOOPS=" + mAudioToneNumberOfLoops);

            // mono
            strDataList.add("START_FREQ=" + mAudioToneStartFreq);
            strDataList.add("STOP_FREQ=" + mAudioToneStopFreq);
            strDataList.add("VOLUME=" + mAudioToneVolume);
            strDataList.add("LOG_SCALE=" + (mAudioToneLogScale ? "1" : "0"));

            // stereo left
            strDataList.add("LEFT_START_FREQ=" + mLeftAudioToneStartFreq);
            strDataList.add("LEFT_STOP_FREQ=" + mLeftAudioToneStopFreq);
            strDataList.add("LEFT_VOLUME=" + mLeftAudioToneVolume);
            strDataList.add("LEFT_LOG_SCALE=" + (mLeftAudioToneLogScale ? "1" : "0"));
            strDataList.add("LEFT_MUTE=" + (mLeftMute ? "1" : "0"));

            // stereo right
            strDataList.add("RIGHT_START_FREQ=" + mRightAudioToneStartFreq);
            strDataList.add("RIGHT_STOP_FREQ=" + mRightAudioToneStopFreq);
            strDataList.add("RIGHT_VOLUME=" + mRightAudioToneVolume);
            strDataList.add("RIGHT_LOG_SCALE=" + (mRightAudioToneLogScale ? "1" : "0"));
            strDataList.add("RIGHT_MUTE=" + (mRightMute ? "1" : "0"));

            // multi tone
            StringBuffer freqList = new StringBuffer();

            for (int i = 0; i < mFreqList.size(); i++)
            {
                freqList.append(mFreqList.get(i));

                // only append "," if not last element
                if ((i + 1) < mFreqList.size())
                {
                    freqList.append(",");
                }
            }

            strDataList.add("FREQ_LIST=" + freqList.toString());

            // MULTI_FREQ_TYPE
            strDataList.add("MULTI_FREQ_TYPE=" + mMultiFreqType);

            // FREQ_AMPLITUDE_LIST
            StringBuffer freqAmplitudeList = new StringBuffer();

            for (int i = 0; i < mFreqAmplitudeList.size(); i++)
            {
                freqAmplitudeList.append(mFreqAmplitudeList.get(i));

                // only append "," if not last element
                if ((i + 1) < mFreqAmplitudeList.size())
                {
                    freqAmplitudeList.append(",");
                }
            }
            strDataList.add("FREQ_AMPLITUDE_LIST=" + freqAmplitudeList.toString());

            // FREQ_PHASE_SHIFT_LIST
            StringBuffer freqPhaseShiftList = new StringBuffer();

            for (int i = 0; i < mFreqPhaseShiftList.size(); i++)
            {
                freqPhaseShiftList.append(mFreqPhaseShiftList.get(i));

                // only append "," if not last element
                if ((i + 1) < mFreqPhaseShiftList.size())
                {
                    freqPhaseShiftList.append(",");
                }
            }
            strDataList.add("FREQ_PHASE_SHIFT_LIST=" + freqPhaseShiftList.toString());

            StringBuffer leftFreqList = new StringBuffer();

            for (int i = 0; i < mLeftFreqList.size(); i++)
            {
                leftFreqList.append(mLeftFreqList.get(i));

                // only append "," if not last element
                if ((i + 1) < mLeftFreqList.size())
                {
                    leftFreqList.append(",");
                }
            }

            strDataList.add("LEFT_FREQ_LIST=" + leftFreqList.toString());

            // FREQ_AMPLITUDE_LIST
            StringBuffer leftFreqAmplitudeList = new StringBuffer();

            for (int i = 0; i < mLeftFreqAmplitudeList.size(); i++)
            {
                leftFreqAmplitudeList.append(mLeftFreqAmplitudeList.get(i));

                // only append "," if not last element
                if ((i + 1) < mLeftFreqAmplitudeList.size())
                {
                    leftFreqAmplitudeList.append(",");
                }
            }
            strDataList.add("LEFT_FREQ_AMPLITUDE_LIST=" + leftFreqAmplitudeList.toString());

            // FREQ_PHASE_SHIFT_LIST
            StringBuffer leftFreqPhaseShiftList = new StringBuffer();

            for (int i = 0; i < mLeftFreqPhaseShiftList.size(); i++)
            {
                leftFreqPhaseShiftList.append(mLeftFreqPhaseShiftList.get(i));

                // only append "," if not last element
                if ((i + 1) < mLeftFreqPhaseShiftList.size())
                {
                    leftFreqPhaseShiftList.append(",");
                }
            }
            strDataList.add("LEFT_FREQ_PHASE_SHIFT_LIST=" + leftFreqPhaseShiftList.toString());

            StringBuffer rightFreqList = new StringBuffer();

            for (int i = 0; i < mRightFreqList.size(); i++)
            {
                rightFreqList.append(mRightFreqList.get(i));

                // only append "," if not last element
                if ((i + 1) < mRightFreqList.size())
                {
                    rightFreqList.append(",");
                }
            }

            strDataList.add("RIGHT_FREQ_LIST=" + rightFreqList.toString());

            // FREQ_AMPLITUDE_LIST
            StringBuffer rightFreqAmplitudeList = new StringBuffer();

            for (int i = 0; i < mRightFreqAmplitudeList.size(); i++)
            {
                rightFreqAmplitudeList.append(mRightFreqAmplitudeList.get(i));

                // only append "," if not last element
                if ((i + 1) < mRightFreqAmplitudeList.size())
                {
                    rightFreqAmplitudeList.append(",");
                }
            }
            strDataList.add("RIGHT_FREQ_AMPLITUDE_LIST=" + rightFreqAmplitudeList.toString());

            // FREQ_PHASE_SHIFT_LIST
            StringBuffer rightFreqPhaseShiftList = new StringBuffer();

            for (int i = 0; i < mRightFreqPhaseShiftList.size(); i++)
            {
                rightFreqPhaseShiftList.append(mRightFreqPhaseShiftList.get(i));

                // only append "," if not last element
                if ((i + 1) < mRightFreqPhaseShiftList.size())
                {
                    rightFreqPhaseShiftList.append(",");
                }
            }
            strDataList.add("RIGHT_FREQ_PHASE_SHIFT_LIST=" + rightFreqPhaseShiftList.toString());

            strDataList.add("NUMBER_OF_FREQS=" + mNumberOfFreqs);
            strDataList.add("LEFT_NUMBER_OF_FREQS=" + mLeftNumberOfFreqs);
            strDataList.add("RIGHT_NUMBER_OF_FREQS=" + mRightNumberOfFreqs);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        else if (strRxCmd.equalsIgnoreCase("SET_AUDIO_PATH_SETTINGS"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("AUDIO_MODE"))
                    {
                        if (value.equalsIgnoreCase("NORMAL"))
                        {
                            audioManager.setMode(AudioManager.MODE_NORMAL);
                        }
                        else if (value.equalsIgnoreCase("RINGTONE"))
                        {
                            audioManager.setMode(AudioManager.MODE_RINGTONE);
                        }
                        else if (value.equalsIgnoreCase("IN_CALL"))
                        {
                            audioManager.setMode(AudioManager.MODE_IN_CALL);
                        }
                        else if (value.equalsIgnoreCase("IN_COMMUNICATION"))
                        {
                            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN AUDIO_MODE: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("SPEAKERPHONE"))
                    {
                        if (value.equalsIgnoreCase("ON"))
                        {
                            audioManager.setSpeakerphoneOn(true);
                        }
                        else if (value.equalsIgnoreCase("OFF"))
                        {
                            audioManager.setSpeakerphoneOn(false);
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN SPEAKERPHONE: " + key);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else if (key.equalsIgnoreCase("STREAM_TYPE"))
                    {
                        if (value.equalsIgnoreCase("ALARM"))
                        {
                            mStreamType = AudioManager.STREAM_ALARM;
                        }
                        else if (value.equalsIgnoreCase("DTMF"))
                        {
                            mStreamType = AudioManager.STREAM_DTMF;
                        }
                        else if (value.equalsIgnoreCase("MUSIC"))
                        {
                            mStreamType = AudioManager.STREAM_MUSIC;
                        }
                        else if (value.equalsIgnoreCase("NOTIFICATION"))
                        {
                            mStreamType = AudioManager.STREAM_NOTIFICATION;
                        }
                        else if (value.equalsIgnoreCase("RING"))
                        {
                            mStreamType = AudioManager.STREAM_RING;
                        }
                        else if (value.equalsIgnoreCase("SYSTEM"))
                        {
                            mStreamType = AudioManager.STREAM_SYSTEM;
                        }
                        else if (value.equalsIgnoreCase("VOICE_CALL"))
                        {
                            mStreamType = AudioManager.STREAM_VOICE_CALL;
                        }
                        else
                        {
                            strReturnDataList.add("UNKNOWN STREAM: " + value);
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN KEY: " + key);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
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
        else if (strRxCmd.equalsIgnoreCase("GET_AUDIO_PATH_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            switch (audioManager.getMode())
            {
                case AudioManager.MODE_NORMAL:
                    strDataList.add("AUDIO_MODE=NORMAL");
                    break;
                case AudioManager.MODE_IN_CALL:
                    strDataList.add("AUDIO_MODE=IN_CALL");
                    break;
                case AudioManager.MODE_IN_COMMUNICATION:
                    strDataList.add("AUDIO_MODE=IN_COMMUNICATION");
                    break;
                case AudioManager.MODE_INVALID:
                    strDataList.add("AUDIO_MODE=INVALID");
                    break;
                case AudioManager.MODE_CURRENT:
                    strDataList.add("AUDIO_MODE=CURRENT");
                    break;
                case AudioManager.MODE_RINGTONE:
                    strDataList.add("AUDIO_MODE=RINGTONE");
                    break;
                default:
                    strDataList.add("AUDIO_MODE=UNKNOWN");
            }

            if (audioManager.isSpeakerphoneOn() == true)
            {
                strDataList.add("SPEAKERPHONE=ON");
            }
            else
            {
                strDataList.add("SPEAKERPHONE=OFF");
            }

            switch (mStreamType)
            {
                case AudioManager.STREAM_ALARM:
                    strDataList.add("STREAM_TYPE=ALARM");
                    break;
                case AudioManager.STREAM_DTMF:
                    strDataList.add("STREAM_TYPE=DTMF");
                    break;
                case AudioManager.STREAM_MUSIC:
                    strDataList.add("STREAM_TYPE=MUSIC");
                    break;
                case AudioManager.STREAM_NOTIFICATION:
                    strDataList.add("STREAM_TYPE=NOTIFICATION");
                    break;
                case AudioManager.STREAM_RING:
                    strDataList.add("STREAM_TYPE=RING");
                    break;
                case AudioManager.STREAM_SYSTEM:
                    strDataList.add("STREAM_TYPE=SYSTEM");
                    break;
                case AudioManager.STREAM_VOICE_CALL:
                    strDataList.add("STREAM_TYPE=VOICE_CALL");
                    break;
                default:
                    strDataList.add("STREAM_TYPE=UNKNOWN");
            }

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);

        }
        else if (strRxCmd.equalsIgnoreCase("SET_VOLUME_SETTINGS"))
        {
            if (strRxCmdDataList.size() > 0)
            {
                List<String> strReturnDataList = new ArrayList<String>();

                for (String keyValuePair : strRxCmdDataList)
                {
                    String splitResult[] = splitKeyValuePair(keyValuePair);
                    String key = splitResult[0];
                    String value = splitResult[1];

                    if (key.equalsIgnoreCase("ALARM_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_ALARM, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("DTMF_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_DTMF, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("MUSIC_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_MUSIC, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("NOTIFICATION_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_NOTIFICATION, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("RING_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_RING, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("SYSTEM_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_SYSTEM, Integer.parseInt(value));
                    }
                    else if (key.equalsIgnoreCase("VOICE_CALL_VOLUME"))
                    {
                        setStreamVolume(AudioManager.STREAM_VOICE_CALL, Integer.parseInt(value));
                    }
                    else
                    {
                        strReturnDataList.add("UNKNOWN KEY: " + key);
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("GET_VOLUME_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("ALARM_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_ALARM));

            strDataList.add("DTMF_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_DTMF));

            strDataList.add("MUSIC_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));

            strDataList.add("NOTIFICATION_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION));

            strDataList.add("RING_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_RING));

            strDataList.add("SYSTEM_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM));

            strDataList.add("VOICE_CALL_VOLUME=" + audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_MAX_VOLUME_SETTINGS"))
        {
            List<String> strDataList = new ArrayList<String>();

            strDataList.add("ALARM_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM));

            strDataList.add("DTMF_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_DTMF));

            strDataList.add("MUSIC_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

            strDataList.add("NOTIFICATION_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION));

            strDataList.add("RING_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_RING));

            strDataList.add("SYSTEM_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM));

            strDataList.add("VOICE_CALL_VOLUME=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL));

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("GET_CONNECTED_AUDIO_DEVICES"))
        {
            List<String> strDataList = new ArrayList<String>();

            String connectedDevicesResponse = "CONNECTED_AUDIO_DEVICES=";

            int devices = audioManager.getDevicesForStream(mStreamType);

            if((devices & AudioSystem.DEVICE_OUT_EARPIECE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "EARPIECE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_SPEAKER) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "SPEAKER,";
            }

            if((devices & AudioSystem.DEVICE_OUT_WIRED_HEADSET) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "WIRED_HEADSET,";
            }

            if((devices & AudioSystem.DEVICE_OUT_WIRED_HEADPHONE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "WIRED_HEADPHONE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_BLUETOOTH_SCO) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "BLUETOOTH_SCO,";
            }

            if((devices & AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "BLUETOOTH_SCO_HEADSET,";
            }

            if((devices & AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_CARKIT) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "BLUETOOTH_SCO_CARKIT,";
            }

            if((devices & AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "BLUETOOTH_A2DP_HEADPHONES,";
            }

            if((devices & AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "BLUETOOTH_A2DP_SPEAKER,";
            }

            if((devices & AudioSystem.DEVICE_OUT_AUX_DIGITAL) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "AUX_DIGITAL,";
            }

            if((devices & AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "ANLG_DOCK_HEADSET,";
            }

            if((devices & AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "DGTL_DOCK_HEADSET,";
            }

            if((devices & AudioSystem.DEVICE_OUT_USB_ACCESSORY) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "USB_ACCESSORY,";
            }

            if((devices & AudioSystem.DEVICE_OUT_USB_DEVICE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "USB_DEVICE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_REMOTE_SUBMIX) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "REMOTE_SUBMIX,";
            }

            if((devices & AudioSystem.DEVICE_OUT_TELEPHONY_TX) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "TELEPHONY_TX,";
            }

            if((devices & AudioSystem.DEVICE_OUT_LINE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "LINE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_HDMI_ARC) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "HDMI_ARC,";
            }

            if((devices & AudioSystem.DEVICE_OUT_SPDIF) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "SPDIF,";
            }

            if((devices & AudioSystem.DEVICE_OUT_FM) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "FM,";
            }

            if((devices & AudioSystem.DEVICE_OUT_AUX_LINE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "AUX_LINE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_SPEAKER_SAFE) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "SPEAKER_SAFE,";
            }

            if((devices & AudioSystem.DEVICE_OUT_IP) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "IP,";
            }

            if((devices & DEVICE_OUT_FM_TX) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "FM_TX,";
            }

            if((devices & DEVICE_OUT_PROXY) > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse + "PROXY,";
            }

            if(devices > 0)
            {
                connectedDevicesResponse = connectedDevicesResponse.substring(0, connectedDevicesResponse.length() - 1);
            }
            else
            {
                connectedDevicesResponse = connectedDevicesResponse + "NONE";
            }

            strDataList.add(connectedDevicesResponse);

            CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strDataList);
            sendInfoPacketToCommServer(infoPacket);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_MONO_TONE"))
        {
            byte tone[];

            tone = AudioFunctions.createTone(mAudioToneLength, mAudioToneSampleRate, mAudioToneStartFreq, mAudioToneStopFreq, mAudioToneVolume, mAudioToneLogScale);

            playAudioToneGenMono(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_MONO_MULTI_TONE"))
        {
            byte tone[];

            tone = AudioFunctions.createMultiTone(mAudioToneLength, mAudioToneSampleRate, mAudioToneStartFreq, mAudioToneStopFreq, mFreqList,
                    mFreqAmplitudeList, mFreqPhaseShiftList, mAudioToneVolume, mNumberOfFreqs, false, mMultiFreqType);

            playAudioToneGenMono(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_MONO_FREQ_LIST_TONE"))
        {
            byte tone[];

            if ((mFreqAmplitudeList.size() > 1) && (mFreqAmplitudeList.size() != mFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add("Number of amplitudes in FREQ_AMPLITUDE_LIST must equal to number of frequencies in FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            if ((mFreqPhaseShiftList.size() > 1) && (mFreqPhaseShiftList.size() != mFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add("Number of phase shifts in FREQ_PHASE_SHIFT_LIST must equal to number of frequencies in FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            tone = AudioFunctions.createMultiTone(mAudioToneLength, mAudioToneSampleRate, mAudioToneStartFreq, mAudioToneStopFreq, mFreqList,
                    mFreqAmplitudeList, mFreqPhaseShiftList, mAudioToneVolume, mNumberOfFreqs, true, mMultiFreqType);

            playAudioToneGenMono(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_STEREO_TONE"))
        {
            byte tone[];

            tone = AudioFunctions.createStereoTone(mAudioToneLength, mAudioToneSampleRate, mLeftAudioToneStartFreq, mLeftAudioToneStopFreq,
                    mLeftAudioToneVolume, mLeftAudioToneLogScale, mRightAudioToneStartFreq, mRightAudioToneStopFreq, mRightAudioToneVolume,
                    mRightAudioToneLogScale);

            playAudioToneGenStereo(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_STEREO_MULTI_TONE"))
        {
            byte tone[];

            tone = AudioFunctions.createStereoMultiTone(mAudioToneLength, mAudioToneSampleRate, mLeftAudioToneStartFreq, mLeftAudioToneStopFreq,
                    mLeftFreqList, mLeftFreqAmplitudeList, mLeftFreqPhaseShiftList, mLeftAudioToneVolume, mLeftNumberOfFreqs,
                    mRightAudioToneStartFreq, mRightAudioToneStopFreq, mRightFreqList, mRightFreqAmplitudeList, mRightFreqPhaseShiftList,
                    mRightAudioToneVolume, mRightNumberOfFreqs, false, mMultiFreqType);

            playAudioToneGenStereo(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_ON_STEREO_FREQ_LIST_TONE"))
        {
            byte tone[];

            if ((mLeftFreqAmplitudeList.size() > 1) && (mLeftFreqAmplitudeList.size() != mLeftFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add("Number of amplitudes in LEFT_FREQ_AMPLITUDE_LIST must equal to number of frequencies in LEFT_FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            if ((mRightFreqAmplitudeList.size() > 1) && (mRightFreqAmplitudeList.size() != mRightFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList.add("Number of amplitudes in RIGHT_FREQ_AMPLITUDE_LIST must equal to number of frequencies in RIGHT_FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            if ((mLeftFreqPhaseShiftList.size() > 1) && (mLeftFreqPhaseShiftList.size() != mLeftFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList
                        .add("Number of phase shifts in LEFT_FREQ_PHASE_SHIFT_LIST must equal to number of frequencies in LEFT_FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            if ((mRightFreqPhaseShiftList.size() > 1) && (mRightFreqPhaseShiftList.size() != mRightFreqList.size()))
            {
                List<String> strReturnDataList = new ArrayList<String>();
                strReturnDataList
                        .add("Number of phase shifts in RIGHT_FREQ_PHASE_SHIFT_LIST must equal to number of frequencies in RIGHT_FREQ_LIST");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            tone = AudioFunctions.createStereoMultiTone(mAudioToneLength, mAudioToneSampleRate, mLeftAudioToneStartFreq, mLeftAudioToneStopFreq,
                    mLeftFreqList, mLeftFreqAmplitudeList, mLeftFreqPhaseShiftList, mLeftAudioToneVolume, mLeftNumberOfFreqs,
                    mRightAudioToneStartFreq, mRightAudioToneStopFreq, mRightFreqList, mRightFreqAmplitudeList, mRightFreqPhaseShiftList,
                    mRightAudioToneVolume, mRightNumberOfFreqs, true, mMultiFreqType);

            playAudioToneGenStereo(tone);

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("TURN_OFF_TONE"))
        {
            stopAudioToneGen();

            // Generate an exception to send data back to CommServer
            List<String> strReturnDataList = new ArrayList<String>();
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("PLAY_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            String mediaFileName = "";
            boolean isSpecificMediaFile = false;

            if (strRxCmdDataList.size() > 0)
            {
                if (strRxCmdDataList.size() > 2)
                {
                    strReturnDataList.add("TOO MANY PARAMETERS");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                if (strRxCmdDataList.get(0).equalsIgnoreCase("MOTO_AUDIO_TEST_35"))
                {
                    strReturnDataList.add("MOTO_AUDIO_TEST_35 NO LONGER SUPPORTED");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("MULTI_TONE_1"))
                {
                    strReturnDataList.add("MULTI_TONE_1 NO LONGER SUPPORTED");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("MEDIASWEEP_MMI"))
                {
                    mMediaFileID = com.motorola.motocit.R.raw.mediasweep_mmi;
                }
                else if (strRxCmdDataList.get(0).equalsIgnoreCase("FILE_NAME"))
                {
                    if (strRxCmdDataList.size() == 2)
                    {
                        mediaFileName = strRxCmdDataList.get(1).toString();

                        if (mediaFileName.isEmpty())
                        {
                            strReturnDataList.add("MEDIA FILE NAME IS EMPTY");
                            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                        }

                        isSpecificMediaFile = true;
                    }
                    else
                    {
                        strReturnDataList.add("NO_MEDIA_FILE_SPECIFIED");
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                    }
                }
                else
                {
                    strReturnDataList.add("UNKNOWN: " + strRxCmdDataList.get(0));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }
            }
            else
            {
                strReturnDataList.add("NO_MEDIA_FILE_SPECIFIED: " + strRxCmdDataList.get(0));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            if (mediaPlayer != null)
            {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();

            try
            {
                Context context = this;
                AssetFileDescriptor resourcePath = context.getResources().openRawResourceFd(mMediaFileID);
                try
                {
                    if (isSpecificMediaFile)
                    {
                        String path = "";
                        File path_12m = new File("/system/etc/motorola/12m/" + mediaFileName);
                        File path_sdcard = new File("/mnt/sdcard/" + mediaFileName);
                        if (path_sdcard.exists())
                        {
                            path = "/mnt/sdcard/";
                        }
                        else if (path_12m.exists())
                        {
                            path = "/system/etc/motorola/12m/";
                        }

                        dbgLog(TAG, "media file path:" + path + mediaFileName, 'i');
                        // Play media file
                        mediaPlayer.setDataSource(path + mediaFileName);
                    }
                    else
                    {
                        mediaPlayer.setDataSource(resourcePath.getFileDescriptor(), resourcePath.getStartOffset(), resourcePath.getLength());
                    }
                }
                catch (Exception e)
                {
                    strReturnDataList.add("UNABLE TO SET MEDIA DATA SOURCE");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                mediaPlayer.setAudioStreamType(mStreamType);

                try
                {
                    mediaPlayer.prepare();
                }
                catch (IOException e)
                {
                    strReturnDataList.add("UNABLE TO PREPARE MEDIA FILE");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                if (mediaPlayer == null)
                {
                    strReturnDataList.add("MEDIA FILE NOT FOUND");
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                mediaPlayer.setLooping(true); // Loop playing media file

                mediaPlayer.start();
            }
            catch (IllegalStateException e)
            {
                strReturnDataList.add("UNABLE TO PLAY MEDIA FILE");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_MEDIA_FILE"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if (mediaPlayer != null)
            {
                if (mediaPlayer.isPlaying() == true)
                {
                    mediaPlayer.stop();
                }

                mediaPlayer.release();
                mediaPlayer = null;
            }

            // Generate an exception to send data back to CommServer
            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_AUDIO_MANAGER_PARAMETERS"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if (strRxCmdDataList.size() < 1)
            {
                strReturnDataList.add(String.format("%s command takes at least 1 parameter. You passed in %d", strRxCmd, strRxCmdDataList.size()));
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }

            // technically I could join all parameters into a ';' delimited
            // string
            // but i'll just iterate over it so I can check that each parameter
            // is a keyValue pair
            for (String keyValuePair : strRxCmdDataList)
            {
                if ((keyValuePair.split("=")).length != 2)
                {
                    strReturnDataList.add(String.format("parameter '%s' is not in key=value format.", keyValuePair));
                    throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
                }

                audioManager.setParameters(keyValuePair);
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

    protected void setStreamVolume(int stream, int volume)
    {
        if (volume > audioManager.getStreamMaxVolume(stream))
        {
            audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0);
        }
        else
        {
            audioManager.setStreamVolume(stream, volume, 0);
        }
    }

    @Override
    protected void printHelp()
    {

        List<String> strHelpList = new ArrayList<String>();

        strHelpList.add(TAG);
        strHelpList.add("");
        strHelpList.add("This function will generate an audio tone");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("SET_AUDIO_TONE_SETTINGS - Change audio tone settings for TONE_LENGTH, SAMPLE_RATE, START_FREQ,  STOP_FREQ, VOLUME, NUMBER_OF_LOOPS, and LOG_SCALE");
        strHelpList.add("  ");
        strHelpList.add("  TONE_LENGTH - Length of Audio Tone between 0.001 and 16 seconds");
        strHelpList.add("  ");
        strHelpList.add("  SAMPLE_RATE - Sample Rate of tone between 4000 Hz and 44100 Hz");
        strHelpList.add("  ");
        strHelpList.add("  NUMBER_OF_LOOPS - Number of times to loop the tone. -1 for infinite loops");
        strHelpList.add("  ");

        strHelpList.add("  START_FREQ - Start Frequency of Mono Tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  STOP_FREQ - Stop Frequency of Mono Tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  FREQ_LIST - Comma delimited list of frequencies between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  MULTI_FREQ_TYPE - SIMULTANEOUS: Plays all frequencies at the same time. STEPPED: Plays frequencies one at a time");
        strHelpList.add("  ");
        strHelpList.add("  FREQ_AMPLITUDE_LIST - Comma delimited list of frequency amplitudes between 0% and 100%.");
        strHelpList.add("  ");
        strHelpList.add("  FREQ_PHASE_SHIFT_LIST - Comma delimited list of frequency phase shifts.");
        strHelpList.add("  ");
        strHelpList.add("  NUMBER_OF_FREQS - Number of evenly spaced frequencies between start and stop frequency.");
        strHelpList.add("  ");
        strHelpList.add("  VOLUME - Volume of Mono Tone between 0 and 32767");
        strHelpList.add("  ");
        strHelpList.add("  LOG_SCALE - Linear or Logarithmic Mono tone. 0 for Linear, 1 for Logarithmic");
        strHelpList.add("  ");

        strHelpList.add("  LEFT_START_FREQ - Start Frequency of left channel of stereo tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_STOP_FREQ - Stop Frequency of left channel of stereo tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_FREQ_LIST - Comma delimited list of left channel frequencies between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_FREQ_AMPLITUDE_LIST - Comma delimited list of left channel frequency amplitudes between 0% and 100%.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_FREQ_PHASE_SHIFT_LIST - Comma delimited list of left channel frequency phase shifts.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_NUMBER_OF_FREQS - Number of evenly spaced left channel frequencies between start and stop frequency.");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_VOLUME - Volume of left channel of stereo tone between 0 and 32767");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_LOG_SCALE - Linear or Logarithmic of left channel of stereo tone. 0 for Linear, 1 for Logarithmic");
        strHelpList.add("  ");
        strHelpList.add("  LEFT_MUTE - Mute left channel of stereo tone. 0 for no-mute, 1 for mute");
        strHelpList.add("  ");

        strHelpList.add("  RIGHT_START_FREQ - Start Frequency of right channel of stereo tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_STOP_FREQ - Stop Frequency of right channel of stereo tone between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_FREQ_LIST - Comma delimited list of right channel frequencies between 1 Hz and 20000 Hz.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_FREQ_AMPLITUDE_LIST - Comma delimited list of right channel frequency amplitudes between 0% and 100%.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_FREQ_PHASE_SHIFT_LIST - Comma delimited list of right channel frequency phase shifts.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_NUMBER_OF_FREQS - Number of evenly spaced right channel frequencies between start and stop frequency.");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_VOLUME - Volume of right channel of stereo tone between 0 and 32767");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_LOG_SCALE - Linear or Logarithmic of right channel of stereo tone. 0 for Linear, 1 for Logarithmic");
        strHelpList.add("  ");
        strHelpList.add("  RIGHT_MUTE - Mute right channel of stereo tone. 0 for no-mute, 1 for mute");
        strHelpList.add("  ");

        strHelpList.add("GET_AUDIO_TONE_SETTINGS - Get audio tone settings for all parameters in SET_AUDIO_TONE_SETTINGS.");
        strHelpList.add("  ");
        strHelpList.add("SET_AUDIO_PATH_SETTINGS - Change audio path settings for AUDIO_MODE, and SPEAKERPHONE");
        strHelpList.add("  ");
        strHelpList.add("  AUDIO_MODE - Set Audio Mode to NORMAL, RINGTONE, IN_CALL, or IN_COMMUNICATION");
        strHelpList.add("  ");
        strHelpList.add("  SPEAKERPHONE - Set Speakerphone to ON or OFF");
        strHelpList.add("  ");
        strHelpList.add("  STREAM_TYPE - Set Stream Type to ALARM, DTMF, MUSIC, NOTIFICATION, RING, SYSTEM, or VOICE_CALL");
        strHelpList.add("  ");
        strHelpList.add("GET_AUDIO_PATH_SETTINGS - Change audio path settings for AUDIO_MODE, STREAM_TYPE and SPEAKERPHONE");
        strHelpList.add("  ");
        strHelpList.add("SET_AUDIO_MANAGER_PARAMETERS <KEY_VALUE_PAIRS> - Send key=value pairs directly to the AudioManager");
        strHelpList.add("  <KEY_VALUE_PAIRS> - space delimited key=values pairs");
        strHelpList.add("  ");
        strHelpList.add("GET_CONNECTED_AUDIO_DEVICES - Get List of Connected Audio Devices.");
        strHelpList.add("  ");
        strHelpList.add("  CONNECTED_AUDIO_DEVICES - List of connected audio devices");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_MONO_TONE - Turn On Mono Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_MONO_MULTI_TONE - Turn On Mono Multi Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_MONO_FREQ_LIST_TONE - Turn On Mono Frequency List Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_STEREO_TONE - Turn On Stereo Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_STEREO_MULTI_TONE - Turn On Stereo Multi Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_ON_STEREO_FREQ_LIST_TONE - Turn On Stereo Frequency List Tone.");
        strHelpList.add("  ");
        strHelpList.add("TURN_OFF_TONE - Turn off Tone");
        strHelpList.add("  ");
        strHelpList.add("SET_VOLUME_SETTINGS - Change audio volume settings for ALARM_VOLUME, DTMF_VOLUME, MUSIC_VOLUME, NOTIFICATION_VOLUME, RING_VOLUME, SYSTEM_VOLUME and VOICE_CALL_VOLUME");
        strHelpList.add("  ");
        strHelpList.add("GET_VOLUME_SETTINGS - Get audio volume settings for ALARM_VOLUME, DTMF_VOLUME, MUSIC_VOLUME, NOTIFICATION_VOLUME, RING_VOLUME, SYSTEM_VOLUME and VOICE_CALL_VOLUME");
        strHelpList.add("  ");
        strHelpList.add("GET_MAX_VOLUME_SETTINGS - Get audio max volume settings for ALARM_VOLUME, DTMF_VOLUME, MUSIC_VOLUME, NOTIFICATION_VOLUME, RING_VOLUME, SYSTEM_VOLUME and VOICE_CALL_VOLUME");
        strHelpList.add("  ");
        strHelpList.add("PLAY_MEDIA_FILE - File media file for MOTO_AUDIO_TEST_35, MULTI_TONE_1, or MEDIASWEEP_MMI");
        strHelpList.add("  ");
        strHelpList.add("STOP_MEDIA_FILE - Stop playing media file");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
    }

    private void stopAudioToneGen()
    {
        if (audioTrack != null)
        {
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
            {
                audioTrack.stop();
            }
            audioTrack.release();
        }
    }

    private void playAudioToneGenMono(byte tone[])
    {
        stopAudioToneGen();
        audioTrack = new AudioTrack(mStreamType, mAudioToneSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, tone.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(tone, 0, tone.length);

        audioTrack.setLoopPoints(0, tone.length / 2, mAudioToneNumberOfLoops);

        audioTrack.play();
    }

    private void playAudioToneGenStereo(byte tone[]) throws CmdFailException
    {
        stopAudioToneGen();
        audioTrack = new AudioTrack(mStreamType, mAudioToneSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                tone.length, AudioTrack.MODE_STATIC);
        audioTrack.write(tone, 0, tone.length);

        audioTrack.setLoopPoints(0, tone.length / 4, mAudioToneNumberOfLoops);

        // apply stereo mute settings
        float leftVol = 1.0f;
        float rightVol = 1.0f;
        if (mLeftMute)
        {
            leftVol = 0.0f;
        }

        if (mRightMute)
        {
            rightVol = 0.0f;
        }

        if (audioTrack.setStereoVolume(leftVol, rightVol) != AudioTrack.SUCCESS)
        {
            dbgLog(TAG, "setStereoVolume failed ", 'e');

            List<String> strReturnDataList = new ArrayList<String>();
            strReturnDataList.add("setStereoVolume() failed");
            throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
        }

        audioTrack.play();
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

            contentRecord("testresult.txt", "Audio Tone Test:  PASS" + "\r\n\r\n", MODE_APPEND);
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

            contentRecord("testresult.txt", "Audio Tone Test:  FAILED" + "\r\n\r\n", MODE_APPEND);
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
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Audio Tone Test:  FAILED" + "\r\n\r\n", MODE_APPEND);
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
        contentRecord("testresult.txt", "Audio Tone Test:  PASS" + "\r\n\r\n", MODE_APPEND);
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
