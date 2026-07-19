package com.solar.launcher;

import android.content.Context;
import android.util.Log;

/**
 * 2026-07-19 — JJ presence check only (no download / system install).
 * Layman: if JJ is already on the device you can switch to it; Solar will not fetch it.
 * Was: OtaCompanionInstaller.installJjIfNeeded downloaded jj_latest.apk to /system/app.
 * Reversal: restore OtaCompanionInstaller.installJjIfNeeded call.
 */
public final class JjLauncherInstaller {

    private static final String TAG = "JjLauncherInstaller";

    private JjLauncherInstaller() {}

    /**
     * True when com.themoon.y1 is already registered — never installs.
     * 2026-07-19
     */
    public static boolean ensureInstalledBlocking(Context context) {
        if (context == null) return false;
        boolean ok = LauncherSwitch.isJjInstalled(context);
        if (!ok) {
            Log.i(TAG, "JJ not installed — Solar will not download/install it");
        }
        return ok;
    }

    /** Background presence check — callback on worker thread. 2026-07-19 */
    public static void ensureInstalledAsync(final Context context, final InstallCallback callback) {
        if (context == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = ensureInstalledBlocking(context);
                if (callback != null) {
                    callback.onComplete(ok);
                }
            }
        }, "JjLauncherInstall").start();
    }

    public interface InstallCallback {
        void onComplete(boolean installed);
    }
}
