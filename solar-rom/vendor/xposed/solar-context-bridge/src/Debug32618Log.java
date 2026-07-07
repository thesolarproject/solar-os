package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 32618e — ResolverActivity / system:ui crash loop diagnosis.
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-32618e.ndjson
 */
final class Debug32618Log {

    private static final String SESSION = "32618e";
    private static final String FILE = "debug-32618e.ndjson";
    /** 2026-07-05 — off in production; no SD writes from system_server hooks. */
    private static final boolean ENABLED = false;

    private Debug32618Log() {}

    static void event(String location, String message, String hypothesisId, JSONObject data) {
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
            SolarContextBridge.log("DBG32618 " + line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
        } catch (Throwable ignored) {}
    }

    private static void append(File f, String line) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Throwable ignored) {}
    }
}
