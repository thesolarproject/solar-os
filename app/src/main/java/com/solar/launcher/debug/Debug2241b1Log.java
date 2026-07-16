package com.solar.launcher.debug;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-15 — Session {@code 2241b1} NDJSON sink for Plex/JF save+playback debug.
 * Host workspace path + best-effort device files; never throws; never logs tokens.
 * Reversal: delete this class and {@code // #region agent log} callers.
 */
public final class Debug2241b1Log {
    private static final String SESSION = "2241b1";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-2241b1.log";
    public static volatile boolean ENABLED = false; // 2026-07-16 — off: sync disk/HTTP on wheel/key paths tanked Y1/Y2 scroll

    private Debug2241b1Log() {}

    /** Append one NDJSON line tagged with hypothesis + optional runId. */
    public static void log(String location, String message, String hypothesisId, String runId,
            JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            if (runId != null && runId.length() > 0) o.put("runId", runId);
            if (data != null) o.put("data", data);
            append(new File(HOST_PATH), o.toString());
        } catch (Exception ignored) {}
    }

    private static void append(File file, String line) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(file, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
