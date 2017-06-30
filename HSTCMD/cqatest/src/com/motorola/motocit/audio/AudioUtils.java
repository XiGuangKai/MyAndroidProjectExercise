/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 *
 */

package com.motorola.motocit.audio;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.Context;
import android.media.AudioManager;

public class AudioUtils {
    public static int getDevice()
    {
        int device = -1;
        try
        {
            Class<?> clazz = Class.forName("android.media.AudioSystem");
            Field field = clazz.getDeclaredField("DEVICE_IN_AMBIENT");
            device = (Integer) field.get(null);
        }
        catch (IllegalArgumentException e)
        {

        }
        catch (ClassNotFoundException e)
        {

        }
        catch (NoSuchFieldException e)
        {

        }
        catch (IllegalAccessException e)
        {

        }

        return device;
    }

    public static void setDspAudioPath(Context context)
    {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        try
        {
            Method setWiredDeviceConnectionStateMethod = AudioManager.class.getMethod("setWiredDeviceConnectionState", new Class[] { int.class, int.class, String.class });

            setWiredDeviceConnectionStateMethod.invoke(am, getDevice(), 1, "");
        }
        catch (IllegalArgumentException e)
        {

        }
        catch (NoSuchMethodException e)
        {

        }
        catch (IllegalAccessException e)
        {

        }
        catch (InvocationTargetException e)
        {

        }
    }
}
