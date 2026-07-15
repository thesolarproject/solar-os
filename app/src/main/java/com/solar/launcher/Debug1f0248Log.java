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
 * 2026-07-15 — Debug session 1f0248: portrait keyboard delete vs dismiss.
 * Layman: records which side key gets rewritten upright while typing.
 * Tech: NDJSON to filesDir/SD + HTTP ingest (adb reverse tcp:7642).
 * Reversal: delete class and #region agent log call sites after fix confirmed.
 */
public final class Debug1f0248Log {

    private static final String TAG = "SolarDbg1f0248";
    private static final String SESSION = "1f0248";
    private static final String FILE = "debug-1f0248.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";

    /** 2026-07-15 — On while hunting portrait keyboard delete; disable after confirmation. */
    public static volatile boolean ENABLED = true;

    private Debug1f0248Log() {}

    /** Append one NDJSON sample (hypothesisId tags theory under test). */
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
            File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
            if (sdRoot != null) {
                try {
                    File dir = new File(sdRoot, ".solar");
                    if (!dir.exists()) dir.mkdirs();
                    append(new File(dir, FILE), line);
                } catch (Exception ignored) {}
            }
            // Host workspace via adb reverse when present.
            postIngest(line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) throws java.io.IOException {
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    private static void postIngest(String line) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(INGEST);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(400);
            conn.setReadTimeout(400);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Debug-Session-Id", SESSION);
            OutputStream os = new BufferedOutputStream(conn.getOutputStream());
            os.write(line.getBytes("UTF-8"));
            os.flush();
            os.close();
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
