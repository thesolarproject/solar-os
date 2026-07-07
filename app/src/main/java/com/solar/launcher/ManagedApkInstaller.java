package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;

/**
 * 2026-07-06 — User APK installs via root pm or stock installer fallback.
 * Layman: Solar puts apps on the player for you when root allows; otherwise opens Android install.
 * Technical: {@link PmInstallPolicy#installInternal} first; ACTION_VIEW package-archive if no root.
 * Reversal: remove; folder browser would call MainActivity.installApk again (Solar-update biased).
 */
public final class ManagedApkInstaller {

    public static final class Result {
        public boolean success;
        public boolean usedRoot;
        public boolean launchedStockInstaller;
        /** {@code ok}, {@code pm_failed}, {@code stock}, {@code no_route}, {@code missing}, {@code solar_self}. */
        public String code = "missing";
    }

    private ManagedApkInstaller() {}

    /**
     * Human label for confirm UI — archive label, package name, or file name.
     * Layman: shows what app you picked before install runs.
     */
    public static String describeApk(Context ctx, File apk) {
        if (apk == null || !apk.isFile()) return "";
        String pkg = readPackageName(ctx, apk);
        if (pkg != null && !pkg.isEmpty()) {
            PackageManager pm = ctx.getPackageManager();
            try {
                PackageInfo installed = pm.getPackageInfo(pkg, 0);
                if (installed != null) {
                    CharSequence label = pm.getApplicationLabel(installed.applicationInfo);
                    if (label != null && label.length() > 0) {
                        return label.toString() + " (" + apk.getName() + ")";
                    }
                }
            } catch (Exception ignored) {}
        }
        PackageManager pm = ctx.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive != null && archive.applicationInfo != null) {
            ApplicationInfo ai = archive.applicationInfo;
            ai.sourceDir = apk.getAbsolutePath();
            ai.publicSourceDir = apk.getAbsolutePath();
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && label.length() > 0) return label.toString();
            if (archive.packageName != null) return archive.packageName;
        }
        return apk.getName();
    }

    /** Package name from APK archive — null when unreadable. */
    public static String readPackageName(Context ctx, File apk) {
        if (ctx == null || apk == null || !apk.isFile()) return null;
        PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        return info != null ? info.packageName : null;
    }

    /**
     * Install a user-selected APK — not for Solar self-update (use OTA path instead).
     * Layman: silent install on rooted players; everyone else gets the normal Android prompt.
     */
    public static Result install(Context ctx, File apkFile) {
        Result out = new Result();
        if (ctx == null || apkFile == null || !apkFile.isFile()) {
            out.code = "missing";
            return out;
        }
        String pkg = readPackageName(ctx, apkFile);
        if ("com.solar.launcher".equals(pkg)) {
            out.code = "solar_self";
            return out;
        }
        if (RootShell.canRun()) {
            out.usedRoot = true;
            out.success = PmInstallPolicy.installInternal(apkFile.getAbsolutePath());
            out.code = out.success ? "ok" : "pm_failed";
            return out;
        }
        out.launchedStockInstaller = launchStockInstaller(ctx, apkFile);
        out.success = out.launchedStockInstaller;
        out.code = out.launchedStockInstaller ? "stock" : "no_route";
        return out;
    }

    /** Stock Android package installer — no root required. */
    public static boolean launchStockInstaller(Context ctx, File apkFile) {
        if (ctx == null || apkFile == null || !apkFile.isFile()) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            SolarLog.w("ManagedApkInstaller", "stock installer failed: " + e.getMessage());
            return false;
        }
    }
}
