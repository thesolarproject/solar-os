package com.solar.input.policy;

/**
 * 2026-07-05 — Sysprop-only stale overlay gate clear shared by Solar, Xposed, and root daemon.
 * Layman: ghost "menu open" flags auto-clear so the next hold still works.
 * Technical: opening=1 >2s or active=1 without ui=1 >2s → zero props via SystemProperties.
 * Reversal: delete; each tier keeps its own stale clear (drift risk).
 */
public final class StaleOverlayGate {

    public static final String ACTIVE_PROPERTY = "sys.solar.overlay.active";
    public static final String UI_PROPERTY = "sys.solar.overlay.ui";
    public static final String OPENING_PROPERTY = "sys.solar.overlay.opening";
    public static final String OPENING_AT_PROPERTY = "sys.solar.overlay.opening_at";
    public static final String ACTIVE_AT_PROPERTY = "sys.solar.overlay.active_at";

    /** Active=1 without ui=1 — ghost gate ceiling (matches OverlayKeyGate watchdog). */
    public static final long ACTIVE_WITHOUT_UI_STALE_MS = 2000L;
    /** opening=1 stuck — longer ceiling while :overlay paints (matches Xposed forwarder). */
    public static final long OPENING_STALE_MS = 5000L;
    /** @deprecated use {@link #ACTIVE_WITHOUT_UI_STALE_MS} or {@link #OPENING_STALE_MS} */
    public static final long STALE_MS = ACTIVE_WITHOUT_UI_STALE_MS;

    private StaleOverlayGate() {}

    /** Clear stale opening/active props — safe to call before every hold arm. */
    public static void clearIfNeeded() {
        long now = elapsedRealtime();
        if ("1".equals(getProp(OPENING_PROPERTY, "0"))) {
            long at = parseLong(getProp(OPENING_AT_PROPERTY, "0"));
            if (at <= 0L || now - at > OPENING_STALE_MS) {
                setProp(OPENING_PROPERTY, "0");
                setProp(OPENING_AT_PROPERTY, "0");
            }
        }
        if ("1".equals(getProp(ACTIVE_PROPERTY, "0"))
                && !"1".equals(getProp(UI_PROPERTY, "0"))) {
            long activeAt = parseLong(getProp(ACTIVE_AT_PROPERTY, "0"));
            if (activeAt <= 0L || now - activeAt > ACTIVE_WITHOUT_UI_STALE_MS) {
                setProp(ACTIVE_PROPERTY, "0");
                setProp(ACTIVE_AT_PROPERTY, "0");
            }
        }
    }

    /** True when overlay props block a new open attempt (after stale sweep). */
    public static boolean isActiveOrOpening() {
        clearIfNeeded();
        return "1".equals(getProp(ACTIVE_PROPERTY, "0"))
                || "1".equals(getProp(OPENING_PROPERTY, "0"));
    }

    /** Uptime ms for prop timestamps — public for companion overlay service. */
    public static long elapsedRealtime() {
        try {
            Class<?> clock = Class.forName("android.os.SystemClock");
            Object v = clock.getMethod("elapsedRealtime").invoke(null);
            return v instanceof Long ? (Long) v : Long.parseLong(String.valueOf(v));
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    static String getProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    static void setProp(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Throwable ignored) {}
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
