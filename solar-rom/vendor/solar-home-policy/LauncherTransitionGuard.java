package com.solar.home.policy;

/**
 * 2026-07-06 — Suppress crash/ANR UI while launcher switch or restart is in flight.
 * Layman: user sees "Switching…" only — not "app has stopped" during home changes.
 * Technical: reads sys.solar.launcher.transition_until uptime deadline via reflection.
 * Reversal: delete; AppErrorHooks show overlay replacement during transitions again.
 */
public final class LauncherTransitionGuard {

    public static final String PROP_TRANSITION_UNTIL = HomeTargetPolicy.PROP_LAUNCHER_TRANSITION_UNTIL;

    private LauncherTransitionGuard() {}

    /** True while solar-launcher-exec armed a switch/restart window. */
    public static boolean isLauncherTransitionActive() {
        long deadline = readLongProperty(PROP_TRANSITION_UNTIL, 0L);
        if (deadline <= 0L) return false;
        return SystemClockCompat.uptimeMillis() < deadline;
    }

    /** Solar / Rockbox / JJ / homehelper — including :overlay style process names. */
    public static boolean isLauncherPackage(String procOrPkg) {
        return LauncherCompetitionPolicy.isKnownLauncherPackage(procOrPkg);
    }

    /** Silent-dismiss stock error UI for launcher-family during an active transition. */
    public static boolean shouldSuppressErrorUi(String procOrPkg) {
        return isLauncherTransitionActive() && isLauncherPackage(procOrPkg);
    }

    /**
     * 2026-07-06 — Skip HOME watchdog while overlay or switch is in flight.
     * Layman: holding Back to open quick menu must not yank Rockbox/JJ away mid-hold.
     * Technical: overlay.active/opening or transition_until — enforce-foreground no-op.
     * Reversal: delete guard; enforcer relaunches when overlay steals mResumedActivity.
     */
    public static boolean shouldPauseHomeEnforcement() {
        if (isLauncherTransitionActive()) return true;
        return isOverlayActiveOrOpening();
    }

    private static boolean isOverlayActiveOrOpening() {
        return "1".equals(readStringProperty("sys.solar.overlay.active", "0"))
                || "1".equals(readStringProperty("sys.solar.overlay.opening", "0"));
    }

    private static String readStringProperty(String key, String defaultValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, key, defaultValue);
            return v != null ? String.valueOf(v) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static long readLongProperty(String key, long defaultValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, key, String.valueOf(defaultValue));
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Uptime ms without importing android.os.SystemClock in policy-only javac. */
    static final class SystemClockCompat {
        private SystemClockCompat() {}

        static long uptimeMillis() {
            try {
                Class<?> c = Class.forName("android.os.SystemClock");
                Object v = c.getMethod("uptimeMillis").invoke(null);
                if (v instanceof Long) return ((Long) v).longValue();
                if (v instanceof Number) return ((Number) v).longValue();
            } catch (Exception ignored) {}
            return System.currentTimeMillis();
        }
    }
}
