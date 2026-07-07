package com.solar.launcher.xposed.rockbox.compat;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Xposed entry: Y2 org.rockbox shell/root compat hooks only.
 * Reversal: disable module; Rockbox execShell paths revert to stock (broken on Y2).
 */
public final class SolarRockboxCompat implements de.robv.android.xposed.IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !"org.rockbox".equals(lpparam.packageName)) return;
        RockboxCompatHooks.install(lpparam);
    }

    static void log(String msg) {
        Log.i("SolarRockboxCompat", msg);
        XposedBridge.log("SolarRockboxCompat: " + msg);
    }
}
