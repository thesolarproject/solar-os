package com.solar.launcher.xposed.bridge;

import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-08 — In-process wheel remap for JJ + stock Innioasis HOME (MEDIA 126/127 → DPAD 21/22).
 * Layman: factory y1/y2 launcher sees left/right wheel keys like JJ without waiting for root inject.
 * Technical: gated by sys.solar.handoff.jj=1; JJ hooks MainActivity; Innioasis hooks Activity.
 * Reversal: JJ_PKG-only install; set persist.solar.jj.xposed_shim=0 for inject-only path.
 */
final class JjInputHooks {

    private static final String JJ_PKG = "com.themoon.y1";
    private static final String JJ_ACTIVITY = "com.themoon.y1.MainActivity";
    private static final String PROP_XPOSED_SHIM = "persist.solar.jj.xposed_shim";
    private static final String PROP_JJ_HANDOFF = "sys.solar.handoff.jj";
    /** 2026-07-08 — Saved HOME target (jj/stock/...) — survives Solar being pm-disabled. */
    private static final String PROP_HOME_TARGET = "persist.solar.home.target";

    private JjInputHooks() {}

    /**
     * 2026-07-08 — Arm remap in JJ or Innioasis stock launcher process only.
     * Reversal: JJ_PKG.equals check only (prior behaviour).
     */
    static void install(LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if (!isJjKeylayoutPackage(lpparam.packageName)) return;
        if (!isXposedShimEnabled()) return;
        if (JJ_PKG.equals(lpparam.packageName)) {
            installJjMainActivity(lpparam);
        } else {
            // Stock Innioasis HOME — activity class varies; hook framework Activity.
            installActivityDispatch(lpparam);
        }
    }

    /** True for com.themoon.y1 or com.innioasis.y1 / .y2 style HOME packages. */
    private static boolean isJjKeylayoutPackage(String pkg) {
        if (JJ_PKG.equals(pkg)) return true;
        return com.solar.input.policy.GlobalInputPolicy.isInnioasisStockLauncher(pkg);
    }

    /** JJ MainActivity.dispatchKeyEvent — known class name. */
    private static void installJjMainActivity(LoadPackageParam lpparam) {
        try {
            Class<?> main = XposedHelpers.findClass(JJ_ACTIVITY, lpparam.classLoader);
            hookDispatchKeyEvent(main);
            SolarContextBridge.log("hooked JJ MainActivity.dispatchKeyEvent wheel remap");
        } catch (Throwable t) {
            SolarContextBridge.log("JjInputHooks JJ skip: " + t.getClass().getSimpleName());
        }
    }

    /** Innioasis HOME — rewrite MEDIA wheel keys on any Activity in that process. */
    private static void installActivityDispatch(LoadPackageParam lpparam) {
        try {
            Class<?> activity = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
            hookDispatchKeyEvent(activity);
            SolarContextBridge.log("hooked Innioasis Activity.dispatchKeyEvent wheel remap pkg="
                    + lpparam.packageName);
        } catch (Throwable t) {
            SolarContextBridge.log("JjInputHooks innioasis skip: " + t.getClass().getSimpleName());
        }
    }

    private static void hookDispatchKeyEvent(Class<?> target) {
        XposedHookKit.hookAll(target, "dispatchKeyEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isOverlayActive()) {
                    return;
                }
                if (!isJjHandoffActive()) {
                    return;
                }
                KeyEvent event = (KeyEvent) param.args[0];
                if (event == null) return;
                int mapped = remapMediaToDpad(event.getKeyCode());
                if (mapped <= 0) return;
                KeyEvent rewritten = new KeyEvent(event.getDownTime(), event.getEventTime(),
                        event.getAction(), mapped, event.getRepeatCount(),
                        event.getMetaState(), event.getDeviceId(), event.getScanCode());
                param.args[0] = rewritten;
            }
        });
    }

    private static boolean isOverlayActive() {
        return "1".equals(readProp("sys.solar.overlay.active", "0"))
                || "1".equals(readProp("sys.solar.overlay.opening", "0"));
    }

    private static boolean isXposedShimEnabled() {
        return !"0".equals(readProp(PROP_XPOSED_SHIM, "1"));
    }

    /**
     * 2026-07-08 — Remap armed by Solar's runtime flag OR the saved HOME target.
     * Layman: wheel keeps scrolling in JJ/factory launcher even if the Solar app is off.
     * Technical: sys.solar.handoff.jj is written by Solar's ExternalInputHandoff — that process
     * may be pm-disabled or LMK-killed; persist.solar.home.target=jj|stock (set by root switch
     * scripts + boot init) is the launcher-of-record fallback, so this in-process hook stays
     * self-sufficient with zero Solar dependency.
     * Reversal: return the sys-prop check alone (prior behaviour — remap dies with Solar).
     */
    private static boolean isJjHandoffActive() {
        if ("1".equals(readProp(PROP_JJ_HANDOFF, "0"))) return true;
        return com.solar.input.policy.GlobalInputPolicy
                .isJjKeylayoutHomeTarget(readProp(PROP_HOME_TARGET, ""));
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = XposedHelpers.callStaticMethod(sp, "get", key, def);
            return String.valueOf(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private static int remapMediaToDpad(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            return KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        return 0;
    }
}
