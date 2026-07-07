package com.solar.launcher.platform;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.PmInstallPolicy;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;

/**
 * 2026-07-06 — Stages Solar hook APKs on /system/app then pm install from that path.
 * APK/ROM parity: mirrors lib-xposed-install.sh (push system, wipe /data/app, pm -r).
 * When changing: XposedModuleRegistry + sync-platform-assets.sh modules[] + install-xposed-system.sh.
 * Reversal: delete; XposedModuleEnsurer enable-only again (modules may land on /data/app).
 */
public final class XposedModuleInstaller {

    private static final String TAG = "XposedModuleInstaller";

    /** Per-module install outcome. */
    public static final class Result {
        public boolean installed;
        public boolean systemCopied;
        public String pkg;
        public String error;
    }

    private XposedModuleInstaller() {}

    /** Install all required modules for this device when missing or older than bundled. */
    public static int installRequiredModules(Context ctx, PlatformPrepManifest manifest,
            boolean copyToSystem) {
        if (ctx == null || manifest == null || !RootShell.canRun()) return 0;
        int count = 0;
        boolean y2 = DeviceFeatures.isY2();
        for (PlatformPrepManifest.ModuleEntry m : manifest.requiredModulesForDevice(y2)) {
            Result r = installModule(ctx, manifest, m, copyToSystem);
            if (r.installed) count++;
        }
        return count;
    }

    /** Install one module — /system/app canonical, relocate /data/app drift (2026-07-06). */
    public static Result installModule(Context ctx, PlatformPrepManifest manifest,
            PlatformPrepManifest.ModuleEntry m, boolean copyToSystem) {
        Result out = new Result();
        out.pkg = m.pkg;
        if (ctx == null || m == null || !RootShell.canRun()) {
            out.error = "no root";
            return out;
        }

        if (copyToSystem && m.systemApk != null && !PlatformProbe.fileExists(m.systemApk)) {
            out.systemCopied = copyToSystemPath(ctx, m);
        }

        int installedVc = PlatformProbe.installedVersionCode(m.pkg);
        boolean pmRegistered = PlatformProbe.packageRegisteredInPm(m.pkg);
        boolean onDataApp = pmRegistered && PlatformProbe.packageRegisteredOnDataApp(m.pkg);
        boolean needsVersionUpgrade = m.versionCode > 0 && installedVc > 0 && m.versionCode > installedVc;
        boolean needsInstall = !pmRegistered
                || onDataApp
                || needsVersionUpgrade
                || (m.versionCode > 0 && pmRegistered && installedVc == 0);

        if (!needsInstall) {
            out.installed = pmRegistered;
            out.systemCopied = m.systemApk != null && PlatformProbe.fileExists(m.systemApk);
            return out;
        }

        if (needsVersionUpgrade && copyToSystem) {
            copyToSystemPath(ctx, m);
        }

        if (m.systemApk != null && PlatformProbe.fileExists(m.systemApk)) {
            if (PmInstallPolicy.installSystemApp(m.systemApk, m.pkg)) {
                out.installed = true;
                out.systemCopied = true;
                SolarLog.i(TAG, "registered module " + m.pkg + " from " + m.systemApk);
                return out;
            }
        }

        java.io.File apk = PlatformAssetExtractor.extractAsset(ctx, m.asset);
        if (apk == null || !apk.isFile()) {
            out.error = "asset missing: " + m.asset;
            return out;
        }
        if (copyToSystem && m.systemApk != null) {
            out.systemCopied = copyToSystemPath(ctx, m);
        }
        if (m.systemApk != null && PlatformProbe.fileExists(m.systemApk)) {
            out.installed = PmInstallPolicy.installSystemApp(m.systemApk, m.pkg);
        } else {
            out.installed = PmInstallPolicy.installInternal(apk.getAbsolutePath());
        }
        if (!out.installed) {
            out.error = "pm install failed";
            SolarLog.w(TAG, "pm install failed for " + m.pkg);
        } else {
            SolarLog.i(TAG, "installed module " + m.pkg);
        }
        return out;
    }

    /** Copy extracted APK to /system/app when remount succeeds. */
    static boolean copyToSystemPath(Context ctx, PlatformPrepManifest.ModuleEntry m) {
        java.io.File apk = PlatformAssetExtractor.extractAsset(ctx, m.asset);
        if (apk == null || m.systemApk == null) return false;
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "cp " + PlatformProbe.shellQuote(apk.getAbsolutePath()) + " "
                + PlatformProbe.shellQuote(m.systemApk) + " && "
                + "chmod 644 " + PlatformProbe.shellQuote(m.systemApk) + " && "
                + "sync";
        return RootShell.run(sh);
    }
}
