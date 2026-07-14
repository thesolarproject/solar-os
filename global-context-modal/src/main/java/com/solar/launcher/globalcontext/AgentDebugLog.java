package com.solar.launcher.globalcontext;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * 2026-07-08 — Session debug ingest for overlay spawn latency (host via adb reverse).
 * Layman: quietly phones home timing crumbs so we can see where menus stall.
 * Technical: fire-and-forget HTTP POST NDJSON; also append /sdcard + Log.i for API17.
 * Reversal: delete this class + call sites once spawn lag is fixed.
 */
public final class AgentDebugLog {

    private static final String TAG = "AgentDebugOverlay";
    private static final String ENDPOINT =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";
    private static final String SESSION = "bfdc46";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Object FILE_LOCK = new Object();

    /**
     * Off by default — HTTP + file + Log.i on every overlay paint causes observer-effect lag
     * under adb logcat (2026-07-11). Flip true only for focused spawn-latency debug.
     */
    public static volatile boolean ENABLED = false;

    private AgentDebugLog() {}

    /** Emit one NDJSON debug line; never throws into callers. */
    public static void log(final String hypothesisId, final String location,
            final String message, final String dataJson) {
        if (!ENABLED) return;
        final long ts = System.currentTimeMillis();
        final long up = SystemClock.uptimeMillis();
        final String payload = "{\"sessionId\":\"" + SESSION
                + "\",\"hypothesisId\":\"" + esc(hypothesisId)
                + "\",\"location\":\"" + esc(location)
                + "\",\"message\":\"" + esc(message)
                + "\",\"data\":" + (dataJson != null ? dataJson : "{}")
                + ",\"timestamp\":" + ts
                + ",\"uptimeMs\":" + up + "}";
        Log.i(TAG, hypothesisId + " " + location + " " + message + " " + dataJson);
        appendSdcard(payload);
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(ENDPOINT);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(400);
                    conn.setReadTimeout(400);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("X-Debug-Session-Id", SESSION);
                    byte[] body = payload.getBytes(UTF8);
                    OutputStream os = conn.getOutputStream();
                    os.write(body);
                    os.close();
                    conn.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) {
                        try {
                            conn.disconnect();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, "agent-debug").start();
    }

    /** API17-safe host pull path — /sdcard/debug-bfdc46.ndjson. */
    private static void appendSdcard(String payload) {
        synchronized (FILE_LOCK) {
            FileOutputStream fos = null;
            try {
                File f = new File(Environment.getExternalStorageDirectory(),
                        "debug-bfdc46.ndjson");
                fos = new FileOutputStream(f, true);
                fos.write(payload.getBytes(UTF8));
                fos.write('\n');
                fos.flush();
            } catch (Exception ignored) {
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
