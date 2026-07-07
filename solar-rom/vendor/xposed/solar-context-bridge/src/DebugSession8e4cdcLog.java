package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-07 — Debug session 8e4cdc NDJSON log (device + host pull).
 * Layman: trace overlay/ANR/toast routing during hardware repro.
 * Technical: append one JSON line per event; adb pull from SD or workspace path.
 */
final class DebugSession8e4cdcLog {

    private static final String SESSION = "8e4cdc";
    /** Device path — adb pull to .cursor/debug-8e4cdc.log on host. */
    private static final String[] PATHS = {
            "/storage/sdcard0/solar/debug-8e4cdc.log",
            "/storage/sdcard1/solar/debug-8e4cdc.log"
    };

    private DebugSession8e4cdcLog() {}

    static void event(String location, String message, String hypothesisId, JSONObject data) {
        try {
            JSONObject row = new JSONObject();
            row.put("sessionId", SESSION);
            row.put("timestamp", System.currentTimeMillis());
            row.put("location", location);
            row.put("message", message);
            row.put("hypothesisId", hypothesisId);
            if (data != null) row.put("data", data);
            String line = row.toString() + "\n";
            for (int i = 0; i < PATHS.length; i++) {
                try {
                    File f = new File(PATHS[i]);
                    File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    FileWriter w = new FileWriter(f, true);
                    w.write(line);
                    w.close();
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
