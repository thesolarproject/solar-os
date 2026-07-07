package com.solar.ota;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 2026-07-05 — Install or replace com.solar.launcher from a downloaded APK.
 * Layman: puts a chosen Solar build on the device — upgrade or rollback.
 * Technical: pm install -r -d for user installs; root script for /system/app replace + reboot.
 */
public final class SolarApkInstaller {
    public static final String SYSTEM_APK_PATH = "/system/app/com.solar.launcher.apk";
    private static final String[] SU_PATHS = {"/system/xbin/su", "/system/bin/su", "su"};

    private SolarApkInstaller() {}

    public static boolean shouldReplaceSystemApk(Context context) {
        if (context == null) return new File(SYSTEM_APK_PATH).exists();
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(SolarLauncherVersion.SOLAR_PACKAGE, 0);
            if (info != null && (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true;
            }
        } catch (Exception ignored) {}
        return new File(SYSTEM_APK_PATH).exists();
    }

    /** Install downloaded Solar APK — system replace reboots when /system/app is canonical. */
    public static boolean install(Context context, File apkFile, AssetManager assets) {
        if (apkFile == null || !apkFile.isFile()) return false;
        if (shouldReplaceSystemApk(context)) {
            return installSystemApk(apkFile, assets);
        }
        return installViaPackageManager(apkFile, true);
    }

    public static boolean installViaPackageManager(File apkFile, boolean allowDowngrade) {
        if (apkFile == null || !apkFile.isFile()) return false;
        String cmd = allowDowngrade
                ? "pm install -r -d " + shQuote(apkFile.getAbsolutePath())
                : "pm install -r " + shQuote(apkFile.getAbsolutePath());
        return runSu(cmd);
    }

    public static boolean installSystemApk(File apkFile, AssetManager assets) {
        if (installViaBundledScript(apkFile, assets)) return true;
        String cmd = "mount -o remount,rw /system && rm -rf /data/app/com.solar.launcher* /data/app-lib/com.solar.launcher* && cp "
                + shQuote(apkFile.getAbsolutePath()) + " " + shQuote(SYSTEM_APK_PATH)
                + " && chmod 644 " + shQuote(SYSTEM_APK_PATH) + " && sync && reboot";
        return runSu(cmd);
    }

    private static boolean installViaBundledScript(File apkFile, AssetManager assets) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = assets.open("scripts/update-system-apk.sh");
            File script = new File(apkFile.getParentFile(), "update-system-apk.sh");
            out = new FileOutputStream(script);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.close();
            out = null;
            in.close();
            in = null;
            script.setExecutable(true, false);
            return runSu("sh " + shQuote(script.getAbsolutePath()) + " "
                    + shQuote(apkFile.getAbsolutePath()));
        } catch (Exception ignored) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    private static boolean runSu(String command) {
        if (command == null || command.isEmpty()) return false;
        for (String su : SU_PATHS) {
            Process proc = null;
            try {
                proc = Runtime.getRuntime().exec(new String[] {su, "-c", command});
                drain(proc.getErrorStream());
                int code = proc.waitFor();
                if (code == 0) return true;
            } catch (Exception ignored) {
            } finally {
                if (proc != null) proc.destroy();
            }
        }
        return false;
    }

    private static void drain(InputStream in) {
        if (in == null) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            while (r.readLine() != null) { /* discard */ }
        } catch (Exception ignored) {}
    }

    static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
