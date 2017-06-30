/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense;

public abstract class RssiExtractor {

    public enum RssiType {
        SIMPLE, AVERAGE, MATRIX, APT, CA, CA_INV, GPS_CN, GPS_IQ, BLUETOOTH, WLAN;
    }

}
