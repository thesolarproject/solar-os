package com.solar.launcher.youtube;

import android.content.Context;

import com.solar.launcher.PmInstallPolicy;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;
import com.solar.launcher.platform.PlatformAssetExtractor;
import com.solar.launcher.platform.PlatformProbe;

import java.io.File;

/**
 * 2026-07-06 — Registers notPipe from /system/app (not /data/app cache installs).
 * Layman: YouTube helper lives on the system partition like ROM-baked apps.
 * Technical: stage bundled APK to /system/app, pm install -r -f from that path.
 * Reversal: delete; rely on platform repair wizard only.
 */
public final class NotPipePmRegistrar {

    private static final String TAG = "NotPipePmRegistrar";
    private static final String SYSTEM_APK = "/system/app/io.github.gohoski.notpipe.apk";
    private static final String NOTPIPE_PKG = "io.github.gohoski.notpipe";
    private static final String ASSET_PATH = "thirdparty/notPipe-0.3.0-release.apk";

    private static volatile Context appCtx;

    private NotPipePmRegistrar() {}

    /** Remember app context for asset extract on bootstrap thread. */
    public static void bindContext(Context ctx) {
        if (ctx != null) {
            appCtx = ctx.getApplicationContext();
        }
    }

    /** Background bootstrap — idempotent pm register (2026-07-06). */
    public static void ensureRegisteredAsync(final Context ctx) {
        bindContext(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ensureRegisteredBlocking(ctx != null ? ctx : appCtx);
            }
        }, "NotPipePmReg").start();
    }

    /** Blocking register — YouTube row may call before opening browse (2026-07-06). */
    public static boolean ensureRegisteredBlocking(Context ctx) {
        if (ctx != null) bindContext(ctx);
        if (!RootShell.canRun()) {
            return false;
        }
        boolean registered = PlatformProbe.packageRegisteredInPm(NOTPIPE_PKG);
        boolean onData = registered && PlatformProbe.packageRegisteredOnDataApp(NOTPIPE_PKG);
        if (registered && !onData) {
            return true;
        }

        if (!PlatformProbe.fileExists(SYSTEM_APK)) {
            Context extractCtx = ctx != null ? ctx : appCtx;
            if (extractCtx == null) return false;
            File apk = PlatformAssetExtractor.extractAsset(extractCtx, ASSET_PATH);
            if (apk == null || !apk.isFile()) {
                SolarLog.w(TAG, "bundled notPipe asset missing");
                return false;
            }
            stageToSystem(apk.getAbsolutePath());
        }

        if (PlatformProbe.fileExists(SYSTEM_APK)) {
            boolean ok = PmInstallPolicy.installSystemApp(SYSTEM_APK, NOTPIPE_PKG);
            SolarLog.i(TAG, "pm install notPipe from system ok=" + ok);
            return ok;
        }
        return false;
    }

    /** Copy bundled APK to /system/app for ROM parity. */
    private static void stageToSystem(String srcPath) {
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "cp " + PlatformProbe.shellQuote(srcPath) + " "
                + PlatformProbe.shellQuote(SYSTEM_APK) + " && "
                + "chmod 644 " + PlatformProbe.shellQuote(SYSTEM_APK) + " && "
                + "sync";
        RootShell.run(sh);
    }
}
