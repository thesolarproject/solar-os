package com.solar.launcher.platform;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05 — Debug session 9972e0 NDJSON for platform prep loop diagnosis.
 * Pull: adb shell run-as com.solar.launcher cat files/debug-9972e0.log
 */
public final class PlatformPrepDebugLog {

    private static final String SESSION = "9972e0";
    private static final String FILE = "debug-9972e0.log";

    /** Off by default — prep is silent; file I/O during boot hurts cold start (2026-07-11). */
    public static volatile boolean ENABLED = false;

    private PlatformPrepDebugLog() {}

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
            String line = o.toString() + "\n";
            if (ctx != null) {
                File f = new File(ctx.getFilesDir(), FILE);
                FileWriter w = new FileWriter(f, true);
                w.write(line);
                w.close();
            }
        } catch (Exception ignored) {}
    }

    public static JSONObject gapArray(PlatformProbe.Report report) {
        JSONObject o = new JSONObject();
        try {
            JSONArray arr = new JSONArray();
            if (report != null && report.gaps != null) {
                for (PlatformProbe.Gap g : report.gaps) {
                    JSONObject row = new JSONObject();
                    row.put("id", g.id);
                    row.put("detail", g.detail);
                    arr.put(row);
                }
            }
            o.put("gaps", arr);
            o.put("count", report != null && report.gaps != null ? report.gaps.size() : 0);
        } catch (Exception ignored) {}
        return o;
    }
}
