/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.database;

import java.util.ArrayList;
import java.util.List;

//Class that represents a Record Stored in the Database
// Database Visualization:
//+---------------------------
//| TABLE | C0 | C1 | C2 | ...
//|---------------------------
//| NAME1 | V1 | V2 | V3 | ...
//|---------------------------
//| NAME2 | V4 | V5 | V6 | ...
//+---------------------------

public class DatabaseRecord
{
    String table;
    String name;
    List<String> columns;
    List<String> values;

    public DatabaseRecord()
    {
    }

    public DatabaseRecord(String table, String name, List<String> columns, List<String> values)
    {
        this.table = new String(table);
        this.name = new String(name);
        this.columns = new ArrayList<String>(columns);
        this.values = new ArrayList<String>(values);
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
}
