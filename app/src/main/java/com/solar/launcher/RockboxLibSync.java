package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 2026-07-06 — Syncs org.rockbox native libs from APK to /data/data/org.rockbox/lib/.
 * Stale .so after APK update causes silent Rockbox playback (Solar MediaPlayer unaffected).
 * Triggers: boot (99SolarInit.sh), switch-to-rockbox, RockboxCoexistence.ensureOnSolarStart.
 * When changing: platform/rockbox/sync-rockbox-*.sh primary; y1/ legacy fallback.
 * Reversal: skip sync; Rockbox may play silence until manual lib refresh.
 */
public final class RockboxLibSync {
    private static final String TAG = "RockboxLibSync";
    private static final String ASSET_LIBS_PLATFORM = "platform/rockbox/sync-rockbox-libs.sh";
    private static final String ASSET_ASSETS_PLATFORM = "platform/rockbox/sync-rockbox-assets.sh";
    private static final String ASSET_LIBS_LEGACY = "y1/sync-rockbox-libs.sh";
    private static final String ASSET_ASSETS_LEGACY = "y1/sync-rockbox-assets.sh";
    private static final String SYSTEM_LIBS = "/system/etc/solar/sync-rockbox-libs.sh";
    private static final String SYSTEM_ASSETS = "/system/etc/solar/sync-rockbox-assets.sh";

    private RockboxLibSync() {}

    /** Install bundled sync scripts and run lib + asset bootstrap before Rockbox. */
    public static boolean syncBeforeRockboxSwitch(Context context) {
        if (!LauncherSwitch.isRockboxAvailable(context)) return true;
        File libs = ensureRunnableScript(context, resolveLibsAsset(context), SYSTEM_LIBS, "sync-rockbox-libs.sh");
        File assets = ensureRunnableScript(context, resolveAssetsAsset(context), SYSTEM_ASSETS,
                "sync-rockbox-assets.sh");
        if (libs == null) {
            Log.w(TAG, "lib sync script unavailable");
            return false;
        }
        boolean ok = runSu("sh " + shellQuote(libs.getAbsolutePath()));
        if (assets != null) {
            ok = runSu("sh " + shellQuote(assets.getAbsolutePath())) && ok;
        }
        if (ok) Log.i(TAG, "Rockbox libs + assets synced before switch");
        return ok;
    }

    /** Boot-time: seed scripts to system and sync if Rockbox APK is present. */
    static void ensureOnSolarStart(Context context) {
        if (!LauncherSwitch.isRockboxAvailable(context)) return;
        File libs = ensureRunnableScript(context, resolveLibsAsset(context), SYSTEM_LIBS, "sync-rockbox-libs.sh");
        ensureRunnableScript(context, resolveAssetsAsset(context), SYSTEM_ASSETS, "sync-rockbox-assets.sh");
        if (libs == null) return;
        runSu("[ -f " + shellQuote(SYSTEM_LIBS) + " ] && sh " + shellQuote(SYSTEM_LIBS));
        runSu("[ -f " + shellQuote(SYSTEM_ASSETS) + " ] && sh " + shellQuote(SYSTEM_ASSETS));
    }

    private static String resolveLibsAsset(Context context) {
        if (assetExists(context, ASSET_LIBS_PLATFORM)) return ASSET_LIBS_PLATFORM;
        return ASSET_LIBS_LEGACY;
    }

    private static String resolveAssetsAsset(Context context) {
        if (assetExists(context, ASSET_ASSETS_PLATFORM)) return ASSET_ASSETS_PLATFORM;
        return ASSET_ASSETS_LEGACY;
    }

    private static boolean assetExists(Context context, String path) {
        try {
            InputStream in = context.getAssets().open(path);
            in.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static File ensureRunnableScript(Context context, String assetPath, String systemPath,
            String cacheName) {
        runSu("mkdir -p /system/etc/solar");
        File cached = new File(context.getCacheDir(), cacheName);
        if (!extractAsset(context, assetPath, cached)) return null;
        runSu("cp " + shellQuote(cached.getAbsolutePath()) + " " + shellQuote(systemPath)
                + " && chmod 755 " + shellQuote(systemPath));
        if (new File(systemPath).exists()) return new File(systemPath);
        runSu("chmod 755 " + shellQuote(cached.getAbsolutePath()));
        return cached;
    }

    private static boolean extractAsset(Context context, String assetPath, File out) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = context.getAssets().open(assetPath);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) fos.write(buf, 0, n);
            }
            fos.flush();
            return out.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
        }
    }

    private static boolean runSu(String command) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
