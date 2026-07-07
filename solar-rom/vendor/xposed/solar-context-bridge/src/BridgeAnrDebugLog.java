package com.solar.launcher.xposed.bridge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session edc27b — slow-path timing in system_server Xposed hooks (ANR diagnosis).
 * Pull: adb shell cat /storage/sdcard0/.solar/debug-edc27b.log
 */
final class BridgeAnrDebugLog {

    private static final String SESSION = "edc27b";
    private static final String FILE = "debug-edc27b.log";
    /** 2026-07-05 — off in production; sync SD I/O on PWM thread ANRs system_server. */
    private static final boolean ENABLED = false;
    /** Log PWM hook work taking longer than this — sub-threshold is normal noise. */
    private static final long SLOW_MS = 8L;
    /** Burst detector — many hook hits in one window suggests input-thread overload. */
    private static final int STORM_THRESHOLD = 40;
    private static final long STORM_WINDOW_MS = 500L;

    private static volatile long stormWindowStart;
    private static volatile int stormCount;
    private static volatile long lastStormLogMs;

    private BridgeAnrDebugLog() {}

    /** Record hook duration; logs only slow paths or storm bursts. */
    static void hookTiming(String location, String hypothesisId, long startNs, JSONObject data) {
        if (!ENABLED) return;
        long ms = (System.nanoTime() - startNs) / 1_000_000L;
        noteStorm(location, hypothesisId, ms, data);
        if (ms < SLOW_MS) return;
        try {
            if (data == null) data = new JSONObject();
            data.put("durationMs", ms);
            write(location, "slow hook", hypothesisId, data);
        } catch (Throwable ignored) {}
    }

    /** Unconditional branch marker for rare paths (USB onCreate, GlobalActions). */
    static void event(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        write(location, message, hypothesisId, data);
    }

    private static void noteStorm(String location, String hypothesisId, long ms, JSONObject data) {
        long now = System.currentTimeMillis();
        if (now - stormWindowStart > STORM_WINDOW_MS) {
            stormWindowStart = now;
            stormCount = 0;
        }
        stormCount++;
        if (stormCount < STORM_THRESHOLD) return;
        if (now - lastStormLogMs < STORM_WINDOW_MS) return;
        lastStormLogMs = now;
        try {
            JSONObject d = data != null ? data : new JSONObject();
            d.put("stormCount", stormCount);
            d.put("lastDurationMs", ms);
            d.put("windowMs", STORM_WINDOW_MS);
            write(location, "hook storm", hypothesisId, d);
        } catch (Throwable ignored) {}
    }

    private static void write(String location, String message, String hypothesisId, JSONObject data) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            SolarContextBridge.log("ANRDBG " + line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
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
