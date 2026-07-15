package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-14 — Debug session b4208e: A5 portrait aspect-ratio investigation.
 * Layman: samples device family + screen size so we can see why UI stays wide.
 * Tech: NDJSON to SD + filesDir; pull into .cursor/debug-b4208e.log.
 * Reversal: delete class and #region agent log call sites when portrait is fixed.
 */
public final class DebugB4208eLog {

    private static final String TAG = "SolarDebugB4208e";
    private static final String SESSION = "b4208e";
    private static final String FILE = "debug-b4208e.log";

    /** 2026-07-14 — On for A5 portrait debug; turn off after confirmed fix. */
    public static volatile boolean ENABLED = true;

    private DebugB4208eLog() {}

    /** Append one NDJSON sample (family / orientation / display). */
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
            append(new File("/storage/sdcard0/solar", FILE), line);
            append(new File("/storage/sdcard1/solar", FILE), line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
