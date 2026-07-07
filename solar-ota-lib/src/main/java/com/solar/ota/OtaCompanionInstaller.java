package com.solar.ota;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.solar.ota.net.OtaDownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 2026-07-06 — Download and install JJ + Rockbox companions before Solar OTA install.
 * Layman: refreshes sidecar launchers/music app when you update Solar; skips quietly on failure.
 * Technical: parallel-friendly downloadAll + installDownloaded; /system/app replace + pm -r -f.
 * Reversal: remove hook from MainActivity/Updater; companions stay on last successful install.
 */
public final class OtaCompanionInstaller {

    private static final String TAG = "OtaCompanion";
    private static final long MIN_APK_BYTES = 1024L * 1024L;
    private static final String USER_AGENT = "SolarOtaCompanion/1.0";

    private OtaCompanionInstaller() {}

    public interface Progress {
        void onPhase(String label);
    }

    /** Outcome flags — Solar install must proceed regardless. */
    public static final class Result {
        public final boolean jjOk;
        public final boolean rockboxApkOk;
        public final boolean rockboxLibsOk;

        public Result(boolean jjOk, boolean rockboxApkOk, boolean rockboxLibsOk) {
            this.jjOk = jjOk;
            this.rockboxApkOk = rockboxApkOk;
            this.rockboxLibsOk = rockboxLibsOk;
        }
    }

    /** Download + install on worker thread — never throws. */
    public static Result prepareAndInstall(Context context, File workDir, Progress progress) {
        downloadAll(context, workDir, progress);
        return installDownloaded(context, workDir, progress);
    }

    /** Batch download companions (safe to run parallel with Solar APK download). */
    public static void downloadAll(Context context, File workDir, Progress progress) {
        if (context == null || workDir == null) return;
        if (!isOnline(context)) {
            Log.w(TAG, "companion download skipped — offline");
            return;
        }
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            Log.w(TAG, "companion work dir unavailable");
            return;
        }
        OtaTlsInit(context);
        notify(progress, "jj");
        downloadOptional(new String[] {OtaCompanionUrls.JJ_APK_URL},
                new File(workDir, OtaCompanionUrls.FILE_JJ), "JJ");
        notify(progress, "rockbox");
        File installApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_INSTALL);
        downloadOptional(OtaCompanionUrls.ROCKBOX_INSTALL_URLS, installApk, "Rockbox APK");
        File libsApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_LIBS);
        if (!sameFile(installApk, libsApk)) {
            downloadOptional(OtaCompanionUrls.ROCKBOX_LIBS_APK_URLS, libsApk, "Rockbox libs APK");
        } else if (isValidApk(installApk)) {
            try {
                copyFile(installApk, libsApk);
            } catch (IOException e) {
                Log.w(TAG, "Rockbox libs APK copy failed: " + e.getMessage());
            }
        }
    }

    /** Install downloaded artifacts — JJ then Rockbox APK then native lib sync. */
    public static Result installDownloaded(Context context, File workDir, Progress progress) {
        if (context == null || workDir == null) {
            return new Result(false, false, false);
        }
        if (!OtaRootShell.canRun()) {
            Log.w(TAG, "companion install skipped — no root");
            return new Result(false, false, false);
        }
        boolean jjOk = false;
        boolean rockboxApkOk = false;
        boolean rockboxLibsOk = false;
        try {
            notify(progress, "jj_install");
            jjOk = installJj(context, workDir);
            notify(progress, "rockbox_install");
            rockboxApkOk = installRockboxApk(workDir);
            notify(progress, "rockbox_libs");
            rockboxLibsOk = syncRockboxLibs(context, workDir);
        } catch (Exception e) {
            Log.w(TAG, "companion install error: " + e.getMessage());
        }
        Log.i(TAG, "companion result jj=" + jjOk + " rbApk=" + rockboxApkOk + " rbLibs=" + rockboxLibsOk);
        return new Result(jjOk, rockboxApkOk, rockboxLibsOk);
    }

    /**
     * 2026-07-06 — Fetch and install latest JJ Launcher (Settings / Solar Versions row).
     * Layman: copies jj_latest.apk onto /system/app and reinstalls — keeps JJ settings intact.
     * Technical: stage /system/app/com.themoon.y1.apk + pm install -r -f; never wipes /data/data.
     */
    public static boolean installJjLatest(Context context, File workDir, Progress progress) {
        if (context == null) return false;
        if (!isOnline(context)) {
            Log.w(TAG, "JJ latest skipped — offline");
            return false;
        }
        if (!OtaRootShell.canRun()) {
            Log.w(TAG, "JJ latest skipped — no root");
            return false;
        }
        if (workDir == null) {
            workDir = context.getDir("update", Context.MODE_PRIVATE);
        }
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            Log.w(TAG, "JJ latest work dir unavailable");
            return false;
        }
        OtaTlsInit(context);
        notify(progress, "jj");
        File dest = new File(workDir, OtaCompanionUrls.FILE_JJ);
        downloadOptional(new String[] {OtaCompanionUrls.JJ_APK_URL}, dest, "JJ");
        if (!isValidApk(dest)) {
            Log.w(TAG, "JJ latest download invalid");
            return false;
        }
        notify(progress, "jj_install");
        boolean ok = installJjFromApk(dest);
        return ok && isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE);
    }

    /**
     * 2026-07-06 — Fetch and install latest Rockbox APK + native libs (on-demand row).
     * Layman: refreshes Rockbox and its engine files from the web; skips quietly when blocked.
     * Technical: download ladder + /system staging + lib sync; Result.rockbox* flags only.
     */
    public static Result installRockboxLatest(Context context, File workDir, Progress progress) {
        if (context == null) {
            return new Result(false, false, false);
        }
        if (!isOnline(context)) {
            Log.w(TAG, "Rockbox latest skipped — offline");
            return new Result(false, false, false);
        }
        if (!OtaRootShell.canRun()) {
            Log.w(TAG, "Rockbox latest skipped — no root");
            return new Result(false, false, false);
        }
        if (workDir == null) {
            workDir = context.getDir("update", Context.MODE_PRIVATE);
        }
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            Log.w(TAG, "Rockbox latest work dir unavailable");
            return new Result(false, false, false);
        }
        OtaTlsInit(context);
        notify(progress, "rockbox");
        File installApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_INSTALL);
        downloadOptional(OtaCompanionUrls.ROCKBOX_INSTALL_URLS, installApk, "Rockbox APK");
        File libsApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_LIBS);
        if (!sameFile(installApk, libsApk)) {
            downloadOptional(OtaCompanionUrls.ROCKBOX_LIBS_APK_URLS, libsApk, "Rockbox libs APK");
        } else if (isValidApk(installApk)) {
            try {
                copyFile(installApk, libsApk);
            } catch (IOException e) {
                Log.w(TAG, "Rockbox libs APK copy failed: " + e.getMessage());
            }
        }
        boolean rockboxApkOk = false;
        boolean rockboxLibsOk = false;
        try {
            notify(progress, "rockbox_install");
            rockboxApkOk = installRockboxApk(workDir);
            notify(progress, "rockbox_libs");
            rockboxLibsOk = syncRockboxLibs(context, workDir);
        } catch (Exception e) {
            Log.w(TAG, "Rockbox latest install error: " + e.getMessage());
        }
        Log.i(TAG, "Rockbox latest rbApk=" + rockboxApkOk + " rbLibs=" + rockboxLibsOk);
        return new Result(false, rockboxApkOk, rockboxLibsOk);
    }

    /** JJ install for on-demand launcher picker — stages to /system/app when missing. */
    public static boolean installJjIfNeeded(Context context, File workDir) {
        if (context == null) return false;
        if (isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE)) return true;
        if (!OtaRootShell.canRun()) {
            Log.w(TAG, "JJ install skipped — no root");
            return false;
        }
        File sysApk = new File(OtaCompanionUrls.JJ_SYSTEM_APK);
        if (sysApk.isFile()) {
            if (pmInstallSystemApp(OtaCompanionUrls.JJ_SYSTEM_APK, OtaCompanionUrls.JJ_PACKAGE)
                    && isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE)) {
                return true;
            }
            Log.w(TAG, "system JJ install failed");
        }
        if (!isOnline(context)) {
            Log.w(TAG, "JJ install skipped — offline");
            return false;
        }
        if (workDir == null) {
            workDir = context.getDir("update", Context.MODE_PRIVATE);
        }
        OtaTlsInit(context);
        File dest = new File(workDir, OtaCompanionUrls.FILE_JJ);
        try {
            OtaDownload.downloadToFile(OtaCompanionUrls.JJ_APK_URL, dest, USER_AGENT);
        } catch (IOException e) {
            Log.w(TAG, "JJ download failed: " + e.getMessage());
            return false;
        }
        if (!isValidApk(dest)) {
            Log.w(TAG, "JJ download empty or too small");
            return false;
        }
        if (!installJjFromApk(dest)) {
            Log.w(TAG, "JJ system staging failed");
            return false;
        }
        return isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE);
    }

    /** 2026-07-06 — JJ from workDir or existing /system/app — refresh when APK downloaded. */
    private static boolean installJj(Context context, File workDir) {
        File downloaded = new File(workDir, OtaCompanionUrls.FILE_JJ);
        if (isValidApk(downloaded)) {
            return installJjFromApk(downloaded)
                    && isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE);
        }
        File sysApk = new File(OtaCompanionUrls.JJ_SYSTEM_APK);
        if (sysApk.isFile()) {
            return pmInstallSystemApp(OtaCompanionUrls.JJ_SYSTEM_APK, OtaCompanionUrls.JJ_PACKAGE)
                    && isPackageInstalled(context, OtaCompanionUrls.JJ_PACKAGE);
        }
        Log.w(TAG, "JJ APK not available");
        return false;
    }

    /** Copy APK to /system/app — canonical ROM slot for JJ and Rockbox. */
    private static boolean stageSystemApk(File apk, String systemApkPath) {
        if (!isValidApk(apk) || systemApkPath == null || systemApkPath.isEmpty()) {
            return false;
        }
        String qApk = SolarApkInstaller.shQuote(apk.getAbsolutePath());
        String qSys = SolarApkInstaller.shQuote(systemApkPath);
        String cmd = "mount -o remount,rw /system && mkdir -p /system/app && cp " + qApk + " " + qSys
                + " && chmod 644 " + qSys + " && sync";
        return OtaRootShell.run(cmd);
    }

    /**
     * 2026-07-06 — pm install -r -f from /system/app; clears /data/app drift only.
     * Layman: registers the system-partition APK while keeping app settings and caches.
     * Technical: rm /data/app/pkg* then pm install -r -f; /data/data untouched.
     */
    private static boolean pmInstallSystemApp(String systemApkPath, String pkg) {
        if (systemApkPath == null || !systemApkPath.startsWith("/system/")) return false;
        if (!new File(systemApkPath).isFile()) return false;
        OtaRootShell.enforceInternalInstallLocation();
        if (pkg != null && !pkg.isEmpty()) {
            OtaRootShell.run("rm -rf /data/app/" + pkg + "* /data/app-lib/" + pkg + "*");
        }
        String out = OtaRootShell.runCapture(
                "pm install -r -f " + SolarApkInstaller.shQuote(systemApkPath));
        return out != null && out.contains("Success");
    }

    /** Stage downloaded JJ to /system/app/com.themoon.y1.apk and pm reinstall. */
    private static boolean installJjFromApk(File apk) {
        if (!stageSystemApk(apk, OtaCompanionUrls.JJ_SYSTEM_APK)) {
            Log.w(TAG, "JJ system staging failed");
            return false;
        }
        if (!pmInstallSystemApp(OtaCompanionUrls.JJ_SYSTEM_APK, OtaCompanionUrls.JJ_PACKAGE)) {
            Log.w(TAG, "JJ system pm install failed");
            return new File(OtaCompanionUrls.JJ_SYSTEM_APK).isFile();
        }
        return true;
    }

    private static boolean installRockboxApk(File workDir) {
        File apk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_INSTALL);
        if (!isValidApk(apk)) {
            Log.w(TAG, "Rockbox install APK missing");
            return false;
        }
        if (!stageSystemApk(apk, OtaCompanionUrls.ROCKBOX_SYSTEM_APK)) {
            Log.w(TAG, "Rockbox system staging failed");
            return false;
        }
        if (!pmInstallSystemApp(OtaCompanionUrls.ROCKBOX_SYSTEM_APK, OtaCompanionUrls.ROCKBOX_PACKAGE)) {
            Log.w(TAG, "Rockbox system pm install failed (APK may still be on /system)");
            return new File(OtaCompanionUrls.ROCKBOX_SYSTEM_APK).isFile();
        }
        return true;
    }

    private static boolean syncRockboxLibs(Context context, File workDir) {
        if (OtaRootShell.run("[ -f /system/etc/solar/sync-rockbox-libs.sh ]"
                + " && sh /system/etc/solar/sync-rockbox-libs.sh")) {
            return libDirHasRockbox();
        }
        File libsApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_LIBS);
        File installApk = new File(workDir, OtaCompanionUrls.FILE_ROCKBOX_INSTALL);
        File apkForUnzip = isValidApk(libsApk) ? libsApk
                : (isValidApk(installApk) ? installApk : null);
        if (apkForUnzip != null && unzipLibsFromApk(apkForUnzip)) {
            return libDirHasRockbox();
        }
        File zip = new File(workDir, OtaCompanionUrls.FILE_UPDATE_ZIP);
        if (!zip.isFile()) {
            if (isOnline(context)) {
                try {
                    OtaDownload.downloadToFile(OtaCompanionUrls.ROCKBOX_UPDATE_ZIP_URL, zip, USER_AGENT);
                } catch (IOException e) {
                    Log.w(TAG, "update.zip download failed: " + e.getMessage());
                }
            }
        }
        if (zip.isFile() && unzipLibsFromUpdateZip(zip)) {
            return libDirHasRockbox();
        }
        Log.w(TAG, "Rockbox lib sync failed");
        return false;
    }

    private static boolean unzipLibsFromApk(File apk) {
        String qApk = SolarApkInstaller.shQuote(apk.getAbsolutePath());
        String cmd = "_tmp=/data/local/tmp/solar-rb-lib-extract;"
                + "LIBDIR=/data/data/org.rockbox/lib;"
                + "rm -rf $_tmp; mkdir -p $_tmp $_tmp/lib/armeabi $LIBDIR;"
                + "unzip -o -q " + qApk + " 'lib/armeabi/*.so' -d $_tmp"
                + " && rm -f $LIBDIR/*.so"
                + " && cp -a $_tmp/lib/armeabi/. $LIBDIR/"
                + " && chmod 755 $LIBDIR/*.so"
                + " && echo package:" + OtaCompanionUrls.ROCKBOX_SYSTEM_APK
                + " > /data/data/org.rockbox/.solar_lib_apk_path"
                + " && rm -rf $_tmp";
        return OtaRootShell.run(cmd);
    }

    private static boolean unzipLibsFromUpdateZip(File zip) {
        String qZip = SolarApkInstaller.shQuote(zip.getAbsolutePath());
        String cmd = "_tmp=/data/local/tmp/solar-rb-lib-extract;"
                + "LIBDIR=/data/data/org.rockbox/lib;"
                + "rm -rf $_tmp; mkdir -p $_tmp $LIBDIR;"
                + "unzip -o -q " + qZip + " 'update/libs/armeabi/*.so' -d $_tmp"
                + " && rm -f $LIBDIR/*.so"
                + " && cp -a $_tmp/update/libs/armeabi/. $LIBDIR/"
                + " && chmod 755 $LIBDIR/*.so"
                + " && echo package:" + OtaCompanionUrls.ROCKBOX_SYSTEM_APK
                + " > /data/data/org.rockbox/.solar_lib_apk_path"
                + " && rm -rf $_tmp";
        return OtaRootShell.run(cmd);
    }

    private static boolean libDirHasRockbox() {
        String out = OtaRootShell.runCapture(
                "[ -f /data/data/org.rockbox/lib/librockbox.so ] && echo yes");
        return out != null && out.contains("yes");
    }

    /** True when pm must not read apkPath directly (other apps' private storage). */
    static boolean needsPmStage(String apkPath) {
        return apkPath != null
                && (apkPath.startsWith("/data/data/") || apkPath.startsWith("/data/user/"));
    }

    private static void downloadOptional(String[] urls, File dest, String label) {
        try {
            OtaDownload.downloadFirstOk(urls, dest, USER_AGENT);
            if (!isValidApk(dest) && !dest.getName().endsWith(".zip")) {
                Log.w(TAG, label + " download invalid size");
                dest.delete();
            }
        } catch (IOException e) {
            Log.w(TAG, label + " download failed: " + e.getMessage());
            if (dest.isFile()) dest.delete();
        }
    }

    private static boolean isValidApk(File f) {
        if (f == null || !f.isFile() || f.length() < MIN_APK_BYTES) return false;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] magic = new byte[2];
            return in.read(magic) == 2 && magic[0] == 'P' && magic[1] == 'K';
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean isPackageInstalled(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isOnline(Context context) {
        if (context == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private static void OtaTlsInit(Context context) {
        try {
            com.solar.ota.net.OtaTlsHelper.ensureSecurityProvider();
        } catch (Exception ignored) {}
    }

    private static void notify(Progress progress, String phase) {
        if (progress != null) progress.onPhase(phase);
    }

    private static boolean sameFile(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    private static void copyFile(File src, File dest) throws IOException {
        FileInputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }
}
