package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Forward hardware keys to Solar while the global overlay is active (sys.solar.overlay.active=1).
 * WM overlay is NOT_FOCUSABLE — all navigation must flow through here into OverlayKeyReceiver.
 */
final class OverlayKeyForwarder {

    /** Must match {@link com.solar.launcher.OverlayKeyGate#ACTIVE_PROPERTY}. */
    static final String ACTIVE_PROPERTY = "sys.solar.overlay.active";
    /** Legacy persist prop — auto-cleared by Solar; never swallow Rockbox when this alone is set. */
    private static final String LEGACY_ACTIVE_PROPERTY = "persist.solar.overlay.active";
    static final String ACTION_OVERLAY_KEY = "com.solar.launcher.action.OVERLAY_KEY";
    static final String EXTRA_KEY_CODE = "overlay_key_code";
    static final String EXTRA_KEY_ACTION = "overlay_key_action";
    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String KEY_RECEIVER = SOLAR_PKG + ".OverlayKeyReceiver";
    private static final String OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";

    private OverlayKeyForwarder() {}

    static void hookPhoneWindowManager(Class<?> pwm) {
        XC_MethodHook overlayHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleOverlayKeyIntercept(param, false);
            }
        };
        XC_MethodHook queueHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleOverlayKeyIntercept(param, true);
            }
        };
        try {
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", overlayHook);
            // MTK Y1/Y2 often queue wheel/back before dispatch — forward from both hooks.
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", queueHook);
            SolarContextBridge.log("hooked overlay key capture on PhoneWindowManager");
        } catch (Throwable t) {
            SolarContextBridge.log("overlay key hook failed: " + t);
        }
    }

    /** Shared intercept — swallow + forward so NOT_FOCUSABLE overlay receives wheel/back/center. */
    private static void handleOverlayKeyIntercept(XC_MethodHook.MethodHookParam param, boolean queueing) {
        long t0 = System.nanoTime();
        boolean forwarded = false;
        int keyCode = 0;
        try {
        KeyEvent event = findKeyEvent(param.args);
        // Hardware volume always goes to AudioService first — passive HUD only refreshes via hooks.
        if (event != null && isHardwareVolumeKey(event.getKeyCode())) {
            return;
        }
        // Overlay dismisses on BACK down — swallow BACK until cooldown ends so app never navigates back.
        if (event != null && Y1InputKeysBridge.isBackKey(event.getKeyCode())
                && SystemServerHooks.isPostOverlayCooldown()) {
            SolarContextBridge.log("edc27b swallow post-dismiss BACK action=" + event.getAction());
            param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            return;
        }
        if (!isOverlayActive()) {
            if (!queueing) {
                SystemServerHooks.absorbOverlayDisarmPulse();
            }
            return;
        }
        if (event == null) return;
        Context ctx = SystemServerHooks.resolveContext(param.thisObject);
        String fg = SystemServerHooks.foregroundPackage(ctx);
        if (!shouldCaptureOverlayKeys(fg)) {
            // #region agent log
            SolarContextBridge.log("edc27b skip capture fg=" + fg + " key=" + event.getKeyCode());
            // #endregion
            return;
        }
        // #region agent log
        if ("org.rockbox".equals(fg)) {
            SolarContextBridge.log("edc27b RB-CAPTURE key=" + event.getKeyCode()
                    + " action=" + event.getAction() + " queue=" + queueing);
        }
        // #endregion
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return;
        }
        keyCode = event.getKeyCode();
        // Back always goes to overlay dismiss handler — except while open-gesture finger is still down.
        if (Y1InputKeysBridge.isBackKey(keyCode)) {
            if (SystemServerHooks.shouldBlockBackForwardToOverlay()) {
                // #region agent log
                SolarContextBridge.log("edc27b BACK-OPEN-HOLD swallow action=" + action
                        + " queue=" + queueing);
                // #endregion
                param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
                return;
            }
            if (ctx != null) {
                forwardKey(ctx, keyCode, action);
                forwarded = true;
            }
            param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            return;
        }
        if (!shouldForwardOverlayKey(keyCode, action, event)) {
            return;
        }
        if (Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU) {
            if (SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
                // #region agent log
                SolarContextBridge.log("edc27b OK-OPEN-HOLD swallow action=" + action
                        + " queue=" + queueing);
                // #endregion
                param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
                return;
            }
        }
        if (ctx != null) {
            forwardKey(ctx, keyCode, action);
            forwarded = true;
        }
        param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
        } finally {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("queueing", queueing);
                d.put("forwarded", forwarded);
                d.put("keyCode", keyCode);
                d.put("overlayActive", isOverlayActive());
                BridgeAnrDebugLog.hookTiming("OverlayKeyForwarder.handleOverlayKeyIntercept", "B", t0, d);
            } catch (Throwable ignored) {}
            // #endregion
        }
    }

    /**
     * App-process hook entry — used when PWM misses keys (Dialog focus, MTK media-key routing).
     * @return true when the foreground app/dialog must not see this key.
     */
    static boolean tryForwardFromAppContext(Context ctx, KeyEvent event) {
        if (event == null) return false;
        if (!isOverlayActive()) {
            if (Y1InputKeysBridge.isBackKey(event.getKeyCode())
                    && SystemServerHooks.isPostOverlayCooldown()) {
                return true;
            }
            return false;
        }
        if (Y1InputKeysBridge.isBackKey(event.getKeyCode())
                && SystemServerHooks.shouldBlockBackForwardToOverlay()) {
            return true;
        }
        if ((Y1InputKeysBridge.isCenterKey(event.getKeyCode())
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU)
                && SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
            return true;
        }
        return tryForwardFromAppContext(ctx, event.getKeyCode(), event.getAction(), event.getRepeatCount());
    }

    /** Primitive keyCode/action — for onKeyDown/onKeyUp hooks without a full KeyEvent. */
    static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action) {
        return tryForwardFromAppContext(ctx, keyCode, action, 0);
    }

    /** keyCode/action with repeat count — skips OK repeat storms while overlay is up. */
    static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action, int repeatCount) {
        if (ctx == null) return false;
        if (!isOverlayActive()) {
            // Dismiss uses BACK down — block the matching up/repeat from reaching the stock Activity.
            if (Y1InputKeysBridge.isBackKey(keyCode) && SystemServerHooks.isPostOverlayCooldown()) {
                return true;
            }
            return false;
        }
        String pkg = null;
        try {
            pkg = ctx.getPackageName();
        } catch (Throwable ignored) {}
        if (pkg == null || pkg.length() == 0) return false;
        if (!shouldCaptureOverlayKeys(pkg)) return false;
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return false;
        }
        if (Y1InputKeysBridge.isBackKey(keyCode)
                && SystemServerHooks.shouldBlockBackForwardToOverlay()) {
            return true;
        }
        if ((Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU)
                && SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
            return true;
        }
        if (action == KeyEvent.ACTION_UP && Y1InputKeysBridge.isWheelKey(keyCode)) {
            // #region agent log
            SolarContextBridge.log("edc27b app consume wheel up key=" + keyCode + " pkg=" + pkg);
            // #endregion
            return true;
        }
        if (!shouldForwardOverlayKey(keyCode, action, repeatCount)) {
            return false;
        }
        forwardKey(ctx, keyCode, action);
        // #region agent log
        SolarContextBridge.log("edc27b app forward key=" + keyCode + " action=" + action + " pkg=" + pkg);
        // #endregion
        return true;
    }

    /** Whether this key event should be forwarded to Solar overlay service. */
    private static boolean shouldForwardOverlayKey(int keyCode, int action, KeyEvent event) {
        int repeat = event != null ? event.getRepeatCount() : 0;
        return shouldForwardOverlayKey(keyCode, action, repeat);
    }

    private static boolean shouldForwardOverlayKey(int keyCode, int action, int repeatCount) {
        if (Y1InputKeysBridge.isBackKey(keyCode)) return true;
        if (action == KeyEvent.ACTION_DOWN && repeatCount > 0) {
            if (Y1InputKeysBridge.isCenterKey(keyCode) || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                return false;
            }
        }
        if (action == KeyEvent.ACTION_UP) {
            return isOverlayNavigationKeyUp(keyCode);
        }
        return isOverlayNavigationKey(keyCode);
    }

    /** Third-party, Rockbox (Y2 power tier), and Solar — not stock Innioasis shells. */
    private static boolean shouldCaptureOverlayKeys(String fg) {
        if (fg == null || fg.length() == 0) return false;
        if ("com.solar.launcher".equals(fg)) return true;
        if ("org.rockbox".equals(fg)) return true;
        if (fg.startsWith("com.innioasis.")) return false;
        return true;
    }

    private static final long KEY_CONSUMED_DISPATCH = -1L;
    private static final int KEY_CONSUMED_QUEUE = 0;

    static boolean isOverlayActive() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", ACTIVE_PROPERTY, "0");
            if ("1".equals(String.valueOf(v))) return true;
            // Legacy stuck persist — treat as inactive so Rockbox keeps back/OK.
            Object legacy = XposedHelpers.callStaticMethod(sp, "get", LEGACY_ACTIVE_PROPERTY, "0");
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void forwardKey(Context ctx, int keyCode, int action) {
        // startService is more reliable than broadcast from system_server on JB/KK.
        if (ctx != null) {
            try {
                Intent svc = new Intent(ACTION_OVERLAY_KEY);
                svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
                svc.putExtra(EXTRA_KEY_CODE, keyCode);
                svc.putExtra(EXTRA_KEY_ACTION, action);
                ctx.startService(svc);
                // #region agent log
                SolarContextBridge.log("edc27b forward svc key=" + keyCode + " action=" + action);
                // #endregion
                return;
            } catch (Throwable t) {
                SolarContextBridge.log("overlay key startService failed: "
                        + t.getClass().getSimpleName());
            }
        }
        Intent intent = new Intent(ACTION_OVERLAY_KEY);
        intent.setComponent(new ComponentName(SOLAR_PKG, KEY_RECEIVER));
        intent.putExtra(EXTRA_KEY_CODE, keyCode);
        intent.putExtra(EXTRA_KEY_ACTION, action);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            if (ctx != null) {
                ctx.sendBroadcast(intent);
            }
            // #region agent log
            SolarContextBridge.log("edc27b forward bcast key=" + keyCode + " action=" + action);
            // #endregion
        } catch (Throwable t) {
            SolarContextBridge.log("overlay key forward failed: " + t.getClass().getSimpleName());
        }
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof KeyEvent) return (KeyEvent) arg;
        }
        return null;
    }

    /** Wheel, center/OK, side skip, dpad horizontal, volume, play/pause while overlay is up. */
    private static boolean isOverlayNavigationKey(int keyCode) {
        return Y1InputKeysBridge.isWheelKey(keyCode)
                || Y1InputKeysBridge.isCenterKey(keyCode)
                || Y1InputKeysBridge.isTrackSkipKey(keyCode)
                || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    }

    /** KEY_UP for center, play/pause, and side skip — consume so foreground app does not act on release. */
    private static boolean isOverlayNavigationKeyUp(int keyCode) {
        return Y1InputKeysBridge.isCenterKey(keyCode)
                || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                || Y1InputKeysBridge.isTrackSkipKey(keyCode);
    }

    /** Dedicated volume buttons — never steal from AudioService (passive HUD syncs via VolumePanelHooks). */
    private static boolean isHardwareVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == 160 || keyCode == 161;
    }
}
