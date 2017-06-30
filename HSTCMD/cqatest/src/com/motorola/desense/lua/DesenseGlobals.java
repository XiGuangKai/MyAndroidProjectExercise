/*
 * Copyright (c) 2017 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */
package com.motorola.desense.lua;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import com.motorola.desense.DiagSocket;
import com.motorola.desense.RssiExtractor.RssiType;

import android.os.SystemClock;
import android.util.Log;

public class DesenseGlobals {

    static {
        System.loadLibrary("desense");
    }

    public static final boolean USE_DIAG_SOCKET = true;
    public static final String SCRIPT_BASE_DIR = "/system/etc/motorola/12m/batch/bands/";
    private static final String TAG = DesenseGlobals.class.getSimpleName();;

    private static DiagSocket diag_socket = null;

    public static Globals standardScriptGlobals(String dir, String script_name){
        Globals globals = JsePlatform.standardGlobals();
        globals.set("ftm_send", new SendFtmCommand());
        globals.set("delay", new Delay());
        globals.get("string").set("byteswap", new ByteSwap());

        globals.load(new DesenseIoLib(dir, script_name));
        globals.get("io").set("writeline", new WriteLine(globals));
        globals.get("io").set("read_first_line", new ReadFirstLineFromFile());

        globals.set("extract_rssi_avg", new ExtractRxAgc(RssiType.AVERAGE));
        globals.set("extract_rssi_ca", new ExtractRxAgc(RssiType.CA));

        // TODO: Make configurable in UI
        LuaTable settings = new LuaTable();
        settings.set("loop_count", 1);
        settings.set("step_size", 1);
        globals.set("settings", settings);

        return globals;
    }

    private static class SendFtmCommand extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue command) {
            try {
                String response = executeFtmCommand(command.checkjstring());
                return LuaValue.valueOf(response);
            } catch (IOException e) {
                return LuaValue.valueOf("FAIL: " + e.getMessage());
            }
        }
    }

    public static synchronized void openSocket() throws IOException{
        if (diag_socket == null) {
            diag_socket = new DiagSocket();
            diag_socket.open();
        }
    }
    public static synchronized void closeSocket(){
        if (diag_socket != null) {
            diag_socket.close();
            diag_socket = null;
        }
    }

    public static synchronized String executeFtmCommand(String command) throws IOException {
        Log.d(TAG, "ftm_send " + command);

        openSocket();
        diag_socket.sendPacket(command);
        String response = diag_socket.recievePacket();
        Log.d(TAG, "response " + response);
        return response;
    }

    private static class Delay extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue delay) {
            SystemClock.sleep(delay.checkint());
            return null;
        }
    }

    public static class ByteSwap extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue arg0, LuaValue arg1) {

            long val = arg0.checklong();
            int bytes = arg1.checkint();

            StringBuilder builder = new StringBuilder(bytes << 1);
            for (int i = 0; i < bytes; i++) {
                builder.append(String.format("%02X", val & 0xFF));
                val >>= 8;
            }

            return LuaValue.valueOf(builder.toString());
        }
    }

    private static class WriteLine extends OneArgFunction {

        final Globals globals;
        static final LuaValue NEW_LINE = LuaValue.valueOf("\n");

        public WriteLine(Globals g) {
            globals = g;
        }

        @Override
        public LuaValue call(LuaValue val) {
            LuaValue write = globals.get("io").get("write");
            write.call(val);
            return write.call(NEW_LINE);
        }
    }

    private static class ReadFirstLineFromFile extends VarArgFunction {
        @Override
        public Varargs invoke(Varargs v) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(v.arg1().checkjstring()));
                String line = reader.readLine();
                reader.close();
                return LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf(line) });
            } catch (IOException e) {
                return LuaValue.varargsOf(new LuaValue[] { LuaValue.NIL, LuaValue.valueOf(e.getMessage()) });
            }
        }
    }

    // TODO: implement RssiExtractor
    private static class ExtractRxAgc extends FourArgFunction {
        // final String script_name;
        // final ScriptExecutor executor;
        final RssiType type;

        public ExtractRxAgc(RssiType t)
        // public ExtractRxAgc(String s, ScriptExecutor e, RssiType t)
        {
            // script_name = s;
            // executor = e;
            type = t;
        }

        @Override
        public LuaValue call(LuaValue raw_filename, LuaValue xls_filename, LuaValue modem_type,
                LuaValue offender_suffix) {
//            try {
//                String raw_path = executor.getLogFileName(script_name, raw_filename.checkjstring());
//                String xls_path = executor.getExcelFileName(xls_filename.checkjstring());
//                RssiExtractorLua extractor = new RssiExtractorLua(modem_type.checkjstring(), type);
//                extractor.extractRssi(executor.getActivity(), executor.getOffenderName(), raw_path, xls_path,
//                        offender_suffix.optjstring(null));
//            } catch (IOException e) {
//                throw new ScriptException(e.getMessage());
//            }
            return null;
        }
    }
}
