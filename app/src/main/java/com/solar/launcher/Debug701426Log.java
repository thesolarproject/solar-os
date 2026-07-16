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
 * 2026-07-15 — Debug session 701426: Get Music / Reach results focus jumps on live refresh.
 * Layman: writes tiny JSON crumbs so we can see if highlight dies when new songs appear.
 * Tech: NDJSON to filesDir + SD .solar + HTTP ingest (adb reverse tcp:7642).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug701426Log {

    private static final String TAG = "SolarDbg701426";
    private static final String SESSION = "701426";
    private static final String FILE = "debug-701426.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-701426.log";

    /** 2026-07-15 — On while hunting results-list focus; disable after confirmation. */
    public static volatile boolean ENABLED = false; // 2026-07-16 — off: sync disk/HTTP on wheel/key paths tanked Y1/Y2 scroll

    private Debug701426Log() {}

    /** Append one NDJSON sample (hypothesisId tags which focus theory). */
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
        }, "dbg701426").start();
    }
}
