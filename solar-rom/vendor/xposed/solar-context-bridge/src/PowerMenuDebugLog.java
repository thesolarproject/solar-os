package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 4881df — power-menu / BACK-long / GlobalActions hook tracing.
 * Pull: adb shell cat /storage/sdcard1/.solar/debug-4881df.log (Y2)
 *       adb shell cat /storage/sdcard0/.solar/debug-4881df.log (Y1)
 */
final class PowerMenuDebugLog {

    private static final String SESSION = "e93bdb";
    /** Off by default — system_server power/BACK tracing should not add file I/O on live input paths. */
    private static final boolean ENABLED = false;

    private PowerMenuDebugLog() {}

    /** Unconditional trace for power-menu hypothesis testing. */
    static void event(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            SolarContextBridge.log("PMDBG " + line);
            appendBoth(new File("/storage/sdcard0/.solar/debug-e93bdb.log"), line);
            appendBoth(new File("/storage/sdcard1/.solar/debug-e93bdb.log"), line);
        } catch (Throwable ignored) {}
    }

    private static void appendBoth(File f, String line) {
        append(f, line);
    }

    private static void append(File f, String line) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Throwable ignored) {}
    }
}
