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

public class CmdPassException extends Exception
{
    private static final long serialVersionUID = 1L;

    public int nSeqTag;
    public String strCmd;
    public String strTag;
    public List<String> strReturnDataList = new ArrayList<String>();

    public CmdPassException(int nSeqTag, String strCmd, List<String> strReturnDataList)
    {
        this.nSeqTag = nSeqTag;
        this.strCmd = strCmd;
        this.strReturnDataList.addAll(strReturnDataList);
    }
}
