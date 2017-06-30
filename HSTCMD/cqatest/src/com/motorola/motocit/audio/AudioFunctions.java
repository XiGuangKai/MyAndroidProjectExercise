/*
 * Copyright (c) 2012 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.audio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import com.motorola.motocit.TestUtils;

public class AudioFunctions
{
    private static final String TAG = "AudioFunctions";

    public static byte[] createTone(double toneLength, int sampleRate, double startFreq, double stopFreq, int volume, boolean logScale)
    {
        // validate inputs
        toneLength = validateToneLength(toneLength);
        sampleRate = validateSampleRate(sampleRate);
        startFreq = validateFreq(startFreq);
        stopFreq = validateFreq(stopFreq);
        volume = validateVolume(volume);

        double samples[] = generateToneSamples(startFreq, stopFreq, toneLength, sampleRate, logScale);

        byte tone[] = new byte[2 * samples.length];

        int toneIndex = 0;

        for (double dValue : samples)
        {
            short sValue = (short) (dValue * volume);
            tone[toneIndex++] = (byte) (sValue & 0x00ff);
            tone[toneIndex++] = (byte) ((sValue & 0xff00) >>> 8);
        }

        return tone;
    }

    public static byte[] createStereoTone(double toneLength, int sampleRate, double leftStartFreq, double leftStopFreq, int leftVolume,
            boolean leftLogScale, double rightStartFreq, double rightStopFreq, int rightVolume, boolean rightLogScale)
    {
        // validate inputs
        toneLength = validateToneLength(toneLength);
        sampleRate = validateSampleRate(sampleRate);

        leftStartFreq = validateFreq(leftStartFreq);
        leftStopFreq = validateFreq(leftStopFreq);
        leftVolume = validateVolume(leftVolume);

        rightStartFreq = validateFreq(rightStartFreq);
        rightStopFreq = validateFreq(rightStopFreq);
        rightVolume = validateVolume(rightVolume);

        // generate left samples
        double leftSamples[] = generateToneSamples(leftStartFreq, leftStopFreq, toneLength, sampleRate, leftLogScale);

        // generate right samples
        double rightSamples[] = generateToneSamples(rightStartFreq, rightStopFreq, toneLength, sampleRate, rightLogScale);

        byte tone[] = new byte[4 * leftSamples.length];

        int toneIndex = 0;

        for (int i = 0; i < leftSamples.length; i++)
        {
            // populate left channel data
            short leftSample = (short) (leftSamples[i] * leftVolume);
            tone[toneIndex++] = (byte) (leftSample & 0x00ff);
            tone[toneIndex++] = (byte) ((leftSample & 0xff00) >>> 8);

            // populate right channel data
            short rightSample = (short) (rightSamples[i] * rightVolume);
            tone[toneIndex++] = (byte) (rightSample & 0x00ff);
            tone[toneIndex++] = (byte) ((rightSample & 0xff00) >>> 8);
        }

        return tone;
    }

    public static byte[] createMultiTone(double toneLength, int sampleRate, double startFreq, double stopFreq, List<Double> freqList,
            List<Double> freqAmplitudeList, List<Double> freqPhaseShiftList, int volume, int numberOfFreqs, boolean useFreqList, String multiFreqType)
    {
        // validate inputs
        toneLength = validateToneLength(toneLength);
        sampleRate = validateSampleRate(sampleRate);
        startFreq = validateFreq(startFreq);
        stopFreq = validateFreq(stopFreq);
        volume = validateVolume(volume);

        if (numberOfFreqs < 2)
        {
            numberOfFreqs = 2;
        }

        double samples[] = generateMultiToneSamples(startFreq, stopFreq, freqList, freqAmplitudeList, freqPhaseShiftList, toneLength, sampleRate,
                numberOfFreqs, useFreqList, multiFreqType);

        byte tone[] = new byte[2 * samples.length];

        int toneIndex = 0;

        for (double dValue : samples)
        {
            short sValue = (short) (dValue * volume);
            tone[toneIndex++] = (byte) (sValue & 0x00ff);
            tone[toneIndex++] = (byte) ((sValue & 0xff00) >>> 8);
        }

        return tone;
    }

    public static byte[] createStereoMultiTone(double toneLength, int sampleRate, double leftStartFreq, double leftStopFreq,
            List<Double> leftFreqList, List<Double> leftFreqAmplitudeList, List<Double> leftFreqPhaseShiftList, int leftVolume,
            int leftNumberOfFreqs, double rightStartFreq, double rightStopFreq, List<Double> rightFreqList, List<Double> rightFreqAmplitudeList,
            List<Double> rightFreqPhaseShiftList, int rightVolume, int rightNumberOfFreqs, boolean useFreqList, String multiFreqType)
    {
        // validate inputs
        toneLength = validateToneLength(toneLength);
        sampleRate = validateSampleRate(sampleRate);

        leftStartFreq = validateFreq(leftStartFreq);
        leftStopFreq = validateFreq(leftStopFreq);
        leftVolume = validateVolume(leftVolume);

        rightStartFreq = validateFreq(rightStartFreq);
        rightStopFreq = validateFreq(rightStopFreq);
        rightVolume = validateVolume(rightVolume);

        if (leftNumberOfFreqs < 2)
        {
            leftNumberOfFreqs = 2;
        }

        if (rightNumberOfFreqs < 2)
        {
            rightNumberOfFreqs = 2;
        }

        // generate left samples
        double leftSamples[] = generateMultiToneSamples(leftStartFreq, leftStopFreq, leftFreqList, leftFreqAmplitudeList, leftFreqPhaseShiftList,
                toneLength, sampleRate, leftNumberOfFreqs, useFreqList, multiFreqType);

        // generate right samples
        double rightSamples[] = generateMultiToneSamples(rightStartFreq, rightStopFreq, rightFreqList, rightFreqAmplitudeList,
                rightFreqPhaseShiftList, toneLength, sampleRate, rightNumberOfFreqs, useFreqList, multiFreqType);

        byte tone[] = new byte[4 * leftSamples.length];

        int toneIndex = 0;

        for (int i = 0; i < leftSamples.length; i++)
        {
            // populate left channel data
            short leftSample = (short) (leftSamples[i] * leftVolume);
            tone[toneIndex++] = (byte) (leftSample & 0x00ff);
            tone[toneIndex++] = (byte) ((leftSample & 0xff00) >>> 8);

            // populate right channel data
            short rightSample = (short) (rightSamples[i] * rightVolume);
            tone[toneIndex++] = (byte) (rightSample & 0x00ff);
            tone[toneIndex++] = (byte) ((rightSample & 0xff00) >>> 8);
        }

        return tone;
    }

    private static double[] generateToneSamples(double startFreq, double stopFreq, double toneLength, int sampleRate, boolean logScale)
    {
        int numberTargetSamples = (int) (toneLength * sampleRate);

        double samples[] = new double[numberTargetSamples];

        double freqIncreaseRate = Math.pow(stopFreq / startFreq, 1.0 / numberTargetSamples);
        double a = 0;
        double b = 0;
        double t2 = 0;

        double startFreqOverSampleRate = startFreq / sampleRate;
        double stopFreqOverSampleRate = stopFreq / sampleRate;

        a = (2.0 * Math.PI * (stopFreqOverSampleRate - startFreqOverSampleRate)) / numberTargetSamples;
        b = 2.0 * Math.PI * startFreqOverSampleRate;
        for (int t = 0; t < numberTargetSamples; t++)
        {
            if (logScale == true)
            {
                // Logarathimic
                samples[t] = Math.sin((2.0 * Math.PI * (startFreqOverSampleRate) * (Math.pow(freqIncreaseRate, t) - 1))
                        / (Math.log(freqIncreaseRate)));
            }
            else
            {
                // Linear
                t2 = Math.pow(t, 2.0);
                samples[t] = Math.sin(((a * t2) / 2.0) + (b * t));
            }
        }
        return samples;
    }

    private static double[] generateMultiToneSamples(double startFreq, double stopFreq, List<Double> freqList, List<Double> freqAmplitudeList,
            List<Double> freqPhaseShiftList, double toneLength, int sampleRate, int numberOfFreqs, boolean useFreqList, String multiFreqType)
    {
        int numberTargetSamples = (int) (toneLength * sampleRate);

        int samplesPerFreq = 0;

        if (multiFreqType.equalsIgnoreCase("STEPPED"))
        {
            samplesPerFreq = numberTargetSamples / freqList.size();
            numberTargetSamples = samplesPerFreq * freqList.size();
        }

        double samples[] = new double[numberTargetSamples];

        if (useFreqList == true)
        {
            if (multiFreqType.equalsIgnoreCase("SIMULTANEOUS"))
            {
                for (int freqNumber = 0; freqNumber < freqList.size(); freqNumber++)
                {
                    double frequency = validateFreq(freqList.get(freqNumber));
                    double amplitude = 1.0;

                    if (freqAmplitudeList.size() > 1)
                    {
                        amplitude = validateFreqAmplitude(freqAmplitudeList.get(freqNumber)) / 100.0;
                    }

                    double phaseShift = 0.0;

                    if (freqPhaseShiftList.size() > 1)
                    {
                        phaseShift = freqPhaseShiftList.get(freqNumber);
                    }

                    for (int t = 0; t < numberTargetSamples; t++)
                    {
                        samples[t] += amplitude * Math.sin(((2.0 * Math.PI * t) / (sampleRate / frequency)) + ((Math.PI * phaseShift) / 180.0));
                    }
                }
            }
            else if (multiFreqType.equalsIgnoreCase("STEPPED"))
            {
                int sampleNumber = 0;
                double currentRadians = 0;

                for (int freqNumber = 0; freqNumber < freqList.size(); freqNumber++)
                {
                    double frequency = validateFreq(freqList.get(freqNumber));
                    double amplitude = 1.0;

                    if (freqAmplitudeList.size() > 1)
                    {
                        amplitude = validateFreqAmplitude(freqAmplitudeList.get(freqNumber)) / 100.0;
                    }

                    double twoPiDivRateFreq = (2.0 * Math.PI) / (sampleRate / frequency);

                    for (int t = 0; t < samplesPerFreq; t++)
                    {
                        samples[sampleNumber] += amplitude * Math.sin(currentRadians);
                        currentRadians += twoPiDivRateFreq;
                        sampleNumber++;
                    }
                }
            }
        }
        else
        {
            double freqSpacing = (stopFreq - startFreq) / (numberOfFreqs - 1);

            for (int freqNumber = 0; freqNumber < numberOfFreqs; freqNumber++)
            {
                double frequency = startFreq + (freqNumber * freqSpacing);

                for (int t = 0; t < numberTargetSamples; t++)
                {
                    samples[t] += Math.sin((2.0 * Math.PI * t) / (sampleRate / frequency));
                }
            }
        }

        double maxVolume = 0.0;

        for (int t = 0; t < numberTargetSamples; t++)
        {
            if (Math.abs(samples[t]) > maxVolume)
            {
                maxVolume = Math.abs(samples[t]);
            }
        }

        for (int t = 0; t < numberTargetSamples; t++)
        {
            samples[t] = samples[t] / maxVolume;
        }

        return samples;
    }

    public static boolean getAudioSettingsFromConfig(AudioSettings audioparam)
    {
        boolean result = false;
        String SequenceFileInUse = TestUtils.getSequenceFileInUse();
        File file_local_12m = new File("/data/local/12m/" + SequenceFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + SequenceFileInUse);
        File file_system_sdcard = new File("/mnt/sdcard/CQATest/" + SequenceFileInUse);
        String config_file = null;
        double length = 0;
        int sample_rate = 0;
        int start_freq = 0;
        int end_freq = 0;
        int volume = 0;

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else if (file_system_sdcard.exists())
        {
            config_file = file_system_sdcard.toString();
        }
        else
        {
            dbgLog(TAG, "!! CANN'T FIND AUDIO CONFIG FILE", 'd');
        }

        if ((config_file != null) && (SequenceFileInUse != null))
        {
            try
            {
                BufferedReader breader = new BufferedReader(new FileReader(config_file));
                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    if (line.contains("<AUDIO SETTINGS>") == true)
                    {
                        break;
                    }
                }

                if (null != line)
                {
                    dbgLog(TAG, "Settings: " + line, 'd');
                    String[] fields = line.split(",");
                    for (String field : fields)
                    {
                        if (field.contains("LENGTH"))
                        {
                            String[] tokens = field.split("=");
                            length = Double.parseDouble(tokens[1]);
                        }
                        if (field.contains("SAMPLE_RATE"))
                        {
                            String[] tokens = field.split("=");
                            sample_rate = Integer.parseInt(tokens[1], 10);
                        }
                        if (field.contains("START_FREQ"))
                        {
                            String[] tokens = field.split("=");
                            start_freq = Integer.parseInt(tokens[1], 10);
                        }
                        if (field.contains("END_FREQ"))
                        {
                            String[] tokens = field.split("=");
                            end_freq = Integer.parseInt(tokens[1], 10);
                        }
                        if (field.contains("VOLUME"))
                        {
                            String[] tokens = field.split("=");
                            volume = Integer.parseInt(tokens[1], 10);
                        }
                    }

                    dbgLog(TAG, "Parsed: length=" + Double.toString(length) + ", sample_rate=" + Integer.toString(sample_rate) + ", start_freq="
                            + Integer.toString(start_freq) + ", end_freq=" + Integer.toString(end_freq) + ", volume=" + Integer.toString(volume), 'd');
                }

                /* Value verification */
                if ((length != 0) && (sample_rate != 0) && (start_freq != 0) && (end_freq != 0) && (volume != 0))
                {
                    audioparam.m_length = length;
                    audioparam.m_sample_rate = sample_rate;
                    audioparam.m_start_freq = start_freq;
                    audioparam.m_end_freq = end_freq;
                    audioparam.m_volume = volume;
                    result = true;
                }

                breader.close();
            }
            catch (Exception e)
            {
                dbgLog(TAG, "!!! Some exception in parsing audio settings", 'd');
            }
        }

        return result;
    }

    public static double validateToneLength(double toneLength)
    {
        // Limit tone length to 16 seconds
        if (toneLength < 0.001)
        {
            toneLength = 0.001;
        }
        if (toneLength > 16)
        {
            toneLength = 16;
        }

        return toneLength;
    }

    public static int validateSampleRate(int sampleRate)
    {
        // Limit sample rate between 4 kHz and 44.1 kHz
        if (sampleRate < 4000)
        {
            sampleRate = 4000;
        }
        if (sampleRate > 44100)
        {
            sampleRate = 44100;
        }

        return sampleRate;
    }

    public static double validateFreq(double freq)
    {
        // Limit start freq between 1 Hz and 20 kHz
        if (freq < 1)
        {
            freq = 1;
        }
        if (freq > 20000)
        {
            freq = 20000;
        }

        return freq;
    }

    public static double validateFreqAmplitude(double freqAmplitude)
    {
        // Limit freq amplitude between 0% and 100%
        if (freqAmplitude < 0.0)
        {
            freqAmplitude = 0.0;
        }
        if (freqAmplitude > 100.0)
        {
            freqAmplitude = 100.0;
        }

        return freqAmplitude;
    }

    public static int validateVolume(int volume)
    {
        // Limit stop freq between 1 Hz and 20 kHz
        if (volume < 0)
        {
            volume = 0;
        }
        if (volume > 32767)
        {
            volume = 32767;
        }

        return volume;
    }

    private static void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
