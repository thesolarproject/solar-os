package com.solar.launcher;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;

/**
 * 2026-07-16 — Y1 (~512 MB) memory-pressure gate for heavy background work.
 * Layman: when free RAM is tight, Solar pauses expensive scans and art baking
 * so the UI and music keep working instead of thrashing into restarts.
 * Technical: {@link ActivityManager.MemoryInfo} + optional {@code /proc/meminfo}
 * MemFree; {@link #onSystemTrim} from Application callbacks. Pure thresholds —
 * no profile framework. Reversal: always return false from isPressured / shouldDefer.
 */
public final class LowMemoryGate {
    /** availMem / MemFree below this → pressured (Y1 safe floor). */
    public static final long AVAIL_FLOOR_BYTES = 48L * 1024L * 1024L;
    /** MemFree alone under this is also pressure (page cache may still look "cached"). */
    public static final long MEMFREE_FLOOR_BYTES = 32L * 1024L * 1024L;
    /** Defer heavy work this long when pressured (then re-check). */
    public static final long DEFER_MS = 12_000L;

    private static volatile int lastTrimLevel = -1;
    private static volatile long pressureGen;
    private static volatile Boolean lastPressuredLogged;
    private static volatile long lastProcessStartMs;
    private static volatile Context appContext;

    private LowMemoryGate() {}

    /** Call once from Application.onCreate so context-less callers can sample MemoryInfo. */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** Bump generation from {@link android.app.Application#onTrimMemory}. */
    public static void onSystemTrim(int level) {
        lastTrimLevel = level;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            pressureGen++;
        }
    }

    public static void onLowMemory() {
        lastTrimLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
        pressureGen++;
    }

    public static long pressureGeneration() {
        return pressureGen;
    }

    public static int lastTrimLevel() {
        return lastTrimLevel;
    }

    /**
     * True when system reports low memory or free RAM is under Y1-safe floors.
     * Null-safe: false when context/services unavailable.
     */
    public static boolean isPressured(Context context) {
        return evaluate(context != null ? context : appContext).pressured;
    }

    /**
     * Defer heavy background work when RAM is tight (callers reschedule).
     * Input-busy deferral stays on {@link InputPriorityGate} — combine at call site.
     * 2026-07-18 — Also defer while Stem Player mixes (exclusive session).
     */
    public static boolean shouldDeferHeavyWork(Context context) {
        // 2026-07-19 — Stem or Mix exclusive jam owns the CPU.
        if (StemOrMixSession.isActive()) return true;
        if (com.solar.launcher.stem.StemPlayerHost.isSessionActive()) return true;
        return isPressured(context != null ? context : appContext);
    }

    /** Snapshot for logs / unit-style pure checks. */
    public static Snapshot evaluate(Context context) {
        Context ctx = context != null ? context : appContext;
        Snapshot s = new Snapshot();
        s.trimLevel = lastTrimLevel;
        if (lastTrimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            s.pressured = true;
            s.reason = "trim=" + lastTrimLevel;
        }
        if (ctx != null) {
            try {
                ActivityManager am = (ActivityManager) ctx.getApplicationContext()
                        .getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    s.availMem = mi.availMem;
                    s.threshold = mi.threshold;
                    s.systemLowMemory = mi.lowMemory;
                    if (mi.lowMemory) {
                        s.pressured = true;
                        s.reason = "lowMemory_flag";
                    } else if (mi.availMem > 0L && mi.availMem < AVAIL_FLOOR_BYTES) {
                        s.pressured = true;
                        s.reason = "availMem=" + mi.availMem;
                    }
                }
            } catch (Throwable ignored) {}
        }
        long memFree = readMemFreeBytes();
        s.memFree = memFree;
        if (memFree > 0L && memFree < MEMFREE_FLOOR_BYTES) {
            s.pressured = true;
            if (s.reason == null) s.reason = "memFree=" + memFree;
        }
        maybeLogFlip(s.pressured, s.reason);
        return s;
    }

    /**
     * Pure threshold helper for tests — no Context.
     * @param availMem ActivityManager availMem (0 = unknown)
     * @param memFreeBytes /proc MemFree (0 = unknown)
     * @param systemLowMemory MemoryInfo.lowMemory
     * @param trimLevel last ComponentCallbacks2 level (-1 = none)
     */
    public static boolean isPressuredSnapshot(long availMem, long memFreeBytes,
            boolean systemLowMemory, int trimLevel) {
        if (systemLowMemory) return true;
        if (trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            return true;
        }
        if (availMem > 0L && availMem < AVAIL_FLOOR_BYTES) return true;
        if (memFreeBytes > 0L && memFreeBytes < MEMFREE_FLOOR_BYTES) return true;
        return false;
    }

    /** Process-start thrash: second start within 10s → log once. */
    public static void noteProcessStart() {
        long now = System.currentTimeMillis();
        long prev = lastProcessStartMs;
        lastProcessStartMs = now;
        if (prev > 0L && now - prev < 10_000L) {
            try {
                com.solar.launcher.diag.SolarDiagFeatureLog.warn("app",
                        "process_restart_thrash dtMs=" + (now - prev)
                                + " " + snapshotOneLine(null));
            } catch (Throwable ignored) {}
        }
    }

    public static String snapshotOneLine(Context context) {
        Snapshot s = evaluate(context);
        return "pressured=" + s.pressured
                + " avail=" + s.availMem
                + " memFree=" + s.memFree
                + " thr=" + s.threshold
                + " lowFlag=" + s.systemLowMemory
                + " trim=" + s.trimLevel
                + (s.reason != null ? " reason=" + s.reason : "");
    }

    private static void maybeLogFlip(boolean pressured, String reason) {
        Boolean prev = lastPressuredLogged;
        if (prev != null && prev.booleanValue() == pressured) return;
        lastPressuredLogged = pressured;
        try {
            if (pressured) {
                com.solar.launcher.diag.SolarDiagFeatureLog.warn("app",
                        "mem_pressure_on " + (reason != null ? reason : ""));
            } else {
                com.solar.launcher.diag.SolarDiagFeatureLog.event("app", "mem_pressure_off");
            }
        } catch (Throwable ignored) {}
    }

    /** Parse MemFree from /proc/meminfo → bytes; 0 if unavailable. */
    public static long readMemFreeBytes() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/meminfo")));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemFree:")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            long kb = Long.parseLong(parts[1]);
                            return kb * 1024L;
                        }
                    }
                }
            } finally {
                br.close();
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    public static final class Snapshot {
        public boolean pressured;
        public long availMem;
        public long threshold;
        public long memFree;
        public boolean systemLowMemory;
        public int trimLevel = -1;
        public String reason;
    }
}
