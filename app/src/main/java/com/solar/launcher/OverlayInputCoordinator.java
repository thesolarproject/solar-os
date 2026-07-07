package com.solar.launcher;

import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONObject;

/**
 * 2026-07-06 — Input routing coordinator across overlay tiers (microservice helpers).
 * Layman: decides which helper owns wheel/back when the quick menu is up — Xposed first, root backup.
 * Technical: tier-1 {@code OverlayKeyForwarder} (PWM) → tier-2 {@link OverlayRootKeyShell} (evdev)
 * → tier-3 {@link MainActivity#forwardOverlayKeyIfGlobalModalActive} when main is alive.
 * Reversal: delete and restore swallow-only root path in {@link GlobalOverlayTriggerMain}.
 */
public final class OverlayInputCoordinator {

    /** Match shared {@link com.solar.input.policy.StaleOverlayGate} active ceiling. */
    private static final long ACTIVE_STALE_MS =
            com.solar.input.policy.StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS;
    /** Throttle root heal probes — evdev loop is hot. */
    private static final long HEAL_MIN_INTERVAL_MS = 2000L;
    /** Cache ps probe — was forked sh every heal tick (2026-07-06). */
    private static final long OVERLAY_ALIVE_CACHE_MS = 4000L;
    private static volatile long lastHealAt;
    private static volatile long lastAliveCheckAt;
    private static volatile boolean lastAlive;

    private OverlayInputCoordinator() {}

    /**
     * Tier-2 — root evdev forwards navigation keys while overlay gate is armed.
     * Layman: if system_server miss, the keypad daemon still feeds the modal.
     */
    public static void forwardFromRootTier(int scancode, int value) {
        if (!isOverlayGateArmed() && !isOverlayUiVisible()) return;
        ensureOverlayProcessFromRoot("root-key-forward");
        int keyCode = GlobalOverlayTriggerMain.scancodeToKeyCode(scancode);
        if (keyCode == 0 || !OverlayKeyGate.isOverlayNavigationKey(keyCode)) return;
        if (value != 0 && value != 1) return;
        int action = value == 1 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        OverlayRootKeyShell.forward(keyCode, action);
    }

    /**
     * Root-side stale gate heal — no Android Context; safe from app_process / rescue daemon.
     * Layman: clears ghost "overlay open" flags that freeze the wheel when UI never painted.
     */
    public static void healStaleGatesFromRoot() {
        if (!isOverlayGateArmed() && !isOverlayUiVisible() && !isImeActiveFromProp()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastHealAt < HEAL_MIN_INTERVAL_MS) return;
        lastHealAt = now;
        StaleOverlayGate.clearIfNeeded();
        if (!isOverlayGateArmed() && !isOverlayUiVisible()) return;
        if (isOverlayProcessAliveCached()) return;
        if (isOverlayUiVisible() || "1".equals(readProp(OverlayKeyGate.ACTIVE_PROPERTY, "0"))) {
            ensureOverlayProcessFromRoot("heal-revive");
            return;
        }
        long activeAt = readLongProp(OverlayKeyGate.ACTIVE_AT_PROPERTY, 0L);
        if (activeAt > 0L && now - activeAt < ACTIVE_STALE_MS) return;
        if (isOverlayOpening()) return;
        writeProp(OverlayKeyGate.ACTIVE_PROPERTY, "0");
        writeProp(OverlayKeyGate.UI_PROPERTY, "0");
        writeProp(OverlayKeyGate.OPENING_PROPERTY, "0");
        writeProp(OverlayKeyGate.OPENING_AT_PROPERTY, "0");
    }

    private static boolean isImeActiveFromProp() {
        return "1".equals(readProp(SolarImeRouteArbiter.ACTIVE_PROPERTY, "0"));
    }

    /** Cached ps probe — evdev hot path must not fork sh every 500ms (2026-07-06). */
    private static boolean isOverlayProcessAliveCached() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAliveCheckAt < OVERLAY_ALIVE_CACHE_MS) return lastAlive;
        lastAliveCheckAt = now;
        lastAlive = isOverlayProcessAlive();
        return lastAlive;
    }

    /**
     * 2026-07-06 — Main hung/force-stop may kill :overlay; root restarts keepalive so modal keeps keys.
     * Layman: if the menu flag is up but the paint process died, nudge Solar overlay back awake.
     */
    public static void ensureOverlayProcessFromRoot(String scenario) {
        if (isOverlayProcessAliveCached()) return;
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "am startservice -a " + OverlayTriggers.ACTION_OVERLAY_KEEPALIVE
                            + " -n com.solar.launcher/.SolarOverlayService 2>/dev/null"});
        } catch (Exception ignored) {}
    }

    /** True when tier-2 should route keys — active or opening sysprop. */
    static boolean isOverlayGateArmed() {
        return "1".equals(readProp(OverlayKeyGate.ACTIVE_PROPERTY, "0"))
                || "1".equals(readProp(OverlayKeyGate.OPENING_PROPERTY, "0"));
    }

    private static boolean isOverlayUiVisible() {
        return "1".equals(readProp(OverlayKeyGate.UI_PROPERTY, "0"));
    }

    private static boolean isOverlayOpening() {
        return "1".equals(readProp(OverlayKeyGate.OPENING_PROPERTY, "0"));
    }

    /** Cheap ps probe — root daemon has no ActivityManager. */
    private static boolean isOverlayProcessAlive() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "ps | grep -q '[c]om.solar.launcher:overlay'"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static long readLongProp(String key, long def) {
        try {
            return Long.parseLong(readProp(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void writeProp(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
        } catch (Exception ignored) {}
    }
}
