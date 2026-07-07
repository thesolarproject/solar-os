package com.solar.launcher.xposed.bridge;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Shared Xposed hook router; Y1/Y2 entry APKs pick Target enum at construction.
 * APK/ROM parity: separate bridge APKs per family; Rockbox org.rockbox is special (BACK hold excluded).
 * When changing: add hooks in installY1/installY2 paths; never rethrow — stock app must keep running.
 * Reversal: remove hook install calls; affected behavior reverts to stock Android paths.
 */
public final class SolarContextBridge implements IXposedHookLoadPackage {

    /** Y1 vs Y2 hook set — separate APKs per device family. */
    public enum Target { Y1, Y2 }

    private final Target target;

    public SolarContextBridge(Target target) {
        this.target = target;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        try {
            if ("android".equals(lpparam.packageName)) {
                log("system_server load target=" + target);
                if (target == Target.Y2) {
                    SystemServerHooks.installY2(lpparam);
                } else {
                    SystemServerHooks.installY1(lpparam);
                }
                // system:ui hosts android/com.android.internal.app.ResolverActivity for HOME picks.
                LauncherResolverHooks.install(lpparam);
                return;
            }
        if (target == Target.Y2 && "com.android.systemui".equals(lpparam.packageName)) {
            VolumePanelHooks.installSystemUi(lpparam);
            UsbStorageHooks.installSystemUi(lpparam);
            return;
        }
        if (target == Target.Y1 && "com.android.systemui".equals(lpparam.packageName)) {
            VolumePanelHooks.installSystemUi(lpparam);
            UsbStorageHooks.installSystemUi(lpparam);
            return;
        }
        if (lpparam.packageName == null || lpparam.packageName.startsWith("com.solar.launcher")) {
            return;
        }
        AppMenuHooks.install(lpparam);
        DialogHooks.install(lpparam);
        ToastHooks.install(lpparam);
        BluetoothPairingHooks.install(lpparam);
        JjInputHooks.install(lpparam);
        ActivityOverlayKeyHooks.install(lpparam);
        ImeSessionHooks.install(lpparam);
        ImeFocusHooks.installApp(lpparam);
        LauncherResolverHooks.install(lpparam);
        } catch (Throwable t) {
            log("handleLoadPackage failed pkg=" + (lpparam != null ? lpparam.packageName : "?")
                    + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
            // Never rethrow — an uncaught hook init exception kills system:ui / system_server
            // and surfaces as "Android System has stopped" on Y1/Y2.
        }
    }

    /** Log to logcat for on-device diagnosis. */
    static void log(String msg) {
        Log.i("SolarCtxBridge", msg);
    }
}
