/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LogTools
{

    public synchronized static void log(String path, String content)
    {
        File file = null;
        FileWriter writer = null;
        try
        {
            file = new File(path);
            if (!file.exists())
            {
                file.createNewFile();
            }
            writer = new FileWriter(file, true);
            writer.write(content);
        }
        catch (Exception e)
        {}
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {}
            }
            file = null;
        }
    }

    public static String bytesToHexString(byte[] bytes)
    {
        if (bytes == null || bytes.length <= 0)
        {
            return null;
        }

        StringBuilder ret = new StringBuilder(2 * bytes.length);
        for (int i = 0; i < bytes.length; i++)
        {
            int b;
            b = 0x0f & (bytes[i] >> 4);
            ret.append("0123456789ABCDEF".charAt(b));
            b = 0x0f & bytes[i];
            ret.append("0123456789ABCDEF".charAt(b));
        }

        return ret.toString();
    }
}
