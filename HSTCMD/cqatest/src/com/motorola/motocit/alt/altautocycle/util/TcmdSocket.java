/*
 * Copyright (c) 2015 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.motocit.alt.altautocycle.util;

import java.io.IOException;
import java.io.InputStream;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

public class TcmdSocket
{
    private static final String TCMD_SERVER = "local_tcmd"; // /dev/socket/local_tcmd
    private static final int TCMD_MAX_BYTES = 1024;

    /*
     * Byte 1: request/response flag
     * request
     */
    private static byte TC_REQUEST_FLAG = 0x0;
    /* Byte 2: sequence tag */
    private static byte TC_SEQ_TAG = 0x0;
    /* Byte 3-4: 2 bytes opcode */
    private static byte[] TC_OPCODE = { 0x0, 0x0 };
    /* Byte 5-6: */
    private static byte[] TC_NEED_RESPONSE_FLAG = { 0x0, 0x0 };
    /*
     * Byte 7-8:
     * Reserved
     */
    private static final byte[] TC_RESERVED = { 0x0, 0x0 };
    /*
     * Byte
     * 9-12:
     */
    private static byte[] TC_DATA_LENGTH = { 0x00, 0x00, 0x00, 0x00 };

    public static final int TCMD_HEADER_LENGTH = 12;

    public static final String TCMD_BATCH_OPCODE = "0C2F";
    public static final String TCMD_BATCHDATA_PREFIX = "000000000001";

    private LocalSocket socket_client = null;

    public static class TcmdResponseData
    {
        private byte[] mData;
        private int mDataLength;

        public TcmdResponseData(byte[] data, int dataLength){
            mData = data;
            mDataLength = dataLength;
        }

        public byte[] getData()
        {
            return mData;
        }

        public int getDataLength()
        {
            return mDataLength;
        }
    }

    public static class TcmdParsedResponse
    {
        private String mResponse;
        private int mBytesProcessed;

        public TcmdParsedResponse(String response, int bytesProcessed){
            mResponse = response;
            mBytesProcessed = bytesProcessed;
        }

        public String getParsedResponse()
        {
            return mResponse;
        }

        public int getBytesProcessed()
        {
            return mBytesProcessed;
        }
    }

    public TcmdSocket() throws IOException
    {
        socket_client = new LocalSocket();
        LocalSocketAddress l = new LocalSocketAddress(TCMD_SERVER, LocalSocketAddress.Namespace.RESERVED);
        socket_client.connect(l);
    }

    public void close() throws IOException
    {
        if (socket_client != null)
        {
            socket_client.close();
            socket_client = null;
        }
    }

    public void send(String command) throws IOException
    {
        byte[] data = parseTcmdRequest(command);
        socket_client.getOutputStream().write(data);
        socket_client.getOutputStream().flush();
    }

    public TcmdResponseData receive() throws IOException
    {
        byte[] buffer = new byte[TCMD_MAX_BYTES];

        InputStream is = socket_client.getInputStream();

        int bytes_read = is.read(buffer);

        if (bytes_read < 0)
            return null;

        return new TcmdResponseData(buffer, bytes_read);
    }

    public static byte[] parseTcmdRequest(String request)
    {
        if ((request.length() & 1) == 1)
        {
            System.out.println("Warning: Odd length request string, last character will be skipped.");
        }

        /* string to hex conversion */
        int request_length = request.length() / 2;

        TC_OPCODE[0] = (byte) Integer.parseInt(request.substring(0, 2), 16);
        TC_OPCODE[1] = (byte) Integer.parseInt(request.substring(2, 4), 16);

        byte[] tcmdRequest = new byte[TCMD_HEADER_LENGTH + request_length - 2]; // minus
                                                                                // 2
                                                                                // byte
                                                                                // opcode

        tcmdRequest[0] = TC_REQUEST_FLAG;
        tcmdRequest[1] = TC_SEQ_TAG++;
        tcmdRequest[2] = TC_OPCODE[0];
        tcmdRequest[3] = TC_OPCODE[1];
        tcmdRequest[4] = TC_NEED_RESPONSE_FLAG[0];
        tcmdRequest[5] = TC_NEED_RESPONSE_FLAG[1];
        tcmdRequest[6] = tcmdRequest[7] = TC_RESERVED[0];
        tcmdRequest[8] = tcmdRequest[9] = tcmdRequest[10] = TC_DATA_LENGTH[0];

        if (request_length > TC_OPCODE.length)
        {
            String data = request.substring(4);
            tcmdRequest[11] = (byte) (data.length() / 2);
            tcmdRequest[10] = (byte) (data.length() / 512);

            for (int i = 0; i < tcmdRequest.length - TCMD_HEADER_LENGTH; i++)
                tcmdRequest[TCMD_HEADER_LENGTH + i] = (byte) Integer.parseInt(data.substring(2 * i, 2 * i + 2), 16);
        }
        else
        {
            tcmdRequest[11] = TC_DATA_LENGTH[3];
        }

        return tcmdRequest;
    }

    private static boolean responseFlag(byte data)
    {
        if ((data & 0x80) == 0x80)
            return true;
        else
            return false;
    }

    private static boolean tfFlag(byte data)
    {
        if ((data & 0x04) == 0x04)
            return true;
        else
            return false;
    }

    public static TcmdParsedResponse parseTcmdResponse(byte[] data)
    {
        int data_length = 256 * (data[10] & 0xFF) + (data[11] & 0xFF);

        if (!responseFlag(data[0])) // Get response flag bit
        {
            return new TcmdParsedResponse("FAIL: RESPONSE CORRPUPTED", -1);
        }
        else
        {
            String response = "";

            if (tfFlag(data[0])) // To show whether our cmd succeed or not
            {
                // Error
                response = "FAIL: ";

                for (int i = TCMD_HEADER_LENGTH; i < TCMD_HEADER_LENGTH + data_length; i++)
                    if (data[i] != 0)
                        response += (char) data[i];
            }
            else
            {
                // Success
                for (int i = 0; i < TCMD_HEADER_LENGTH + data_length; i++)
                    response += String.format("%02X ", data[i]);
            }

            return new TcmdParsedResponse(response, TCMD_HEADER_LENGTH + data_length);
        }
    }
}