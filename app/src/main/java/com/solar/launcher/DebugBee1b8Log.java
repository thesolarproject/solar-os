package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-06 — Debug session bee1b8: hold-timing + perf samples for Y1/Y2 embedded targets.
 * Layman: records when menus open and how hard the chip is working — for adb pull or ingest.
 * Technical: NDJSON to SD + optional POST via {@code adb reverse tcp:7642 tcp:7642}.
 * Reversal: set ENABLED false after verified; delete class.
 */
public final class DebugBee1b8Log {

    private static final String TAG = "SolarDbgBee1b8";
    private static final String SESSION = "bee1b8";
    private static final String FILE = "debug-bee1b8.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";

    /** Off by default — async ingest/file writes should not run during wheel perf verification. */
    public static volatile boolean ENABLED = false;

    private DebugBee1b8Log() {}

    /** One NDJSON line — hold schedule, modal open, BACK UP passthrough, perf tick. */
    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            final String line = o.toString();
            Log.i(TAG, line);
            appendSd(line);
            postIngestAsync(line);
        } catch (Exception ignored) {}
    }

    private static void appendSd(String line) {
        try {
            File root = DeviceFeatures.getPrimaryStorageRoot();
            if (root == null) return;
            File dir = new File(root, ".solar");
            if (!dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(new File(dir, FILE), true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }

    private static void postIngestAsync(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(INGEST);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("X-Debug-Session-Id", SESSION);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(800);
                    conn.setReadTimeout(800);
                    conn.getOutputStream().write(line.getBytes("UTF-8"));
                    conn.getInputStream().close();
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "DbgBee1b8Ingest").start();
    }
}
