package com.solar.launcher;

import org.json.JSONObject;

import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * 2026-07-14 — Debug session c54726 host-side NDJSON (policy / Solar-only modal).
 * Layman: unit tests drop proof lines into the Cursor debug log file.
 * Reversal: delete after session closes.
 */
final class DebugC54726Log {

    private static final String PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-c54726.log";

    private DebugC54726Log() {}

    /** Append one NDJSON line for hypothesis evidence. */
    static void log(String location, String message, String hypothesisId, JSONObject data) {
        // #region agent log
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", "c54726");
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("data", data != null ? data : new JSONObject());
            PrintWriter w = new PrintWriter(new FileWriter(PATH, true));
            w.println(o.toString());
            w.close();
        } catch (Throwable ignored) {}
        // #endregion
    }
}
