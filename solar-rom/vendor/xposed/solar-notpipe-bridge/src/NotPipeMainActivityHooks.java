package com.solar.launcher.xposed.notpipe;

import android.app.Activity;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-10 — Headless process wake: never show notPipe home to the user.
 * Layman: Solar only needs notPipe awake for IPC; no home UI flash.
 * Technical: finish MainActivity on solar_wake_only; SolarWakeService keeps process.
 * 2026-07-14 — Rely on sticky service keep-alive (blank Activity starved main looper).
 * Reversal: blank Activity keep-alive without finishing.
 */
public final class NotPipeMainActivityHooks {

    private NotPipeMainActivityHooks() {}

    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> main = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.MainActivity", lpparam.classLoader);
            XC_MethodHook wakeHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Activity act = (Activity) param.thisObject;
                    if (!isWakeOnly(act)) return;
                    try {
                        act.setTheme(android.R.style.Theme_Translucent_NoTitleBar);
                    } catch (Throwable ignored) {}
                    try {
                        act.finish();
                    } catch (Throwable ignored) {}
                    try {
                        act.overridePendingTransition(0, 0);
                    } catch (Throwable ignored) {}
                    param.setResult(null);
                    SolarNotPipeBridge.log("MainActivity wake-only finished (service keep-alive)");
                }
            };
            int n = NotPipeXposedKit.hookExact(main, "onCreate", wakeHook, Bundle.class);
            if (n == 0) {
                n = NotPipeXposedKit.hookDeclared(main, "onCreate", wakeHook);
            }
            SolarNotPipeBridge.log("MainActivity.onCreate hooks=" + n);
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeMainActivityHooks failed: " + t);
        }
    }

    private static boolean isWakeOnly(Activity act) {
        return act != null && act.getIntent() != null
                && act.getIntent().getBooleanExtra(NotPipeIpc.EXTRA_SOLAR_WAKE_ONLY, false);
    }
}
