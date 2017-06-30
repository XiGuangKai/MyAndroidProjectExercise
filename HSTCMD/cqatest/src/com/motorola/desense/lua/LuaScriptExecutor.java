/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense.lua;

import org.luaj.vm2.Globals;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class LuaScriptExecutor {


    public static boolean executeScript(String dir, String filename) {
        boolean passed = false;
        Globals globals = DesenseGlobals.standardScriptGlobals(dir, filename);
        Reader reader = null;

        try {
            reader = new FileReader(filename);
            globals.load(reader, filename).call();
            passed = true;
        } catch (Throwable t) {
            passed = false;
            System.out.println("Lua error: " + t.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return passed;
    }

    public static void finish() {
        DesenseGlobals.closeSocket();
    }

}
