package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session b85099: Y1 wheel scroll latency + YouTube list bind cost.
 * Pull: adb pull /storage/sdcard0/.solar/debug-b85099.log .cursor/debug-b85099.log
 */
public final class DebugB85099Log {

    private static final String TAG = "SolarDbgB85099";
    private static final String SESSION = "b85099";
    private static final String FILE = "debug-b85099.log";
    /** Off by default — sync SD I/O on every wheel key blocks the UI thread. */
    public static volatile boolean ENABLED = false;

    private DebugB85099Log() {}

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
