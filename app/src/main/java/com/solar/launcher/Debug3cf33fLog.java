package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05 — Debug session 3cf33f NDJSON logger (Y2 OK-hold + NP volume hypotheses).
 * Writes to device .solar/; pull with adb after repro on Y2 hardware.
 */
public final class Debug3cf33fLog {
    private static final String TAG = "SolarDbg3cf33f";
    private static final String FILE = "debug-3cf33f.log";
    private static final String SESSION = "3cf33f";
    public static volatile boolean ENABLED = false;

    private Debug3cf33fLog() {}

    /** Append one NDJSON line tagged with hypothesis id for debug-mode analysis. */
    public static void log(Context ctx, String location, String message, String hypothesisId, JSONObject data) {
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
            writeLine(DeviceFeatures.getPrimaryStorageRoot(), line);
            if (ctx != null) {
                writeLine(new File(ctx.getFilesDir(), FILE), line);
            }
        } catch (Exception ignored) {}
    }

    private static void writeLine(File root, String line) {
        if (root == null) return;
        try {
            File dir = root.isDirectory() ? new File(root, ".solar") : root.getParentFile();
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            File out = root.isDirectory() ? new File(dir, FILE) : root;
            FileWriter w = new FileWriter(out, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
