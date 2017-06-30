/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.dft;

import com.motorola.motocit.TestUtils;

public class DFT
{
    private static final String TAG = "DFT";

    private double sampleFrequency = 0.0;

    public int inputSampleSize = 0;
    public int outputSampleSize = 0;

    public double mDftReal[];
    public double mDftImag[];
    public double mPowerSpectralDensity[];
    public double mFrequency[];
    public double filteredSamples[];

    public boolean newDftTransform(short[] samples, double inputSampleFrequency, String timeDomainFilter)
    {
        if ((samples == null) || (0 == samples.length))
        {
            return false;
        }

        sampleFrequency = inputSampleFrequency;
        inputSampleSize = samples.length;
        outputSampleSize = (inputSampleSize / 2) + 1;

        mDftReal = new double[outputSampleSize];
        mDftImag = new double[outputSampleSize];

        filteredSamples = new double[inputSampleSize];

        for (int i = 0; i < inputSampleSize; i++)
        {
            filteredSamples[i] = samples[i];
        }

        applyTimeDomainFilter(filteredSamples, timeDomainFilter);

        // k: discrete frequency index
        // n: sample index
        // j: imaginarynumber sqrt(-1)
        // pi: ratio of the circumference to the diameter of a circle
        for (int k = 0; k < outputSampleSize; k++)
        {
            for (int n = 0; n < inputSampleSize; n++)
            {
                double w = (2.0 * Math.PI * k * n) / inputSampleSize;

                mDftReal[k] += filteredSamples[n] * Math.cos(w);
                mDftImag[k] -= filteredSamples[n] * Math.sin(w);
            }
        }

        // Next calculate the power spectral density
        mPowerSpectralDensity = new double[outputSampleSize];
        double sizeSquared = (double) inputSampleSize * (double) inputSampleSize;
        for (int k = 0; k < outputSampleSize; k++)
        {
            double lfMagnitudeSquared = (mDftReal[k] * mDftReal[k]) + (mDftImag[k] * mDftImag[k]);
            if (k > 0)
            {
                mPowerSpectralDensity[k] = Math.sqrt(lfMagnitudeSquared / sizeSquared) * Math.sqrt(2);
            }
            else
            {
                mPowerSpectralDensity[k] = Math.sqrt(lfMagnitudeSquared / sizeSquared);
            }
        }

        mFrequency = new double[outputSampleSize];
        for (int n = 0; n < outputSampleSize; n++)
        {
            mFrequency[n] = (n * sampleFrequency) / inputSampleSize;
        }

        return true;
    }

    public boolean newFftTransform(short[] samples, double inputSampleFrequency, String timeDomainFilter)
    {
        if ((samples == null) || (0 == samples.length))
        {
            return false;
        }

        sampleFrequency = inputSampleFrequency;
        inputSampleSize = samples.length;

        if (Integer.highestOneBit(inputSampleSize) != inputSampleSize)
        {
            // Sample size must be an integer size of 2^n
            inputSampleSize = Integer.highestOneBit(inputSampleSize);
        }

        outputSampleSize = (inputSampleSize / 2) + 1;

        mDftReal = new double[inputSampleSize];
        mDftImag = new double[inputSampleSize];

        mPowerSpectralDensity = new double[outputSampleSize];
        mFrequency = new double[outputSampleSize];

        filteredSamples = new double[inputSampleSize];

        for (int i = 0; i < inputSampleSize; i++)
        {
            filteredSamples[i] = samples[i];
        }

        applyTimeDomainFilter(filteredSamples, timeDomainFilter);


        // Fast Fourier Transform Reference: http://www.dspguide.com/ch12/2.htm
        // Perform FFT bit reversal sorting
        // i = iterator through all samples
        // j = reverse location for sample i
        // Example 2^3 samples = 8 samples
        // i dec i bin -> j dec j bin
        // 0 000 -> 0 000
        // 1 001 -> 4 100
        // 2 010 -> 2 010
        // 3 011 -> 6 110
        // 4 100 -> 1 001
        // 5 101 -> 5 101
        // 6 110 -> 3 011
        // 7 111 -> 7 111
        int shiftBits = 1 + Integer.numberOfLeadingZeros(inputSampleSize);

        for (int i = 0; i < inputSampleSize; i++)
        {
            // Shift bits since 2^n is smaller than size of int
            int j = (Integer.reverse(i) >>> shiftBits);

            if (j > i)
            {
                double tempSample = filteredSamples[j];
                filteredSamples[j] = filteredSamples[i];
                filteredSamples[i] = tempSample;
            }
        }

        // Copy sample data into Dft Real and Imaginary parts
        // Since sample data is real data, Imaginary data is all 0
        for (int i = 0; i < inputSampleSize; i++)
        {
            mDftReal[i] = filteredSamples[i];
            mDftImag[i] = 0.0;
        }

        // Perform FFT

        // Loop for each stage of the FFT operation
        // stage = 2, 4, 8, 16, 32, 64, ... inputSampleSize
        for (int stage = 2; stage <= inputSampleSize; stage = stage + stage)
        {
            // Loop for each sub DFT
            for (int subDft = 0; subDft < (stage / 2); subDft++)
            {
                double theta = (-2 * subDft * Math.PI) / stage;
                // Cos is real value component
                double cosRealVal = Math.cos(theta);
                // Sin is Imaginary value component
                double sinImagVal = Math.sin(theta);
                // Butterfly Loop
                for (int bLoop = 0; bLoop < (inputSampleSize / stage); bLoop++)
                {
                    int oddIndex = (bLoop * stage) + subDft;
                    int evenIndex = (bLoop * stage) + subDft + (stage / 2);

                    double tempReal = (cosRealVal * mDftReal[evenIndex]) - (sinImagVal * mDftImag[evenIndex]);
                    double tempImag = (cosRealVal * mDftImag[evenIndex]) + (sinImagVal * mDftReal[evenIndex]);

                    mDftReal[evenIndex] = mDftReal[oddIndex] - tempReal;
                    mDftImag[evenIndex] = mDftImag[oddIndex] - tempImag;

                    mDftReal[oddIndex] = mDftReal[oddIndex] + tempReal;
                    mDftImag[oddIndex] = mDftImag[oddIndex] + tempImag;
                }
            }
        }

        // Next calculate the power spectral density
        // mPowerSpectralDensity = new double[outputSampleSize];
        double sizeSquared = (double) inputSampleSize * (double) inputSampleSize;
        for (int k = 0; k < outputSampleSize; k++)
        {
            double lfMagnitudeSquared = (mDftReal[k] * mDftReal[k]) + (mDftImag[k] * mDftImag[k]);
            if (k > 0)
            {
                mPowerSpectralDensity[k] = Math.sqrt(lfMagnitudeSquared / sizeSquared) * Math.sqrt(2);
            }
            else
            {
                mPowerSpectralDensity[k] = Math.sqrt(lfMagnitudeSquared / sizeSquared);
            }
        }

        // mFrequency = new double[outputSampleSize];
        for (int n = 0; n < outputSampleSize; n++)
        {
            mFrequency[n] = (n * sampleFrequency) / inputSampleSize;
        }

        return true;
    }

    public double maxPowerFrequency()
    {
        double maxMagnitude = 0;
        int maxIndex = 0;

        TestUtils.dbgLog(TAG, "Finding Freq", 'd');
        TestUtils.dbgLog(TAG, "Finding Freq OutputSampleSize" + outputSampleSize, 'd');

        for (int i = 0; i < outputSampleSize; i++)
        {
            if (mPowerSpectralDensity[i] > maxMagnitude)
            {
                maxMagnitude = mPowerSpectralDensity[i];
                maxIndex = i;
            }
        }
        TestUtils.dbgLog(TAG, "Finding Freq Complete", 'd');

        return mFrequency[maxIndex];
    }

    public double maxAmplitude()
    {
        double maxMagnitude = 0;

        for (int i = 0; i < outputSampleSize; i++)
        {
            if (mPowerSpectralDensity[i] > maxMagnitude)
            {
                maxMagnitude = mPowerSpectralDensity[i];
            }
        }

        return maxMagnitude;
    }

    public double maxPowerFrequency(double lowFreq, double highFreq)
    {
        double maxMagnitude = 0;
        int maxIndex = 0;

        int startIndex = frequencyToIndex(lowFreq);
        int endIndex = frequencyToIndex(highFreq);

        for (int i = startIndex; i <= endIndex; i++)
        {
            if (mPowerSpectralDensity[i] > maxMagnitude)
            {
                maxMagnitude = mPowerSpectralDensity[i];
                maxIndex = i;
            }
        }

        return mFrequency[maxIndex];
    }

    private int frequencyToIndex(double frequency)
    {
        int index = (int) ((frequency * inputSampleSize) / sampleFrequency);

        return index;
    }

    void applyTimeDomainFilter(double[] samples, String timeDomainFilter)
    {
        // Info for these filters from
        // http://zone.ni.com/reference/en-XX/help/lv/71/lvanls/Scaled_Time_Domain_Window/
        int nNumberWindowCoeffs = 0;

        double lfCoherentGain = 0.0;
        double w = 0.0;
        double lfSum = 0.0;

        double lfWindowCoeffs[] = new double[10];

        if (timeDomainFilter.equalsIgnoreCase("HANNING"))
        {
            nNumberWindowCoeffs = 2;
            lfCoherentGain = 0.5;
            lfWindowCoeffs[0] = 0.5;
            lfWindowCoeffs[1] = 0.5;
        }
        else if (timeDomainFilter.equalsIgnoreCase("HAMMING"))
        {
            nNumberWindowCoeffs = 2;
            lfCoherentGain = 0.54;
            lfWindowCoeffs[0] = 0.54;
            lfWindowCoeffs[1] = 0.46;
        }
        else if (timeDomainFilter.equalsIgnoreCase("BLACKMAN-HARRIS"))
        {
            nNumberWindowCoeffs = 3;
            lfCoherentGain = 0.42323;
            lfWindowCoeffs[0] = 0.42323;
            lfWindowCoeffs[1] = 0.49755;
            lfWindowCoeffs[2] = 0.07922;
        }
        else if (timeDomainFilter.equalsIgnoreCase("EXACT-BLACKMAN"))
        {
            nNumberWindowCoeffs = 3;
            lfCoherentGain = 0.42659071367153911200;
            lfWindowCoeffs[0] = 0.42659071367153911200;
            lfWindowCoeffs[1] = 0.49656061908856408100;
            lfWindowCoeffs[2] = 0.07684866723989682010;
        }
        else if (timeDomainFilter.equalsIgnoreCase("BLACKMAN"))
        {
            nNumberWindowCoeffs = 3;
            lfCoherentGain = 0.42;
            lfWindowCoeffs[0] = 0.42;
            lfWindowCoeffs[1] = 0.5;
            lfWindowCoeffs[2] = 0.08;
        }
        else if (timeDomainFilter.equalsIgnoreCase("FLAPTOP"))
        {
            nNumberWindowCoeffs = 5;
            lfCoherentGain = 0.215578948;
            lfWindowCoeffs[0] = 0.215578948;
            lfWindowCoeffs[1] = 0.41663158;
            lfWindowCoeffs[2] = 0.277263158;
            lfWindowCoeffs[3] = 0.083578947;
            lfWindowCoeffs[4] = 0.006947368;
        }
        else if (timeDomainFilter.equalsIgnoreCase("4TERM-B-HARRIS"))
        {
            nNumberWindowCoeffs = 4;
            lfCoherentGain = 0.35875;
            lfWindowCoeffs[0] = 0.35875;
            lfWindowCoeffs[1] = 0.48829;
            lfWindowCoeffs[2] = 0.14128;
            lfWindowCoeffs[3] = 0.01168;
        }
        else if (timeDomainFilter.equalsIgnoreCase("7TERM-B-HARRIS"))
        {
            nNumberWindowCoeffs = 7;
            lfCoherentGain = 0.27105140069342415;
            lfWindowCoeffs[0] = 0.27105140069342415;
            lfWindowCoeffs[1] = 0.43329793923448606;
            lfWindowCoeffs[2] = 0.21812299954311062;
            lfWindowCoeffs[3] = 0.065925446388030898;
            lfWindowCoeffs[4] = 0.010811742098372268;
            lfWindowCoeffs[5] = 7.7658482522509342E-4;
            lfWindowCoeffs[6] = 1.3887217350903198E-5;
        }
        else if (timeDomainFilter.equalsIgnoreCase("LOW-SIDEBAND"))
        {
            nNumberWindowCoeffs = 5;
            lfCoherentGain = 0.323215218;
            lfWindowCoeffs[0] = 0.323215218;
            lfWindowCoeffs[1] = 0.471492057;
            lfWindowCoeffs[2] = 0.17553428;
            lfWindowCoeffs[3] = 0.028497078;
            lfWindowCoeffs[4] = 0.001261367;
        }

        if (nNumberWindowCoeffs > 0)
        {
            for (int i = 0; i < inputSampleSize; i++)
            {
                lfSum = 0.0;
                w = (2 * Math.PI * i) / (inputSampleSize - 1);
                for (int k = 0; k < nNumberWindowCoeffs; k++)
                {
                    lfSum += Math.pow(-1, k) * lfWindowCoeffs[k] * Math.cos(k * w);
                }
                samples[i] = (samples[i] / lfCoherentGain) * lfSum;
            }
        }

        return;
    }

}
