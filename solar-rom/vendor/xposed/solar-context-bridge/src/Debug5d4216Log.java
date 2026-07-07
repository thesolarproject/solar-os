package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 5d4216 — HOME resolver freeze loop on Y1 (SLBB993DB09F).
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-5d4216.log
 */
final class Debug5d4216Log {

    private static final String SESSION = "5d4216";
    private static final String FILE = "debug-5d4216.log";
    /** Off by default — resolver-loop tracing should not keep appending from Xposed hooks. */
    private static final boolean ENABLED = false;

    private Debug5d4216Log() {}

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
            SolarContextBridge.log("DBG5d4216 " + line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
        } catch (Throwable ignored) {}
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
