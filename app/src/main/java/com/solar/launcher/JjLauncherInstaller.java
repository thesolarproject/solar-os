package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import com.solar.ota.OtaCompanionInstaller;

import java.io.File;

/**
 * 2026-07-06 — Download and install JJ Launcher from Solar OTA when user picks it as HOME.
 * Layman: fetches jj_latest.apk over Wi‑Fi and installs to /system/app when root allows.
 * Technical: delegates to OtaCompanionInstaller system-app staging (same as OTA version screens).
 */
public final class JjLauncherInstaller {

    private static final String TAG = "JjLauncherInstaller";

    private JjLauncherInstaller() {}

    /** Install JJ when missing — returns true when PM sees com.themoon.y1 afterward. */
    public static boolean ensureInstalledBlocking(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        com.solar.launcher.net.TlsHelper.init(app);
        File workDir = app.getDir("update", Context.MODE_PRIVATE);
        boolean ok = OtaCompanionInstaller.installJjIfNeeded(app, workDir);
        if (!ok) {
            Log.w(TAG, "JJ install did not complete");
        }
        return ok;
    }

    /** Background install — callback on worker thread. */
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
