package com.solar.launcher.xposed.notpipe;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — Xposed entry: hooks io.github.gohoski.notpipe for Solar YouTube IPC + wheel player.
 * Layman: lets Solar search/play YouTube through notPipe without touch UI.
 * Technical: Application receiver + VideoActivity key/orientation hooks when solar_hosted.
 * Reversal: disable module — Solar YouTube browse shows unavailable; notPipe standalone still works.
 */
public final class SolarNotPipeBridge implements IXposedHookLoadPackage {

    private static final String NOTPIPE_PKG = "io.github.gohoski.notpipe";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !NOTPIPE_PKG.equals(lpparam.packageName)) return;
        NotPipeApplicationHooks.install(lpparam);
        NotPipeMainActivityHooks.install(lpparam);
        NotPipeVideoActivityHooks.install(lpparam);
        log("installed for " + NOTPIPE_PKG);
    }

    static void log(String msg) {
        Log.i("SolarNotPipeBridge", msg);
        XposedBridge.log("SolarNotPipeBridge: " + msg);
    }
}
