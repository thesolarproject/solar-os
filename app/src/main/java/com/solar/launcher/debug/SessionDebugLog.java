package com.solar.launcher.debug;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-15 — Session {@code 1ccb92} NDJSON debug sink (host workspace + device SD + logcat).
 * Layman: writes tiny JSON lines we can pull later to prove what the radio/web/music code did.
 * Reversal: delete this class and callers' {@code // #region agent log} blocks.
 */
public final class SessionDebugLog {
    private static final String TAG = "SolarDbg1ccb92";
    private static final String SESSION = "1ccb92";
    private static final String FILE = "debug-1ccb92.log";
    /** Off by default — NDJSON on radio/web paths still allocates when true. */
    public static final boolean ENABLED = false;

    private SessionDebugLog() {}

    /**
     * Append one NDJSON line for hypothesis {@code hypothesisId}.
     * Never throws; never logs secrets.
     */
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
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            appendFile(new File(
                    "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor", FILE), line);
            if (ctx != null) {
                try {
                    appendFile(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            appendFile(new File("/storage/sdcard0", FILE), line);
            appendFile(new File("/storage/sdcard1", FILE), line);
            try {
                File primary = com.solar.launcher.DeviceFeatures.getPrimaryStorageRoot();
                if (primary != null) {
                    appendFile(new File(primary, FILE), line);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private static void appendFile(File file, String line) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(file, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
