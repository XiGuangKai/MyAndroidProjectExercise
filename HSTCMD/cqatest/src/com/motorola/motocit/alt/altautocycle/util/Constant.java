/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import android.os.Build;

public class Constant
{
    public static final String PACKAGE_NAME = "com.motorola.motocit.alt.altautocycle";
    public static final int TEST_RESULT_PASS = 0;
    public static final int TEST_RESULT_FAIL = 1;
    public static final int HANDLER_MESSAGE_STOP = 0;
    public static final int HANDLER_MESSAGE_TAKE_PICTURE = 1;
    public static final int HANDLER_MESSAGE_NEXT = 2;
    public static final int HANDLER_MESSAGE_WAIT = 3;
    public static final int HANDLER_MESSAGE_LCD = 4;
    public static final int HANDLER_MESSAGE_SENSOR = 5;
    public static final int HANDLER_MESSAGE_LOG = 6;
    public static final int HANDLER_MESSAGE_VIDEO = 7;
    public static final int HANDLER_MESSAGE_BTWIFI = 8;
    public static final int HANDLER_MESSAGE_CHECK_PROCESS = 9;
    public static final int HANDLER_MESSAGE_ELPANNEL = 10;
    public static final int HANDLER_MESSAGE_AUDIO = 11;
    public static final int HANDLER_MESSAGE_VIBRATE = 12;
    public static final int HANDLER_MESSAGE_EARPIECE = 13;
    public static final int HANDLER_MESSAGE_VIDEO_CHECK = 14;
    public static final int HANDLER_MESSAGE_RUNSCRIPTS = 15;
    public static final int HANDLER_MESSAGE_TIMER = 16;
    public static final int HANDLER_MESSAGE_SWITCH_CAMERA = 17;
    public static final int HANDLER_MESSAGE_SET_CONTENT_VIEW = 18;
    public static int CYCLE_WAITING_TIME = 20 * 60 * 1000; // millisecond
    public static int CYCLE_INTERVAL_TIME = 30 * 60 * 1000; // millisecond
    public static int CYCLE_RUNNING_LOG = 1 * 60; // second
    public static int VERIFY_CYCLE_WAITING_TIME = 20 * 60 * 1000; // millisecond
    public static int VERIFY_CYCLE_INTERVAL_TIME = 1 * 60 * 1000; // millisecond
    public static int VERIFY_CYCLE_RUNNING_LOG = 10; // second
    public static int ELPANNEL_INTERVAL_TIME = 15 * 1000; // millisecond
    public static final long[] VIBRATOR_PATTERN = { 1, 15 * 1000, 15 * 1000 };
    public static final boolean DEBUG_FLAG = false;
    public static final boolean AUTO_STARTING_CYCLE = true;
    public static final boolean CHECK_GPS_FUNCTION = true;
    public static final String AUTO_CYCLE_BROADCAST_NEXT = "AUTO_CYCLE_BROADCAST_NEXT";
    public static final String AUTO_CYCLE_BROADCAST_ALARM = "AUTO_CYCLE_BROADCAST_ALARM";
    public static final String SD_VIDEO_PHATH = "/sdcard/alt_autocycle/alt_autocycle_video.3gp";
    public static final String SD_AUDIO_PATH = "/sdcard/alt_autocycle/alt_audio_350_to_450.wav";
    public static final String SD_PICTURE_PATH = "/sdcard/alt_autocycle/alt_picture.jpg";
    public static final String SD_TEXT_PATH = "/sdcard/alt_autocycle/alt_tex_file.txt";
    public static final String SD_LOG_FILE_PATH = "/sdcard/alt_autocycle/autocycle.log";
    public static final String SD_PATH = "/sdcard/alt_autocycle/";
    public static String[] AUTO_CYCLE_ITEMS = {
            "FunctionVideoTestActivity",
            "ParallelAudioVibratorBTWiFiActivity",
            "ParallelCameraEarpieceProxLightAcceleSDActivity",
            "ParallelLCDEarpieceGyroMagFPRActivity",
            "ParallelLCDEarpieceBarometerGPSSDActivity",
            "ParallelEarpieceProxLightAcceleVibratorBTWiFiActivity",
            "RunBatchScriptsActivity"
    };

    public static void setAutoCycleItems() {
        if (Build.DEVICE.toLowerCase().contains("nash")) {
            AUTO_CYCLE_ITEMS = new String[]{
                    "FunctionVideoTestActivity",
                    "ParallelAudioVibratorBTWiFiActivity",
                    "ParallelCamera2EarpieceProxLightAcceleSDActivity",
                    "ParallelLCDEarpieceGyroMagFPRActivity",
                    "ParallelLCDEarpieceBarometerGPSSDActivity",
                    "ParallelEarpieceProxLightAcceleVibratorBTWiFiActivity",
                    "RunBatchScriptsActivity"
            };
        } else {
            AUTO_CYCLE_ITEMS = new String[]{
                    "FunctionVideoTestActivity",
                    "ParallelAudioVibratorBTWiFiActivity",
                    "ParallelCameraEarpieceProxLightAcceleSDActivity",
                    "ParallelLCDEarpieceGyroMagFPRActivity",
                    "ParallelLCDEarpieceBarometerGPSSDActivity",
                    "ParallelEarpieceProxLightAcceleVibratorBTWiFiActivity",
                    "RunBatchScriptsActivity"
            };
        }
    }

    public static int getActivityStep(String name)
    {
        int stepInt = 0;
        for (int i = 0; i < AUTO_CYCLE_ITEMS.length; i++)
        {
            if (name.equals(AUTO_CYCLE_ITEMS[i]))
            {
                stepInt = i + 1;
                break;
            }
        }
        return stepInt;
    }
}
