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
    /**
     * 2026-07-08 — Shell-owned: WM addView done / removeView done.
     * Layman: “is the global menu window still painted on screen?”
     * Technical: tracks attached shell independent of active/ui gate props.
     * Reversal: stop writing; stuck BACK heal becomes a no-op.
     */
    public static final String SHELL_VISIBLE_PROPERTY = "sys.solar.overlay.shell_visible";

    /** Active=1 without ui=1 — ghost gate ceiling (matches OverlayKeyGate watchdog). */
    public static final long ACTIVE_WITHOUT_UI_STALE_MS = 2000L;
    /** opening=1 stuck — longer ceiling while :overlay paints (matches Xposed forwarder). */
    public static final long OPENING_STALE_MS = 5000L;
    /** ui=1 or active=1 without shell_visible — props armed but no WM window. */
    public static final long UI_WITHOUT_SHELL_STALE_MS = 500L;
    /** @deprecated use {@link #ACTIVE_WITHOUT_UI_STALE_MS} or {@link #OPENING_STALE_MS} */
    public static final long STALE_MS = ACTIVE_WITHOUT_UI_STALE_MS;
    /**
     * 2026-07-08 — Debounce stuck-shell DISMISS fires (KeyEvent.ACTION_DOWN == 0).
     * Layman: one Back press clears a ghost menu; spam keys don’t thrash dismiss.
     */
    public static final long STUCK_SHELL_BACK_DEBOUNCE_MS = 1000L;
    /**
     * 2026-07-08 — Heal fires on DOWN so hold/open never arms first.
     * Matches android.view.KeyEvent.ACTION_DOWN without importing Android into JVM unit tests.
     * Was KEY_ACTION_UP — DOWN still scheduled companion openPowerOverlay (JJ ~200ms).
     */
    public static final int KEY_ACTION_DOWN = 0;
    /** Kept for callers that still name UP explicitly. */
    public static final int KEY_ACTION_UP = 1;

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
        // Ghost gate: props say menu is up but WM shell is not painted (Home IPC regression).
        // 2026-07-11 — Was: active_at<=0 → wipe immediately. On Y2, companion setprop for
        // shell_visible often lags (async su) while active/ui already arm → keys die at open.
        // Now: stamp active_at when missing, then wait UI_WITHOUT_SHELL_STALE_MS.
        // Reversal: restore activeAt<=0L immediate clear if false ghost arms return.
        if (("1".equals(getProp(UI_PROPERTY, "0")) || "1".equals(getProp(ACTIVE_PROPERTY, "0")))
                && !"1".equals(getProp(SHELL_VISIBLE_PROPERTY, "0"))) {
            long activeAt = parseLong(getProp(ACTIVE_AT_PROPERTY, "0"));
            if (activeAt <= 0L) {
                setProp(ACTIVE_AT_PROPERTY, String.valueOf(now));
                return;
            }
            if (now - activeAt > UI_WITHOUT_SHELL_STALE_MS) {
                setProp(ACTIVE_PROPERTY, "0");
                setProp(UI_PROPERTY, "0");
                setProp(OPENING_PROPERTY, "0");
                setProp(ACTIVE_AT_PROPERTY, "0");
                setProp(OPENING_AT_PROPERTY, "0");
            }
        }
    }

    /** True when overlay props block a new open attempt (after stale sweep). */
    public static boolean isActiveOrOpening() {
        clearIfNeeded();
        return "1".equals(getProp(ACTIVE_PROPERTY, "0"))
                || "1".equals(getProp(OPENING_PROPERTY, "0"));
    }

    /**
     * 2026-07-08 — Pure decision: fire DISMISS when shell painted but gate not owning keys.
     * Layman: short Back closes a stuck global menu instead of opening another one.
     * Technical: ACTION_DOWN only; never when capture armed or post-dismiss cooldown; 1s debounce.
     * Was ACTION_UP — DOWN still armed HOLD → openPowerOverlay (replacement menu).
     * Reversal: return false / restore KEY_ACTION_UP check; callers stop consuming BACK.
     */
    public static boolean shouldDismissStuckShellOnBack(
            boolean captureArmed,
            boolean postDismissCooldown,
            boolean shellVisible,
            int keyAction,
            long now,
            long lastFiredAt) {
        if (captureArmed) return false;
        if (postDismissCooldown) return false;
        if (!shellVisible) return false;
        if (keyAction != KEY_ACTION_DOWN) return false;
        if (lastFiredAt > 0L && now - lastFiredAt < STUCK_SHELL_BACK_DEBOUNCE_MS) {
            return false;
        }
        return true;
    }

    /**
     * 2026-07-08 — Whole short-BACK gesture must be eaten while a ghost shell is up.
     * Layman: Back only closes the stuck menu — it must not reach JJ or start a new hold.
     * Technical: unarmed + shell_visible → consume DOWN/UP/repeat; DISMISS itself is gated separately.
     * Was: heal never consumed — BACK still armed SystemServerHooks / coordinator open.
     * Reversal: always false; PWM back-long path owns short BACK again.
     */
    public static boolean shouldConsumeStuckShellBack(
            boolean captureArmed,
            boolean postDismissCooldown,
            boolean shellVisible) {
        if (captureArmed) return false;
        if (postDismissCooldown) return false;
        return shellVisible;
    }

    /** Read shell_visible prop — used by Xposed stuck BACK heal. */
    public static boolean isShellVisible() {
        return "1".equals(getProp(SHELL_VISIBLE_PROPERTY, "0"));
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
