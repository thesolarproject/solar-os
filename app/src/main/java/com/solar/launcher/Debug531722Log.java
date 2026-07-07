package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session 531722: notPipe PM gap + input latency on wheel paths.
 * Pull: adb shell run-as com.solar.launcher cat files/debug-531722.log
 */
public final class Debug531722Log {

    private static final String TAG = "SolarDbg531722";
    private static final String SESSION = "531722";
    private static final String FILE = "debug-531722.log";
    /** Session 531722 — flip false after verification. */
    public static volatile boolean ENABLED = false;

    private Debug531722Log() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
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
