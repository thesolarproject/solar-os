package com.solar.launcher.xposed.notpipe;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — Finishes notPipe MainActivity when Solar only needs the process alive.
 * Layman: no touch UI flash — Solar woke notPipe for backend IPC only.
 * Technical: solar_wake_only extra → finish() in onCreate after bridge registered.
 * Reversal: remove hook; wake launches show notPipe home briefly.
 */
public final class NotPipeMainActivityHooks {

    private NotPipeMainActivityHooks() {}

    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> main = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.MainActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(main, "onCreate",
                    android.os.Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity act = (Activity) param.thisObject;
                    if (act.getIntent() != null
                            && act.getIntent().getBooleanExtra(
                                    NotPipeIpc.EXTRA_SOLAR_WAKE_ONLY, false)) {
                        act.finish();
                        SolarNotPipeBridge.log("MainActivity wake-only finished");
                    }
                }
            });
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeMainActivityHooks failed: " + t.getMessage());
        }
    }
}
