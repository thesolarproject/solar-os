package com.solar.launcher.platform;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.RootShell;
import com.solar.launcher.XposedModuleEnsurer;
import com.solar.launcher.XposedModuleRegistry;
import com.solar.launcher.XposedModuleStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-05 — Read-only gap audit (root, remount, missing modules/framework) before prep ladder.
 * APK/ROM parity: probe results drive wizard gate; ROM-fresh devices often skip wizard entirely.
 * When changing: align gap ids with SolarPlatformPrep degradedReasons strings.
 * Reversal: delete; no structured gap list (ensurer tries blind enable).
 */
public final class PlatformProbe {

    /** One missing or misconfigured item the prep ladder may fix. */
    public static final class Gap {
        public final String id;
        public final String detail;

        Gap(String id, String detail) {
            this.id = id;
            this.detail = detail;
        }
    }

    /** Snapshot of device readiness. */
    public static final class Report {
        public final boolean rootAvailable;
        public final boolean remountWritable;
        public final List<Gap> gaps;

        Report(boolean rootAvailable, boolean remountWritable, List<Gap> gaps) {
            this.rootAvailable = rootAvailable;
            this.remountWritable = remountWritable;
            this.gaps = gaps;
        }

        /** True when any required gap remains. */
        public boolean hasRequiredGaps() {
            return gaps != null && !gaps.isEmpty();
        }
    }

    private PlatformProbe() {}

    /** Audit live device against bundled manifest expectations. */
    public static Report probe(PlatformPrepManifest manifest) {
        List<Gap> gaps = new ArrayList<Gap>();
        boolean root = RootShell.canRun();
        boolean remount = false;
        if (root) {
            remount = RootShell.run("mount -o remount,rw /system 2>/dev/null; test -w /system && echo ok")
                    || fileExists("/system/build.prop");
        }
        if (manifest == null) {
            gaps.add(new Gap("manifest", "bundled manifest missing"));
            return new Report(root, remount, gaps);
        }
        boolean y2 = DeviceFeatures.isY2();
        PlatformPrepManifest.FrameworkSystemPaths sp = manifest.systemPaths;

        if (!fileExists(sp.initHook)) {
            gaps.add(new Gap("init_hook", sp.initHook));
        }
        if (!fileExists(sp.bridgeJarFramework)) {
            gaps.add(new Gap("framework_jar", sp.bridgeJarFramework));
        }
        if (!XposedModuleEnsurer.isXposedPresent()) {
            gaps.add(new Gap("runtime_jar", "XposedBridge runtime not seeded"));
        }
        if (!appProcessHasXposed()) {
            gaps.add(new Gap("app_process", "zygote binary lacks Xposed support"));
        }
        if (!fileExists(sp.installerApk)) {
            gaps.add(new Gap("installer_apk", sp.installerApk));
        }

        for (PlatformPrepManifest.ModuleEntry m : manifest.requiredModulesForDevice(y2)) {
            // 2026-07-06 — PM registration required for YouTube/notPipe IPC; /system APK alone is not enough.
            if (!packageRegisteredInPm(m.pkg)) {
                gaps.add(new Gap("module_missing", m.pkg));
            } else if (!XposedModuleStore.isUserDisabled(m.pkg)
                    && !XposedModuleStore.isModuleEnabled(m.pkg)) {
                gaps.add(new Gap("module_disabled", m.pkg));
            }
        }
        return new Report(root, remount, gaps);
    }

    /** True when Package Manager knows the package (pm path) — required for app IPC. */
    public static boolean packageRegisteredInPm(String pkg) {
        return packageRegisteredInPmForTest(pkg, null);
    }

    /** Test hook — supply pm path output or null for live shell probe. */
    public static boolean packageRegisteredInPmForTest(String pkg, String pmPathOut) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pmPathOut != null) {
            return pmPathOut.contains("package:");
        }
        String out = RootShell.runCapture("pm path " + shellQuote(pkg) + " 2>/dev/null");
        return out != null && out.contains("package:");
    }

    /**
     * 2026-07-06 — Installed APK directory from dumpsys (codePath=).
     * Layman: tells whether Android loaded the app from /system/app or /data/app.
     */
    public static String packageCodePath(String pkg) {
        return packageCodePathForTest(pkg, null);
    }

    /** Test hook — parse codePath line without shell. */
    public static String packageCodePathForTest(String pkg, String dumpsysLine) {
        if (pkg == null || pkg.isEmpty()) return null;
        String line = dumpsysLine;
        if (line == null) {
            line = RootShell.runCapture("dumpsys package " + shellQuote(pkg)
                    + " 2>/dev/null | grep -m1 codePath");
        }
        if (line == null) return null;
        int idx = line.indexOf("codePath=");
        if (idx < 0) return null;
        return line.substring(idx + 9).trim();
    }

    /** True when PM registered the package under /data/app (OTA drift from /system/app). */
    public static boolean packageRegisteredOnDataApp(String pkg) {
        String path = packageCodePath(pkg);
        return path != null && path.contains("/data/app/");
    }

    /** True when PM codePath is on /system/ (Solar platform contract). */
    public static boolean packageRegisteredOnSystemApp(String pkg) {
        String path = packageCodePath(pkg);
        return path != null && path.startsWith("/system/");
    }

    /** Shell test -f helper — works without root for /system on many Solar ROMs. */
    public static boolean fileExists(String path) {
        if (path == null || path.isEmpty()) return false;
        if (new File(path).isFile()) return true;
        String out = RootShell.runCapture("test -f " + shellQuote(path) + " && echo yes || echo no");
        return out != null && out.trim().contains("yes");
    }

    /**
     * 2026-07-06 — PM path, dumpsys, or baked /system/app APK counts as installed.
     * Layman: ROM modules on disk count even when pm list packages hides them.
     * Tech: KitKat often omits /system/app/*.apk from pm path until pm install -r once.
     */
    public static boolean packageInstalled(String pkg) {
        return packageInstalledForTest(pkg, null, null, false);
    }

    /** Test hook — pure path checks when pmPathOut/system args set; else live shell probe. */
    public static boolean packageInstalledForTest(String pkg, String pmPathOut,
            String systemApkPath, boolean systemApkExists) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pmPathOut != null && pmPathOut.length() > 0) {
            return pmPathOut.contains("package:");
        }
        if (systemApkPath != null) {
            return systemApkExists;
        }
        String out = RootShell.runCapture("pm path " + shellQuote(pkg) + " 2>/dev/null");
        if (out != null && out.contains("package:")) return true;
        String sys = XposedModuleRegistry.systemApkPathForPackage(pkg);
        if (sys != null && fileExists(sys)) {
            return true;
        }
        String ds = RootShell.runCapture("dumpsys package " + shellQuote(pkg)
                + " 2>/dev/null | grep -q codePath && echo yes");
        return ds != null && ds.trim().contains("yes");
    }

    /** grep zygote binary for rovo89 Xposed marker string. */
    static boolean appProcessHasXposed() {
        String out = RootShell.runCapture(
                "grep -aq 'with Xposed support' /system/bin/app_process && echo yes || echo no");
        if (out != null && out.trim().contains("yes")) return true;
        String staged = RootShell.runCapture(
                "test -f /system/bin/app_process.xposed.staged && echo yes || echo no");
        return staged != null && staged.trim().contains("yes");
    }

    /** Installed module versionCode from dumpsys — 0 when unknown. */
    public static int installedVersionCode(String pkg) {
        String out = RootShell.runCapture("dumpsys package " + shellQuote(pkg)
                + " 2>/dev/null | grep versionCode");
        if (out == null) return 0;
        int idx = out.indexOf("versionCode=");
        if (idx < 0) return 0;
        String tail = out.substring(idx + 12).trim();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c >= '0' && c <= '9') num.append(c);
            else break;
        }
        if (num.length() == 0) return 0;
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
