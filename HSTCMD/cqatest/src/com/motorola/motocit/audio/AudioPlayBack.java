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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.motorola.motocit.CmdFailException;
import com.motorola.motocit.CmdPassException;
import com.motorola.motocit.CommServerDataPacket;
import com.motorola.motocit.TestUtils;
import com.motorola.motocit.Test_Base;

public class AudioPlayBack extends Test_Base implements MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnCompletionListener
{
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private Button recordButton;
    private Button playButton;
    private int maxMediaVolume;
    private int maxCallVolume;
    private int initMediaVolume;
    private int initVoiceCallVolume;
    private int userMediaVolume;
    private int userCallVolume;
    private boolean mMediaRecorderRecording = false;
    private boolean mAudioPlaying = false;
    private File mAudioFile;
    private String mAudioPath;
    private MediaRecorder mMediaRecorder = null;
    private final int MAX_DURATION_IN_MS = 10 * 60 * 1000; // max 10 min record
                                                           // duration
    private final long MAX_FILESIZE_IN_BYTE = 10 * 100 * 1024; // 10 min about
                                                               // 1M
    private final long MIN_FILESIZE_IN_BYTE = 100 * 1024; // In smi, the file
                                                          // size is about 100k
                                                          // when recording 60s
    private final String AUDIO_FILENAME = "CQATest_audiorecord.3gp";
    private long mStartTime;
    private TextView mShowTimeView;
    private static final int UPDATE_TIME = 1;
    private final Handler mHandler = new MainHandler();

    // Path needed for low power audio, from MediaRecorder.java
    private static final int AUDIO_SOURCE_MOT_VR_SOURCE = 11;

    private int audio_source = -1;

    private final static String MIC_KEY = "mic_path";
    private final static String PRIMARY_MIC_PATH = "primary";
    private final static String SECONDARY_MIC_PATH = "secondary";
    private final static String TERTIARY_MIC_PATH = "tertiary";
    private final static String HEADSET_MIC_PATH = "headset";
    private final static String MIC_4_PATH = "mic4";
    private final static String MIC_5_PATH = "mic5";
    private final static String MIC_PATH_NONE = "none";

    private String MIC_PATH_BE_SET = "none";

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
    private TextView playback_prompt_ui;
    private Spinner spinnerPlayback;
    private ArrayAdapter<String> adapterPlayback;

    private CheckBox speakerCheckbox;

    private boolean isPermissionAllowed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        TAG = "Audio_Playback";

        super.onCreate(savedInstanceState);

        View thisView = adjustViewDisplayArea(com.motorola.motocit.R.layout.audio_playback);
        if (mGestureListener != null)
        {
            thisView.setOnTouchListener(mGestureListener);
        }
    }

    private void initAudioSource()
    {
        playback_prompt_ui = (TextView) findViewById(com.motorola.motocit.R.id.micSelectUiPrompt);
        spinnerPlayback = (Spinner) findViewById(com.motorola.motocit.R.id.spinnerMicSelect);

        adapterPlayback = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);

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
                adapterPlayback.add(audiosource_spinner[i]);
            }
        }

        adapterPlayback.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerPlayback.setAdapter(adapterPlayback);
        spinnerPlayback.setOnItemSelectedListener(new Spinner.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3)
            {
                String micName = spinnerPlayback.getSelectedItem().toString();
                playback_prompt_ui.setText("Audio record input path: " + micName);

                if (micName.equalsIgnoreCase("DEFAULT MIC"))
                {
                    dbgLog(TAG, "Selected default mic from UI", 'i');
                    MIC_PATH_BE_SET = PRIMARY_MIC_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("CAMCORDER MIC"))
                {
                    dbgLog(TAG, "Selected camcorder mic from UI", 'i');
                    MIC_PATH_BE_SET = MIC_PATH_NONE;
                    audio_source = MediaRecorder.AudioSource.CAMCORDER;
                }
                else if (micName.equalsIgnoreCase("PRIMARY MIC"))
                {
                    dbgLog(TAG, "Selected primary mic from UI", 'i');
                    MIC_PATH_BE_SET = PRIMARY_MIC_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("SECONDARY MIC"))
                {
                    dbgLog(TAG, "Selected secondary mic from UI", 'i');
                    MIC_PATH_BE_SET = SECONDARY_MIC_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("TERTIARY MIC"))
                {
                    dbgLog(TAG, "Selected tertiary mic from UI", 'i');
                    MIC_PATH_BE_SET = TERTIARY_MIC_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("HEADSET MIC"))
                {
                    dbgLog(TAG, "Selected headset mic from UI", 'i');
                    MIC_PATH_BE_SET = HEADSET_MIC_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("PRIMARY MIC LOW PWR"))
                {
                    dbgLog(TAG, "Selected primary low pwr mic from UI", 'i');
                    MIC_PATH_BE_SET = PRIMARY_MIC_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else if (micName.equalsIgnoreCase("SECONDARY MIC LOW PWR"))
                {
                    dbgLog(TAG, "Selected secondary low pwr mic from UI", 'i');
                    MIC_PATH_BE_SET = SECONDARY_MIC_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else if (micName.equalsIgnoreCase("TERTIARY MIC LOW PWR"))
                {
                    dbgLog(TAG, "Selected tertiary low pwr mic from UI", 'i');
                    MIC_PATH_BE_SET = TERTIARY_MIC_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else if (micName.equalsIgnoreCase("HEADSET MIC LOW PWR"))
                {
                    dbgLog(TAG, "Selected headset low pwr mic from UI", 'i');
                    MIC_PATH_BE_SET = HEADSET_MIC_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else if (micName.equalsIgnoreCase("MIC 4"))
                {
                    dbgLog(TAG, "Selected mic 4 from UI", 'i');
                    MIC_PATH_BE_SET = MIC_4_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("MIC 4 LOW PWR"))
                {
                    dbgLog(TAG, "Selected mic 4 low pwr from UI", 'i');
                    MIC_PATH_BE_SET = MIC_4_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else if (micName.equalsIgnoreCase("MIC 5"))
                {
                    dbgLog(TAG, "Selected mic 5 from UI", 'i');
                    MIC_PATH_BE_SET = MIC_5_PATH;
                    audio_source = MediaRecorder.AudioSource.DEFAULT;
                }
                else if (micName.equalsIgnoreCase("MIC 5 LOW PWR"))
                {
                    dbgLog(TAG, "Selected mic 5 low pwr from UI", 'i');
                    MIC_PATH_BE_SET = MIC_5_PATH;
                    audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                    AudioUtils.setDspAudioPath(getApplicationContext());
                }
                else
                {
                    dbgLog(TAG, "Error of mic selection", 'e');
                }

                arg0.setVisibility(View.VISIBLE);

            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {

            }
        });
    }

    private void setSelectedMicPath(String audioMicSelect)
    {
        String keyPair = MIC_KEY + "=" + audioMicSelect;
        dbgLog(TAG, "Setting selected mic with keyPair '" + keyPair + "'", 'd');
        audioManager.setParameters(keyPair);
    }

    private int getSpinnerLocation(String audio_input_source)
    {
        int position = -1;

        if (adapterPlayback != null)
        {
            for (int i = 0; i < adapterPlayback.getCount(); i++)
            {
                if (adapterPlayback.getItem(i).toString().equals(audio_input_source))
                {
                    position = i;
                }
            }
        }

        dbgLog(TAG, "get position for " + audio_input_source + " =" + position, 'i');
        return position;
    }

    private void initAudio()
    {
        // Rout audio to speaker
        audioManager = (AudioManager) AudioPlayBack.this.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
        dbgLog(TAG, "SetSpeakerOn = True", 'i');

        audioManager.setMode(AudioManager.MODE_NORMAL);

        // Record current media volume, will restore it
        initMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        initVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        maxMediaVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        maxCallVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void startRecordAudio(int audio_source_type)
    {
        // Check if sdcard is mounted.
        if (!(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)))
        {
            dbgLog(TAG, "No sdcard mounted, exit recording.", 'v');
            Toast.makeText(AudioPlayBack.this, "No sdcard. Exit recording.", Toast.LENGTH_LONG).show();
            return;
        }

        // Check if storage is enough to store video.
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().toString());
        long avaliableStorage = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        if (avaliableStorage < MIN_FILESIZE_IN_BYTE)
        {
            dbgLog(TAG, "storage is not enough, exit recording.", 'v');
            Toast.makeText(AudioPlayBack.this, "No enough storage. Exit recording.", Toast.LENGTH_LONG).show();
            return;
        }

        if (mAudioFile.exists())
        {
            mAudioFile.delete();
        }

        mMediaRecorderRecording = true;

        if (mMediaRecorder == null)
        {
            mMediaRecorder = new MediaRecorder();
        }
        else
        {
            mMediaRecorder.reset();
        }

        mMediaRecorder.setAudioSource(audio_source_type);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setMaxDuration(MAX_DURATION_IN_MS);
        mMediaRecorder.setMaxFileSize(MAX_FILESIZE_IN_BYTE);
        mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());
        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);

        try
        {
            mMediaRecorder.prepare();
        }
        catch (IOException e)
        {
            dbgLog(TAG, "prepare failed for " + mAudioFile, 'e');
            stopRecordAudio();
            throw new RuntimeException(e);
        }

        try
        {
            mMediaRecorder.start(); // Recording is now started
        }
        catch (RuntimeException e)
        {
            dbgLog(TAG, "Could not start media recorder. ", 'e');
            stopRecordAudio();
            return;
        }

        updateRecordingOrPlayingTime();

    }

    private void stopRecordAudio()
    {
        dbgLog(TAG, "stopRecordAudio", 'v');

        if (mMediaRecorderRecording)
        {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            try
            {
                mMediaRecorder.stop();
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "stop fail: " + e.getMessage(), 'e');
            }
            mMediaRecorderRecording = false;
            audioManager.setParameters(MIC_KEY + "=" + MIC_PATH_NONE);
            recordButton.setText("Start to Record");
            recordButton.setBackgroundColor(Color.TRANSPARENT);
            recordButton.invalidate();
        }
        releaseMediaRecorder();
    }

    private void releaseMediaRecorder()
    {
        dbgLog(TAG, "Releasing media recorder.", 'v');
        if (mMediaRecorder != null)
        {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void startAudioPlayback()
    {
        if (!mAudioFile.exists())
        {
            dbgLog(TAG, "Audio file does not exist.", 'v');
            Toast.makeText(AudioPlayBack.this, "Audio file does not exist.", Toast.LENGTH_LONG).show();
            stopAudioPlayback();
            return;
        }

        mAudioPlaying = true;

        if (mediaPlayer == null)
        {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
        }
        else
        {
            mediaPlayer.reset();
        }

        mediaPlayer.setOnCompletionListener(this);

        try
        {
            mediaPlayer.setDataSource(mAudioPath + AUDIO_FILENAME);
            dbgLog(TAG, "data_source= " + mAudioPath + AUDIO_FILENAME, 'i');

            try
            {
                mediaPlayer.prepare();
            }
            catch (IOException e)
            {
                dbgLog(TAG, "prepare failed for " + mAudioFile, 'e');
                stopAudioPlayback();
                throw new RuntimeException(e);
            }

            try
            {
                mediaPlayer.start(); // playing is now started
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "Could not play video. ", 'e');
                stopAudioPlayback();
                return;
            }

            updateRecordingOrPlayingTime();

        }
        catch (IllegalStateException e)
        {
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void stopAudioPlayback()
    {
        if (mAudioPlaying)
        {
            try
            {
                mediaPlayer.stop();
            }
            catch (RuntimeException e)
            {
                dbgLog(TAG, "stop fail: " + e.getMessage(), 'e');
            }

            mAudioPlaying = false;
            playButton.setText("Start to playback");
            playButton.setBackgroundColor(Color.TRANSPARENT);
            playButton.invalidate();
        }
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer()
    {
        dbgLog(TAG, "Releasing media player.", 'v');
        if (mediaPlayer != null)
        {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private OnClickListener playMediaFileListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            // avoid misoperation.
            if (mMediaRecorderRecording)
            {
                Toast.makeText(AudioPlayBack.this, "Recording audio... Pls playback after recording complete.", Toast.LENGTH_LONG).show();
                return;
            }

            if (!mAudioPlaying)
            {
                startAudioPlayback();
                if (mAudioPlaying == true)
                {
                    playButton.setText("Stop playback");
                    playButton.setBackgroundColor(Color.RED);
                    playButton.invalidate();
                }
            }
            else
            {
                stopAudioPlayback();
            }
        }
    };

    private OnClickListener recordMediaFileListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            // avoid misoperation.
            if (mAudioPlaying)
            {
                Toast.makeText(AudioPlayBack.this, "Playing audio... Pls stop playback firstly before record.", Toast.LENGTH_LONG).show();
                return;
            }

            if (!mMediaRecorderRecording)
            {
                dbgLog(TAG, "audio_source=" + audio_source, 'i');
                setSelectedMicPath(MIC_PATH_BE_SET);
                startRecordAudio(audio_source);

                if (mMediaRecorderRecording == true)
                {
                    recordButton.setText("Stop Recording");
                    recordButton.setBackgroundColor(Color.RED);
                    recordButton.invalidate();
                }
            }
            else
            {
                stopRecordAudio();
            }
        }
    };

    private void release()
    {
        if (mediaPlayer != null)
        {
            // Restore media volume;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initMediaVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, initVoiceCallVolume, 0);

            try
            {
                Thread.sleep(1000);
            }
            catch (Exception e)
            {
                dbgLog(TAG, "sleep exceptions..." + e, 'd');
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
        finish();
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
    public void onResume()
    {
        super.onResume();

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
            }
            else
            {
                isPermissionAllowed = true;
            }
        }

        if (isPermissionAllowed)
        {
            recordButton = (Button) findViewById(com.motorola.motocit.R.id.audiorecord);
            playButton = (Button) findViewById(com.motorola.motocit.R.id.audioplay);
            mShowTimeView = (TextView) findViewById(com.motorola.motocit.R.id.showrecordtime);
            speakerCheckbox = (CheckBox) findViewById(com.motorola.motocit.R.id.speakerOnOff);
            mAudioPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator;
            dbgLog(TAG, "audio output path: " + mAudioPath, 'd');
            mAudioFile = new File(mAudioPath + AUDIO_FILENAME);

            dbgLog(TAG, "audio_file_path=" + mAudioFile.getAbsolutePath(), 'i');

            speakerCheckbox.setChecked(false); // default set to loudspeaker

            speakerCheckbox.setText("output: Loudspeaker");

            mShowTimeView.setText("00:00"); // Init time to zero and show on UI

            initAudio();

            speakerCheckbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener()
            {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    if (speakerCheckbox.isChecked())
                    {
                        audioManager.setSpeakerphoneOn(false);
                        dbgLog(TAG, "SetSpeakerOn = False", 'i');
                        // Record current in-call volume, will restore it
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVolume, 0);

                        audioManager.setMode(AudioManager.MODE_NORMAL);

                        speakerCheckbox.setText("output: Earpiece");
                    }
                    else
                    {
                        audioManager.setSpeakerphoneOn(true);
                        dbgLog(TAG, "SetSpeakerOn = True", 'i');
                        // Record current in-call volume, will restore it
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMediaVolume, 0);
                        setVolumeControlStream(AudioManager.STREAM_MUSIC);

                        audioManager.setMode(AudioManager.MODE_NORMAL);

                        speakerCheckbox.setText("output: Loudspeaker");
                    }

                }
            });

            initAudioSource();

            recordButton.setOnClickListener(recordMediaFileListener);

            playButton.setOnClickListener(playMediaFileListener);

            sendStartActivityPassed();
        }
        else
        {
            sendStartActivityFailed("No Permission Granted to run Camera test");
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (isPermissionAllowed)
        {
            if (mMediaRecorderRecording == true)
            {
                stopRecordAudio();
            }

            if (mAudioPlaying == true)
            {
                stopAudioPlayback();
            }

            // Restore audio volume
            if (audioManager != null)
            {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initMediaVolume, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, initVoiceCallVolume, 0);
            }

            // delete video file
            if (mAudioFile.exists())
            {
                dbgLog(TAG, "delete audio file.", 'd');
                mAudioFile.delete();
                return;
            }
        }
    }

    @Override
    public void onDestroy()
    {
        dbgLog(TAG, "onDestroy", 'd');

        super.onDestroy();
    }

    @Override
    public void onCompletion(MediaPlayer player)
    {
        dbgLog(TAG, "onCompletion called", 'v');
        Toast.makeText(AudioPlayBack.this, "playing audio complete.", Toast.LENGTH_LONG).show();
        stopAudioPlayback();
        return;
    }

    // MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra)
    {
        dbgLog(TAG, "MediaRecorder onError.", 'v');
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN)
        {
            // may have run out of space on the sdcard.
            stopRecordAudio();
        }
    }

    // MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra)
    {
        dbgLog(TAG, "MediaRecorder OnInfo.", 'v');
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
        {
            dbgLog(TAG, "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED.", 'v');
            Toast.makeText(AudioPlayBack.this, "Max recording time reached", Toast.LENGTH_LONG).show();
        }
        else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
        {
            dbgLog(TAG, "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED", 'v');
            Toast.makeText(AudioPlayBack.this, "Max file size reached", Toast.LENGTH_LONG).show();
        }

        if (mMediaRecorderRecording)
        {
            stopRecordAudio();
        }
    }

    // MediaPlayer.onInfo
    @Override
    public boolean onInfo(MediaPlayer player, int whatInfo, int extra)
    {
        dbgLog(TAG, " Mediaplayer onInfo", 'v');
        switch (whatInfo)
        {
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                dbgLog(TAG, "MEDIA_INFO_BAD_INTERLEAVING", 'v');
                break;
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                dbgLog(TAG, "MEDIA_INFO_METADATA_UPDATE", 'v');
                break;
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                dbgLog(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING", 'v');
                break;
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                dbgLog(TAG, "MEDIA_INFO_NOT_SEEKABLE", 'v');
                break;
            default:
                break;
        }

        if (mAudioPlaying)
        {
            stopAudioPlayback();
        }

        return true;
    }

    // Mediaplayer onError
    @Override
    public boolean onError(MediaPlayer player, int whatError, int extra)
    {
        dbgLog(TAG, " Mediaplayer onError", 'v');
        switch (whatError)
        {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                dbgLog(TAG, "MEDIA_ERROR_SERVER_DIED", 'v');
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                dbgLog(TAG, "MEDIA_ERROR_UNKNOWN", 'v');
                break;
            default:
                break;
        }

        if (mAudioPlaying)
        {
            stopAudioPlayback();
        }

        return true;
    }

    private class MainHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case UPDATE_TIME:
                    updateRunningTime();
                    break;

                default:
                    dbgLog(TAG, "Unhandled message: " + msg.what, 'v');
                    break;
            }
        }
    }

    private void updateRecordingOrPlayingTime()
    {
        dbgLog(TAG, "updateRecordingOrPlayingTime called", 'v');
        mStartTime = SystemClock.uptimeMillis();
        mShowTimeView.setText("");
        mShowTimeView.setVisibility(View.VISIBLE);
        updateRunningTime();
    }

    private void updateRunningTime()
    {
        dbgLog(TAG, "updateRunningTime called", 'v');

        if (!mMediaRecorderRecording && !mAudioPlaying)
        {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mStartTime;
        long next_update_delay = 1000 - (delta % 1000);
        long seconds = delta / 1000; // round to nearest

        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        String secondsString = Long.toString(remainderSeconds);
        if (secondsString.length() < 2)
        {
            secondsString = "0" + secondsString;
        }
        String minutesString = Long.toString(remainderMinutes);
        if (minutesString.length() < 2)
        {
            minutesString = "0" + minutesString;
        }
        String text = minutesString + ":" + secondsString;
        if (hours > 0)
        {
            String hoursString = Long.toString(hours);
            if (hoursString.length() < 2)
            {
                hoursString = "0" + hoursString;
            }
            text = hoursString + ":" + text;
        }

        mShowTimeView.setText(text);
        mHandler.sendEmptyMessageDelayed(UPDATE_TIME, next_update_delay);
    }

    @Override
    protected void handleTestSpecificActions() throws CmdFailException, CmdPassException
    {
        if (strRxCmd.equalsIgnoreCase("START_AUDIO_RECORD"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            if (mAudioPlaying)
            {
                strReturnDataList.add("Audio playback is in process, stop playback at first");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mMediaRecorderRecording)
            {
                strReturnDataList.add("Audio recording is in process, stop then record it again");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {
                UpdateUiButton updateUi = new UpdateUiButton(recordButton, true);
                runOnUiThread(updateUi);

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_AUDIO_RECORD"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if (mAudioPlaying)
            {
                strReturnDataList.add("Audio playback is in process, can not perform stop record at this time");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mMediaRecorderRecording)
            {
                // Only perform stop when recording is on-going
                UpdateUiButton updateUi = new UpdateUiButton(recordButton, true);
                runOnUiThread(updateUi);
            }

            throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
        }
        else if (strRxCmd.equalsIgnoreCase("SET_AUDIO_SETTINGS"))
        {
            // if list has no data .. return NACK
            if (strRxCmdDataList.size() == 0)
            {
                List<String> strErrMsgList = new ArrayList<String>();
                strErrMsgList.add("No key=value pairs sent for SET_AUDIO_SETTINGS cmd");
                dbgLog(TAG, strErrMsgList.get(0), 'i');
                throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
            }

            for (String keyValuePair : strRxCmdDataList)
            {
                String splitResult[] = splitKeyValuePair(keyValuePair);
                String key = splitResult[0];
                String value = splitResult[1];

                // Get AUDIO_INPUT
                if (key.equalsIgnoreCase("AUDIO_INPUT"))
                {
                    if (value.equalsIgnoreCase("DEFAULT"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("DEFAULT MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("CAMCORDER"))
                    {
                        audio_source = MediaRecorder.AudioSource.CAMCORDER;

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("CAMCORDER MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("PRIMARY"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(PRIMARY_MIC_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("PRIMARY MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("SECONDARY"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(SECONDARY_MIC_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("SECONDARY MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("TERTIARY"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(TERTIARY_MIC_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("TERTIARY MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("HEADSET"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(HEADSET_MIC_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("HEADSET MIC"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("PRIMARY_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(PRIMARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("PRIMARY MIC LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("SECONDARY_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(SECONDARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("SECONDARY MIC LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("TERTIARY_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(TERTIARY_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("TERTIARY MIC LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("HEADSET_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(HEADSET_MIC_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("HEADSET MIC LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("MIC_4"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(MIC_4_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("MIC 4"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("MIC4_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(MIC_4_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("MIC 4 LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("MIC_5"))
                    {
                        audio_source = MediaRecorder.AudioSource.DEFAULT;
                        setSelectedMicPath(MIC_5_PATH);

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("MIC 5"));
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("MIC5_LOW_PWR"))
                    {
                        audio_source = AUDIO_SOURCE_MOT_VR_SOURCE;
                        setSelectedMicPath(MIC_5_PATH);
                        AudioUtils.setDspAudioPath(getApplicationContext());

                        UpdateUiDropBox updateUi = new UpdateUiDropBox(spinnerPlayback, getSpinnerLocation("MIC 5 LOW PWR"));
                        runOnUiThread(updateUi);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("UNKNOWN value " + value);
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                // Get AUDIO_OUTPUT
                else if (key.equalsIgnoreCase("AUDIO_OUTPUT"))
                {
                    if (value.equalsIgnoreCase("LOUDSPEAKER"))
                    {
                        audioManager.setSpeakerphoneOn(true);
                        dbgLog(TAG, "CommServer audio output_SetSpeakerOn = True", 'i');

                        UpdateUiCheckbox updateUi = new UpdateUiCheckbox(speakerCheckbox, false);
                        runOnUiThread(updateUi);
                    }
                    else if (value.equalsIgnoreCase("EARPIECE"))
                    {
                        audioManager.setSpeakerphoneOn(false);
                        dbgLog(TAG, "CommServer audio output_SetSpeakerOn = False", 'i');

                        UpdateUiCheckbox updateUi = new UpdateUiCheckbox(speakerCheckbox, true);
                        runOnUiThread(updateUi);
                    }
                    else
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("UNKNOWN value " + value);
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                }
                // Get MEDIA_VOLUME
                else if (key.equalsIgnoreCase("MEDIA_VOLUME"))
                {
                    userMediaVolume = Integer.parseInt(value);
                    if ((userMediaVolume < 0) || (userMediaVolume > 100))
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("The value " + value + " is incorrect");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                    else
                    {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (userMediaVolume * 0.01 * maxMediaVolume), AudioManager.FLAG_SHOW_UI);
                        setVolumeControlStream(AudioManager.STREAM_MUSIC);
                    }
                }
                // Get INCALL_VOLUME
                else if (key.equalsIgnoreCase("INCALL_VOLUME"))
                {
                    userCallVolume = Integer.parseInt(value);
                    if ((userCallVolume < 0) || (userCallVolume > 100))
                    {
                        List<String> strErrMsgList = new ArrayList<String>();
                        strErrMsgList.add("The value " + value + " is incorrect");
                        dbgLog(TAG, strErrMsgList.get(0), 'i');
                        throw new CmdFailException(nRxSeqTag, strRxCmd, strErrMsgList);
                    }
                    else
                    {
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (int) (userCallVolume * 0.01 * maxCallVolume), AudioManager.FLAG_SHOW_UI);
                        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
                    }
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
        else if (strRxCmd.equalsIgnoreCase("START_AUDIO_PLAYBACK"))
        {
            List<String> strReturnDataList = new ArrayList<String>();
            if (mMediaRecorderRecording)
            {
                strReturnDataList.add("Audio recording is in process, stop record at first");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mAudioPlaying)
            {
                strReturnDataList.add("Audio playback is in process, stop then play it again");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else
            {

                UpdateUiButton updateUi = new UpdateUiButton(playButton, true);
                runOnUiThread(updateUi);

                // Generate an exception to send data back to CommServer
                throw new CmdPassException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
        }
        else if (strRxCmd.equalsIgnoreCase("STOP_AUDIO_PLAYBACK"))
        {
            List<String> strReturnDataList = new ArrayList<String>();

            if (mMediaRecorderRecording)
            {
                strReturnDataList.add("Audio recording is in process, can not perform stop playback at this time");
                throw new CmdFailException(nRxSeqTag, strRxCmd, strReturnDataList);
            }
            else if (mAudioPlaying)
            {
                // Only perform stop playback when auido playback is on-going
                UpdateUiButton updateUi = new UpdateUiButton(playButton, true);
                runOnUiThread(updateUi);
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
        strHelpList.add("This function will record audio from different mic and playback through loudspeaker or earpiece");
        strHelpList.add("");

        strHelpList.addAll(getBaseHelp());

        strHelpList.add("Activity Specific Commands");
        strHelpList.add("  ");
        strHelpList.add("  START_AUDIO_RECORD     - Start to record audio. Audio_input will select default mic by default");
        strHelpList.add("  STOP_AUDIO_RECORD      - Stop audio recording");
        strHelpList.add("  SET_AUDIO_SETTINGS     - Set specific audio parameters. Valid settings below:");
        strHelpList.add("    AUDIO_INPUT          - Audio input. DEFAULT, CAMERCODER, PRIMARY, SECONDARY, TERTIARY, HEADSET, PRIMARY_LOW_PWR, SECONDARY_LOW_PWR, TERTIARY_LOW_PWR, HEADSET_LOW_PWR, MIC_4, MIC4_LOW_PWR, MIC_5 and MIC5_LOW_PWR");
        strHelpList.add("    AUDIO_OUTPUT         - Audio output. LOUDSPEAKER, EARPIECE");
        strHelpList.add("    MEDIA_VOLUME         - Percentage of max media volume.   0-100");
        strHelpList.add("    INCALL_VOLUME        - Percentage of max in-call volume. 0-100");
        strHelpList.add("  START_AUDIO_PLAYBACK   - Start playback recorded audio");
        strHelpList.add("  STOP_AUDIO_PLAYBACK    - Stop playback audio");
        strHelpList.add("  ");

        CommServerDataPacket infoPacket = new CommServerDataPacket(nRxSeqTag, strRxCmd, TAG, strHelpList);
        sendInfoPacketToCommServer(infoPacket);
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

            contentRecord("testresult.txt", "Audio - Record and PlayBack:  PASS" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            release();

            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Audio - Record and PlayBack:  FAILED" + "\r\n\r\n", MODE_APPEND);
            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            release();

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
                release();
            }
        }

        return true;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Audio - Record and PlayBack:  FAILED" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        release();

        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {
        contentRecord("testresult.txt", "Audio - Record and PlayBack:  PASS" + "\r\n\r\n", MODE_APPEND);
        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        release();

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
            release();
        }
        return true;
    }
}
