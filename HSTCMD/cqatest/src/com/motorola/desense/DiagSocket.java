/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.SystemClock;

public class DiagSocket implements Closeable {

    private static final byte ESC_CHAR = 0x7D;
    private static final byte CONTROL_CHAR = 0x7E;
    private static final byte ESC_MASK = 0x20;

    private static final int BUFFER_SIZE = 32768;
    private static final int READ_TIMEOUT = 10000; // 10 seconds
    private byte[] buffer;

    public DiagSocket() {
        buffer = new byte[BUFFER_SIZE];
    }

    public void open() throws IOException {
        if (openSocket() < 0)
            throw new IOException("Unable to open DIAG socket.");
    }

    public void close() {
        closeSocket();
    }

    public Iterable<List<Byte>> readPackets() throws IOException {
        int length = receive(buffer);
        if (length < 0)
            throw new IOException("Unable to read from DIAG socket.");

        // TODO: remove
        /*
         * System.out.print("RAW_DATA: "); for(int i = 0; i < length; i++) {
         *
         * System.out.print(String.format("%02X ", buffer[i]));
         *
         * if((i & 0xff) == 0xff) { System.out.println();
         * System.out.print("RAW_DATA: "); } } System.out.println();
         */

        return new PacketIterator(buffer, length);
    }

    // NOTE: mask_request_validate function in diagchar_core.c only allows
    // certain packet types to be sent:
    /*
     * 0x00: // Version Number 0x0C: // CDMA status packet 0x1C: // Diag Version
     * 0x1D: // Time Stamp 0x60: // Event Report Control 0x63: // Status
     * snapshot 0x73: // Logging Configuration 0x7C: // Extended build ID 0x7D:
     * // Extended Message configuration 0x81: // Event get mask 0x82: // Set
     * the event mask
     */
    public void sendPacket(String command) throws IOException {
        byte[] bytes = FtmSocket.parseFtmRequest(command);

        // Append 0x00000020 for USER_SPACE_DATA_TYPE
        byte[] data = new byte[bytes.length + 4];
        data[0] = 0x20;
        data[1] = 0x00;
        data[2] = 0x00;
        data[3] = 0x00;
        for (int i = 0; i < bytes.length; i++)
            data[i + 4] = bytes[i];

        // System.out.println("Sending packet: ");
        // for(byte b : data) System.out.print(String.format("%02X ", b));
        // System.out.println();

        int ret = send(data);
        if (ret < 0)
            throw new IOException("Unable to write to DIAG socket: Write error.");
        // System.out.println("send returned: " + ret);
    }

    // Returns first available packet, drops the rest.
    public String recievePacket() throws IOException {
        long startTime = SystemClock.elapsedRealtime();

        while (SystemClock.elapsedRealtime() - startTime < READ_TIMEOUT) {
            for (List<Byte> resp : readPackets()) {
                if (resp.size() == 0)
                    continue;

                StringBuilder sb = new StringBuilder(3 * resp.size());

                for (Byte b : resp)
                    sb.append(String.format("%02X ", b));
                return sb.toString();
            }
        }

        throw new IOException("Unable to read from DIAG socket: Read timeout.");
    }

    private static native int openSocket();

    private static native void closeSocket();

    private static native int receive(byte[] data);

    private static native int send(byte[] data);

    protected static class PacketIterator implements Iterator<List<Byte>>, Iterable<List<Byte>> {
        private byte[] data;
        private int packets;

        private int data_pos;
        private int bytes_left;

        public PacketIterator(byte[] buffer, int length) {
            // Skip 4 byte header. TODO: Assert type == USER_SPACE_DATA_TYPE ?
            // TODO: Check decoded packet sizes match length of received data

            if (length < 8)
                return;

            data = buffer;
            packets = getInteger(buffer, 4); // Read # of packets from buffer
            data_pos = 8;
        }

        @Override
        public Iterator<List<Byte>> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return packets > 0;
        }

        @Override
        public List<Byte> next() {

            if (bytes_left == 0) {
                bytes_left = getInteger(data, data_pos);
                data_pos += 4;

                // System.out.println("packet length: " + bytes_left + ",
                // data_pos: " + data_pos);
            }

            List<Byte> list = new ArrayList<Byte>();
            byte mask = 0;

            while (bytes_left > 0) {
                bytes_left--;
                int pos = data_pos++;

                if (data[pos] == CONTROL_CHAR) {
                    if (list.size() < 2)
                        System.out.println("packet CRC missing, length = " + list.size());

                    // Skip CRC at end. TODO: Add CRC check!
                    if (list.size() > 0)
                        list.remove(list.size() - 1);
                    if (list.size() > 0)
                        list.remove(list.size() - 1);
                    break;
                } else if (data[pos] == ESC_CHAR) {
                    mask = ESC_MASK;
                    continue;
                }

                list.add((byte) (data[pos] ^ mask));
                mask = 0;
            }

            if (bytes_left == 0)
                packets--;
            return list;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Cannot remove from PacketIterator.");
        }

        private static int getInteger(byte[] b, int pos) {
            return (b[pos] & 0xff) | ((b[pos + 1] & 0xff) << 8) | ((b[pos + 2] & 0xff) << 16)
                    | ((b[pos + 3] & 0xff) << 24);
        }
    }
}
