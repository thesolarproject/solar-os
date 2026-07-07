package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 2d4745 — global overlay dim without themed menu panel (Y1/Y2 third-party apps).
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-2d4745.log (Y1) or sdcard1 (Y2).
 */
public final class Debug2d4745Log {

    private static final String TAG = "SolarDbg2d4745";
    private static final String SESSION = "2d4745";
    private static final String FILE = "debug-2d4745.log";
    /** 2026-07-05 — on only while diagnosing dim-only overlay shell. */
    public static volatile boolean ENABLED = false;

    private Debug2d4745Log() {}

    /** One NDJSON line — hypothesisId tags which failure mode we are testing. */
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
