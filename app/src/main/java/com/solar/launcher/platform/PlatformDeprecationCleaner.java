package com.solar.launcher.platform;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;

/**
 * 2026-07-05 — Removes legacy /system APKs listed in manifest deprecated[] (wrong-family bridges).
 * APK/ROM parity: Rockbox behavior lives in SolarContextBridge Xposed hooks — not ROM smali patches.
 * When changing: add deprecated rows in sync-platform-assets.sh and bump prepVersion.
 * Reversal: delete; stale SolarContextBridge.apk may linger on upgraded devices.
 */
public final class PlatformDeprecationCleaner {

    private static final String TAG = "PlatformDeprecationCleaner";

    /** One deprecated artifact row from manifest.json. */
    public static final class DeprecatedEntry {
        public final String systemApk;
        public final String pkg;
        public final String device;
        public final String reason;

        public DeprecatedEntry(String systemApk, String pkg, String device, String reason) {
            this.systemApk = systemApk;
            this.pkg = pkg;
            this.device = device;
            this.reason = reason;
        }
    }

    private PlatformDeprecationCleaner() {}

    /** Remove deprecated files/packages for this device — returns count removed. */
    public static int removeDeprecatedArtifacts(Context ctx, PlatformPrepManifest manifest,
            SolarPlatformPrep.ProgressListener listener) {
        if (ctx == null || manifest == null || !RootShell.canRun()) return 0;
        boolean y2 = DeviceFeatures.isY2();
        int removed = 0;
        for (DeprecatedEntry d : manifest.deprecatedForDevice(y2)) {
            // 2026-07-15 — A5 ROM keeps unmodified NotPipe for touch; never wipe that APK.
            // Layman: touch A5 users still get the NotPipe app from their ROM.
            // Reversal: remove isA5 skip — A5 OTA would uninstall NotPipe like Y1/Y2.
            if (DeviceFeatures.isA5()
                    && "io.github.gohoski.notpipe".equals(d.pkg)) {
                continue;
            }
            if (d.systemApk != null && !d.systemApk.isEmpty()) {
                if (removeSystemApk(d.systemApk, d.pkg)) {
                    removed++;
                    log(listener, "Removed deprecated " + d.systemApk
                            + (d.reason != null ? " (" + d.reason + ")" : ""));
                }
            } else if (d.pkg != null && !d.pkg.isEmpty()) {
                if (uninstallPackage(d.pkg)) {
                    removed++;
                    log(listener, "Uninstalled deprecated " + d.pkg);
                }
            }
        }
        if (removed > 0) {
            SolarLog.i(TAG, "removed deprecated artifacts count=" + removed);
        }
        return removed;
    }

    /** Delete /system/app APK and PM registration when present. */
    static boolean removeSystemApk(String systemApk, String pkg) {
        if (!PlatformProbe.fileExists(systemApk)) return false;
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "rm -f " + PlatformProbe.shellQuote(systemApk) + "; "
                + "sync";
        if (pkg != null && !pkg.isEmpty()) {
            sh += "; pm uninstall " + PlatformProbe.shellQuote(pkg) + " 2>/dev/null || true";
        }
        return RootShell.run(sh);
    }

    /** pm uninstall when package still registered. */
    static boolean uninstallPackage(String pkg) {
        if (!PlatformProbe.packageInstalled(pkg)) return false;
        String out = RootShell.runCapture("pm uninstall " + PlatformProbe.shellQuote(pkg));
        return out != null && out.contains("Success");
    }

    private static void log(SolarPlatformPrep.ProgressListener listener, String line) {
        if (listener != null) listener.onLogLine(line);
    }
}
