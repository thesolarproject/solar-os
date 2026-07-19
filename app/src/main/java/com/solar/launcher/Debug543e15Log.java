package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 543e15 — blank/slow Solar + library/stem probes.
 * Writes NDJSON to SD for adb pull; also Logcat tag SolarBlank543e15.
 * 2026-07-19
 */
public final class Debug543e15Log {
    private static final String SESSION = "543e15";
    private static final String FILE = "debug-543e15.log";
    private static final String TAG = "SolarBlank543e15";
    public static final boolean ENABLED = false; // 2026-07-19 — off: multi-path FileWriter during library scan

    private Debug543e15Log() {}

    /**
     * Append one NDJSON line. Also mirrors to workspace path when present (host/debug).
     * 2026-07-19
     */
    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location != null ? location : "");
            o.put("message", message != null ? message : "");
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.e(TAG, line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
            append(new File("/storage/sdcard0/solar", FILE), line);
            append(new File("/sdcard/.solar", FILE), line);
            // Host workspace (only when debugging on a machine that mounts the tree).
            append(new File("/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor", FILE), line);
        } catch (Exception ignored) {}
        // #region agent log
        Debug391bb9Log.note("543e15", "H3", android.os.SystemClock.uptimeMillis() - t0);
        // #endregion
    }

    private static void append(File f, String line) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
