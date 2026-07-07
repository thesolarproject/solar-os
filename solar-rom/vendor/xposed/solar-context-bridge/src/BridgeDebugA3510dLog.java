package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05 — Y2 UMS debug session a3510d (system_server + SystemUI hooks).
 * Pull: adb shell su -c 'cat /data/local/tmp/debug-a3510d.log'
 */
final class BridgeDebugA3510dLog {

    private static final String SESSION = "a3510d";
    private static final String FILE = "debug-a3510d.log";

    private BridgeDebugA3510dLog() {}

    /** NDJSON line for debug-mode hypothesis tracking. */
    static void log(String location, String message, String hypothesisId, JSONObject data) {
        // #region agent log
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            SolarContextBridge.log("a3510d " + location + " " + message);
            append(new File("/data/local/tmp", FILE), line);
            File sdDir = new File("/storage/sdcard1/.solar");
            if (!sdDir.exists()) sdDir.mkdirs();
            append(new File(sdDir, FILE), line);
        } catch (Throwable ignored) {}
        // #endregion
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Throwable ignored) {}
    }
}
