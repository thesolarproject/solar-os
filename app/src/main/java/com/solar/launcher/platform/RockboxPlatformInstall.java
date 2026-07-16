package com.solar.launcher.platform;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.PmInstallPolicy;
import com.solar.launcher.RockboxCoexistence;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;
import com.solar.launcher.platform.SolarPlatformPrep.PrepResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * 2026-07-06 — Installs org.rockbox + staged libs from APK platform bundle (Y2 prep-delivered).
 * APK/ROM parity: mirrors install_rockbox_from_y1_base in build-rom.sh without ROM-time patch chain.
 * When changing: sync-platform-assets.sh rockbox section + stage-index.json generator.
 * Reversal: delete; Y2 ROM build installs Rockbox again via SOLAR_ROM_LEGACY_ROCKBOX=1.
 */
public final class RockboxPlatformInstall {

    private static final String TAG = "RockboxPlatformInstall";
    private static final String PKG = "org.rockbox";

    private RockboxPlatformInstall() {}

    /** Install Rockbox APK + stage native assets when bundled prep is ahead or gaps exist. */
    public static boolean ensure(Context ctx, PlatformPrepManifest manifest,
            boolean copyToSystem, PrepResult result) {
        if (ctx == null || manifest == null || manifest.rockbox == null) return true;
        if (!RootShell.canRun()) return false;
        boolean y2 = DeviceFeatures.isY2();
        PlatformPrepManifest.RockboxApkEntry apk = manifest.rockbox.apkForDevice(y2);
        if (apk == null) return true;

        boolean ok = true;
        if (needsApkInstall(apk)) {
            ok = installRockboxApk(ctx, apk, copyToSystem) && ok;
        }
        if (copyToSystem) {
            ok = stageRockboxAssets(ctx, manifest.rockbox, y2, true) && ok;
            if (y2 && manifest.rockbox.systemLib != null) {
                ok = copySystemLib(ctx, manifest.rockbox.systemLib) && ok;
            }
        }
        RockboxCoexistence.ensureOnSolarStart(ctx);
        if (!ok && result != null) {
            result.degradedReasons.add("rockbox");
        }
        return ok;
    }

    private static boolean needsApkInstall(PlatformPrepManifest.RockboxApkEntry apk) {
        int installedVc = PlatformProbe.installedVersionCode(PKG);
        boolean pmRegistered = PlatformProbe.packageRegisteredInPm(PKG);
        if (!pmRegistered) return true;
        if (apk.versionCode > 0 && installedVc > 0 && apk.versionCode > installedVc) return true;
        if (apk.systemApk != null && !PlatformProbe.fileExists(apk.systemApk)) return true;
        return PlatformProbe.packageRegisteredOnDataApp(PKG);
    }

    private static boolean installRockboxApk(Context ctx, PlatformPrepManifest.RockboxApkEntry apk,
            boolean copyToSystem) {
        if (copyToSystem && apk.systemApk != null && !PlatformProbe.fileExists(apk.systemApk)) {
            copyApkToSystem(ctx, apk);
        }
        if (needsApkInstall(apk)) {
            if (apk.systemApk != null && PlatformProbe.fileExists(apk.systemApk)) {
                if (PmInstallPolicy.installSystemApp(apk.systemApk, PKG)) {
                    SolarLog.i(TAG, "registered org.rockbox from " + apk.systemApk);
                    return true;
                }
            }
            File extracted = PlatformAssetExtractor.extractAsset(ctx, apk.asset);
            if (extracted == null) {
                SolarLog.w(TAG, "missing rockbox asset " + apk.asset);
                return false;
            }
            if (copyToSystem && apk.systemApk != null) {
                copyApkToSystem(ctx, apk);
            }
            if (apk.systemApk != null && PlatformProbe.fileExists(apk.systemApk)) {
                return PmInstallPolicy.installSystemApp(apk.systemApk, PKG);
            }
            return PmInstallPolicy.installInternal(extracted.getAbsolutePath());
        }
        return true;
    }

    private static void copyApkToSystem(Context ctx, PlatformPrepManifest.RockboxApkEntry apk) {
        File extracted = PlatformAssetExtractor.extractAsset(ctx, apk.asset);
        if (extracted == null || apk.systemApk == null) return;
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "mkdir -p /system/app; "
                + "cp " + PlatformProbe.shellQuote(extracted.getAbsolutePath()) + " "
                + PlatformProbe.shellQuote(apk.systemApk) + " && "
                + "chmod 644 " + PlatformProbe.shellQuote(apk.systemApk) + " && sync";
        RootShell.run(sh);
    }

    private static boolean stageRockboxAssets(Context ctx, PlatformPrepManifest.RockboxConfig rockbox,
            boolean y2, boolean force) {
        String indexAsset = rockbox.stageIndex;
        if (indexAsset == null || indexAsset.isEmpty()) return true;
        File indexFile = PlatformAssetExtractor.extractAsset(ctx, indexAsset);
        if (indexFile == null || !indexFile.isFile()) {
            SolarLog.w(TAG, "missing rockbox stage index");
            return false;
        }
        boolean ok = true;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(indexFile), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray files = root.getJSONArray("files");

            java.util.Set<String> dirs = new java.util.HashSet<String>();
            java.util.List<String> copyCmds = new java.util.ArrayList<String>();

            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                String asset = f.getString("asset");
                String dest = f.getString("dest");
                String mode = f.optString("mode", "644");
                if (dest.equals("/system/lib/librockbox.so")) continue;
                if (!y2 && dest.contains("rockbox-y2")) continue;

                File extracted = PlatformAssetExtractor.extractAsset(ctx, asset);
                if (extracted == null) {
                    SolarLog.w(TAG, "missing asset " + asset);
                    ok = false;
                    continue;
                }
                File destFile = new File(dest);
                String parent = destFile.getParent();
                if (parent != null) dirs.add(parent);

                String srcQuote = PlatformProbe.shellQuote(extracted.getAbsolutePath());
                String destQuote = PlatformProbe.shellQuote(dest);
                if (force) {
                    copyCmds.add("cp -f " + srcQuote + " " + destQuote + " && chmod " + mode + " " + destQuote);
                } else {
                    copyCmds.add("[ -f " + destQuote + " ] || { cp -f " + srcQuote + " " + destQuote + " && chmod " + mode + " " + destQuote + "; }");
                }
            }

            if (copyCmds.isEmpty()) {
                return ok;
            }

            File cacheRoot = PlatformAssetExtractor.cacheRoot(ctx);
            if (!cacheRoot.exists()) cacheRoot.mkdirs();
            File scriptFile = new File(cacheRoot, "stage_rockbox_batch.sh");
            java.io.FileOutputStream fos = null;
            try {
                fos = new java.io.FileOutputStream(scriptFile);
                StringBuilder script = new StringBuilder();
                script.append("#!/system/bin/sh\n");
                script.append("mount -o remount,rw /system 2>/dev/null\n");
                for (String d : dirs) {
                    script.append("mkdir -p ").append(PlatformProbe.shellQuote(d)).append("\n");
                }
                for (String cmd : copyCmds) {
                    script.append(cmd).append("\n");
                }
                script.append("sync\n");
                fos.write(script.toString().getBytes("UTF-8"));
                fos.flush();
            } finally {
                if (fos != null) {
                    try { fos.close(); } catch (Exception ignored) {}
                }
            }
            scriptFile.setReadable(true, false);
            scriptFile.setExecutable(true, false);

            if (!RootShell.run("sh " + PlatformProbe.shellQuote(scriptFile.getAbsolutePath()))) {
                SolarLog.w(TAG, "batch staging script failed");
                ok = false;
            }
            try { scriptFile.delete(); } catch (Exception ignored) {}
        } catch (Exception e) {
            SolarLog.w(TAG, "stage index parse failed: " + e.getMessage());
            return false;
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
        }
        return ok;
    }

    private static boolean copySystemLib(Context ctx, PlatformPrepManifest.RockboxSystemLib lib) {
        return copyAssetFileIfNeeded(ctx, lib.asset, lib.dest, "644", true);
    }

    private static boolean copyAssetFileIfNeeded(Context ctx, String asset, String dest,
            String mode, boolean force) {
        if (!force && PlatformProbe.fileExists(dest)) return true;
        File extracted = PlatformAssetExtractor.extractAsset(ctx, asset);
        if (extracted == null) {
            SolarLog.w(TAG, "missing asset " + asset);
            return false;
        }
        String sh = ""
                + "mount -o remount,rw /system 2>/dev/null; "
                + "mkdir -p $(dirname " + PlatformProbe.shellQuote(dest) + "); "
                + "cp " + PlatformProbe.shellQuote(extracted.getAbsolutePath()) + " "
                + PlatformProbe.shellQuote(dest) + " && "
                + "chmod " + mode + " " + PlatformProbe.shellQuote(dest) + " && sync";
        if (!RootShell.run(sh)) {
            SolarLog.w(TAG, "failed staging " + dest);
            return false;
        }
        return true;
    }
}
