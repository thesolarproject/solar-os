package com.solar.launcher.xposed.bridge;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — SystemUI concierge: finish stock {@code UsbStorageActivity} and route to Solar.
 * Layman: PC plug-in never shows Android's USB dialog — Solar overlay or in-app USB owns it.
 * Reversal: remove hooks; {@code Y1UsbFocusHelper} USB_STATE polling becomes primary again.
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
            // Belt-and-suspenders — stock may resume after config change before finish completes.
            XposedHookKit.hookAll(activityClass, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Activity)) return;
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
