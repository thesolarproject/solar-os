package com.solar.launcher.xposed.bridge;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Shared Xposed router — Y1 and Y2 entry classes pick which hooks load.
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
        ActivityOverlayKeyHooks.install(lpparam);
        LauncherResolverHooks.install(lpparam);
        } catch (Throwable t) {
            log("handleLoadPackage failed pkg=" + (lpparam != null ? lpparam.packageName : "?")
                    + ": " + t.getClass().getSimpleName() + " " + t.getMessage());
            throw t;
        }
    }

    /** Log to logcat and XposedBridge for on-device diagnosis. */
    static void log(String msg) {
        Log.i("SolarCtxBridge", msg);
        XposedBridge.log("SolarCtxBridge: " + msg);
    }
}
