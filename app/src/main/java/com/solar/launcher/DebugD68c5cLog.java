package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session d68c5c: Rockbox restart loop on Y1 SL8212ED5C48.
 * Layman: leaves a trail on SD when something kills or relaunches Rockbox.
 * Technical: NDJSON to /storage/sdcard0/solar/debug-d68c5c.log; adb pull after repro.
 * Reversal: delete class when triage complete.
 */
public final class DebugD68c5cLog {

    private static final String TAG = "SolarDbgD68c5c";
    private static final String SESSION = "d68c5c";
    private static final String FILE = "debug-d68c5c.log";
    /** Off by default — restart-loop tracing should not add file I/O to normal launcher flow. */
    public static volatile boolean ENABLED = false;

    private DebugD68c5cLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
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
            Log.i(TAG, line);
            File dir = new File("/storage/sdcard0/solar");
            if (!dir.exists()) dir.mkdirs();
            append(new File(dir, FILE), line);
            try {
                File appDir = new File("/data/data/com.solar.launcher/files");
                if (!appDir.exists()) appDir.mkdirs();
                append(new File(appDir, FILE), line);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
