package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session e93bdb: JJ BACK-long modal + wheel handoff on Y1.
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-e93bdb.log
 */
public final class DebugE93bdbLog {

    private static final String TAG = "SolarDbgE93bdb";
    private static final String SESSION = "e93bdb";
    private static final String FILE = "debug-e93bdb.log";
    /** Off by default — sync file I/O on wheel handoff is too expensive for release-like device testing. */
    public static volatile boolean ENABLED = false;

    private DebugE93bdbLog() {}

    /** One NDJSON line — maps to hypothesisId for parallel hypothesis testing. */
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
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                append(new File(dir, FILE), line);
            }
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
