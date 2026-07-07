package com.solar.launcher.xposed.bridge;

import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — In-process JJ wheel remap when inject path is cold (MEDIA 126/127 → DPAD 21/22).
 * Layman: JJ sees left/right wheel keys natively without waiting for root inject.
 * Technical: gated by sys.solar.handoff.jj=1 and persist.solar.jj.xposed_shim default on.
 * Reversal: set persist.solar.jj.xposed_shim=0; inject-only path remains.
 */
final class JjInputHooks {

    private static final String JJ_PKG = "com.themoon.y1";
    private static final String JJ_ACTIVITY = "com.themoon.y1.MainActivity";
    private static final String PROP_XPOSED_SHIM = "persist.solar.jj.xposed_shim";
    private static final String PROP_JJ_HANDOFF = "sys.solar.handoff.jj";

    private JjInputHooks() {}

    static void install(LoadPackageParam lpparam) {
        if (lpparam == null || !JJ_PKG.equals(lpparam.packageName)) return;
        if (!isXposedShimEnabled()) return;
        try {
            Class<?> main = XposedHelpers.findClass(JJ_ACTIVITY, lpparam.classLoader);
            XposedHookKit.hookAll(main, "dispatchKeyEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isOverlayActive()) {
                        XposedHookKit.skipMethod(param);
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
            SolarContextBridge.log("hooked JJ MainActivity.dispatchKeyEvent wheel remap");
        } catch (Throwable t) {
            SolarContextBridge.log("JjInputHooks skip: " + t.getClass().getSimpleName());
        }
    }

    private static boolean isOverlayActive() {
        return "1".equals(readProp("sys.solar.overlay.active", "0"))
                || "1".equals(readProp("sys.solar.overlay.opening", "0"));
    }

    private static boolean isXposedShimEnabled() {
        return !"0".equals(readProp(PROP_XPOSED_SHIM, "1"));
    }

    private static boolean isJjHandoffActive() {
        return "1".equals(readProp(PROP_JJ_HANDOFF, "0"));
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
