package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05 — Debug session 9870a6: unexpected Solar IME in Settings Apps + ghost overlay.
 * Layman: writes a trace file we pull after reproducing the bug on hardware.
 * Technical: NDJSON to SD .solar/ and app filesDir; tag SolarDbg9870a6 for logcat.
 */
public final class DebugImeLog {
    private static final String TAG = "SolarDbg9870a6";
    private static final String FILE = "debug-9870a6.log";
    private static final String SESSION = "9870a6";
    /** 2026-07-05 — Off in release; flip true manually for short adb debug sessions. */
    public static volatile boolean ENABLED = false;

    private DebugImeLog() {}

    /** Append one NDJSON line — hypothesisId tags which theory we're testing. */
    public static void log(Context ctx, String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            long ts = System.currentTimeMillis();
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", ts);
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
            if (sdRoot != null) {
                try {
                    File dir = new File(sdRoot, ".solar");
                    if (!dir.exists()) dir.mkdirs();
                    append(new File(dir, FILE), line);
                } catch (Exception ignored2) {}
            }
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored3) {}
            }
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) throws java.io.IOException {
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    /** Root daemon has no Context — SD + package files path only. */
    public static void logRoot(String location, String message, String hypothesisId, JSONObject data) {
        log(null, location, message, hypothesisId, data);
    }

    /** Decode EditorInfo inputType class/variation for hypothesis H1/H2 logging. */
    public static JSONObject inputTypeFields(int inputType) throws org.json.JSONException {
        JSONObject o = new JSONObject();
        int cls = inputType & android.view.inputmethod.EditorInfo.TYPE_MASK_CLASS;
        o.put("raw", inputType);
        o.put("classBits", cls);
        o.put("isText", cls == android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT);
        o.put("isNumber", cls == android.view.inputmethod.EditorInfo.TYPE_CLASS_NUMBER);
        o.put("isPhone", cls == android.view.inputmethod.EditorInfo.TYPE_CLASS_PHONE);
        o.put("isDatetime", cls == android.view.inputmethod.EditorInfo.TYPE_CLASS_DATETIME);
        o.put("isNull", cls == 0);
        return o;
    }
}
