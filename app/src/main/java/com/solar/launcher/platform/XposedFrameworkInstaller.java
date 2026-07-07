package com.solar.launcher.platform;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;
import com.solar.launcher.XposedModuleStore;

/**
 * 2026-07-05 — Stages Dalvik Xposed from bundled api17-arm or api19-arm vendor tree.
 * APK/ROM parity: mirrors lib-xposed-install.sh; ETXTBUSY stages app_process for post-reboot apply.
 * When changing: sync-platform-assets.sh framework paths; lib-xposed-install.sh constants.
 * Reversal: delete; framework ROM/adb install only.
 */
public final class XposedFrameworkInstaller {

    private static final String TAG = "XposedFrameworkInstaller";
    private static final String RUNTIME_JAR = XposedModuleStore.XPOSED_DATA + "/bin/XposedBridge.jar";
    private static final String STAGED_APP_PROCESS = "/system/bin/app_process.xposed.staged";

    /** Install outcome — rebootRequired when zygote binary was staged. */
    public static final class Result {
        public boolean frameworkPresent;
        public boolean rebootRequired;
        public String error;
    }

    private XposedFrameworkInstaller() {}

    /** Install framework files when runtime jar or Xposed app_process string is missing. */
    public static Result ensureFramework(Context ctx, PlatformPrepManifest manifest) {
        Result out = new Result();
        if (ctx == null || manifest == null || !RootShell.canRun()) {
            out.error = "no root";
            return out;
        }
        boolean needsFramework = !PlatformProbe.fileExists(manifest.systemPaths.bridgeJarFramework)
                || !PlatformProbe.appProcessHasXposed();
        if (!needsFramework) {
            out.frameworkPresent = true;
            seedRuntime(manifest);
            ensureInstallerPm(ctx, manifest);
            return out;
        }

        boolean y2 = DeviceFeatures.isY2();
        PlatformPrepManifest.FrameworkVendor vendor = manifest.vendorForDevice(y2);
        PlatformPrepManifest.FrameworkSystemPaths sp = manifest.systemPaths;

        RootShell.run("mount -o remount,rw /system 2>/dev/null || true");

        if (!PlatformProbe.fileExists(sp.appProcessOrig)) {
            RootShell.run("cp -a " + PlatformProbe.shellQuote(sp.appProcess) + " "
                    + PlatformProbe.shellQuote(sp.appProcessOrig)
                    + " && chmod 755 " + PlatformProbe.shellQuote(sp.appProcessOrig));
        }

        boolean copied = copyAssetToSystem(ctx, vendor.appProcess, sp.appProcess, "755");
        if (!copied) {
            // ETXTBUSY — stage for 99XposedInit.sh on next boot.
            java.io.File src = PlatformAssetExtractor.extractAsset(ctx, vendor.appProcess);
            if (src != null) {
                RootShell.run("cp " + PlatformProbe.shellQuote(src.getAbsolutePath()) + " "
                        + PlatformProbe.shellQuote(STAGED_APP_PROCESS)
                        + " && chmod 755 " + PlatformProbe.shellQuote(STAGED_APP_PROCESS));
                out.rebootRequired = true;
            } else {
                out.error = "app_process asset missing";
                return out;
            }
        } else {
            RootShell.run("chown root:shell " + PlatformProbe.shellQuote(sp.appProcess) + " 2>/dev/null || true");
            out.rebootRequired = true;
        }

        copyAssetToSystem(ctx, vendor.bridgeJar, sp.bridgeJarFramework, "644");
        copyAssetToSystem(ctx, vendor.bridgeJar, sp.bridgeJarSolar, "644");
        copyAssetToSystem(ctx, vendor.xposedProp, sp.xposedProp, "644");
        copyAssetToSystem(ctx, manifest.installerAsset, sp.installerApk, "644");

        for (PlatformPrepManifest.FileEntry f : manifest.files) {
            copyAssetToSystem(ctx, f.asset, f.dest, f.mode);
        }
        copyAssetToSystem(ctx, manifest.initHookAsset, sp.initHook, "755");

        ensureInstallerPm(ctx, manifest);

        out.frameworkPresent = PlatformProbe.fileExists(sp.bridgeJarFramework);
        SolarLog.i(TAG, "framework staged rebootRequired=" + out.rebootRequired);
        return out;
    }

    /** 2026-07-06 — Xposed Installer on /system/app + PM register (never /data/app drift). */
    public static void ensureInstallerPm(Context ctx, PlatformPrepManifest manifest) {
        if (ctx == null || manifest == null) return;
        PlatformPrepManifest.FrameworkSystemPaths sp = manifest.systemPaths;
        if (!PlatformProbe.fileExists(sp.installerApk)) {
            copyAssetToSystem(ctx, manifest.installerAsset, sp.installerApk, "644");
        }
        String installerPkg = "de.robv.android.xposed.installer";
        if (!PlatformProbe.packageRegisteredInPm(installerPkg)
                || PlatformProbe.packageRegisteredOnDataApp(installerPkg)) {
            com.solar.launcher.PmInstallPolicy.installSystemApp(sp.installerApk, installerPkg);
        }
        seedRuntime(manifest);
        runInitHook(sp.initHook);
        XposedModuleStore.fixInstallerOwnership();
    }

    /** Seed /data runtime jar + empty modules.list (XposedBridge v54 requirement). */
    static void seedRuntime(PlatformPrepManifest manifest) {
        if (manifest == null) return;
        PlatformPrepManifest.FrameworkSystemPaths sp = manifest.systemPaths;
        String sh = ""
                + "mkdir -p " + XposedModuleStore.XPOSED_DATA + "/bin "
                + XposedModuleStore.XPOSED_DATA + "/conf "
                + XposedModuleStore.XPOSED_DATA + "/log; "
                + "cp -f " + PlatformProbe.shellQuote(sp.bridgeJarFramework) + " "
                + PlatformProbe.shellQuote(RUNTIME_JAR) + "; "
                + "chmod 644 " + PlatformProbe.shellQuote(RUNTIME_JAR) + "; "
                + "rm -f " + XposedModuleStore.XPOSED_DATA + "/conf/disabled; "
                + "touch " + XposedModuleStore.XPOSED_DATA + "/conf/modules.list";
        RootShell.run(sh);
        XposedModuleStore.fixInstallerOwnership();
    }

    static void runInitHook(String initHook) {
        if (initHook == null) return;
        RootShell.run("test -x " + PlatformProbe.shellQuote(initHook)
                + " && sh " + PlatformProbe.shellQuote(initHook) + " || true");
    }

    /** Extract asset to cache then su cp onto /system. */
    static boolean copyAssetToSystem(Context ctx, String asset, String dest, String mode) {
        java.io.File src = PlatformAssetExtractor.extractAsset(ctx, asset);
        if (src == null || dest == null) return false;
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "mkdir -p $(dirname " + PlatformProbe.shellQuote(dest) + "); "
                + "cp " + PlatformProbe.shellQuote(src.getAbsolutePath()) + " "
                + PlatformProbe.shellQuote(dest) + " && "
                + "chmod " + mode + " " + PlatformProbe.shellQuote(dest) + " && sync";
        return RootShell.run(sh);
    }
}
