/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;

public class SDCardTestHandler
{
    private final static int length = 1024;
    private final static String surf1 = ".surf1";
    private final static String surf2 = ".surf2";
    private final static String[] paths = { Constant.SD_VIDEO_PHATH,
            Constant.SD_AUDIO_PATH, Constant.SD_PICTURE_PATH,
            Constant.SD_TEXT_PATH };
    private final static int[] rawIDs = { com.motorola.motocit.R.raw.alt_audio_350_to_450, com.motorola.motocit.R.raw.alt_picture, com.motorola.motocit.R.raw.alt_tex_file };
    private Context context;

    public SDCardTestHandler(Context context){
        this.context = context;
    }

    public void test()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    for (int i = 0; i < paths.length; i++)
                    {
                        // File f1 = new File(paths[i]);
                        File f2 = new File(paths[i] + surf1);
                        File f3 = new File(paths[i] + surf2);
                        if (f2.exists())
                        {
                            f2.delete();
                        }
                        if (f3.exists())
                        {
                            f3.delete();
                        }
                        fileCopy(rawIDs[i], f2, f3);
                    }
                }
                catch (Exception e)
                {}
            }
        }).start();
    }

    private void fileCopy(int rawID, File f2, File f3)
    {
        InputStream in = null;
        FileOutputStream out = null;
        FileOutputStream out2 = null;
        try
        {
            in = this.context.getResources().openRawResource(rawID);
            out = new FileOutputStream(f2);
            out2 = new FileOutputStream(f3);
            byte[] buffer = new byte[length];
            while (true)
            {
                int ins = in.read(buffer);
                if (ins == -1)
                {
                    out.flush();
                    out2.flush();
                    out.close();
                    out = null;
                    out2.close();
                    out2 = null;
                }
                else
                {
                    out.write(buffer, 0, ins);
                    out2.write(buffer, 0, ins);
                }
            }
        }
        catch (Exception e)
        {}
        finally
        {
            try
            {
                if (f3.exists())
                {
                    f3.delete();
                }
                if (in != null)
                {
                    in.close();
                    in = null;
                }
                if (out != null)
                {
                    out.close();
                    out = null;
                }
                if (out2 != null)
                {
                    out2.close();
                    out2 = null;
                }
            }
            catch (Exception e)
            {}
        }
    }
}
