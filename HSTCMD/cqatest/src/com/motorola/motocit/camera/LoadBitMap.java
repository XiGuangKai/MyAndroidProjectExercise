/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *   Date           CR           Author                Description
 * 2012/06/22    IKHSS7-39967    Rich Hammon   - wrh002  Add load image from jpeg file or data stream to Bitmap image
 * */

package com.motorola.motocit.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class LoadBitMap
{

    private static final String TAG = "LoadBitMap";

    public static Bitmap loadImageFromJpeg(String fileName)
    {
        String path = fileName;

        Bitmap imageBitMap;

        int size;
        int ratio;

        // find the size of the image (without decoding it)
        BitmapFactory.Options BitMapOptions = new BitmapFactory.Options();
        BitMapOptions.inJustDecodeBounds = true;
        BitMapOptions.inScaled = false;
        BitmapFactory.decodeFile(path, BitMapOptions);

        // find a scale factor that is a power of 2
        if (BitMapOptions.outHeight > BitMapOptions.outWidth)
        {
            size = BitMapOptions.outHeight;
        }
        else
        {
            size = BitMapOptions.outWidth;
        }
        ratio = (int) (size * 0.0015625); // i.e. (size/640)
        size = 1;

        while (size <= ratio)
        {
            size = size << 1;
        }
        size = size >> 1;

            // decode the imageUnderTest while scaling it
            BitMapOptions.inSampleSize = size;
            BitMapOptions.inJustDecodeBounds = false;
            BitMapOptions.inDither = false;
            BitMapOptions.inScaled = false;

            imageBitMap = BitmapFactory.decodeFile(path, BitMapOptions);

            return imageBitMap;
    }

    public static Bitmap loadImageFromByte(byte[] data)
    {
        Bitmap imageBitMap;

        int size;
        int ratio;

        int offset = 0;

        int length = data.length;

        // find the size of the image (without decoding it)
        BitmapFactory.Options BitMapOptions = new BitmapFactory.Options();
        BitMapOptions.inJustDecodeBounds = true;
        BitMapOptions.inScaled = false;
        BitmapFactory.decodeByteArray(data, offset, length, BitMapOptions);

        // find a scale factor that is a power of 2
        if (BitMapOptions.outHeight > BitMapOptions.outWidth)
        {
            size = BitMapOptions.outHeight;
        }
        else
        {
            size = BitMapOptions.outWidth;
        }
        ratio = (int) (size * 0.0015625); // i.e. (size/640)
        size = 1;

        while (size <= ratio)
        {
            size = size << 1;
        }
        size = size >> 1;

            // decode the imageUnderTest while scaling it
            BitMapOptions.inSampleSize = size;
            BitMapOptions.inJustDecodeBounds = false;
            BitMapOptions.inDither = false;
            BitMapOptions.inScaled = false;

            imageBitMap = BitmapFactory.decodeByteArray(data, offset, length, BitMapOptions);

            return imageBitMap;
    }
}
