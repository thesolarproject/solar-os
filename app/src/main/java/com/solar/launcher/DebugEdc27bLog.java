package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session edc27b — global overlay key routing when WM is NOT_FOCUSABLE.
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-edc27b.log
 */
public final class DebugEdc27bLog {

    private static final String TAG = "SolarDbgEdc27b";
    private static final String SESSION = "edc27b";
    private static final String FILE = "debug-edc27b.log";
    public static volatile boolean ENABLED = false;

    private DebugEdc27bLog() {}

    /** One NDJSON line for overlay key-path debugging. */
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
