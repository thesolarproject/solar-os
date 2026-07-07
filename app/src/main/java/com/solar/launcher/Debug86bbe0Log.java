package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session 86bbe0: home-page + USB modal hang investigation on Y1.
 * Layman: writes timing samples to SD so adb pull can prove what is starving the UI.
 * Technical: NDJSON to /storage/sdcard0/solar/debug-86bbe0.log + logcat SolarDebug86bbe0.
 * Reversal: delete class and #region agent log call sites when hang is fixed.
 */
public final class Debug86bbe0Log {

    private static final String TAG = "SolarDebug86bbe0";
    private static final String SESSION = "86bbe0";
    private static final String FILE = "debug-86bbe0.log";

    /** Off by default — sync file I/O is too expensive for wheel/home perf verification. */
    public static volatile boolean ENABLED = false;

    private Debug86bbe0Log() {}

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
            append(new File("/storage/sdcard0/solar", FILE), line);
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
