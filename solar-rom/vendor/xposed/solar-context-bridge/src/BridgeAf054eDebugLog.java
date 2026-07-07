package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — af054e USB concierge instrumentation from SystemUI Xposed hooks.
 * Pull: adb shell su -c 'cat /data/local/tmp/debug-af054e.log'
 */
final class BridgeAf054eDebugLog {

    private static final String SESSION = "af054e";
    private static final String FILE = "debug-af054e.log";
    /** Off after af054e verification — sync file I/O on SystemUI hooks stalls input. */
    private static final boolean ENABLED = false;

    private BridgeAf054eDebugLog() {}

    static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("runId", "xposed");
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            append(new File("/data/local/tmp", FILE), o.toString());
        } catch (Throwable ignored) {}
    }

    private static void append(File file, String line) {
        FileWriter w = null;
        try {
            w = new FileWriter(file, true);
            w.write(line);
            w.write('\n');
        } catch (Throwable ignored) {
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignored) {}
            }
        }
    }
}
