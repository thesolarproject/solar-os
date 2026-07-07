package com.solar.launcher.xposed.rockbox.compat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Y2 Rockbox Connectivity.execShell + daemonsu warm-up via Xposed.
 * Reversal: disable SolarRockboxCompat module; Rockbox shell commands fail on Y2.
 */
final class RockboxCompatHooks {

    private static volatile boolean installed;

    private RockboxCompatHooks() {}

    static void install(LoadPackageParam lpparam) {
        if (installed) return;
        installed = true;
        try {
            Class<?> connectivity = XposedHelpers.findClass(
                    "org.rockbox.Helper.Connectivity", lpparam.classLoader);
            hookExecShell(connectivity);
            hookSetContext(connectivity);
            warmDaemonsu();
            SolarRockboxCompat.log("RockboxCompatHooks installed");
        } catch (Throwable t) {
            SolarRockboxCompat.log("RockboxCompatHooks failed: " + t.getClass().getSimpleName());
        }
    }

    private static void hookExecShell(Class<?> connectivity) {
        XposedHookKit.hookAll(connectivity, "execShell", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                String cmd = null;
                if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                    cmd = String.valueOf(param.args[0]);
                }
                if (cmd == null || cmd.length() == 0) return null;
                try {
                    Runtime.getRuntime().exec(new String[]{"/system/xbin/su", "-c", cmd});
                } catch (Throwable ignored) {}
                return null;
            }
        });
    }

    private static void hookSetContext(Class<?> connectivity) {
        XposedHookKit.hookAll(connectivity, "setContext", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                warmDaemonsu();
            }
        });
    }

    private static void warmDaemonsu() {
        try {
            Runtime.getRuntime().exec(new String[]{"/system/xbin/daemonsu", "--auto-daemon"});
        } catch (Throwable ignored) {}
    }
}
