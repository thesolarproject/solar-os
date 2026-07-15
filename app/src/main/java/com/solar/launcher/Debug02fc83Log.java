package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * 2026-07-14 — Debug session 02fc83: USB prompt keep-alive / poll after show.
 * Layman: cheap logcat breadcrumbs — no SD writes (those froze Y1 during USB prompt).
 * Tech: logcat-only NDJSON; sync FileWriter/HTTP removed (GC_FOR_ALLOC ~1.7s on Y1).
 * Reversal: restore file+ingest write path from Debug210a10Log pattern if host pull needed.
 */
public final class Debug02fc83Log {

    private static final String TAG = "SolarDbg02fc83";
    private static final String SESSION = "02fc83";

    /** 2026-07-14 — On while verifying USB post-prompt idle; disable after confirmation. */
    public static volatile boolean ENABLED = false;

    private Debug02fc83Log() {}

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
            o.put("runId", "post-fix");
            if (data != null) o.put("data", data);
            // Logcat only — Y1 cannot afford sync SD/HTTP on USB_STATE storms.
            Log.e(TAG, o.toString());
        } catch (Exception ignored) {}
    }
}
