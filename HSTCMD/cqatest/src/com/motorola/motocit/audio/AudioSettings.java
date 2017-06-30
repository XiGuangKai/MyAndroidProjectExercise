/*
 * Copyright (c) 2011 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *   Date           CR            Author                 Description
 * 2012/05/09  IKHSS7-27207  Kurt Walker - QA3843        Tone lengths support milliseconds
 * 2011/11/15  IKMAIN-32830  Hang Chen   - qxcp47        Add AUDIO_TONE and read audio setting from config
 */

package com.motorola.motocit.audio;

public class AudioSettings
{
    public double m_length;
    public int m_sample_rate;
    public int m_start_freq;
    public int m_end_freq;
    public int m_volume;

    AudioSettings()
    {
        m_length = 6;
        m_sample_rate = 44100;
        m_start_freq = 100;
        m_end_freq = 1000;
        m_volume = 400;
    }
}
