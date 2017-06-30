/*
 * Copyright (c) 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.database;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

//Database is stored: data/data/com.motorola.motocit/databases and persists after power cycle

// Handler for Database.  Manages Database and makes SQL transactions
// Methods throw SQLException
public class DatabaseHandler extends SQLiteOpenHelper
{

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "Database";

    public DatabaseHandler(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Adds a record to the table
    @SuppressWarnings("deprecation")
    public void addRecord(DatabaseRecord databaseRecord) throws SQLException
    {
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            String str = "select 1 from " + "\"" + databaseRecord.table + "\"" + " where " + "\"" + databaseRecord.table + "\"" + "=" + "'"
                    + databaseRecord.name + "'";
            Cursor c = db.rawQuery(str, null);
            boolean exists = c.getCount() > 0;
            c.close();
            if (exists == false)
            {
                str = "INSERT INTO \"" + databaseRecord.table + "\" (\"" + databaseRecord.table + "\",";
                for (int i = 0; i < databaseRecord.columns.size(); i++)
                {
                    str = str + "\"" + databaseRecord.columns.get(i) + "\"" + ",";
                }
                str = str.substring(0, str.length() - 1) + ") values ('" + databaseRecord.name + "',";
                for (int i = 0; i < databaseRecord.values.size(); i++)
                {
                    str = str + "'" + databaseRecord.values.get(i) + "'" + ",";
                }
                str = str.substring(0, str.length() - 1) + ")";
                // If record does not exist use INSERT INTO
                // INSERT INTO "TABLE" ("TABLE","C0","C1","C2") values
                // ('RECORD','V1','V2','V3');
                db.execSQL(str);
            }
            else
            {
                str = "UPDATE " + "\"" + databaseRecord.table + "\"" + " set ";
                for (int i = 0; i < databaseRecord.columns.size(); i++)
                {
                    str = str + "\"" + databaseRecord.columns.get(i) + "\"" + "='" + databaseRecord.values.get(i) + "', ";
                }
                str = str.substring(0, str.length() - 2);
                str = str + "where " + "\"" + databaseRecord.table + "\"" + "='" + databaseRecord.name + "';";
                // If record exists use UPDATE
                // UPDATE "TABLE" set "C0"='V1', "C1"='V2', "C2"='V3'where
                // "TABLE"='RECORD';
                db.execSQL(str);
            }
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }

    // Adds a table to the database
    // CREATE TABLE IF NOT EXISTS "TABLE"("TABLE" STRING PRIMARY KEY UNIQUE,
    // "C0" STRING, "C1" STRING, "C2" STRING);
    @SuppressWarnings("deprecation")
    public void addTable(String table, List<String> columns) throws SQLException
    {
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            String str = "CREATE TABLE IF NOT EXISTS " + "\"" + table + "\"" + "(" + "\"" + table + "\"" + " STRING PRIMARY KEY UNIQUE,";
            for (String s : columns)
            {
                str = str + " " + "\"" + s + "\"" + " STRING" + ",";
            }
            str = str.substring(0, str.length() - 1) + ")";
            db.execSQL(str);
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }

    // Deletes a record
    @SuppressWarnings("deprecation")
    public void deleteRecord(String table, String name) throws SQLException
    {
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            String str = "DELETE FROM \"" + table + "\" WHERE \"" + table + "\"=" + "'" + name + "'";
            // DELETE FROM "TABLE" WHERE "TABLE"='RECORD';
            db.execSQL(str);
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }

    // Deletes a table
    @SuppressWarnings("deprecation")
    public void deleteTable(String table) throws SQLException
    {
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            // DROP TABLE IF EXISTS "TABLE";
            String str = "DROP TABLE IF EXISTS " + "\"" + table + "\"";
            db.execSQL(str);
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
    }

    // Returns all records in a table
    @SuppressWarnings("deprecation")
    public List<DatabaseRecord> getAllRecords(String table) throws SQLException, CursorIndexOutOfBoundsException
    {
        List<String> columns = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        List<DatabaseRecord> records = new ArrayList<DatabaseRecord>();
        try
        {
            SQLiteDatabase db = this.getReadableDatabase();
            // SELECT * FROM "TABLE";
            String str = "SELECT * FROM " + "\"" + table + "\"";
            Cursor c = db.rawQuery(str, null);
            c.moveToFirst();
            do
            {
                for (int i = 1; i < c.getColumnCount(); i++)
                {
                    columns.add(c.getColumnName(i));
                    values.add(c.getString(i));
                }
                records.add(new DatabaseRecord(table, c.getString(0), columns, values));
                columns.clear();
                values.clear();
            }
            while (c.moveToNext());
            c.close();
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
        catch (CursorIndexOutOfBoundsException ex)
        {
            throw ex;
        }
        return records;
    }

    // Returns a database record
    @SuppressWarnings("deprecation")
    public DatabaseRecord getRecord(String table, String name) throws SQLException, CursorIndexOutOfBoundsException
    {
        List<String> columns = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        try
        {
            SQLiteDatabase db = this.getReadableDatabase();
            // SELECT * FROM "TABLE" WHERE "TABLE"='RECORD';
            String str = "SELECT * FROM " + "\"" + table + "\"" + " WHERE " + "\"" + table + "\"='" + name + "'";
            Cursor c = db.rawQuery(str, null);
            c.moveToFirst();
            for (int i = 1; i < c.getColumnCount(); i++)
            {
                columns.add(c.getColumnName(i));
                values.add(c.getString(i));
            }
            c.close();
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
        catch (CursorIndexOutOfBoundsException ex)
        {
            throw ex;
        }

        return new DatabaseRecord(table, name, columns, values);
    }

    // Returns all table names
    @SuppressWarnings("deprecation")
    public List<String> getTables() throws SQLException
    {
        List<String> tables = new ArrayList<String>();
        try
        {
            SQLiteDatabase db = this.getWritableDatabase();
            // SELECT NAME FROM SQLITE_MASTER WHERE TYPE='table';
            Cursor c = db.rawQuery("SELECT NAME FROM SQLITE_MASTER WHERE TYPE='table'", null);
            while (c.moveToNext())
            {
                String str = c.getString(0);
                // Ignore the table that android automatically adds
                if (str.equals("android_metadata"))
                {
                    continue;
                }
                else
                {
                    tables.add(str);
                }
            }
            c.close();
            db.close();
        }
        catch (SQLException ex)
        {
            throw ex;
        }
        return tables;
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
    }
}