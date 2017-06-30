/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *    Date          CR            Author                Description
 * 2012/12/19   IKMAIN-49324     Kurt Walker - qa3843  Added stereo sampling capability
 * 2012/05/23   IKHSS7-34818     Ken Moy - wkm039      Created new service for use with NexTest testing
 */

package com.motorola.motocit.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Binder;
import android.os.IBinder;

import com.motorola.motocit.R;
import com.motorola.motocit.TestUtils;

public class AudioSampleService extends Service
{
    private static final String TAG = "Audio_Sample_Service";

    private final IBinder mBinder = new LocalBinder();

    // Notification manager allows the service icon to show up in the
    // notification window.
    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.audio_sample_service_started;

    protected AudioSampleThread mAudioSampleThread = null;
    private volatile boolean mAudioSampleThreadStatus = false;
    @SuppressWarnings("unused")
    private volatile String mAudioSampleThreadMesg;

    private int sampleBufferDepth = 1;
    private volatile List<short[]> mSampleBufferLeft = new ArrayList<short[]>();
    private volatile List<short[]> mSampleBufferRight = new ArrayList<short[]>();
    private final Lock mSampleBufferLock = new ReentrantLock();
    private int mNumberOfChannelsSampled = 0;

    protected class AudioSampleThread extends Thread
    {
        private volatile AudioSample.AudioSampleSettings sampleSettings;
        volatile boolean finished = false;
        volatile boolean paused = false;
        volatile boolean requestPause = false;
        volatile boolean sampleReset = false;
        volatile boolean requestSampleReset = false;

        AudioSampleThread(AudioSample.AudioSampleSettings settings)
        {
            this.sampleSettings = settings;
        }

        public void stopMe()
        {
            dbgLog(TAG, "AudioSampleThread stopMe", 'd');
            finished = true;
        }

        public boolean pauseMe(int timeoutMs)
        {
            dbgLog(TAG, "AudioSampleThread pauseMe", 'd');
            paused = false;
            requestPause = true;

            // wait for thread to set paused boolean
            long startTime = System.currentTimeMillis();

            while (paused == false)
            {
                if ((System.currentTimeMillis() - startTime) > timeoutMs)
                {
                    dbgLog(TAG, "AudioSampleThread pauseMe timeout", 'd');
                    return false;
                }

                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException ignore)
                {

                }
            }

            return true;
        }

        public void unpauseMe()
        {
            dbgLog(TAG, "AudioSampleThread unpauseMe", 'd');
            requestPause = false;
        }

        public boolean resetCurrentSample(int timeoutMs)
        {
            dbgLog(TAG, "AudioSampleThread resetCurrentSample", 'd');
            sampleReset = false;
            requestSampleReset = true;

            // wait for thread to set paused boolean
            long startTime = System.currentTimeMillis();

            while (sampleReset == false)
            {
                if ((System.currentTimeMillis() - startTime) > timeoutMs)
                {
                    dbgLog(TAG, "AudioSampleThread resetCurrentSample timeout", 'd');
                    return false;
                }

                try
                {
                    Thread.sleep(1);
                }
                catch (InterruptedException ignore)
                {

                }
            }
            dbgLog(TAG, "AudioSampleThread resetCurrentSample Complete", 'd');
            return true;
        }

        public void updateSampleSettings(AudioSample.AudioSampleSettings newSettings)
        {
            dbgLog(TAG, "AudioSampleThread updateSampleSettings", 'd');
            this.sampleSettings = newSettings;
        }

        public AudioSample.AudioSampleSettings getCurrentSampleSettings()
        {
            return sampleSettings;
        }

        @Override
        public void run()
        {
            dbgLog(TAG, "Start AudioSampleThread", 'd');

            AudioRecord recorder = null;
            mAudioSampleThreadStatus = true;
            mAudioSampleThreadMesg = "";

            try
            {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

                String keyPair = sampleSettings.micKey + "=" + sampleSettings.audioMicSelect;
                dbgLog(TAG, "Setting mic select with keyPair '" + keyPair + "'", 'd');

                mAudioManager.setParameters(keyPair);

                int audioRecordBufferSize = AudioRecord.getMinBufferSize(sampleSettings.sampleRate, sampleSettings.audioInputFormat,
                        AudioFormat.ENCODING_PCM_16BIT);

                recorder = new AudioRecord(sampleSettings.audioInputSource, sampleSettings.sampleRate, sampleSettings.audioInputFormat,
                        AudioFormat.ENCODING_PCM_16BIT, audioRecordBufferSize);

                // Report back the number of channels
                mNumberOfChannelsSampled = recorder.getChannelCount();

                recorder.startRecording();

                // until we fill sampleBuffer
                short[] tempBuffer = new short[audioRecordBufferSize];

                int numDiscardSamples = (int) (sampleSettings.sampleRate * (sampleSettings.audioSampleDiscardTimeMs / 1000.0));

                while (finished == false)
                {
                    // see if we need to pause sampling
                    if (requestPause == true)
                    {
                        paused = true;
                        Thread.sleep(1);
                        continue;
                    }

                    int totalShortsRead = 0;
                    long startTime = System.currentTimeMillis();

                    // setup sampleBuffer
                    int numSamples = (int) (sampleSettings.sampleRate * ((sampleSettings.sampleLengthMs) / 1000.0));
                    short[] sampleBufferLeft = new short[numSamples];
                    short[] sampleBufferRight = new short[numSamples];

                    while ((requestSampleReset == false) && (sampleBufferLeft.length > totalShortsRead))
                    {
                        if (finished)
                        {
                            return;
                        }

                        // see if hit timeout
                        if ((System.currentTimeMillis() - startTime) > sampleSettings.audioCaptureTimeoutMs)
                        {
                            dbgLog(TAG, "AudioSampleThread timeout reached", 'd');
                            throw new Exception("AudioSampleThread timeout reached. Timeout (ms) is " + sampleSettings.audioCaptureTimeoutMs);
                        }

                        int shortsRead = recorder.read(tempBuffer, 0, tempBuffer.length);

                        if (shortsRead < 0)
                        {
                            throw new Exception("AudioRecord.read() returned error " + shortsRead);
                        }

                        dbgLog(TAG, "AudioSampleThread read " + shortsRead + " shorts", 'd');

                        int startOffset = 0;

                        // throw out numDiscardSamples
                        for (int i = 0; (i < shortsRead) && (numDiscardSamples > 0); i++)
                        {
                            startOffset++;
                            numDiscardSamples--;
                        }

                        dbgLog(TAG, "AudioSampleThread startOffset " + startOffset, 'd');

                        // append to sampleBuffer
                        for (int i = startOffset; (i < shortsRead) && (sampleBufferLeft.length > totalShortsRead); i++)
                        {
                            // see if we need to reset sampling
                            if (requestSampleReset == true)
                            {
                                break;
                            }

                            sampleBufferLeft[totalShortsRead] = tempBuffer[i];

                            if (mNumberOfChannelsSampled == 2)
                            {
                                i++;
                                sampleBufferRight[totalShortsRead] = tempBuffer[i];
                            }

                            totalShortsRead++;
                        }
                    }

                    // see if we need to reset sampling
                    if (requestSampleReset == true)
                    {
                        sampleReset = true;
                        requestSampleReset = false;
                    }
                    else
                    {
                        // add complete sample
                        addToSampleBuffer(sampleBufferLeft, sampleBufferRight);
                    }
                }

                mAudioSampleThreadStatus = true;
                mAudioSampleThreadMesg = "AudioCapture success";
            }
            catch (Exception e)
            {
                mAudioSampleThreadStatus = false;
                mAudioSampleThreadMesg = String.format("AudioCapture failed. %s", e.getMessage());
            }
            finally
            {
                if (recorder != null)
                {
                    try
                    {
                        recorder.stop();
                    }
                    catch (IllegalStateException e)
                    {
                        if (mAudioSampleThreadStatus)
                        {
                            mAudioSampleThreadStatus = false;
                            mAudioSampleThreadMesg = "AudioCapture failed to stop recorder";
                        }
                    }
                    recorder.release();
                }
            }
        }

    };

    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        AudioSampleService getService()
        {
            dbgLog(TAG, "Retrieving binding service", 'i');
            return AudioSampleService.this;
        }
    }

    /**
     * @return
     * @see android.app.Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent)
    {
        dbgLog(TAG, "Binding to AudioSampleService", 'i');
        return mBinder;
    }


    /**
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate()
    {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        dbgLog(TAG, "OnCreate of AudioSampleService called", 'i');

        // Display a notification about us starting. We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dbgLog(TAG, "Received start id " + startId + ": " + intent, 'i');
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        dbgLog(TAG, "OnDestroy() of AudioSampleService called", 'i');

        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);
    }

    private boolean needToRestartThread(AudioSample.AudioSampleSettings oldSettings, AudioSample.AudioSampleSettings newSettings)
    {
        if (oldSettings == null)
        {
            return true;
        }

        // check old and new settings to see if current thread needs to be recreated
        if ((oldSettings.sampleRate != newSettings.sampleRate) || (oldSettings.audioInputFormat != newSettings.audioInputFormat)
                || (oldSettings.audioInputSource != newSettings.audioInputSource)
                || (oldSettings.audioMicSelect.equals(newSettings.audioMicSelect) == false)
                || (oldSettings.micKey.equals(newSettings.micKey) == false))
        {
            return true;
        }

        return false;
    }

    public boolean startSampling(AudioSample.AudioSampleSettings settings, int sampleBufferDepth, StringBuffer sbStatusMesg)
    {
        // make sure settings are not null
        if (settings == null)
        {
            sbStatusMesg.append("Audio Sample Settings are null");
            return false;
        }

        // check to make sure sampleBufferDepth is valid
        if (sampleBufferDepth < 1)
        {
            sbStatusMesg.append(String.format("Sample Buffer Depth %s is invalid", sampleBufferDepth));
            return false;
        }

        // see if we need to pause the thread or restart it
        if ((mAudioSampleThread == null) || (mAudioSampleThread.isAlive() == false)
                || needToRestartThread(mAudioSampleThread.getCurrentSampleSettings(), settings))
        {
            // stop any existing sampling thread
            if (stopSampling(sbStatusMesg) == false)
            {
                sbStatusMesg.append("Failed to stop existing sample thread");
                return false;
            }

            // with thread stop, set buffer depth
            this.sampleBufferDepth = sampleBufferDepth;

            // clear sampling buffers
            clearSampleBuffer();

            // start new sample thread
            mAudioSampleThread = new AudioSampleThread(settings);
            mAudioSampleThread.start();
        }
        else
        {
            int pausetimeoutMs = mAudioSampleThread.getCurrentSampleSettings().audioCaptureTimeoutMs;

            // pause existing thread
            if (mAudioSampleThread.pauseMe(pausetimeoutMs) == false)
            {
                sbStatusMesg.append("Failed to pause existing sample thread");
                return false;
            }

            // with thread pause, set buffer depth
            this.sampleBufferDepth = sampleBufferDepth;

            // clear sampling buffers
            clearSampleBuffer();

            // apply new settings
            mAudioSampleThread.updateSampleSettings(settings);

            // unpause thread
            mAudioSampleThread.unpauseMe();
        }


        return true;
    }

    public boolean stopSampling(StringBuffer sbStatusMesg)
    {
        // stop any existing thread
        if ((mAudioSampleThread != null) && (mAudioSampleThread.isAlive()))
        {
            dbgLog(TAG, "Stopping existing thread", 'i');
            mAudioSampleThread.stopMe();
            try
            {
                mAudioSampleThread.join();
                dbgLog(TAG, "Existing thread stopped", 'i');
            }
            catch (InterruptedException e)
            {
                sbStatusMesg.append("Failed to stop previous audio sample thread");
                return false;
            }
        }

        return true;
    }

    private void clearSampleBuffer()
    {
        dbgLog(TAG, "clearSampleBuffer()", 'd');
        try
        {
            mSampleBufferLock.lock();
            mSampleBufferLeft.clear();
            mSampleBufferRight.clear();
        }
        finally
        {
            mSampleBufferLock.unlock();
        }

    }

    private void addToSampleBuffer(short[] bufferLeft, short[] bufferRight)
    {
        dbgLog(TAG, "addToSampleBuffer()", 'd');
        try
        {
            mSampleBufferLock.lock();

            if (mSampleBufferLeft.size() < sampleBufferDepth)
            {
                // if full then we don't add anymore on.
                mSampleBufferLeft.add(bufferLeft);
                dbgLog(TAG, "addToSampleBuffer() Left size = " + mSampleBufferLeft.size(), 'd');
            }

            if ((mNumberOfChannelsSampled == 2) && (mSampleBufferRight.size() < sampleBufferDepth))
            {
                // if full then we don't add anymore on.
                mSampleBufferRight.add(bufferRight);
                dbgLog(TAG, "addToSampleBuffer() Right size = " + mSampleBufferRight.size(), 'd');
            }
        }
        finally
        {
            mSampleBufferLock.unlock();
        }
    }

    public Object[] getBufferedSample()
    {
        dbgLog(TAG, "getBufferedSample()", 'd');
        try
        {
            mSampleBufferLock.lock();

            if ((mNumberOfChannelsSampled == 2) && (mSampleBufferLeft.size() > 0) && (mSampleBufferRight.size() > 0))
            {
                return new Object[] { mSampleBufferLeft.remove(0), mSampleBufferRight.remove(0) };
            }
            else if ((mSampleBufferLeft.size() > 0))
            {

                return new Object[] { mSampleBufferLeft.remove(0) };
            }
            else
            {
                return null;
            }
        }
        finally
        {
            mSampleBufferLock.unlock();
        }
    }

    public Object[] getCurrentSample(int timeoutMs)
    {
        dbgLog(TAG, "getCurrentSample()", 'd');

        Object[] buffer = null;

        // If sample thread is running, then reset sample currently in progress
        if (mAudioSampleThreadStatus == true)
        {
            int pausetimeoutMs = mAudioSampleThread.getCurrentSampleSettings().audioCaptureTimeoutMs;
            if (mAudioSampleThread.resetCurrentSample(pausetimeoutMs) == false)
            {
                return null;
            }
        }

        // clear the buffer and wait for another sample
        clearSampleBuffer();

        long startTime = System.currentTimeMillis();

        while (true)
        {
            buffer = getBufferedSample();

            if (buffer != null)
            {
                break;
            }

            // see we hit timeout limit
            if ((System.currentTimeMillis() - startTime) > timeoutMs)
            {
                break;
            }

            try
            {
                Thread.sleep(1);
            }
            catch (InterruptedException ignore)
            {

            }
        }

        return buffer;
    }

    public int getNumberOfChannelsSampled()
    {
        return mNumberOfChannelsSampled;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification()
    {
        // Setup the text from the string resource
        CharSequence text = getText(R.string.audio_sample_service_started);

        // Set the icon, scrolling text and time stamp
        Notification notification = new Notification(R.drawable.audio_sample_service_icon, text, System.currentTimeMillis());

        notification.flags |= Notification.FLAG_NO_CLEAR;

        // The PendingIntent to launch our activity if the user selects this notification
        // Right now, this launches the Test_Main activity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, com.motorola.motocit.audio.AudioSample.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.audio_sample_service_label), text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    private void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
