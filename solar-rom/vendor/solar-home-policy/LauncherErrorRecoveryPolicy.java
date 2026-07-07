package com.solar.home.policy;

/**
 * 2026-07-07 — Silent launcher kill + crash-loop recovery policy (shared JAR).
 * Layman: warns hooks before we force-stop a home app so "has stopped" never flashes mid-switch.
 * Technical: sysprops pre-arm kill; rolling 2 min crash window → recovery overlay tier.
 * Reversal: delete; LauncherTransitionGuard-only 8s window + stock AMS dialogs again.
 */
public final class LauncherErrorRecoveryPolicy {

    /** Package about to be force-stopped — hooks read before AMS paints error UI. */
    public static final String PROP_PENDING_KILL = "sys.solar.launcher.pending_kill";
    /** switch | restart | recover — why pending_kill was armed. */
    public static final String PROP_KILL_REASON = "sys.solar.launcher.kill_reason";
    /** Comma-separated epoch-ms crash timestamps for active HOME launcher. */
    public static final String PROP_CRASH_TIMES = "persist.solar.crash_times";
    /** Last process that hit recovery threshold — overlay titles. */
    public static final String PROP_RECOVERY_PROCESS = "persist.solar.recovery_proc";

    public static final String REASON_SWITCH = "switch";
    public static final String REASON_RESTART = "restart";
    public static final String REASON_RECOVER = "recover";

    /** Rolling window — 3 crashes within this span triggers fallback picker. */
    public static final long CRASH_WINDOW_MS = 120_000L;
    public static final int CRASH_THRESHOLD = 3;

    /** Pending-kill hint expires — stale prop must not suppress real crashes. */
    public static final long PENDING_KILL_TTL_MS = 12_000L;

    private static volatile String testHomeTargetOverride;
    private static volatile String testPendingKillOverride;
    private static volatile String testKillReasonOverride;
    private static volatile String testCrashTimesOverride;
    private static volatile long testNowMsOverride;

    private LauncherErrorRecoveryPolicy() {}

    /**
     * 2026-07-07 — Skip stock ANR/crash UI: active switch window, pending kill, or expected switch victim.
     * Layman: user switching Rockbox→Solar must not see "Solar isn't responding" from the old kill.
     */
    public static boolean shouldSilentlyDismissErrorUi(String procOrPkg) {
        if (procOrPkg == null || procOrPkg.length() == 0) return false;
        if (LauncherTransitionGuard.shouldSuppressErrorUi(procOrPkg)) return true;
        if (matchesPendingKill(procOrPkg)) return true;
        if (isExpectedSwitchVictim(procOrPkg)) return true;
        // 2026-07-07 — HOME apply in flight — Solar force-stop must not flash stock ANR/crash.
        if (HomeTargetPolicy.SOLAR_PKG.equals(basePackage(procOrPkg))
                && "1".equals(readStringProp(HomeTargetPolicy.PROP_HOME_APPLYING, "0"))) {
            return true;
        }
        return false;
    }

    /** True when proc matches armed pending_kill and TTL not expired. */
    public static boolean matchesPendingKill(String procOrPkg) {
        String pending = readPendingKill();
        if (pending == null || pending.length() == 0) return false;
        return basePackage(procOrPkg).equals(basePackage(pending));
    }

    /**
     * 2026-07-07 — Solar dying while user prefers Rockbox/JJ during switch — silent, not scary ANR.
     */
    public static boolean isExpectedSwitchVictim(String procOrPkg) {
        String reason = readKillReason();
        if (reason == null || reason.length() == 0) return false;
        if (!REASON_SWITCH.equals(reason) && !REASON_RESTART.equals(reason)) return false;
        String base = basePackage(procOrPkg);
        if (!HomeTargetPolicy.SOLAR_PKG.equals(base)) return false;
        String target = readHomeTarget();
        return !HomeTargetPolicy.TARGET_SOLAR.equals(HomeTargetPolicy.normalizeTarget(target));
    }

    /**
     * Record crash/ANR for launcher-family process; returns count in rolling window.
     * When return >= {@link #CRASH_THRESHOLD}, caller should open recovery overlay.
     */
    public static int recordCrashInWindow(String procOrPkg) {
        if (!LauncherTransitionGuard.isLauncherPackage(procOrPkg)
                && !HomeTargetPolicy.SOLAR_PKG.equals(basePackage(procOrPkg))) {
            return 0;
        }
        long now = nowMs();
        String joined = pruneAndAppend(readCrashTimes(), now);
        writeCrashTimes(joined);
        syncProp(PROP_RECOVERY_PROCESS, basePackage(procOrPkg));
        return countInWindow(joined, now);
    }

    /** True when rolling window count meets threshold — offer fallback HOME overlay. */
    public static boolean shouldOfferRecoveryOverlay(String procOrPkg) {
        if (procOrPkg == null) return false;
        String base = basePackage(procOrPkg);
        if (!HomeTargetPolicy.SOLAR_PKG.equals(base)) return false;
        if (countInWindow(readCrashTimes(), nowMs()) < CRASH_THRESHOLD) return false;
        // 2026-07-07 — Only when Solar is the user's chosen HOME — not background crashes.
        return HomeTargetPolicy.TARGET_SOLAR.equals(
                HomeTargetPolicy.normalizeTarget(readHomeTarget()));
    }

    public static void clearCrashWindow() {
        writeCrashTimes("");
        syncProp(PROP_RECOVERY_PROCESS, "");
    }

    /** Arm before force-stop — hooks on system_server read without blocking fg app. */
    public static void armPendingKill(String pkg, String reason) {
        if (pkg == null || pkg.length() == 0) return;
        syncProp(PROP_PENDING_KILL, basePackage(pkg));
        syncProp(PROP_KILL_REASON, reason != null ? reason : REASON_SWITCH);
        long until = LauncherTransitionGuard.SystemClockCompat.uptimeMillis() + PENDING_KILL_TTL_MS;
        syncProp("sys.solar.launcher.kill_until", String.valueOf(until));
    }

    public static void clearPendingKill() {
        syncProp(PROP_PENDING_KILL, "");
        syncProp(PROP_KILL_REASON, "");
        syncProp("sys.solar.launcher.kill_until", "0");
    }

    static int countInWindow(String csv, long now) {
        if (csv == null || csv.length() == 0) return 0;
        String[] parts = csv.split(",");
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            try {
                long t = Long.parseLong(parts[i].trim());
                if (now - t <= CRASH_WINDOW_MS) n++;
            } catch (Exception ignored) {}
        }
        return n;
    }

    static String pruneAndAppend(String csv, long now) {
        StringBuilder sb = new StringBuilder();
        if (csv != null && csv.length() > 0) {
            String[] parts = csv.split(",");
            for (int i = 0; i < parts.length; i++) {
                try {
                    long t = Long.parseLong(parts[i].trim());
                    if (now - t <= CRASH_WINDOW_MS) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(t);
                    }
                } catch (Exception ignored) {}
            }
        }
        if (sb.length() > 0) sb.append(',');
        sb.append(now);
        return sb.toString();
    }

    private static String readPendingKill() {
        if (testPendingKillOverride != null) return testPendingKillOverride;
        String pending = readStringProp(PROP_PENDING_KILL, "");
        if (pending.length() == 0) return "";
        long until = readLongProp("sys.solar.launcher.kill_until", 0L);
        if (until > 0L && LauncherTransitionGuard.SystemClockCompat.uptimeMillis() > until) {
            return "";
        }
        return pending;
    }

    private static String readKillReason() {
        if (testKillReasonOverride != null) return testKillReasonOverride;
        return readStringProp(PROP_KILL_REASON, "");
    }

    private static String readCrashTimes() {
        if (testCrashTimesOverride != null) return testCrashTimesOverride;
        return readStringProp(PROP_CRASH_TIMES, "");
    }

    private static void writeCrashTimes(String csv) {
        if (testNowMsOverride > 0L) {
            testCrashTimesOverride = csv != null ? csv : "";
        } else {
            testCrashTimesOverride = null;
        }
        syncProp(PROP_CRASH_TIMES, csv != null ? csv : "");
    }

    private static String readHomeTarget() {
        if (testHomeTargetOverride != null) return testHomeTargetOverride;
        return readStringProp(HomeTargetPolicy.PROP_HOME_TARGET, HomeTargetPolicy.TARGET_SOLAR);
    }

    private static long nowMs() {
        if (testNowMsOverride > 0L) return testNowMsOverride;
        return System.currentTimeMillis();
    }

    static String basePackage(String procOrPkg) {
        return LauncherCompetitionPolicy.basePackageName(procOrPkg);
    }

    private static void syncProp(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Exception ignored) {}
    }

    private static String readStringProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? String.valueOf(v) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static long readLongProp(String key, long def) {
        try {
            return Long.parseLong(readStringProp(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    // --- test hooks ---
    static void setHomeTargetForTest(String target) { testHomeTargetOverride = target; }
    static void setPendingKillForTest(String pkg) { testPendingKillOverride = pkg; }
    static void setKillReasonForTest(String reason) { testKillReasonOverride = reason; }
    static void setCrashTimesForTest(String csv) { testCrashTimesOverride = csv; }
    static void setNowMsForTest(long ms) { testNowMsOverride = ms; }
    static void resetForTest() {
        testHomeTargetOverride = null;
        testPendingKillOverride = null;
        testKillReasonOverride = null;
        testCrashTimesOverride = null;
        testNowMsOverride = 0L;
    }
}
