package com.solar.launcher.xposed.bridge;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * system_server hooks: long BACK / OK in stock apps → Solar overlay; Y2 power menu → overlay.
 */
final class SystemServerHooks {

    private static final String PWM = "com.android.internal.policy.impl.PhoneWindowManager";
    private static final String GLOBAL_ACTIONS = "com.android.internal.policy.impl.GlobalActions";
    private static final long LONG_PRESS_MS = 600L;
    /** Ultra-long BACK — restart Solar from Rockbox / third-party apps (Solar handles in-app). */
    private static final long BACK_RESTART_SOLAR_MS = 6000L;
    /** PWM interceptKeyBeforeDispatching: return value >= 0 means key consumed. */
    private static final long KEY_CONSUMED_DISPATCH = 0L;
    /** PWM interceptKeyBeforeQueueing: return 0 drops the key before dispatch. */
    private static final int KEY_CONSUMED_QUEUE = 0;

    /** Must match {@link com.solar.launcher.OverlayKeyGate#DISARM_PULSE_PROPERTY}. */
    private static final String DISARM_PULSE_PROPERTY = "sys.solar.overlay.disarm_pulse";
    /** Must match {@link com.solar.launcher.OverlayKeyGate#COOLDOWN_UNTIL_PROPERTY}. */
    private static final String COOLDOWN_UNTIL_PROPERTY = "sys.solar.overlay.cooldown_until";
    /** Must match {@link com.solar.launcher.ExternalInputHandoff#HANDOFF_ACTIVE_PROPERTY}. */
    private static final String HANDOFF_ACTIVE_PROPERTY = "sys.solar.handoff.active";

    private static Handler mainHandler;
    private static HandlerThread mainHandlerThread;

    private static boolean backLongFired;
    private static boolean backRestartFired;
    private static boolean backTracking;
    private static boolean backUpHandled;
    /** Let synthetic short-BACK taps reach the app — do not re-enter long-press swallow. */
    private static volatile boolean ignoreBackHookForInject;
    private static Runnable backLongRunnable;
    private static Runnable backRestartRunnable;
    /** Foreground pkg captured on BACK DOWN — avoids getRunningTasks again at 600ms fire. */
    private static String cachedBackLongFg;

    private static boolean centerLongFired;
    private static boolean centerTracking;
    private static boolean centerUpHandled;
    /** Swallow re-entrant PWM hooks while a synthetic OK/MENU tap is injecting (async inject loops otherwise). */
    private static volatile boolean ignoreCenterHookForInject;
    private static Runnable centerLongRunnable;
    /** InputManager.injectInputEvent mode — async; never block PWM thread (causes System ANR). */
    private static final int INJECT_MODE_ASYNC = 0;
    /** Delay before clearing inject guard — covers async inject delivery on MTK (120ms was too short). */
    private static final long INJECT_GUARD_CLEAR_MS = 450L;
    /** Gap after UP before synthetic BACK inject — lets tracking state settle on queue+dispatch hooks. */
    private static final long BACK_INJECT_DELAY_MS = 30L;

    private static Object phoneWindowManagerRef;
    private static Object[] lastDispatchArgs;
    private static long lastDisarmPulseSeen;

    private SystemServerHooks() {}

    /** Y1: long BACK (no power key) + long OK in third-party apps → Solar global context modal. */
    static void installY1(LoadPackageParam lpparam) {
        Class<?> pwm = findPhoneWindowManager(lpparam);
        if (pwm == null) return;
        SolarContextBridge.log("Y1 PhoneWindowManager loaded");
        installLongPressHooks(pwm);
        VolumePanelHooks.installSystemServer(lpparam);
    }

    /** Y2: power-hold or long BACK + long OK in third-party apps → same global modal. */
    static void installY2(LoadPackageParam lpparam) {
        Class<?> pwm = findPhoneWindowManager(lpparam);
        if (pwm == null) return;
        SolarContextBridge.log("Y2 PhoneWindowManager loaded");
        hookSuppressGlobalActions(pwm, lpparam.classLoader);
        installLongPressHooks(pwm);
        VolumePanelHooks.installSystemServer(lpparam);
    }

    private static void installLongPressHooks(Class<?> pwm) {
        ensureMainHandler();
        initBackLongRunnable();
        initBackRestartRunnable();
        initCenterLongRunnable();
        hookBackLongPress(pwm);
        hookBackLongPressQueueing(pwm);
        hookCenterLongPress(pwm);
        hookCenterBeforeQueueing(pwm);
        hookBackBeforeQueueing(pwm);
        OverlayKeyForwarder.hookPhoneWindowManager(pwm);
    }

    /** HandlerThread — system_server may not have MainLooper when Xposed first hooks android. */
    private static void ensureMainHandler() {
        if (mainHandler != null) return;
        mainHandlerThread = new HandlerThread("SolarCtxKeys");
        mainHandlerThread.start();
        mainHandler = new Handler(mainHandlerThread.getLooper());
    }

    private static Class<?> findPhoneWindowManager(LoadPackageParam lpparam) {
        try {
            return XposedHelpers.findClass(PWM, lpparam.classLoader);
        } catch (Throwable t) {
            SolarContextBridge.log("PhoneWindowManager missing: " + t);
            return null;
        }
    }

    /** Y2 power-hold — Solar overlay in third-party apps; Rockbox/Solar/Innioasis keep stock menu. */
    private static void hookSuppressGlobalActions(Class<?> pwm, ClassLoader cl) {
        XC_MethodHook intercept = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                phoneWindowManagerRef = param.thisObject;
                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackage(ctx);
                // Solar foreground — in-app context menu at power tier (:overlay not needed).
                if ("com.solar.launcher".equals(fg)) {
                    SolarOverlayClient.showInAppPowerMenu(ctx);
                    param.setResult(null);
                    SolarContextBridge.log("suppressed GlobalActions → Solar in-app power");
                    return;
                }
                if (!shouldOfferOverlayForPackage(fg)) {
                    return;
                }
                showSolarPowerOverlay(param.thisObject);
                param.setResult(null);
                SolarContextBridge.log("suppressed GlobalActions → Solar overlay fg=" + fg);
            }
        };
        int n = 0;
        n += XposedHookKit.hookAll(pwm, "showGlobalActionsDialog", intercept);
        n += XposedHookKit.hookAll(pwm, "showGlobalActionsInternal", intercept);
        try {
            Class<?> ga = XposedHelpers.findClass(GLOBAL_ACTIONS, cl);
            n += XposedHookKit.hookAll(ga, "showDialog", intercept);
            n += XposedHookKit.hookAll(ga, "handleShow", intercept);
            n += XposedHookKit.hookAll(ga, "prepareDialog", intercept);
        } catch (Throwable t) {
            SolarContextBridge.log("GlobalActions class missing: " + t.getClass().getSimpleName());
        }
        SolarContextBridge.log("GlobalActions hooks=" + n);
    }

    private static void initBackLongRunnable() {
        if (backLongRunnable != null) return;
        backLongRunnable = new Runnable() {
            @Override
            public void run() {
                backLongFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = cachedBackLongFg;
                if (!shouldOfferOverlayForPackage(fg)) {
                    SolarContextBridge.log("back-long skipped fg=" + fg);
                    return;
                }
                SolarOverlayClient.showPowerOverlay(ctx);
                SolarContextBridge.log("back-long overlay fg=" + fg);
            }
        };
    }

    /** Schedule 6s BACK hold → force-stop Solar and relaunch HOME (Rockbox + stock apps). */
    private static void initBackRestartRunnable() {
        if (backRestartRunnable != null) return;
        backRestartRunnable = new Runnable() {
            @Override
            public void run() {
                if (!backTracking) return;
                backRestartFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                SolarRestartClient.restartSolarApp(ctx);
                SolarContextBridge.log("back-ultra restart Solar");
            }
        };
    }

    private static void initCenterLongRunnable() {
        if (centerLongRunnable != null) return;
        centerLongRunnable = new Runnable() {
            @Override
            public void run() {
                centerLongFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = foregroundPackageFromArgs(lastDispatchArgs, ctx, phoneWindowManagerRef);
                if (!shouldOfferOverlayForPackage(fg)) return;
                if (openOptionsMenuForForeground(ctx)) {
                    SolarContextBridge.log("center-long openOptionsMenu fg=" + fg);
                } else {
                    injectKeyEvent(KeyEvent.KEYCODE_MENU);
                    SolarContextBridge.log("center-long injected MENU fg=" + fg);
                }
            }
        };
    }

    /** Swallow BACK down/repeat/up until finger lifts after long-press opened the overlay. */
    private static boolean consumeBackHeldAfterLongOpen(XC_MethodHook.MethodHookParam param,
            Object[] args, Object consumeResult) {
        absorbOverlayDisarmPulse();
        if (!backLongFired && !backRestartFired) return false;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clearBackLongPressState();
            SolarContextBridge.log("back-long release swallowed");
        }
        param.setResult(consumeResult);
        return true;
    }

    /** Swallow OK/MENU until finger lifts after long-press opened the app context menu. */
    private static boolean consumeCenterHeldAfterLongOpen(XC_MethodHook.MethodHookParam param,
            Object[] args, Object consumeResult) {
        absorbOverlayDisarmPulse();
        if (!centerLongFired) return false;
        KeyEvent event = findKeyEvent(args);
        if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clearCenterLongPressState();
            SolarContextBridge.log("center-long release swallowed");
        }
        param.setResult(consumeResult);
        return true;
    }

    /** Swallow BACK down/repeat while detecting long-press; inject short back on quick release. */
    private static void hookBackLongPress(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_DISPATCH)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (handleBackLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_DISPATCH);
                }
            }
        });
        SolarContextBridge.log("hooked BACK long-press on interceptKeyBeforeDispatching");
    }

    /** MTK Y1 often routes BACK only through queueing — mirror long-press detection here. */
    private static void hookBackLongPressQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (handleBackLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_QUEUE);
                }
            }
        });
        SolarContextBridge.log("hooked BACK long-press on interceptKeyBeforeQueueing");
    }

    /** Shared BACK long-press state machine for dispatch + queueing hooks. */
    private static boolean handleBackLongPressEvent(Object pwmThis, Object[] args) {
        if (ignoreBackHookForInject) {
            return false;
        }
        absorbOverlayDisarmPulse();
        phoneWindowManagerRef = pwmThis;
        lastDispatchArgs = args;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;

        int action = event.getAction();
        Context ctx = resolveContext(pwmThis);
        String fg;
        if (action == KeyEvent.ACTION_DOWN || !backTracking) {
            long fgT0 = System.nanoTime();
            fg = foregroundPackageForBackLong(args, ctx, pwmThis);
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("fg", fg != null ? fg : "");
                d.put("backTracking", backTracking);
                BridgeAnrDebugLog.hookTiming("SystemServerHooks.backLong.fgLookup", "A", fgT0, d);
            } catch (Throwable ignored) {}
            // #endregion
        } else {
            fg = cachedBackLongFg;
        }
        // Solar in-app handles BACK short/long/restart — never steal here.
        if ("com.solar.launcher".equals(fg)) {
            clearBackLongPressState();
            return false;
        }
        // Ultra-long BACK restart — Rockbox, stock, and third-party apps.
        if (!shouldTrackBackUltraLong(fg)) {
            clearBackLongPressState();
            return false;
        }
        boolean overlayEligible = shouldOfferOverlayForPackage(fg);

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && !backTracking) {
                backLongFired = false;
                backRestartFired = false;
                backUpHandled = false;
                backTracking = true;
                cachedBackLongFg = fg;
                mainHandler.removeCallbacks(backLongRunnable);
                mainHandler.removeCallbacks(backRestartRunnable);
                mainHandler.postDelayed(backRestartRunnable, BACK_RESTART_SOLAR_MS);
                if (overlayEligible) {
                    SolarOverlayClient.warmOverlayProcess(ctx);
                    mainHandler.postDelayed(backLongRunnable, LONG_PRESS_MS);
                }
            }
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            mainHandler.removeCallbacks(backLongRunnable);
            mainHandler.removeCallbacks(backRestartRunnable);
            if (backRestartFired || backLongFired) {
                backRestartFired = false;
                backLongFired = false;
                backTracking = false;
                return true;
            }
            if (backTracking && !backUpHandled) {
                backTracking = false;
                backUpHandled = true;
                scheduleBackInject();
            }
            return true;
        }
        return backTracking;
    }

    /** Long OK / center / MENU — open app options menu; AppMenuHooks redecorates the rows. */
    private static void hookCenterLongPress(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (ignoreCenterHookForInject) {
                    KeyEvent synth = findKeyEvent(param.args);
                    if (synth != null && isCenterOrMenuKey(synth.getKeyCode())) {
                        // #region agent log
                        SolarContextBridge.log("edc27b OK-SYNTH swallow action=" + synth.getAction());
                        // #endregion
                        param.setResult(KEY_CONSUMED_DISPATCH);
                        return;
                    }
                }
                if (consumeCenterHeldAfterLongOpen(param, param.args, KEY_CONSUMED_DISPATCH)) {
                    return;
                }
                absorbOverlayDisarmPulse();
                if (OverlayKeyForwarder.isOverlayActive()) return;
                // Wheel handoff injects via RootKeyInjector — pass hardware OK through unchanged.
                if (isHandoffInjectActive()) return;
                phoneWindowManagerRef = param.thisObject;
                lastDispatchArgs = param.args;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return;

                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageFromArgs(param.args, ctx, param.thisObject);
                if (!shouldOfferOverlayForPackage(fg)) return;

                int action = event.getAction();
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        centerLongFired = false;
                        centerTracking = true;
                        centerUpHandled = false;
                        mainHandler.removeCallbacks(centerLongRunnable);
                        mainHandler.postDelayed(centerLongRunnable, LONG_PRESS_MS);
                    }
                    param.setResult(KEY_CONSUMED_DISPATCH);
                    return;
                }
                if (action == KeyEvent.ACTION_UP) {
                    mainHandler.removeCallbacks(centerLongRunnable);
                    param.setResult(KEY_CONSUMED_DISPATCH);
                    if (centerLongFired) {
                        centerLongFired = false;
                        centerTracking = false;
                        centerUpHandled = false;
                        return;
                    }
                    if (centerTracking && !centerUpHandled) {
                        centerTracking = false;
                        centerUpHandled = true;
                        injectKeyEvent(event.getKeyCode());
                    }
                }
            }
        });
        SolarContextBridge.log("hooked OK/center long-press on interceptKeyBeforeDispatching");
    }

    /** MTK Y2 queues center before dispatch — drop it while long-press detection runs. */
    private static void hookCenterBeforeQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (ignoreCenterHookForInject) {
                    KeyEvent synth = findKeyEvent(param.args);
                    if (synth != null && isCenterOrMenuKey(synth.getKeyCode())) {
                        param.setResult(KEY_CONSUMED_QUEUE);
                        return;
                    }
                }
                if (consumeCenterHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                absorbOverlayDisarmPulse();
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (isHandoffInjectActive()) return;
                if (!centerTracking && !centerLongFired) return;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return;
                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageFromArgs(param.args, ctx, param.thisObject);
                if (!shouldOfferOverlayForPackage(fg)) {
                    clearCenterLongPressState();
                    return;
                }
                param.setResult(KEY_CONSUMED_QUEUE);
            }
        });
        SolarContextBridge.log("hooked OK/center swallow on interceptKeyBeforeQueueing");
    }

    /** True while Solar is remapping wheel/side MEDIA keys into a stock app (blocks OK re-inject). */
    private static boolean isHandoffInjectActive() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", HANDOFF_ACTIVE_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    /** MTK paths may queue BACK before dispatch — drop it while long-press detection runs. */
    private static void hookBackBeforeQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (ignoreBackHookForInject) return;
                if (!backTracking && !backLongFired && !backRestartFired) return;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return;
                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageForBackLong(param.args, ctx, param.thisObject);
                if (!shouldTrackBackUltraLong(fg)) {
                    clearBackLongPressState();
                    return;
                }
                param.setResult(KEY_CONSUMED_QUEUE);
            }
        });
        SolarContextBridge.log("hooked BACK swallow on interceptKeyBeforeQueueing");
    }

    /** Reset long-press detector when foreground leaves third-party overlay targets. */
    private static void clearBackLongPressState() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(backLongRunnable);
            mainHandler.removeCallbacks(backRestartRunnable);
        }
        backLongFired = false;
        backRestartFired = false;
        backTracking = false;
        backUpHandled = false;
    }

    /** Reset center/OK long-press after overlay dismiss — blocks spurious MENU inject. */
    private static void clearCenterLongPressState() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(centerLongRunnable);
        }
        centerLongFired = false;
        centerTracking = false;
        centerUpHandled = false;
    }

    /** Overlay disarm pulse from Solar — drop stale BACK/center tracking from the open gesture. */
    static void absorbOverlayDisarmPulse() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", DISARM_PULSE_PROPERTY, "0");
            long pulse = Long.parseLong(String.valueOf(v));
            if (pulse > 0 && pulse != lastDisarmPulseSeen) {
                lastDisarmPulseSeen = pulse;
                clearBackLongPressState();
                clearCenterLongPressState();
                SolarContextBridge.log("overlay disarm pulse cleared long-press state");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * BACK long-press still held after opening overlay — do not forward to modal dismiss handler.
     * Power-hold opens without BACK tracking so this stays false for that path.
     */
    static boolean shouldBlockBackForwardToOverlay() {
        return backLongFired;
    }

    /**
     * OK/MENU long-press still held after app menu opened — do not forward repeats to overlay.
     */
    static boolean shouldBlockCenterForwardToOverlay() {
        return centerLongFired;
    }

    /** True briefly after global modal dismiss — swallow BACK so stock apps never see dismiss gesture. */
    static boolean isPostOverlayCooldown() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", COOLDOWN_UNTIL_PROPERTY, "0");
            long until = Long.parseLong(String.valueOf(v));
            return until > 0 && SystemClock.uptimeMillis() < until;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isCenterOrMenuKey(int keyCode) {
        return Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU;
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof KeyEvent) return (KeyEvent) arg;
        }
        return null;
    }

    /** Post synthetic short BACK after hardware UP — avoids re-entering long-press swallow on inject. */
    private static void scheduleBackInject() {
        if (mainHandler == null) {
            injectKeyEvent(KeyEvent.KEYCODE_BACK);
            return;
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                injectKeyEvent(KeyEvent.KEYCODE_BACK);
            }
        }, BACK_INJECT_DELAY_MS);
    }

    /** Inject a short tap so apps still get quick BACK / OK after we swallowed the real key. */
    private static void injectKeyEvent(int keyCode) {
        // Post-overlay cooldown blocks MENU/OK inject only — short BACK must still reach apps/dialogs.
        if (isPostOverlayCooldown() && keyCode != KeyEvent.KEYCODE_BACK) return;
        if (OverlayKeyForwarder.isOverlayActive() && keyCode != KeyEvent.KEYCODE_BACK) return;
        boolean centerLike = isCenterOrMenuKey(keyCode);
        boolean backLike = keyCode == KeyEvent.KEYCODE_BACK;
        if (centerLike) {
            ignoreCenterHookForInject = true;
        }
        if (backLike) {
            ignoreBackHookForInject = true;
        }
        try {
            Class<?> imClass = XposedHelpers.findClass("android.hardware.input.InputManager", null);
            Object im = XposedHelpers.callStaticMethod(imClass, "getInstance");
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0);
            // #region agent log
            if (centerLike) {
                SolarContextBridge.log("edc27b OK-INJECT code=" + keyCode);
            }
            if (backLike) {
                SolarContextBridge.log("edc27b BACK-INJECT code=" + keyCode);
            }
            // #endregion
            XposedHelpers.callMethod(im, "injectInputEvent", down, INJECT_MODE_ASYNC);
            XposedHelpers.callMethod(im, "injectInputEvent", up, INJECT_MODE_ASYNC);
        } catch (Throwable t) {
            SolarContextBridge.log("injectKey failed code=" + keyCode + ": " + t.getClass().getSimpleName());
            if (centerLike) {
                ignoreCenterHookForInject = false;
            }
            if (backLike) {
                ignoreBackHookForInject = false;
            }
        }
        if (centerLike || backLike) {
            scheduleInjectGuardClear(centerLike, backLike);
        }
    }

    /** Clear inject re-entry guards after async inject — must not run on PWM thread synchronously. */
    private static void scheduleInjectGuardClear(final boolean centerLike, final boolean backLike) {
        if (mainHandler == null) {
            if (centerLike) ignoreCenterHookForInject = false;
            if (backLike) ignoreBackHookForInject = false;
            return;
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (centerLike) ignoreCenterHookForInject = false;
                if (backLike) ignoreBackHookForInject = false;
            }
        }, INJECT_GUARD_CLEAR_MS);
    }

    /** Walk ActivityThread.mActivities for the resumed Activity — openOptionsMenu triggers MenuDialogHelper. */
    @SuppressWarnings("unchecked")
    private static boolean openOptionsMenuForForeground(Context ctx) {
        try {
            Class<?> atm = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object thread = XposedHelpers.callStaticMethod(atm, "currentActivityThread");
            if (thread == null) return false;
            Object mapObj = XposedHelpers.getObjectField(thread, "mActivities");
            if (!(mapObj instanceof Map)) return false;
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            for (Object record : map.values()) {
                if (record == null) continue;
                Object paused = XposedHelpers.getObjectField(record, "paused");
                if (Boolean.TRUE.equals(paused)) continue;
                Object activity = XposedHelpers.getObjectField(record, "activity");
                if (activity == null) continue;
                XposedHelpers.callMethod(activity, "openOptionsMenu");
                return true;
            }
        } catch (Throwable t) {
            SolarContextBridge.log("openOptionsMenu failed: " + t.getClass().getSimpleName());
        }
        return false;
    }

    static Context resolveContext(Object hookThis) {
        if (hookThis != null) {
            try {
                Object ctx = XposedHelpers.getObjectField(hookThis, "mContext");
                if (ctx instanceof Context) return (Context) ctx;
            } catch (Throwable ignored) {}
        }
        return currentContext();
    }

    private static void showSolarPowerOverlay(Object hookThis) {
        Context ctx = resolveContext(hookThis);
        if (ctx == null) {
            ctx = currentContext();
        }
        if (ctx == null) {
            SolarContextBridge.log("showPowerOverlay: no context");
            return;
        }
        SolarContextBridge.log("showPowerOverlay ctx=" + ctx.getPackageName());
        SolarOverlayClient.showPowerOverlay(ctx);
    }

    static Context currentContext() {
        try {
            return (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Foreground for BACK-long eligibility — window focus + running tasks only.
     * No process-list fallback (returns systemui/android and breaks short BACK inject).
     * When this returns null, pass hardware BACK through; root evdev daemon handles long-press.
     */
    private static String foregroundPackageForBackLong(Object[] args, Context ctx, Object pwm) {
        if (args != null) {
            for (Object arg : args) {
                String pkg = packageFromWindowState(arg);
                if (pkg != null) return pkg;
            }
        }
        String pkg = foregroundPackageFromPwm(pwm);
        if (pkg != null) return pkg;
        return foregroundPackageFromTasks(ctx);
    }

    /** RunningTasks only — used for BACK-long; avoids noisy getRunningAppProcesses. */
    @SuppressWarnings("deprecation")
    private static String foregroundPackageFromTasks(Context ctx) {
        if (ctx == null) return null;
        try {
            Object am = ctx.getSystemService("activity");
            if (am == null) return null;
            List<?> tasks = (List<?>) XposedHelpers.callMethod(am, "getRunningTasks", 1);
            if (tasks == null || tasks.isEmpty()) return null;
            Object task = tasks.get(0);
            Object base = XposedHelpers.callMethod(task, "baseActivity");
            if (base == null) base = XposedHelpers.callMethod(task, "topActivity");
            if (base == null) return null;
            return (String) XposedHelpers.callMethod(base, "getPackageName");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Prefer focused WindowState / PWM focus — fall back to tasks then foreground processes (KitKat). */
    private static String foregroundPackageFromArgs(Object[] args, Context ctx, Object pwm) {
        String pkg = foregroundPackageForBackLong(args, ctx, pwm);
        if (pkg != null) return pkg;
        return foregroundPackage(ctx);
    }

    /** Read package from a WindowState arg (dispatch hook) or mFocusedWindow (queueing hook). */
    private static String packageFromWindowState(Object winState) {
        if (winState == null) return null;
        try {
            String pkg = (String) XposedHelpers.callMethod(winState, "getOwningPackage");
            if (pkg != null && pkg.length() > 0) return pkg;
        } catch (Throwable ignored) {}
        try {
            Object attrs = XposedHelpers.getObjectField(winState, "mAttrs");
            if (attrs != null) {
                String pkg = (String) XposedHelpers.getObjectField(attrs, "packageName");
                if (pkg != null && pkg.length() > 0) return pkg;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** PhoneWindowManager focus window — queueing hook args are often KeyEvent-only. */
    private static String foregroundPackageFromPwm(Object pwm) {
        if (pwm == null) return null;
        String[] focusFields = {
                "mFocusedWindow",
                "mTopFullscreenOpaqueWindowState",
                "mTopFullscreenOpaqueOrDimmingWindowState"
        };
        for (String field : focusFields) {
            try {
                Object win = XposedHelpers.getObjectField(pwm, field);
                String pkg = packageFromWindowState(win);
                if (pkg != null) return pkg;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    static String foregroundPackage(Context ctx) {
        long t0 = System.nanoTime();
        String pkg = null;
        if (ctx == null) return null;
        try {
            Object am = ctx.getSystemService("activity");
            if (am == null) return null;
            List<?> tasks = (List<?>) XposedHelpers.callMethod(am, "getRunningTasks", 1);
            if (tasks != null && !tasks.isEmpty()) {
                Object task = tasks.get(0);
                Object base = XposedHelpers.callMethod(task, "baseActivity");
                if (base == null) base = XposedHelpers.callMethod(task, "topActivity");
                if (base != null) {
                    pkg = (String) XposedHelpers.callMethod(base, "getPackageName");
                    if (pkg != null) return pkg;
                }
            }
            // getRunningTasks often empty on KitKat from system_server — use foreground process list.
            List<?> procs = (List<?>) XposedHelpers.callMethod(am, "getRunningAppProcesses");
            if (procs != null) {
                for (Object proc : procs) {
                    if (proc == null) continue;
                    int importance = (Integer) XposedHelpers.getObjectField(proc, "importance");
                    if (importance != 100) continue; // IMPORTANCE_FOREGROUND
                    String[] pkgs = (String[]) XposedHelpers.getObjectField(proc, "pkgList");
                    if (pkgs == null) continue;
                    for (String candidate : pkgs) {
                        if (candidate != null && candidate.length() > 0
                                && !isSystemShellPackage(candidate)) {
                            pkg = candidate;
                            return pkg;
                        }
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("pkg", pkg != null ? pkg : "");
                BridgeAnrDebugLog.hookTiming("SystemServerHooks.foregroundPackage", "A", t0, d);
            } catch (Throwable ignored) {}
            // #endregion
        }
    }

    static boolean isSolarForeground(Context ctx) {
        return "com.solar.launcher".equals(foregroundPackage(ctx));
    }

    /**
     * BACK-long and Y2 power-hold eligibility — mirrors {@code GlobalOverlayPolicy} in the Solar app.
     * Rockbox excluded on Y1 and Y2 (use switch-to-stock to return to Solar).
     */
    static boolean shouldOfferOverlayForPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        if (isSystemShellPackage(pkg)) return false;
        if ("org.rockbox".equals(pkg)) return false;
        if ("com.solar.launcher".equals(pkg)) return false;
        if (pkg.startsWith("com.innioasis.")) return false;
        return true;
    }

    /** Ultra-long BACK restart — any real app except Solar (in-app handler) and system shells. */
    static boolean shouldTrackBackUltraLong(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        if (isSystemShellPackage(pkg)) return false;
        if ("com.solar.launcher".equals(pkg)) return false;
        return true;
    }

    /** Process-list noise — never treat as third-party app for BACK-long intercept. */
    private static boolean isSystemShellPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return true;
        if ("android".equals(pkg)) return true;
        if (pkg.startsWith("com.android.systemui")) return true;
        if (pkg.startsWith("com.android.keyguard")) return true;
        if (pkg.startsWith("com.android.inputmethod")) return true;
        return false;
    }
}
