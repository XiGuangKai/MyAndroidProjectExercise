/*
 * Copyright (c) 2013 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 *    Date           CR            Author                 Description
 * 2013/05/10    IKJBXLINE-3780   Kris Liu     - e13701   Creation
 */

package com.motorola.motocit.simcard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

import com.motorola.motocit.TestUtils;

public final class PlmnTable {
    private static final String TAG = "PlmnTable";

    // plmn_text_table.bin in assets
    private static final String PLMN_FILE_PATH = "plmn_text_table.bin";

    // overlay plmn_text_table.bin
    private static final String PLMN_FILE_PATH_OVERLAY = "/system/etc/motorola/preferred_networks/plmn_text_table.bin";

    // Static map with the PLMNs from
    private static HashMap<String, Mcc> plmnTable = null;

    // Static map with the PLMN names from the PLMN_FILE_PATH table
    private static HashMap<String, String> plmnTableName = null;

    /**
     * Load the PLMN table from file PLMN_FILE_PATH
     */
    private static void loadPlmnTable(final Context context) {
        dbgLog(TAG, "loadPlmnTable >>>>>>>>>>>", 'i');

        if (plmnTable != null && plmnTableName != null) {
            // already loaded, so do nothing
            return;
        }

        InputStream inputStream = null;

        // load plmn_text_table.bin from overlay folder
        dbgLog(TAG, "load file " + PLMN_FILE_PATH_OVERLAY, 'i');
        try {
            inputStream = new FileInputStream(new File(PLMN_FILE_PATH_OVERLAY));
        } catch (FileNotFoundException e) {
            dbgLog(TAG, "File not found : " + PLMN_FILE_PATH_OVERLAY, 'e');
        }

        if (inputStream == null) {
            dbgLog(TAG, "Could not load plmn_text_table.bin", 'e');
            return;
        }

        // table to used for handling the mcc information
        plmnTable = new HashMap<String, Mcc>();

        // BEGIN Motorola dpq864 FID 39593, IKCBS-6452
        plmnTableName = new HashMap<String, String>();
        // END Motorola IKCBS-6452

        // array used to parse the MCC information
        int[] plmnBytes = new int[3];

        int length = 0;
        int skipped_length = 0;

        try {
            // Array to handle the PLMN information (MCC + MNC)
            String[] plmnStr = new String[6];
            // Mcc string
            Mcc mcc = null;

            // BEGIN Motorola dpq864 2012-11-22 IKCBS-6452
            String networkName = null;
            byte[] networkNameFromTable = null;
            // END Motorola IKCBS-6452

            while (((plmnBytes[0] = inputStream.read()) != -1) &&
                    ((plmnBytes[1] = inputStream.read()) != -1) &&
                    ((plmnBytes[2] = inputStream.read()) != -1)) {
                dbgLog(TAG, "values read: plmnBytes:" + plmnBytes[0] + " - " + plmnBytes[1] + " - "
                        + plmnBytes[2], 'i');

                for (int i = 0; i < plmnBytes.length; i++) {
                    plmnStr[2 * i] = Integer.toString((plmnBytes[i] & 240) / 16);
                    plmnStr[2 * i + 1] = Integer.toString(plmnBytes[i] & 15);
                }

                dbgLog(TAG, "values MCC STR: plmnStr:" + plmnStr[1] + " - " + plmnStr[0] + " - "
                        + plmnStr[3], 'i');
                dbgLog(TAG, "values MNC STR: plmnStr:" + plmnStr[5] + " - " + plmnStr[4] + " * "
                        + plmnStr[5] + " - " + plmnStr[4] + " - " + plmnStr[2], 'i');
                // parse the MCC and MNC read from file
                String mccStr = (plmnStr[1] + plmnStr[0] + plmnStr[3]);
                String mncStr = plmnStr[2].equals("15") ? (plmnStr[5] + plmnStr[4]) : (plmnStr[5]
                        + plmnStr[4] + plmnStr[2]);

                if (plmnTable.containsKey(mccStr)) {
                    dbgLog(TAG, "plmnTable contains the key", 'i');
                    mcc = plmnTable.get(mccStr);
                } else {
                    mcc = new Mcc(mccStr);
                    plmnTable.put(mccStr, mcc);
                }
                // adding MCC to the list supported on the device
                if (null != mcc) {
                    mcc.addMnc(mncStr);
                }

                // if failed any reading
                if (inputStream.read() == -1)
                    break;

                // read the next MCC
                if ((length = inputStream.read()) == -1)
                    break;

                // BEGIN Motorola dpq864 FID 39593, IKCBS-6452
                skipped_length = 0;
                // to the next MCC, we will now get the network name.
                dbgLog(TAG, "the following length has the carrier name: " + length, 'i');
                // setup the buffer
                networkNameFromTable = new byte[length];
                // END Motorola IKCBS-6452
                // IKELWAYLA-628 - need to skip 'length' bytes
                // in order to jump to next MCC
                do {
                    // read the specified amount of bytes
                    skipped_length = (inputStream
                            .read(networkNameFromTable, skipped_length, length));
                    // check if a problem happened
                    if (skipped_length == -1) {
                        dbgLog(TAG, "Failed on reading the network name for " + mccStr + mncStr, 'i');
                        break;
                    }
                    dbgLog(TAG, "Read the following amount of characters: " + skipped_length, 'i');
                    // END Motorola IKCBS-6452
                    // Since this function skips at MOST 'length' bytes,
                    // it is necessary to check the return value
                    length = length - skipped_length;
                } while (length > 0 && skipped_length > 0);
                dbgLog(TAG, "checking the network name", 'i');
                networkName = new String(networkNameFromTable);
                // network name shall be ready now
                dbgLog(TAG, "networkName: " + networkName, 'i');
                // get the network key
                String networkKey = mccStr + mncStr;
                // check if key is available on the table. Whe should not have
                // any duplication
                if (!(plmnTableName.containsKey(networkKey))) {
                    plmnTableName.put(networkKey, networkName);
                    dbgLog(TAG, "Adding the following key and name: " + networkKey + " -"
                                + networkName, 'i');
                } else {
                    dbgLog(TAG, "The following key already exists and it should not happen: " + networkKey
                            + " - " + networkName, 'i');
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
        dbgLog(TAG, ">>>>>>>>>>>>>> loadPlmnTable", 'i');
    }

    /**
     * Get the network name based on the table provided in PLMN_FILE_PATH
     *
     * @param networkID The PLMN (MCC/MNC) information to be searched
     */
    public static String getNetworkName(Context context, String networkID) {
        dbgLog(TAG, "getNetworkName, networkID=" + networkID, 'i');

        if (plmnTableName == null) {
            loadPlmnTable(context);
        }

        if ((plmnTableName == null) || (!plmnTableName.containsKey(networkID))) {
            dbgLog(TAG, "DON'T HAVE the key: " + networkID, 'i');
            return null;
        }

        String networkName = plmnTableName.get(networkID);
        dbgLog(TAG, "getNetworkName, networkName=" + networkName, 'i');
        return networkName;
    }

    private static void dbgLog(String tag, String msg, char type)
    {
        TestUtils.dbgLog(tag, msg, type);
    }
}
