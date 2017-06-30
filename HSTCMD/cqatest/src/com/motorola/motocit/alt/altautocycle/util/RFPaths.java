/*
 * Copyright (c) 2015 - 2016 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.alt.altautocycle.util;

import android.util.Log;

import com.motorola.desense.lua.DesenseGlobals;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.motorola.motocit.TestUtils.dbgLog;

public class RFPaths {

    /**
     * See Qualcomm 80-VA888-1 Y
     * Name              Description                               Size (bytes)  Hex (little endian)
     * CMD_CODE          Set to 75 for Subsystem Dispatch Version 1.  1            4B
     * SUB_SYS_ID        Subsystem ID – FTM ID is 11.                 1            0B
     * RF_MODE_ID        Mode ID for FTM_COMMON is 20.                2            14 00
     * FTM_CMD_ID        FTM_GET_RF_PATH_INFORMATION = 655.           2            8F 02
     * REQ_LEN           Unused, set to zero.                         2            00 00
     *
     * Header is the same for request and response.
     */
    private static final String FTM_GET_RF_PATH_HEADER = "4B0B14008F020000";

    private static final String FTM_GET_RF_PATH_QUERY = "0000%02XFFFF0001";
    /**
     * See Qualcomm 80-VA888-1 Y
     * Name              Description                               Size (bytes)  Hex (little endian)
     * RESP_LEN          Unused, set to zero.                         2            00 00
     * Tech              Technology to query.                         1            XX
     * Band              0xFF to query all bands.                     2            FF FF
     * Subband           No split = 0.                                1            00
     * Version           Version of response.                         1            01
     */
    private static final String FTM_GET_RF_PATH_INFORMATION_REQUEST = FTM_GET_RF_PATH_HEADER + FTM_GET_RF_PATH_QUERY;

    private static final int RF_PATH_INFORMATION_PACKET_SIZE = 43;

    private static final String TAG = RFPaths.class.getSimpleName();

    public enum Tech {

        CDMA(0), WCDMA(1), GSM(2), LTE(10), TD_SCDMA(11);

        public int code; Tech(int code) { this.code = code; }
    }

    private enum FTMPathStatus {

        NO_ERROR(0),
        INVALID_TECHNOLOGY(1),
        INVALID_BAND(2),
        INVALID_PATH_TYPE(3),
        NO_TABLE_INFORMATION(4),
        MEM_ALLOC_FAILED(5),
        NULL_POINTER_RECEIVED(6),
        MAX_PACKET_EXCEEDED(7),
        INVALID_VERSION(8);

        public int code; FTMPathStatus(int code) { this.code = code; }

        public static FTMPathStatus fromCode(int code){
            for(FTMPathStatus v: values()){ if(v.code == code) return v; } return null;
        }
    }

    private static final int FTM_GET_RF_PATH_INFORMATION_OFFSET = 13;

    private static byte[] getRFPathInformation(int tech) throws IOException {
        // words are represented as space-separated ascii-encoded little-endian hexadecimals string.
        String request = String.format(FTM_GET_RF_PATH_INFORMATION_REQUEST, tech);
        String payload = DesenseGlobals.executeFtmCommand(request);

        String[] hexa = payload.split("\\s");
        byte[] bytes = new byte[hexa.length];
        for (int i = 0; i < hexa.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hexa[i], 16); // because java don't have unsigned!
        }

        // See Qualcomm 80-VA888-1 Y
        String header = payload.substring(0, 24).replaceAll("\\s", "");
        int RESP_LEN          = littleEndianToInt(bytes, 8, 2);
        int isLastPackets     =   bytes[10];
        int compressionStatus =   bytes[11];
        int errorStatus       =   bytes[12];

        switch (FTMPathStatus.fromCode(errorStatus)){
            case NO_ERROR:
                dbgLog(TAG, "errorStatus = NO_ERROR", 'i');
                break;
            case INVALID_TECHNOLOGY:
                throw new IOException("Invalid Technology");
            case INVALID_BAND:
                throw new IOException("Invalid Band");
            case INVALID_PATH_TYPE:
                throw new IOException("Invalid Path Type");
            case NO_TABLE_INFORMATION:
                throw new IOException("No Table Information");
            case MEM_ALLOC_FAILED:
                throw new IOException("Memory allocation failed");
            case NULL_POINTER_RECEIVED:
                throw new IOException("Null Pointer received");
            case MAX_PACKET_EXCEEDED:
                throw new IOException("Response packet size exceeded");
            case INVALID_VERSION :
                throw new IOException("Invalid version requested");
            default:
                throw new IOException("Unknown errorStatus");
        }

        if (!header.equals(FTM_GET_RF_PATH_HEADER)) {
            throw new IOException(String.format(
                    "Header mismatch. Should be %s, got %s", FTM_GET_RF_PATH_HEADER, header));
        }

        if (RESP_LEN != bytes.length)
        {
            throw new IOException(String.format(Locale.US,
                    "Package length mismatch. FTM_RSP_PKT_SIZE=%d, got %d",
                    RESP_LEN * 2, header.length()));
        }

        int length = 0, packetVersion = 0, numOfRFMPaths = 0;
        byte[] buffer;
        if(compressionStatus == 1)
        {
            buffer = new byte[4 + 512 * RF_PATH_INFORMATION_PACKET_SIZE];
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(bytes, FTM_GET_RF_PATH_INFORMATION_OFFSET,
                        bytes.length - FTM_GET_RF_PATH_INFORMATION_OFFSET);
                length = inflater.inflate(buffer);
            } catch (DataFormatException e) {
                throw new IOException("Invalid compressed data", e);
            } finally {
                inflater.end();
            }

            packetVersion     = littleEndianToInt(buffer, 0, 2);
            numOfRFMPaths     = littleEndianToInt(buffer, 2, 2);
            buffer = Arrays.copyOfRange(buffer, 4, length);
        } else {
            packetVersion     = littleEndianToInt(bytes, 13, 2);
            numOfRFMPaths     = littleEndianToInt(bytes, 15, 2);
            buffer = Arrays.copyOfRange(bytes, FTM_GET_RF_PATH_INFORMATION_OFFSET + 4, bytes.length);
            length = buffer.length;
        }

        if (numOfRFMPaths * RF_PATH_INFORMATION_PACKET_SIZE + 4 != length) {
            throw new IOException(String.format(Locale.US,
                    "Data size mismatch. DATA_SIZE=%d, got %d", numOfRFMPaths * RF_PATH_INFORMATION_PACKET_SIZE, length));
        }

        // If numOfRFMPaths = 0, no need to check packet version (it will always be zero).
        if (numOfRFMPaths > 0 && packetVersion != 1) {
            throw new IOException(String.format(Locale.US,
                    "Version mismatch. Should be 01, got %d", packetVersion));
        }


        Log.d(TAG, new StringBuilder()
                .append(" header=").append(header)
                .append(" RESP_LEN=").append(RESP_LEN)
                .append(" isLastPackets=").append(isLastPackets)
                .append(" compressionStatus=").append(compressionStatus)
                .append(" errorStatus=").append(errorStatus)
                .append(" packetVersion=").append(packetVersion)
                .append(" errorStatus=").append(errorStatus)
                .append(" numOfRFMPaths=").append(numOfRFMPaths)
                .toString()
        );

        return buffer;
    }

    private static int littleEndianToInt(byte[] bytes, int offset, int length){
        if(length > 4){
            throw new IllegalArgumentException("length should be <= 4 bytes to fit a 32 bit int");
        }
        int value = 0;
        for (int i = 0; i < length; i++) {
            value += (bytes[i + offset] & 0xff) << (8* i);
        }
        return value;
    }

    public static Map<Tech, Set<Integer>> getAllRFPathInformation() throws IOException {
        Map<Tech, Set<Integer>> map = new HashMap<>();
        for (Tech tech : Tech.values()) {
            byte[] data = getRFPathInformation(tech.code);

            // See Qualcomm 80-VA888-1 Rev. Y
            for (int i = 0; i < data.length; i += RF_PATH_INFORMATION_PACKET_SIZE) {
                int band = littleEndianToInt(data, i + 0, 2);
                if(map.containsKey(tech)){
                    map.get(tech).add(band);
                } else{
                    map.put(tech, new HashSet<Integer>(band));
                }
            }
        }
        return map;
    }
}
