/*
 * Copyright (c) 2012-2013 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *   Date           CR           Author                    Description
 * 2013/01/16    IKMAIN-49330    Rich Hammon   - wrh002  Change Image Analysis Input and Output Improvements
 * 2013/01/14    IKMAIN-49341    Kris Liu      - e13701  Add IMAGE_ANALYSIS_OPTIONS_LIST
 * 2012/06/22    IKHSS7-39967    Rich Hammon   - wrh002  Image Analysis of Bitmap to report avg luminance and or RI
 * */

package com.motorola.motocit.camera;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Bitmap;

public class ImageAnalysis
{
    private static final String TAG = "LoadBitMap";
    // avgLum is for backwards capability
    public static final String[] IMAGE_ANALYSIS_OPTIONS_LIST = { "avgLum", "CalcImageLuminance" };

    public static List<String> calcImageLuminance(Bitmap imageUnderTest)
    {
        List<String> strDataList = new ArrayList<String>();

        int x, y;
        int X, Y;
        int m, n;
        int stepX, stepY;
        int red, green, blue;

        int count;

        int value;
        int localR, localG, localB;
        int overallR, overallG, overallB;

        int iutWidth = imageUnderTest.getWidth();
        int iutHeight = imageUnderTest.getHeight();

        int[] pixValues = new int[iutHeight * iutWidth];
        imageUnderTest.getPixels(pixValues, 0, iutWidth, 0, 0, iutWidth, iutHeight);

        float icount;
        float lum;

        float min = 1000;
        float max = 0;

        stepX = iutWidth >> 4;
        stepY = stepX;

        overallR = 0;
        overallG = 0;
        overallB = 0;

        for (x = 0; x < iutWidth; x += stepX)
        {
            for (y = 0; y < iutHeight; y += stepY)
            {
                localR = 0;
                localG = 0;
                localB = 0;
                count = 0;

                for (m = 0; m < stepX; m++)
                {
                    X = x + m;

                    if (X < iutWidth)
                    {
                        for (n = 0; n < stepY; n++)
                        {
                            Y = y + n;

                            if (Y < iutHeight)
                            {
                                value = pixValues[X + (Y * iutWidth)];

                                red = (value >> 16) & 0xFF;
                                green = (value >> 8) & 0xFF;
                                blue = value & 0xFF;

                                localR += red;
                                localG += green;
                                localB += blue;

                                count++;
                            }
                        }
                    }
                }

                overallR += localR;
                overallG += localG;
                overallB += localB;

                icount = 1 / (float) count;

                localR *= icount;
                localG *= icount;
                localB *= icount;

                lum = (float) ((0.2990 * localR) + (0.5870 * localG) + (0.1140 * localB));

                if (lum < min)
                {
                    min = lum;
                }
                if (lum > max)
                {
                    max = lum;
                }
            }
        }

        icount = 1 / (float) (iutWidth * iutHeight);

        overallR *= icount;
        overallG *= icount;
        overallB *= icount;

        lum = (float) ((0.2990 * overallR) + (0.5870 * overallG) + (0.1140 * overallB));

        float ri = (min / max) * 100;

        strDataList.add("avgLum=" + lum);
        strDataList.add("RelativeIllumination=" + ri);

        return strDataList;
    };

    public static boolean checkImageAnalysisOption(String datalist)
    {
        String[] imageAnalysisOptions = datalist.split(",");

        boolean isValid = false;

        // go through measurement codes and check if it's in option list
        for (String imageAnalysisOption : imageAnalysisOptions)
        {

            for (int i = 0; i < IMAGE_ANALYSIS_OPTIONS_LIST.length; i++)
            {
                if (imageAnalysisOption.equalsIgnoreCase(ImageAnalysis.IMAGE_ANALYSIS_OPTIONS_LIST[i]))
                {
                    isValid = true;
                    break;
                }
                else
                {
                    isValid = false;
                }
            }
            if (!isValid)
            {
                break;
            }
        }
        if (isValid)
        {
            return true;
        }
        else
        {
            return false;
        }
    };
}
