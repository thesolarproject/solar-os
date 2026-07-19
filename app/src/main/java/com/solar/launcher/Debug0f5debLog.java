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
 * 2026-07-18 — Debug session 0f5deb: YouTube NP title, modal anim, USB lock vs charge-loss.
 * Layman: tiny breadcrumbs for song name, menu pop, and "stuck USB storage" after unplug.
 * Tech: NDJSON to filesDir + SD .solar + HTTP ingest (adb reverse tcp:7652).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug0f5debLog {

    private static final String TAG = "SolarDbg0f5deb";
    private static final String SESSION = "0f5deb";
    private static final String FILE = "debug-0f5deb.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-0f5deb.log";

    /** Off in shipping builds — callers must gate JSONObject alloc on this (compile-time final). */
    public static final boolean ENABLED = false;

    private Debug0f5debLog() {}

    /**
     * 2026-07-18 — Always-on sparse probe for schedule crawl (does not flip {@link #ENABLED}).
     * Layman: a few timed breadcrumbs so we can see if updates fire faster than 80ms.
     * Technical: same NDJSON sink; sample-rate limited inside callers.
     * 2026-07-19 — Gated on ENABLED (was always writing disk/HTTP → wheel/scan jank).
     */
    public static void probe(String location, String message, String hypothesisId,
            JSONObject data) {
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
            o.put("runId", "sched-post-rev");
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.e(TAG, line);
            try {
                append(new File(HOST_PATH), line);
            } catch (Exception ignoredHost) {}
            // Device SD fallback when HOST_PATH is host-only (adb pull /.solar/).
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
        Debug391bb9Log.note("0f5deb", "H1", android.os.SystemClock.uptimeMillis() - t0);
        // #endregion
    }

    public static void log(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
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
            Log.e(TAG, line);
            try {
                append(new File(HOST_PATH), line);
            } catch (Exception ignoredHost) {}
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
            if (sdRoot != null) {
                try {
                    File dir = new File(sdRoot, ".solar");
                    if (!dir.exists()) dir.mkdirs();
                    append(new File(dir, FILE), line);
                } catch (Exception ignored2) {}
            }
            postIngest(line);
        } catch (Exception ignored) {}
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
        }, "dbg-0f5deb").start();
    }
}
