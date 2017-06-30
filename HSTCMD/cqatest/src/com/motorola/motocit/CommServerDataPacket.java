/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 * Date           CR         Author                Description
 * 2012/06/19  IKHSS7-39713 Ken Moy  - wkm039    rename serverResponseType to ServerResponseType
 * 2012/01/19  IKHSS7-4869  Kris Liu - e13701    Port CommServer code to main-dev-ics
 * 2012/01/02  IKHSS7-2305  Ken Moy  - wkm039    Added CommServer functionality
 */

package com.motorola.motocit;

import java.util.ArrayList;
import java.util.List;
import com.motorola.motocit.CommServer.ServerResponseType;

public class CommServerDataPacket
{
    public int nSeqTag;
    public String strCmd;
    public List<String> strInfoDataList = new ArrayList<String>();
    public String strSenderTag;
    public ServerResponseType packetResponseType;

    public CommServerDataPacket()
    {
        nSeqTag = -1;
        strCmd = "UNDEFINED";
        strInfoDataList.clear();
        strSenderTag = "UNDEFINED";
        packetResponseType = ServerResponseType.UNKNOWN;
    }

    public CommServerDataPacket(int nSeqTag, String strCmd, String strSenderTag, List<String> strInfoDataList)
    {
        this.nSeqTag = nSeqTag;
        this.strCmd = strCmd;
        this.strInfoDataList.addAll(strInfoDataList);
        this.strSenderTag = strSenderTag;
        this.packetResponseType = ServerResponseType.UNKNOWN;
    }
}
