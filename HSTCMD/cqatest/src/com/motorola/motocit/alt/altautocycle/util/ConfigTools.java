/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.alt.altautocycle.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import android.os.SystemProperties;
import android.content.Context;
import android.util.Log;

import com.motorola.motocit.TestUtils;

public class ConfigTools
{
    private static final String TAG = "ConfigTool";

    public static String getProductName()
    {
        String product = SystemProperties.get("ro.hw.device", "unknow");
        return product;
    }

    public static File getConfigFile()
    {
        File file0 = null;
        File file1 = new File("/sdcard/alt_autocycle/alt_audio_volume.cfg");
        if (file1.isFile())
        {
            file0 = file1;
        }
        return file0;

    }

    public static int getPlaybackVolume(String keywords) throws Exception
    {
        int volume = -1;
        int loudspeakervolume = -1;
        int earpiecevolume = -1;
        int videoplaybackvolume = -1;
        String AltConfigFileInUse = "alttest_cfg";
        File file_local_12m = new File("/data/local/12m/" + AltConfigFileInUse);
        File file_system_12m = new File("/system/etc/motorola/12m/" + AltConfigFileInUse);
        String config_file = null;

        if (file_local_12m.exists())
        {
            config_file = file_local_12m.toString();
        }
        else if (file_system_12m.exists())
        {
            config_file = file_system_12m.toString();
        }
        else
        {
            TestUtils.dbgLog(TAG, "!! CANN'T FIND ALT TEST AUDIO CONFIG FILE", 'i');
        }

        if (config_file != null)
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
                    TestUtils.dbgLog(TAG, "Settings: " + line, 'i');
                    String[] fields = line.split(",");
                    for (String field : fields)
                    {
                        if (field.contains("LOUDSPEAKER_PLAYBACK_VOLUME"))
                        {
                            String[] tokens = field.split("=");
                            loudspeakervolume = Integer.parseInt(tokens[1]);
                        }

                        if (field.contains("EARPIECE_PLAYBACK_VOLUME"))
                        {
                            String[] tokens = field.split("=");
                            earpiecevolume = Integer.parseInt(tokens[1]);
                        }

                        if (field.contains("VIDEO_PLAYBACK_VOLUME"))
                        {
                            String[] tokens = field.split("=");
                            videoplaybackvolume = Integer.parseInt(tokens[1]);
                        }
                    }

                    TestUtils.dbgLog(TAG, "Parsed: loudspeakervolume=" + Integer.toString(loudspeakervolume) + ", earpiecevolume=" + Integer.toString(earpiecevolume) + ", videoplaybackvolume=" + Integer.toString(videoplaybackvolume), 'i');
                }

                if (keywords.contentEquals("LOUDSPEAKER_PLAYBACK_VOLUME"))
                {
                    volume = loudspeakervolume;
                }
                else if (keywords.contentEquals("EARPIECE_PLAYBACK_VOLUME"))
                {
                    volume = earpiecevolume;
                }
                else if (keywords.contentEquals("VIDEO_PLAYBACK_VOLUME"))
                {
                    volume = videoplaybackvolume;
                }

                breader.close();
            }
            catch (Exception e)
            {
                TestUtils.dbgLog(TAG, "!!! Some exception in parsing ALT audio settings", 'i');
            }
        }

        return volume;
    }

}
