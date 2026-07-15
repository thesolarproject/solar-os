package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug-mode NDJSON logger — session a4dee8.
 * Writes to SD + app files; pull to .cursor/debug-a4dee8.log on host.
 */
public final class DebugSessionLog {
    private static final String TAG = "SolarDbga4dee8";
    private static final String SESSION = "a4dee8";
    private static final String FILE = "debug-a4dee8.log";
    /** 2026-07-05 — Off in release; hot-path sync SD I/O only in debug builds. */
    public static volatile boolean ENABLED = false;

    private DebugSessionLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    /** Root / Rockbox switch diagnostics — same ENABLED gate as log() (2026-07-05). */
    public static void logAlways(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    private static void emit(String location, String message, String hypothesisId, JSONObject data) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            File dir = new File("/storage/sdcard0/solar");
            if (!dir.exists()) dir.mkdirs();
            try {
                FileWriter w = new FileWriter(new File(dir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored2) {}
            try {
                File appDir = new File("/data/data/com.solar.launcher/files");
                if (!appDir.exists()) appDir.mkdirs();
                FileWriter w = new FileWriter(new File(appDir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored3) {}
        } catch (Exception ignored) {}
    }
}
