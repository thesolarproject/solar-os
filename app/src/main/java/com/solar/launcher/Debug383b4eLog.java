package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-11 — Debug session 383b4e boot/scan hang NDJSON (always-on for this session).
 * Pull: adb pull /storage/sdcard1/.solar/debug-383b4e.log (Y2) or sdcard0 (Y1).
 * Reversal: delete this class + call sites after verified fix.
 */
public final class Debug383b4eLog {
    private static final String TAG = "SolarDbg383b4e";
    private static final String FILE = "debug-383b4e.log";
    private static final String SESSION = "383b4e";
    /** 2026-07-11 — Force on while diagnosing "app not starting". */
    public static volatile boolean ENABLED = true;

    private Debug383b4eLog() {}

    /** Append one NDJSON line to logcat + SD + app files. */
    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("runId", "boot-pre");
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            append(new File("/storage/sdcard1/.solar"), line);
            append(new File("/storage/sdcard0/.solar"), line);
            File root = DeviceFeatures.getPrimaryStorageRoot();
            if (root != null) append(new File(root, ".solar"), line);
            if (ctx != null) {
                try {
                    FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE), true);
                    w.write(line);
                    w.write('\n');
                    w.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void append(File dir, String line) {
        try {
            if (!dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(new File(dir, FILE), true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
