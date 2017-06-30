/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 * Date           CR         Author                Description
 * 2012/01/19  IKHSS7-4869  Kris Liu - e13701    Port CommServer code to main-dev-ics
 * 2012/01/02  IKHSS7-2305  Ken Moy  - wkm039    Added CommServer functionality
 */

package com.motorola.motocit;

import java.util.ArrayList;
import java.util.List;

public class CmdFailException extends Exception
{
    private static final long serialVersionUID = 1L;

    public int nSeqTag;
    public String strCmd;
    public String strTag;
    public List<String> strErrMsgList = new ArrayList<String>();

    public CmdFailException(int nSeqTag, String strCmd, List<String> strErrMsgList)
    {
        this.nSeqTag = nSeqTag;
        this.strCmd = strCmd;

        // add file and line number to err msg list
        StackTraceElement frame = this.getStackTrace()[0];
        this.strErrMsgList.add(String.format("%s(%d):", frame.getFileName(), frame.getLineNumber()));
        this.strErrMsgList.addAll(strErrMsgList);
    }

}
