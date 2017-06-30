/*
 * Copyright (c) 2012 Motorola Mobility LLC
 * All Rights Received
 *
 * The contents of this file are Motorola Mobility Confidential Restricted (MCR)
 * Revision History (newest first):
 *
 * Date        CR            Author                  Description
 * 2012/11/27  IKMAIN-49301 Rich Hammon  - wrh002    Add CommServer Functionality for binary data transfer
 *
 */
package com.motorola.motocit;

import java.util.ArrayList;
import java.util.List;

import com.motorola.motocit.CommServer.ServerResponseType;

public class CommServerBinaryPacket
{
    public int nSeqTag;
    public String strCmd;
    public List<String> strInfoDataList = new ArrayList<String>();
    public String strSenderTag;
    public ServerResponseType packetResponseType;
    public byte[] binaryData;

    public CommServerBinaryPacket()
    {
        nSeqTag = -1;
        strCmd = "UNDEFINED";
        strInfoDataList.clear();
        strSenderTag = "UNDEFINED";
        packetResponseType = ServerResponseType.UNKNOWN;
        binaryData = null;
    }

    public CommServerBinaryPacket(int nSeqTag, String strCmd, String strSenderTag, List<String> strInfoDataList, byte[] binaryData)
    {
        this.nSeqTag = nSeqTag;
        this.strCmd = strCmd;
        this.strInfoDataList.addAll(strInfoDataList);
        this.strSenderTag = strSenderTag;
        this.packetResponseType = ServerResponseType.UNKNOWN;
        this.binaryData = binaryData;
    }
}
