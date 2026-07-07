package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * 2026-07-05 — Highest mutex tier: sys.solar.overlay.active blocks IME and handoff while modal is up.
 * Xposed OverlayKeyForwarder reads these props; Solar must disarm on boot/crash (BootReceiver).
 * When changing: align OPENING/COOLDOWN timings with solar-context-bridge OverlayKeyForwarder.
 * Reversal: remove gate; overlay keys and stock DPAD may collide in third-party apps.
 */
public final class OverlayKeyGate {

    /** Read by solar-context-bridge in PhoneWindowManager.interceptKeyBeforeDispatching. */
    public static final String ACTIVE_PROPERTY = "sys.solar.overlay.active";
    /** Set when interactive overlay UI is painted — distinguishes live modal from stale active=1 after crash. */
    public static final String UI_PROPERTY = "sys.solar.overlay.ui";
    /** Set while :overlay is loading theme/menu — blocks duplicate triggers and stale-gate disarm. */
    public static final String OPENING_PROPERTY = "sys.solar.overlay.opening";
    /** Elapsed-realtime ms when opening=1 — stale after ~5s (matches Xposed OverlayKeyForwarder). */
    public static final String OPENING_AT_PROPERTY = "sys.solar.overlay.opening_at";
    /** Elapsed-realtime ms when active=1 without ui=1 — stale after 2s (watchdog ceiling). */
    public static final String ACTIVE_AT_PROPERTY = "sys.solar.overlay.active_at";
    /** Uptime millis until handoff inject must stay off after overlay dismiss (Xposed reads too). */
    public static final String COOLDOWN_UNTIL_PROPERTY = "sys.solar.overlay.cooldown";
    /** Monotonic pulse written on disarm — Xposed clears stale BACK/center long-press state. */
    public static final String DISARM_PULSE_PROPERTY = "sys.solar.overlay.disarm_pulse";
    /** Legacy persist prop — stuck=1 breaks Rockbox/Solar back/OK; cleared on disarm/boot. */
    private static final String LEGACY_ACTIVE_PROPERTY = "persist.solar.overlay.active";
    /** Swallow only the dismiss release tail — repeated open/close cycles must stay snappy. */
    static final long POST_OVERLAY_COOLDOWN_MS = 90L;
    /** Drop duplicate Xposed forwards (queue + dispatch hooks) within this window. */
    private static final long DELIVER_DEDUPE_MS = 45L;

    public interface Handler {
        /** @return true when the key was consumed by the overlay modal */
        boolean onKeyDown(int keyCode);

        /** @return true when the key-up was consumed (queue tap vs long-press). */
        boolean onKeyUp(int keyCode);
    }

    private static volatile Handler handler;
    private static volatile int lastDeliverKeyCode;
    private static volatile int lastDeliverAction;
    private static volatile long lastDeliverAt;

    private OverlayKeyGate() {}

    public static boolean isOverlayUiVisible() {
        return "1".equals(readProperty(UI_PROPERTY, "0"));
    }

    /** Mark whether the global modal is actually on screen (not just key-gate armed during theme load). */
    public static void setOverlayUiVisible(boolean visible) {
        writeProperty(UI_PROPERTY, visible ? "1" : "0");
        if (visible) {
            setOverlayOpening(false);
            writeProperty(ACTIVE_AT_PROPERTY, "0");
        }
        // #region agent log
        try {
            org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
            d.put("visible", visible);
            DebugOverlayStuckLog.log("OverlayKeyGate.setOverlayUiVisible",
                    "ui prop write", "H-A", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** True while :overlay is painting — duplicate BACK/power triggers must coalesce, not tear down. */
    public static void setOverlayOpening(boolean opening) {
        writeProperty(OPENING_PROPERTY, opening ? "1" : "0");
        if (opening) {
            writeProperty(OPENING_AT_PROPERTY, String.valueOf(elapsedRealtimeCompat()));
        } else {
            writeProperty(OPENING_AT_PROPERTY, "0");
        }
    }

    /** Quick check for duplicate open triggers (Xposed + root daemon race on Y1). */
    public static boolean isOverlayOpening() {
        clearStaleGatesIfNeeded();
        return "1".equals(readProperty(OPENING_PROPERTY, "0"));
    }

    /** Shared heal entry — {@link StaleOverlayGate} and Xposed call before hold arm. */
    public static void clearStaleOverlayGatesIfNeeded() {
        clearStaleGatesIfNeeded();
    }

    /** Stuck opening=1 or active=1 without ui=1 after :overlay crash — ceiling 2s per Ponytail rules. */
    private static void clearStaleGatesIfNeeded() {
        com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
    }

    /** Set overlay-active sys prop — SystemProperties first, root setprop fallback. */
    public static boolean setOverlayActive(boolean active) {
        clearLegacyActiveProperty();
        if (!active) {
            setOverlayUiVisible(false);
            writeProperty(ACTIVE_AT_PROPERTY, "0");
        } else if (!"1".equals(readProperty(UI_PROPERTY, "0"))) {
            writeProperty(ACTIVE_AT_PROPERTY, String.valueOf(elapsedRealtimeCompat()));
        }
        boolean ok = writeProperty(ACTIVE_PROPERTY, active ? "1" : "0");
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("active", active);
            d.put("writeOk", ok);
            d.put("propAfter", readProperty(ACTIVE_PROPERTY, "0"));
            DebugEdc27bLog.log("OverlayKeyGate.setOverlayActive", "prop write", "H-B", d);
        } catch (Exception ignored) {}
        // #endregion
        return ok;
    }

    /** Overlay shown — tell system_server to block keys from the foreground app. */
    public static void arm(Handler keyHandler) {
        clearLegacyActiveProperty();
        if (SolarImeRouteArbiter.isActive()) {
            SolarImeKeyGate.pauseForHigherOverlay();
        }
        // Prop first — system_server + root daemon read this before handler wiring completes.
        boolean ok = setOverlayActive(true);
        handler = keyHandler;
        ExternalInputHandoff.pauseForGlobalOverlay();
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("setpropOk", ok);
            d.put("propAfterArm", readProperty(ACTIVE_PROPERTY, "0"));
            d.put("legacyProp", readProperty(LEGACY_ACTIVE_PROPERTY, "0"));
            d.put("y1", DeviceFeatures.isY1());
            DebugOverlayKeyLog.log("OverlayKeyGate.arm", "overlay armed", "H1", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** Overlay dismissed — restore normal key routing after a short inject cooldown. */
    public static void disarm() {
        handler = null;
        lastDeliverKeyCode = 0;
        lastDeliverAt = 0L;
        long now = SystemClock.uptimeMillis();
        writeProperty(COOLDOWN_UNTIL_PROPERTY, String.valueOf(now + POST_OVERLAY_COOLDOWN_MS));
        writeProperty(DISARM_PULSE_PROPERTY, String.valueOf(now));
        writeProperty(ACTIVE_AT_PROPERTY, "0");
        setOverlayOpening(false);
        setOverlayActive(false);
        setOverlayUiVisible(false);
        clearLegacyActiveProperty();
        forceOverlayInactiveProperty();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("cooldownUntil", now + POST_OVERLAY_COOLDOWN_MS);
            d.put("propAfter", readProperty(ACTIVE_PROPERTY, "0"));
            DebugEdc27bLog.log("OverlayKeyGate.disarm", "post-dismiss BACK swallow window", "BACK-F1", d);
            org.json.JSONObject d2 = new org.json.JSONObject();
            d2.put("propAfter", readProperty(ACTIVE_PROPERTY, "0"));
            DebugAgentLog.log(null, "OverlayKeyGate.disarm", "overlay disarmed", "H1", d2);
        } catch (Exception ignored) {}
        // #endregion
        SolarImeKeyGate.clearPausedForHigherOverlay();
        ExternalInputHandoff.resumeFromGlobalOverlay();
    }

    /** Root setprop fallback when app-context SystemProperties.set fails (Y1/Y2 MTK builds). */
    private static void forceOverlayInactiveProperty() {
        if (!"1".equals(readProperty(ACTIVE_PROPERTY, "0"))) return;
        RootShell.run("setprop " + ACTIVE_PROPERTY + " 0");
        RootShell.run("setprop " + UI_PROPERTY + " 0");
        clearLegacyActiveProperty();
    }

    private static boolean isOverlayProcessRunning(Context context) {
        if (context == null) return true;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
                        if ("com.solar.launcher:overlay".equals(p.processName)) return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Recover from a stuck overlay-active prop when :overlay crashed before paint — blocks wheel
     * inject and Xposed key capture while no modal is visible.
     */
    public static void disarmStaleIfNeeded(Context context) {
        // #region agent log
        try {
            org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
            d.put("opening", readProperty(OPENING_PROPERTY, "0"));
            d.put("willDisarm", "1".equals(d.optString("active"))
                    && !"1".equals(d.optString("ui"))
                    && !isOverlayOpening());
            DebugOverlayStuckLog.log("OverlayKeyGate.disarmStaleIfNeeded",
                    "stale gate probe", "H-A", d);
        } catch (Exception ignored) {}
        // #endregion
        boolean overlayRunning = isOverlayProcessRunning(context);
        if (!overlayRunning && ("1".equals(readProperty(ACTIVE_PROPERTY, "0")) || "1".equals(readProperty(OPENING_PROPERTY, "0")))) {
            clearStaleGatesIfNeeded();
            disarm();
            if (context != null) {
                ExternalInputHandoff.restoreAfterOverlayDismiss(context.getApplicationContext());
                SolarOverlayHost.ensureStarted(context);
            }
            return;
        }
        if (!"1".equals(readProperty(ACTIVE_PROPERTY, "0"))) return;
        if ("1".equals(readProperty(UI_PROPERTY, "0"))) return;
        clearStaleGatesIfNeeded();
        if (!"1".equals(readProperty(ACTIVE_PROPERTY, "0"))) return;
        long activeAt = 0L;
        try {
            activeAt = Long.parseLong(readProperty(ACTIVE_AT_PROPERTY, "0"));
        } catch (NumberFormatException ignored) {}
        if (activeAt > 0L && elapsedRealtimeCompat() - activeAt
                < com.solar.input.policy.StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS) {
            return;
        }
        if (isOverlayOpening()) return;
        // Tear down :overlay WM shell — main process must not clear props alone.
        if (context != null) {
            try {
                Intent dismiss = new Intent(context, SolarOverlayService.class);
                dismiss.setAction(OverlayTriggers.ACTION_DISMISS_OVERLAY);
                context.getApplicationContext().startService(dismiss);
            } catch (Exception ignored) {}
        }
        disarm();
        if (context != null) {
            ExternalInputHandoff.restoreAfterOverlayDismiss(context.getApplicationContext());
            SolarOverlayHost.ensureStarted(context);
        }
        // #region agent log
        try {
            org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
            DebugOverlayStuckLog.log("OverlayKeyGate.disarmStaleIfNeeded",
                    "cleared stale overlay gate", "H-A", d);
            DebugInputLog.log("OverlayKeyGate.disarmStaleIfNeeded",
                    "cleared stale overlay gate", "H1", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** Clear stuck persist flag from older builds (breaks Rockbox when left at 1). */
    private static void clearLegacyActiveProperty() {
        if ("1".equals(readProperty(LEGACY_ACTIVE_PROPERTY, "0"))) {
            writeProperty(LEGACY_ACTIVE_PROPERTY, "0");
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("cleared", LEGACY_ACTIVE_PROPERTY);
                DebugOverlayKeyLog.log("OverlayKeyGate.clearLegacy", "legacy prop cleared", "H6", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    /** Solar-side quick check (property may lag one frame after arm). */
    public static boolean isActive() {
        return handler != null || "1".equals(readProperty(ACTIVE_PROPERTY, "0"));
    }

    /** True when BACK must not reach the stock app after overlay dismiss (matches Xposed cooldown prop). */
    public static boolean shouldSwallowBackAfterOverlayDismiss(int keyCode) {
        return Y1InputKeys.isBackKey(keyCode) && isInPostOverlayCooldown();
    }

    /** True briefly after overlay dismiss — blocks handoff inject and swallows stale wheel keys. */
    public static boolean isInPostOverlayCooldown() {
        try {
            long until = Long.parseLong(readProperty(COOLDOWN_UNTIL_PROPERTY, "0"));
            return until > 0 && SystemClock.uptimeMillis() < until;
        } catch (Exception e) {
            return false;
        }
    }

    /** Called from {@link OverlayKeyReceiver} after Xposed forwards a hardware key. */
    public static boolean deliver(int keyCode) {
        if (isDuplicateDeliver(keyCode, KeyEvent.ACTION_DOWN)) {
            return true;
        }
        Handler h = handler;
        boolean consumed = h != null && h.onKeyDown(keyCode);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("keyCode", keyCode);
            d.put("hasHandler", h != null);
            d.put("consumed", consumed);
            d.put("prop", readProperty(ACTIVE_PROPERTY, "0"));
            DebugEdc27bLog.log("OverlayKeyGate.deliver", "deliver down", "H-C", d);
        } catch (Exception ignored) {}
        // #endregion
        return consumed;
    }

    public static boolean deliverUp(int keyCode) {
        if (isDuplicateDeliver(keyCode, KeyEvent.ACTION_UP)) {
            return true;
        }
        Handler h = handler;
        return h != null && h.onKeyUp(keyCode);
    }

    /** MTK may hit queue + dispatch hooks — ignore duplicate for same key+action within window. */
    private static boolean isDuplicateDeliver(int keyCode, int action) {
        long now = SystemClock.uptimeMillis();
        if (keyCode == lastDeliverKeyCode && action == lastDeliverAction
                && now - lastDeliverAt < DELIVER_DEDUPE_MS) {
            return true;
        }
        lastDeliverKeyCode = keyCode;
        lastDeliverAction = action;
        lastDeliverAt = now;
        return false;
    }

    /** Test hook — reset dedupe state between unit cases. */
    static void resetDeliverDedupeForTest() {
        lastDeliverKeyCode = 0;
        lastDeliverAction = 0;
        lastDeliverAt = 0L;
    }

    /** Debug 72b98f — whether this process still holds the overlay key handler. */
    static boolean hasHandlerForTest() {
        return handler != null;
    }

    /** Shared with {@link ExternalInputHandoff} — block stock-app remap while overlay owns keys. */
    public static boolean isOverlayKeysActive() {
        clearStaleGatesIfNeeded();
        clearLegacyActiveProperty();
        boolean propActive = "1".equals(readProperty(ACTIVE_PROPERTY, "0"));
        boolean uiVisible = "1".equals(readProperty(UI_PROPERTY, "0"));
        boolean opening = isOverlayOpening();
        // 2026-07-06 — stale handler after :overlay crash ate OK/wheel in MainActivity with props at 0.
        if (!propActive && !uiVisible && !opening && handler != null) {
            handler = null;
        }
        return propActive || uiVisible || opening || handler != null;
    }

    /**
     * :overlay watchdog — re-publish gate props while WM modal is painted so main-process
     * stale recovery and USB HOME reclaim do not disarm under a live overlay.
     */
    public static void refreshLiveOverlayGate() {
        if (handler == null) return;
        setOverlayActive(true);
        setOverlayUiVisible(true);
    }

    /** Wheel/back/center/side keys routed to the global overlay modal (not volume — AudioService owns that). */
    public static boolean isOverlayNavigationKey(int keyCode) {
        return Y1InputKeys.isWheelKey(keyCode)
                || Y1InputKeys.isBackKey(keyCode)
                || Y1InputKeys.isCenterKey(keyCode)
                || Y1InputKeys.isPlayPauseKey(keyCode)
                || Y1InputKeys.isTrackPreviousKey(keyCode)
                || Y1InputKeys.isTrackNextKey(keyCode);
    }

    /**
     * IPC keys into {@link SolarOverlayService} (:overlay) — handler lives there, not in main process.
     * Used when Solar is foreground (PWM forwards miss) and by the root input daemon on Y1/Y2.
     */
    public static void forwardKeyToOverlay(Context ctx, int keyCode, int action) {
        if (ctx == null || keyCode == 0) return;
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return;
        try {
            Context app = ctx.getApplicationContext();
            Intent svc = new Intent(app, SolarOverlayService.class);
            svc.setAction(OverlayTriggers.ACTION_OVERLAY_KEY);
            svc.putExtra(OverlayTriggers.EXTRA_KEY_CODE, keyCode);
            svc.putExtra(OverlayTriggers.EXTRA_KEY_ACTION, action);
            app.startService(svc);
            // #region agent log
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("keyCode", keyCode);
            d.put("action", action);
            d.put("prop", readProperty(ACTIVE_PROPERTY, "0"));
            DebugEdc27bLog.log("OverlayKeyGate.forwardKeyToOverlay", "startService", "H-A", d);
            // #endregion
        } catch (Exception e) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("err", e.getMessage());
                DebugEdc27bLog.log("OverlayKeyGate.forwardKeyToOverlay", "failed", "H-A", d);
            } catch (Exception ignored) {}
            // #endregion
        }
    }

    /** Write a sys prop — hidden API first, su setprop when SELinux blocks the app. */
    static boolean writeProperty(String key, String value) {
        if (key == null || value == null) return false;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, key, value);
            if (value.equals(readProperty(key, ""))) {
                return true;
            }
        } catch (Exception ignored) {}
        return RootShell.run("setprop " + key + " " + value);
    }

    private static String readProperty(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static long elapsedRealtimeCompat() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }
}
