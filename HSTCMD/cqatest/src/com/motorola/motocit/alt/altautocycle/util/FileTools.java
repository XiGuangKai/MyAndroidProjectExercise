/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileTools
{

    public static void fileCopyOld(File f1, File f2) throws Exception
    {
        int length = 1024;
        FileInputStream in = new FileInputStream(f1);
        FileOutputStream out = new FileOutputStream(f2);
        byte[] buffer = new byte[length];
        while (true)
        {
            int ins = in.read(buffer);
            if (ins == -1)
            {
                in.close();
                out.flush();
                out.close();
            }
            else
                out.write(buffer, 0, ins);
        }

    }

    public static void fileCopy(File f1, File f2) throws Exception
    {
        int length = 1024;
        FileInputStream in = new FileInputStream(f1);
        FileOutputStream out = new FileOutputStream(f2);
        FileChannel inC = in.getChannel();
        FileChannel outC = out.getChannel();
        ByteBuffer b = null;
        while (true)
        {
            if (inC.position() == inC.size())
            {
                inC.close();
                outC.close();
            }
            if ((inC.size() - inC.position()) < length)
            {
                length = (int) (inC.size() - inC.position());
            }
            else
            {
                length = 1024;
            }
            b = ByteBuffer.allocateDirect(length);
            inC.read(b);
            b.flip();
            outC.write(b);
            outC.force(false);
        }

    }
}
