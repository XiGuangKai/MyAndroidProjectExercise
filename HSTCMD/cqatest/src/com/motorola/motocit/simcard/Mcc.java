/*
 * Copyright (c) 2013 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *    Date           CR            Author                 Description
 * 2013/05/10    IKJBXLINE-3780   Kris Liu    - e13701   Creation
 */

package com.motorola.motocit.simcard;

import java.util.ArrayList;

/**
 * Objects of this class represent a MCC record from the PLMN Table loaded from
 * the file.
 */
public final class Mcc {

    private String mcc;
    private ArrayList<String> mncs;

    public Mcc(String mcc) {
        this.mcc = mcc;
        mncs = new ArrayList<String>();
    }

    public String getMcc() {
        return mcc;
    }

    public void addMnc(String mnc) {
        mncs.add(mnc);
    }

    public String[] getMncs() {
        String[] result = new String[mncs.size()];
        mncs.toArray(result);
        return result;
    }

    public boolean hasMnc(String mnc) {
        for (int i = 0; i < mncs.size(); i++) {
            if (mncs.get(i).equals(mnc)) {
                return true;
            }
        }
        return false;
    }
}
