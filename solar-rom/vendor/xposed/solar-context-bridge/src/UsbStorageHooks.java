package com.solar.launcher.xposed.bridge;

import android.app.Activity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * SystemUI hooks — replace stock {@code UsbStorageActivity} with Solar global overlay or USB lock.
 */
final class UsbStorageHooks {

    private static final String USB_STORAGE_ACTIVITY = "com.android.systemui.usb.UsbStorageActivity";

    private UsbStorageHooks() {}

    /** Install in {@code com.android.systemui} on Y1 and Y2. */
    static void installSystemUi(LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(USB_STORAGE_ACTIVITY, lpparam.classLoader);
            XposedHookKit.hookAll(activityClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    long t0 = System.nanoTime();
                    if (!(param.thisObject instanceof Activity)) return;
                    Activity activity = (Activity) param.thisObject;
                    try {
                        activity.finish();
                    } catch (Throwable ignored) {}
                    boolean umsExported = probeMassStorageExported();
                    if (umsExported) {
                        String fg = SystemServerHooks.foregroundPackage(activity);
                        if (!SystemServerHooks.shouldOfferOverlayForPackage(fg)) {
                            SolarOverlayClient.bringSolarToUsbLockScreen(activity);
                        } else {
                            SolarContextBridge.log("UsbStorage ums active — stay in fg=" + fg);
                        }
                    } else {
                        SolarOverlayClient.showUsbStoragePrompt(activity);
                    }
                    SolarContextBridge.log("UsbStorageActivity replaced ums=" + umsExported);
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("umsExported", umsExported);
                        BridgeAnrDebugLog.hookTiming("UsbStorageHooks.onCreate", "E", t0, d);
                    } catch (Throwable ignored) {}
                    // #endregion
                }
            });
            SolarContextBridge.log("hooked UsbStorageActivity in systemui");
        } catch (Throwable t) {
            SolarContextBridge.log("UsbStorageActivity hook skip: " + t.getClass().getSimpleName());
        }
    }

    /** True when kernel mass-storage LUN is bound — same paths as Solar MainActivity probe. */
    private static boolean probeMassStorageExported() {
        String[] paths = new String[]{
                "/sys/class/android_usb/android0/f_mass_storage/lun/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun0/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun1/file"
        };
        for (String path : paths) {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) continue;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                if (line != null && line.trim().length() > 0) {
                    return true;
                }
            } catch (Throwable ignored) {
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Throwable ignored) {}
                }
            }
        }
        return false;
    }
}
