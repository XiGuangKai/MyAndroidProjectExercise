/*
 * Copyright (c) 2011-2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 * Date             CR         Author                Description
 * 2012/04/06 IKASANTIATT-396 ZX Hu   - w18335       Add Minus, Equals, SYM keys.
 * 2012/03/08    IKHSS7-7919  Ken Moy - wkm039       Print keycode for unknown key
 * 2012/02/09    IKHSS7-7725  Ken Moy - wkm039       For SDK >=12 use keyCodeToString()
 *
 */

package com.motorola.motocit.key;

import android.os.Build;
import android.view.KeyEvent;

/**
 * Map Key code to String. The Google API keyCodeToString(int keyCode) is ONLY
 * available in android 3.1
 */
public class Keys
{

    public static String keyCodeToStr(int keyCode) throws Exception
    {
        // check api level if greater than 12 then use keyCodeToString function
        if (Build.VERSION.SDK_INT >= 12)
        {
            return KeyEvent.keyCodeToString(keyCode);
        }

        String keyString = "";

        switch (keyCode)
        {

        case 3: // HOME Soft Key
            keyString = "Home Soft Key";
            break;

        case 4: // BACK Soft Key
            keyString = "BACK Soft key";
            break;

        case 7: // Number 0 Key
            keyString = "Number 0 Key";
            break;

        case 8: // Number 1 Key
            keyString = "Number 1 Key";
            break;

        case 9: // Number 2 Key
            keyString = "Number 2 Key";
            break;

        case 10: // Number 3 Key
            keyString = "Number 3 Key";
            break;

        case 11: // Number 4 Key
            keyString = "Number 4 Key";
            break;

        case 12: // Number 5 Key
            keyString = "Number 5 Key";
            break;

        case 13: // Number 6 Key
            keyString = "Number 6 Key";
            break;

        case 14: // Number 7 Key
            keyString = "Number 7 Key";
            break;

        case 15: // Number 8 Key
            keyString = "Number 8 Key";
            break;

        case 16: // Number 9 Key
            keyString = "Number 9 Key";
            break;

        case 19: // DPad Up Key
            keyString = "DPad Up Key";
            break;

        case 20: // DPad Down Key
            keyString = "DPad Down Key";
            break;

        case 21: // DPad Left Key
            keyString = "DPad Left Key";
            break;

        case 22: // DPad Right Key
            keyString = "DPad Right Key";
            break;

        case 23: // OK Key
            keyString = "OK Key";
            break;

        case 24: // VOLUME_UP
            keyString = "VOLUME UP Key";
            break;

        case 25: // VOLUME_DOWN
            keyString = "VOLUME DOWN Key";
            break;

        case 26: // Power Key
            keyString = "Power Key";
            break;

        case 27: // Camera Key
            keyString = "Camera Key";
            break;

        case 29: // A Key
            keyString = "A Key";
            break;

        case 30: // B Key
            keyString = "B Key";
            break;

        case 31: // C Key
            keyString = "C Key";
            break;

        case 32: // D Key
            keyString = "D Key";
            break;

        case 33: // E Key
            keyString = "E Key";
            break;

        case 34: // F Key
            keyString = "F Key";
            break;

        case 35: // G Key
            keyString = "G Key";
            break;

        case 36: // H Key
            keyString = "H Key";
            break;

        case 37: // I Key
            keyString = "I Key";
            break;

        case 38: // J Key
            keyString = "J Key";
            break;

        case 39: // K Key
            keyString = "K Key";
            break;

        case 40: // L Key
            keyString = "L Key";
            break;

        case 41: // M Key
            keyString = "M Key";
            break;

        case 42: // N Key
            keyString = "N Key";
            break;

        case 43: // O Key
            keyString = "O Key";
            break;

        case 44: // P Key
            keyString = "P Key";
            break;

        case 45: // Q Key
            keyString = "Q Key";
            break;

        case 46: // R Key
            keyString = "R Key";
            break;

        case 47: // S Key
            keyString = "S Key";
            break;

        case 48: // T Key
            keyString = "T Key";
            break;

        case 49: // U Key
            keyString = "U Key";
            break;

        case 50: // V Key
            keyString = "V Key";
            break;

        case 51: // W Key
            keyString = "W Key";
            break;

        case 52: // X Key
            keyString = "X Key";
            break;

        case 53: // Y Key
            keyString = "Y Key";
            break;

        case 54: // Z Key
            keyString = "Z Key";
            break;

        case 55: // Comma Key
            keyString = "Comma Key";
            break;

        case 56: // Period Key
            keyString = "Period Key";
            break;

        case 57: // ALT Left Key
            keyString = "ALT Left Key";
            break;

        case 59: // SHIFT Left Key
            keyString = "SHIFT Left Key";
            break;

        case 61: // TAB Key
            keyString = "TAB Key";
            break;

        case 62: // Space Key
            keyString = "Space Key";
            break;

        case 63: // SYM Key
            keyString = "SYM Key";
            break;

        case 66: // Enter Key
            keyString = "Enter Key";
            break;

        case 67: // Del Key
            keyString = "Del Key";
            break;

        case 68: // Grave Key
            keyString = "Grave Key";
            break;

        case 69: // Minus Key
            keyString = "Minus Key";
            break;

        case 70: // Equals Key
            keyString = "Equals Key";
            break;

        case 76: // Slash Key
            keyString = "Slash Key";
            break;

        case 77: // AT Key
            keyString = "AT Key";
            break;

        case 79: // Headset Key
            keyString = "Headset Key";
            break;

        case 82: // Menu Soft Key
            keyString = "Menu Soft Key";
            break;

        case KeyEvent.KEYCODE_SEARCH:
            keyString = "SEARCH Key";
            break;

        case 111: // Record Key
            keyString = "Record Key";
            break;

        default:
            return keyString = "Unknown Key: " + keyCode;

        }

        return keyString;
    }
}
