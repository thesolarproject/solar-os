package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 9f659f — queue scroll vs transport / overlay vs handoff collisions.
 * Pull: adb shell cat /storage/sdcard1/.solar/debug-9f659f.log (Y2) or sdcard0 (Y1).
 */
public final class DebugInputLog {

    private static final String TAG = "SolarDbg46a569";
    private static final String SESSION = "46a569";
    private static final String FILE = "debug-46a569.log";
    /** Active for this debug session only — disable after verification. */
    public static volatile boolean ENABLED = false;

    private DebugInputLog() {}

    /** One NDJSON line tagged with hypothesisId for parallel hypothesis testing. */
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
