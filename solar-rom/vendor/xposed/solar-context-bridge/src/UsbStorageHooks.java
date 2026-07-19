package com.solar.launcher.xposed.bridge;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — SystemUI concierge: finish stock {@code UsbStorageActivity} and route to Solar
 * — unless stock UI is preferred (skip Solar prompt, no auto-connect).
 * Layman: by default Android’s USB screen stays; Solar only steals it when asked.
 * Was: always finish + route (heavy wake). Reversal: remove preferStockUsbUi early return.
 * 2026-07-19
 */
final class UsbStorageHooks {

    private static final String USB_STORAGE_ACTIVITY = "com.android.systemui.usb.UsbStorageActivity";

    private UsbStorageHooks() {}

    /** Install in {@code com.android.systemui} on Y1 and Y2. */
    static void installSystemUi(LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(USB_STORAGE_ACTIVITY, lpparam.classLoader);
            XC_MethodHook finishAndRoute = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    long t0 = System.nanoTime();
                    if (!(param.thisObject instanceof Activity)) return;
                    // Stock path — leave UsbStorageActivity alone (no finish, no Solar wake).
                    if (SolarUsbSessionPrefs.preferStockUsbUi()) {
                        SolarContextBridge.log("UsbStorageActivity stock pass-through");
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("stockUi", true);
                            BridgeAnrDebugLog.hookTiming("UsbStorageHooks.onCreate", "STOCK", t0, d);
                        } catch (Throwable ignored) {}
                        // #endregion
                        return;
                    }
                    Activity activity = (Activity) param.thisObject;
                    try {
                        activity.finish();
                    } catch (Throwable ignored) {}
                    boolean umsExported = UsbMassStorageProbe.probeMassStorageExported();
                    SolarOverlayClient.routeUsbConcierge(activity, umsExported);
                    SolarContextBridge.log("UsbStorageActivity replaced ums=" + umsExported);
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("umsExported", umsExported);
                        BridgeAnrDebugLog.hookTiming("UsbStorageHooks.onCreate", "E", t0, d);
                    } catch (Throwable ignored) {}
                    // #endregion
                }
            };
            XposedHookKit.hookAll(activityClass, "onCreate", finishAndRoute);
            // Belt-and-suspenders — only when Solar owns USB UI.
            XposedHookKit.hookAll(activityClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Activity)) return;
                    if (SolarUsbSessionPrefs.preferStockUsbUi()) return;
                    try {
                        ((Activity) param.thisObject).finish();
                    } catch (Throwable ignored) {}
                }
            });
            SolarContextBridge.log("hooked UsbStorageActivity in systemui");
        } catch (Throwable t) {
            SolarContextBridge.log("UsbStorageActivity hook skip: " + t.getClass().getSimpleName());
        }
    }
}
