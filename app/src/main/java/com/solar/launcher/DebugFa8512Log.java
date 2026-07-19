package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-18 — Debug session fa8512: post-release scroll coast / "no means no".
 * Layman: tiny breadcrumbs proving why the list keeps walking after you let go.
 * Tech: NDJSON → logcat + SD .solar + HTTP ingest (adb reverse tcp:7652).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class DebugFa8512Log {

    private static final String TAG = "SolarDbgFa8512";
    private static final String SESSION = "fa8512";
    private static final String FILE = "debug-fa8512.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-fa8512.log";

    /** Keep on while debugging coast-after-release; flip false before ship. */
    public static final boolean ENABLED = false; // 2026-07-19 — off: sync disk/HTTP on wheel tanked Y1

    private DebugFa8512Log() {}

    /**
     * 2026-07-18 — Append one NDJSON breadcrumb for hypothesis {@code hypothesisId}.
     * Layman: writes what happened so we can see why scroll kept going.
     * Technical: never throws; async HTTP; sync file append best-effort.
     */
    public static void log(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
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
            Log.e(TAG, line);
            try {
                append(new File(HOST_PATH), line);
            } catch (Exception ignoredHost) {}
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            try {
                File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
                if (sdRoot != null) {
                    File dir = new File(sdRoot, ".solar");
                    if (!dir.exists()) dir.mkdirs();
                    append(new File(dir, FILE), line);
                }
            } catch (Exception ignoredSd) {}
            postIngest(line);
        } catch (Exception ignored) {}
        // #region agent log
        Debug391bb9Log.note("fa8512", "H2", android.os.SystemClock.uptimeMillis() - t0);
        // #endregion
    }

    private static void append(File f, String line) throws java.io.IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    private static void postIngest(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    URL u = new URL(INGEST);
                    c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    OutputStream os = new BufferedOutputStream(c.getOutputStream());
                    os.write(line.getBytes("UTF-8"));
                    os.flush();
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "dbg-fa8512").start();
    }
}
