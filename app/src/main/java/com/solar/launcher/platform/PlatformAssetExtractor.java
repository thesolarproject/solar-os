package com.solar.launcher.platform;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 2026-07-05 — Extract platform/* assets to cache for root cp / pm install.
 * Reversal: delete; installers read /system/app only.
 */
public final class PlatformAssetExtractor {

    private static final String CACHE_SUBDIR = "platform-prep";

    private PlatformAssetExtractor() {}

    /** Cache root — extracted files mirror manifest asset paths. */
    public static File cacheRoot(Context ctx) {
        return new File(ctx.getCacheDir(), CACHE_SUBDIR);
    }

    /** Absolute path after extract — null when asset missing. */
    public static File extractAsset(Context ctx, String assetPath) {
        if (ctx == null || assetPath == null || assetPath.isEmpty()) return null;
        File out = new File(cacheRoot(ctx), assetPath);
        if (out.isFile() && out.length() > 0) return out;
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = ctx.getAssets().open("platform/" + assetPath);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) fos.write(buf, 0, n);
            }
            fos.flush();
            return out.isFile() ? out : null;
        } catch (Exception e) {
            if (out.exists()) out.delete();
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
        }
    }

    /** Bundled module cache path for resolveModuleApkPath fallback. */
    public static String cachedModuleApkPath(Context ctx, String assetPath) {
        File f = extractAsset(ctx, assetPath);
        return f != null ? f.getAbsolutePath() : null;
    }
}
