package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06 — Debug session afe4ef: home menu wheel perf (preview cache / row styling).
 * Layman: writes timing lines to SD .solar for adb pull into .cursor/debug-afe4ef.log.
 * Technical: NDJSON; hypothesisId H1–H4 map to menu preview hypotheses.
 */
public final class DebugAfe4efLog {

    private static final String TAG = "SolarDbgAfe4ef";
    private static final String SESSION = "afe4ef";
    private static final String FILE = "debug-afe4ef.log";

    /** Off by default — home-menu perf logging should not distort wheel responsiveness. */
    public static volatile boolean ENABLED = false;

    private DebugAfe4efLog() {}

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data, String runId) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (runId != null) o.put("runId", runId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            appendHostPaths(line);
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

    private static void appendHostPaths(String line) {
        File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
        if (sdRoot != null) {
            try {
                File dir = new File(sdRoot, ".solar");
                if (!dir.exists()) dir.mkdirs();
                FileWriter w = new FileWriter(new File(dir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            } catch (Exception ignored) {}
        }
    }
}
