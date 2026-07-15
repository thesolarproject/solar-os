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
 * 2026-07-14 — Debug session 391149: A5 crash/freeze investigation.
 * Layman: writes short traces so we can see freeze cause on hardware.
 * Tech: NDJSON to filesDir + optional HTTP ingest (adb reverse tcp:7642).
 * Reversal: delete class and #region agent log call sites after fix confirmed.
 */
public final class Debug391149Log {

    private static final String TAG = "SolarDbg391149";
    private static final String SESSION = "391149";
    private static final String FILE = "debug-391149.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";

    /** 2026-07-14 — On while hunting A5 freeze; disable after confirmation. */
    public static volatile boolean ENABLED = true;

    private Debug391149Log() {}

    /** Append one NDJSON sample (hypothesisId tags which freeze theory). */
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
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.e(TAG, line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            postIngest(line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) throws java.io.IOException {
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    /** Best-effort POST when host forwarded ingest via adb reverse. */
    private static void postIngest(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(INGEST).openConnection();
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    byte[] body = line.getBytes("UTF-8");
                    c.setFixedLengthStreamingMode(body.length);
                    OutputStream os = new BufferedOutputStream(c.getOutputStream());
                    os.write(body);
                    os.flush();
                    os.close();
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "dbg391149").start();
    }
}
