package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 72b98f — overlay visible but keys reach underlying app.
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-72b98f.log (Y1) or sdcard1 (Y2).
 */
public final class DebugOverlayStuckLog {

    private static final String TAG = "SolarDbg72b98f";
    private static final String SESSION = "72b98f";
    private static final String FILE = "debug-72b98f.log";
    /** 2026-07-05 — flip off after stuck-overlay root cause is verified. */
    public static volatile boolean ENABLED = false;

    private DebugOverlayStuckLog() {}

    /** One NDJSON line — hypothesisId tags which failure mode we are testing. */
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
            String line = o.toString();
            Log.i(TAG, line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                append(new File(dir, FILE), line);
            }
        } catch (Exception ignored) {}
    }

    /** Snapshot sys.solar.overlay.* props for cross-process stuck-overlay diagnosis. */
    public static JSONObject overlayPropSnapshot() {
        JSONObject d = new JSONObject();
        try {
            d.put("active", readProp(OverlayKeyGate.ACTIVE_PROPERTY));
            d.put("ui", readProp(OverlayKeyGate.UI_PROPERTY));
            d.put("opening", readProp(OverlayKeyGate.OPENING_PROPERTY));
            d.put("handlerMain", OverlayKeyGate.hasHandlerForTest());
        } catch (Exception ignored) {}
        return d;
    }

    private static String readProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, "?");
            return v != null ? v.toString() : "?";
        } catch (Exception e) {
            return "err";
        }
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
