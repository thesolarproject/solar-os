package com.solar.launcher.globalcontext;

import android.os.SystemClock;
import android.view.KeyEvent;

import com.solar.input.policy.StaleOverlayGate;

/**
 * 2026-07-08 — Companion-side single writer for sys.solar.overlay.* key ownership.
 * Layman: turns on “keys go to the system menu” while that menu is painted.
 * Technical: mirrors Solar OverlayKeyGate props; arms only after UI paint (ui=1).
 * Reversal: delete; Solar OverlayKeyGate alone writes props again (legacy_shell=1).
 */
public final class CompanionOverlayKeyGate {

    public static final String ACTIVE_PROPERTY = StaleOverlayGate.ACTIVE_PROPERTY;
    public static final String UI_PROPERTY = StaleOverlayGate.UI_PROPERTY;
    public static final String OPENING_PROPERTY = StaleOverlayGate.OPENING_PROPERTY;
    public static final String OPENING_AT_PROPERTY = StaleOverlayGate.OPENING_AT_PROPERTY;
    public static final String ACTIVE_AT_PROPERTY = StaleOverlayGate.ACTIVE_AT_PROPERTY;
    /** 2026-07-08 — WM shell attached; stuck BACK heal reads this when gate is disarmed. */
    public static final String SHELL_VISIBLE_PROPERTY = StaleOverlayGate.SHELL_VISIBLE_PROPERTY;
    public static final String COOLDOWN_UNTIL_PROPERTY = "sys.solar.overlay.cooldown";
    public static final String DISARM_PULSE_PROPERTY = "sys.solar.overlay.disarm_pulse";
    private static final String LEGACY_ACTIVE_PROPERTY = "persist.solar.overlay.active";
    /** Short swallow of dismiss release — same ceiling as Solar OverlayKeyGate. */
    static final long POST_OVERLAY_COOLDOWN_MS = 90L;
    /**
     * 2026-07-10 — Coalesce double-hook (queueing + dispatch) only.
     * Was 45ms for all keys → rapid wheel ticks (often &lt;45ms apart) never moved focus.
     * Wheel uses a tighter window so scroll stays usable; non-wheel keeps 45ms.
     */
    private static final long DELIVER_DEDUPE_MS = 45L;
    /**
     * 2026-07-10 — Wheel: nearly no coalesce (Xposed now forwards wheel once from queueing only).
     * Was 12ms still dropped fast scroll. 0 = every tick delivered.
     */
    private static final long WHEEL_DEDUPE_MS = 0L;

    public interface Handler {
        boolean onKeyDown(int keyCode);
        boolean onKeyUp(int keyCode);
    }

    private static volatile Handler handler;
    private static volatile int lastDeliverKeyCode;
    private static volatile int lastDeliverAction;
    private static volatile long lastDeliverAt;
    /**
     * 2026-07-10 — In-process gate when SystemProperties.set is blocked (common on Y2).
     * Xposed still needs real props when they stick; MainActivity uses session + broadcast.
     */
    private static volatile boolean memActive;
    private static volatile boolean memUi;
    private static volatile boolean memOpening;
    private static volatile boolean memShellVisible;

    private CompanionOverlayKeyGate() {}

    /**
     * 2026-07-08 — Publish WM shell attach/detach for stuck-overlay BACK heal.
     * Layman: “tell the system the global menu window is (or isn’t) still painted.”
     * Technical: shell_visible independent of active/ui; written only at addView/removeView.
     * Reversal: no-op setter; OverlayKeyForwarder heal never fires.
     */
    public static void setShellVisible(boolean visible) {
        memShellVisible = visible;
        SysPropHelper.set(SHELL_VISIBLE_PROPERTY, visible ? "1" : "0");
    }

    /** Mark open-in-flight before WM addView — coalesces duplicate triggers. */
    public static void setOverlayOpening(boolean opening) {
        memOpening = opening;
        SysPropHelper.set(OPENING_PROPERTY, opening ? "1" : "0");
        if (opening) {
            SysPropHelper.set(OPENING_AT_PROPERTY, String.valueOf(StaleOverlayGate.elapsedRealtime()));
        } else {
            SysPropHelper.set(OPENING_AT_PROPERTY, "0");
        }
    }

    /**
     * Publish painted UI — clears opening; keeps active_at as "now" so Y2 shell_visible lag
     * does not immediately wipe the gate (StaleOverlayGate UI_WITHOUT_SHELL).
     */
    public static void setOverlayUiVisible(boolean visible) {
        memUi = visible;
        SysPropHelper.set(UI_PROPERTY, visible ? "1" : "0");
        if (visible) {
            setOverlayOpening(false);
            // 2026-07-11 — Was: ACTIVE_AT=0 → StaleOverlayGate treated as instantly stale
            // when shell_visible prop lagged on Y2 → wiped active/ui before wheel ticks.
            SysPropHelper.set(ACTIVE_AT_PROPERTY,
                    String.valueOf(StaleOverlayGate.elapsedRealtime()));
        }
    }

    /** Prop-only arm without handler — used during provisional open window. */
    public static void setOverlayActive(boolean active) {
        clearLegacyActive();
        memActive = active;
        if (!active) {
            setOverlayUiVisible(false);
            SysPropHelper.set(ACTIVE_AT_PROPERTY, "0");
        } else {
            // Always stamp now — grace for shell_visible setprop latency on Y2.
            SysPropHelper.set(ACTIVE_AT_PROPERTY,
                    String.valueOf(StaleOverlayGate.elapsedRealtime()));
        }
        SysPropHelper.set(ACTIVE_PROPERTY, active ? "1" : "0");
    }

    /**
     * Bind key handler after a tier is painted — frontmost shell owns input.
     * Layman: keys now move the menu, not the app behind it.
     * 2026-07-11 — Force shell_visible + sync-critical props so Xposed forwards wheel on Y2.
     */
    public static void arm(Handler keyHandler) {
        clearLegacyActive();
        // Do not clearIfNeeded here — would race shell_visible async write from addView.
        setShellVisible(true);
        setOverlayActive(true);
        handler = keyHandler;
        setOverlayUiVisible(true);
        // Kick root setprop again for Xposed readers (system_server cannot see mem flags).
        SysPropHelper.forceCriticalOverlayProps("1");
    }

    /** Tear down ownership — foreground app gets keys again after brief cooldown. */
    public static void disarm() {
        handler = null;
        lastDeliverKeyCode = 0;
        lastDeliverAt = 0L;
        memActive = false;
        memUi = false;
        memOpening = false;
        memShellVisible = false;
        long now = SystemClock.uptimeMillis();
        SysPropHelper.set(COOLDOWN_UNTIL_PROPERTY, String.valueOf(now + POST_OVERLAY_COOLDOWN_MS));
        SysPropHelper.set(DISARM_PULSE_PROPERTY, String.valueOf(now));
        SysPropHelper.set(ACTIVE_AT_PROPERTY, "0");
        setOverlayOpening(false);
        setOverlayActive(false);
        setOverlayUiVisible(false);
        clearLegacyActive();
        if ("1".equals(SysPropHelper.get(ACTIVE_PROPERTY, "0"))) {
            SysPropHelper.set(ACTIVE_PROPERTY, "0");
            SysPropHelper.set(UI_PROPERTY, "0");
        }
    }

    public static boolean deliver(int keyCode) {
        if (isDuplicate(keyCode, KeyEvent.ACTION_DOWN)) {
            // #region agent log
            DebugSession083511.log("H3", "CompanionOverlayKeyGate.deliver",
                    "dedupe_down", "{\"keyCode\":" + keyCode + "}");
            // #endregion
            return true;
        }
        Handler h = handler;
        // 2026-07-10 — If props lagged but mem says shell is up, keep handler (do not drop).
        if (h == null && (memActive || memUi || memShellVisible)) {
            // 2026-07-14 — Was: swallow forever with dead handler → wheel/Back frozen under shell.
            // Now: disarm ghost gate so next open can re-arm; PWM stop-consuming after props clear.
            // Reversal: return true here again (leaks stuck modal with dead keys).
            // #region agent log
            DebugSession083511.log("H2", "CompanionOverlayKeyGate.deliver",
                    "null_handler_disarm", "{\"keyCode\":" + keyCode
                            + ",\"memActive\":" + memActive
                            + ",\"memUi\":" + memUi
                            + ",\"memShell\":" + memShellVisible + "}");
            // #endregion
            disarm();
            return true;
        }
        boolean handled = h != null && h.onKeyDown(keyCode);
        // #region agent log
        DebugSession083511.log("H2", "CompanionOverlayKeyGate.deliver",
                "down", "{\"keyCode\":" + keyCode + ",\"handled\":" + handled
                        + ",\"hasHandler\":" + (h != null) + "}");
        // #endregion
        return handled;
    }

    public static boolean deliverUp(int keyCode) {
        if (isDuplicate(keyCode, KeyEvent.ACTION_UP)) {
            // #region agent log
            DebugSession083511.log("H3", "CompanionOverlayKeyGate.deliverUp",
                    "dedupe_up", "{\"keyCode\":" + keyCode + "}");
            // #endregion
            return true;
        }
        Handler h = handler;
        if (h == null && (memActive || memUi || memShellVisible)) {
            // #region agent log
            DebugSession083511.log("H2", "CompanionOverlayKeyGate.deliverUp",
                    "null_handler_disarm", "{\"keyCode\":" + keyCode + "}");
            // #endregion
            disarm();
            return true;
        }
        boolean handled = h != null && h.onKeyUp(keyCode);
        // #region agent log
        DebugSession083511.log("H2", "CompanionOverlayKeyGate.deliverUp",
                "up", "{\"keyCode\":" + keyCode + ",\"handled\":" + handled
                        + ",\"hasHandler\":" + (h != null) + "}");
        // #endregion
        return handled;
    }

    public static boolean isKeysActive() {
        StaleOverlayGate.clearIfNeeded();
        boolean prop = "1".equals(SysPropHelper.get(ACTIVE_PROPERTY, "0"));
        boolean ui = "1".equals(SysPropHelper.get(UI_PROPERTY, "0"));
        boolean opening = "1".equals(SysPropHelper.get(OPENING_PROPERTY, "0"));
        // Keep handler when in-memory gate is live even if props never stuck.
        if (!prop && !ui && !opening && !memActive && !memUi && !memOpening && handler != null) {
            handler = null;
        }
        return prop || ui || opening || memActive || memUi || memOpening || handler != null;
    }

    /** True when WM shell is attached (prop or in-process). */
    public static boolean isShellVisibleMem() {
        return memShellVisible
                || "1".equals(SysPropHelper.get(SHELL_VISIBLE_PROPERTY, "0"));
    }

    public static void refreshLiveGate() {
        if (handler == null) return;
        setShellVisible(true);
        setOverlayActive(true);
        setOverlayUiVisible(true);
        SysPropHelper.forceCriticalOverlayProps("1");
    }

    private static boolean isDuplicate(int keyCode, int action) {
        long now = SystemClock.uptimeMillis();
        // 2026-07-10 — Wheel: WHEEL_DEDUPE_MS=0 → every tick delivered (Xposed forwards once).
        long window = isWheelNavKey(keyCode) ? WHEEL_DEDUPE_MS : DELIVER_DEDUPE_MS;
        if (keyCode == lastDeliverKeyCode && action == lastDeliverAction
                && now - lastDeliverAt < window) {
            return true;
        }
        lastDeliverKeyCode = keyCode;
        lastDeliverAction = action;
        lastDeliverAt = now;
        return false;
    }

    /** Host JUnit — expose dedupe window without calling SystemClock.uptimeMillis(). */
    static long dedupeWindowMsForKey(int keyCode) {
        return isWheelNavKey(keyCode) ? WHEEL_DEDUPE_MS : DELIVER_DEDUPE_MS;
    }

    /** Y1/Y2 scroll wheel keycodes — keep aligned with Y1InputKeys / ChipContextMenu. */
    private static boolean isWheelNavKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == 126 || keyCode == 127 || keyCode == 19 || keyCode == 20;
    }

    private static void clearLegacyActive() {
        if ("1".equals(SysPropHelper.get(LEGACY_ACTIVE_PROPERTY, "0"))) {
            SysPropHelper.set(LEGACY_ACTIVE_PROPERTY, "0");
        }
    }
}
