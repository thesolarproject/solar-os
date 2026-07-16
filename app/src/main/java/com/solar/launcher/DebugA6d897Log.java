package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-14 — Debug session a6d897: full-width Get Themes download confirm.
 * Layman: writes what happens when Download confirm shows but won't activate.
 * Tech: NDJSON to SD + filesDir; pull into .cursor/debug-a6d897.log.
 * Reversal: delete class and #region agent log sites after fix verified.
 */
public final class DebugA6d897Log {

    private static final String TAG = "SolarDebugA6d897";
    private static final String SESSION = "a6d897";
    private static final String FILE = "debug-a6d897.log";

    /** 2026-07-14 — On for theme interstitial debug; off after confirmed fix. */
    public static volatile boolean ENABLED = false; // 2026-07-16 — off: sync disk/HTTP on wheel/key paths tanked Y1/Y2 scroll

    private DebugA6d897Log() {}

    /** Append one NDJSON sample for hypothesis checks. */
    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("runId", "pre-fix");
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            File primary = DeviceFeatures.getPrimaryStorageRoot();
            if (primary != null) {
                append(new File(primary, ".solar/" + FILE), line);
            }
            append(new File("/storage/sdcard0/solar", FILE), line);
            append(new File("/storage/sdcard1/solar", FILE), line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
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
