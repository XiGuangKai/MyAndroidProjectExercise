/*
 * Copyright (c) 2012 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 * Revision history (newest first):
 *
 * Date           CR         Author                Description
 * 2012/06/29  IKHSS7-41826  Ken Moy  - wkm039    Create exception class
 */

package com.motorola.motocit;

public class MotoSettingsNotFoundException extends Exception
{
    private static final long serialVersionUID = -28602831122792785L;

    public MotoSettingsNotFoundException()
    {

    }

    public MotoSettingsNotFoundException(String msg)
    {
        super(msg);
    }
}
