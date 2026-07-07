package com.solar.launcher.xposed.rockbox.ime;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Xposed entry: hooks org.rockbox only for KbdInput IME Enter → OK.
 * Reversal: disable module in Solar Debug or Xposed Installer; Rockbox needs manual OK after IME.
 */
public final class SolarRockboxIme implements de.robv.android.xposed.IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !"org.rockbox".equals(lpparam.packageName)) return;
        RockboxKeyboardImeOkHooks.install(lpparam);
    }

    /** Log to logcat + XposedBridge for on-device diagnosis. */
    static void log(String msg) {
        Log.i("SolarRockboxIme", msg);
        XposedBridge.log("SolarRockboxIme: " + msg);
    }
}
