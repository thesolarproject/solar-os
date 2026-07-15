package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-14 — Debug session 8cf8b0: Jellyfin/Plex play fail while Navidrome works.
 * Pull: adb pull /storage/sdcard0/.solar/debug-8cf8b0.log .cursor/debug-8cf8b0.log
 * HTTP ingest via adb reverse tcp:7642 tcp:7642.
 */
public final class Debug8cf8b0Log {

    private static final String TAG = "SolarDbg8cf8b0";
    private static final String SESSION = "8cf8b0";
    private static final String FILE = "debug-8cf8b0.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";
    public static volatile boolean ENABLED = true;

    private Debug8cf8b0Log() {}

    /** 2026-07-14: One NDJSON line — logcat + app files + SD + optional HTTP. */
    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            // 2026-07-14: Family label so A5 vs Y1/Y2 is obvious in harvest.
            if (data == null) data = new JSONObject();
            try {
                data.put("deviceFamily", DeviceFeatures.deviceModel());
                data.put("isA5", DeviceFeatures.isA5());
            } catch (Exception ignored) {}
            o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            // 2026-07-14: Also mirror under /sdcard for non-root adb pull on A5.
            append(new File("/sdcard/.solar", FILE), line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                append(new File(dir, FILE), line);
            }
            postIngest(line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }

    private static void postIngest(final String line) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(INGEST).openConnection();
                    c.setConnectTimeout(800);
                    c.setReadTimeout(800);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    byte[] body = line.getBytes("UTF-8");
                    c.setFixedLengthStreamingMode(body.length);
                    OutputStream os = c.getOutputStream();
                    os.write(body);
                    os.close();
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "dbg8cf8b0").start();
    }
}
