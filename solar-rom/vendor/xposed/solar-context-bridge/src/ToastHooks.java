package com.solar.launcher.xposed.bridge;

import android.content.Context;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-07 — Replace stock Holo Toast with Solar passive hint overlay when delivery succeeds.
 * Layman: brief messages use the same themed bar as volume changes — not tiny system toasts.
 * Technical: hook Toast.show in app processes; fail-open to stock when overlay host missing.
 * Reversal: remove install() from SolarContextBridge — stock Toast returns.
 */
final class ToastHooks {

    private static final int LENGTH_SHORT_MS = 2000;
    private static final int LENGTH_LONG_MS = 3500;

    private ToastHooks() {}

    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> toast = XposedHelpers.findClass("android.widget.Toast", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(toast, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Toast t = (Toast) param.thisObject;
                    if (trySolarToast(t)) {
                        param.setResult(null);
                    }
                }
            });
            SolarContextBridge.log("hooked Toast.show in " + lpparam.packageName);
        } catch (Throwable t) {
            SolarContextBridge.log("Toast.show skip " + lpparam.packageName + ": "
                    + t.getClass().getSimpleName());
        }
    }

    private static boolean trySolarToast(Toast toast) {
        if (toast == null) return false;
        try {
            Context ctx = (Context) XposedHelpers.getObjectField(toast, "mContext");
            if (ctx == null || !SolarOverlayClient.canDeliverOverlay(ctx)) return false;
            CharSequence text = (CharSequence) XposedHelpers.getObjectField(toast, "mText");
            if (text == null || text.length() == 0) return false;
            int duration = XposedHelpers.getIntField(toast, "mDuration");
            long ms = duration == Toast.LENGTH_LONG ? LENGTH_LONG_MS : LENGTH_SHORT_MS;
            if (SolarOverlayClient.showToastOverlay(ctx, text.toString(), ms)) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("len", text.length());
                    DebugSession8e4cdcLog.event("ToastHooks.trySolarToast", "routed", "T1", d);
                } catch (Throwable ignored) {}
                // #endregion
                SolarContextBridge.log("toast overlay len=" + text.length());
                return true;
            }
        } catch (Throwable t) {
            SolarContextBridge.log("toast route fail-open: " + t.getClass().getSimpleName());
        }
        return false;
    }
}
