package com.solar.launcher.theme;

import android.content.Context;

import java.io.File;
import java.util.List;

/**
 * 2026-07-05 — Dual-volume mirror of the active theme tree under every {@code /.solar/active-theme/} root.
 * Layman: keep the same theme folder on app storage and each SD so overlays find it fast when one volume is slow.
 * Technical: app-private {@code filesDir/.solar} first, then {@link com.solar.launcher.DeviceFeatures#getStorageRoots()}.
 */
public final class ThemeMirrorHelper {

    /** Subfolder under each {@link SidecarPublishHelper#SIDECAR_DIR} holding copied theme trees. */
    public static final String ACTIVE_THEME_SUBDIR = "active-theme";

    private ThemeMirrorHelper() {}

    /** Every sidecar root that may hold a mirrored theme folder — same order as snapshot reads. */
    public static List<File> mirrorSidecarRoots(Context ctx) {
        return SidecarPublishHelper.sidecarDirs(ctx);
    }

    /** Full path for one theme folder under a given {@code .solar} parent. */
    public static File mirroredThemeDir(File sidecarRoot, String folderName) {
        if (sidecarRoot == null || folderName == null || folderName.isEmpty()) {
            return new File("/dev/null");
        }
        return new File(new File(sidecarRoot, ACTIVE_THEME_SUBDIR), folderName);
    }

    /**
     * 2026-07-05 — First readable mirrored theme with config.json — app-private, then MicroSD, then Y2 internal.
     * Rollback: overlays fall back to {@link ThemeManager#internalThemesDir} and SD Themes/ scan.
     */
    public static File findMirroredThemeDir(Context ctx, String folderName) {
        if (folderName == null || folderName.isEmpty()) return null;
        for (File root : mirrorSidecarRoots(ctx)) {
            File dir = mirroredThemeDir(root, folderName);
            if (new File(dir, "config.json").isFile()) return dir;
        }
        return null;
    }

    /**
     * Copy {@code sourceDir} to every mirror root when stale or missing.
     * Call after {@link ThemeManager#cacheActiveTheme} writes app-private MMC cache.
     */
    public static void mirrorActiveTheme(Context ctx, File sourceDir, String folderName) {
        if (ctx == null || sourceDir == null || !sourceDir.isDirectory()) return;
        if (folderName == null || folderName.isEmpty()) return;
        if (!new File(sourceDir, "config.json").isFile()) return;
        for (File root : mirrorSidecarRoots(ctx)) {
            File dest = mirroredThemeDir(root, folderName);
            if (shouldSkipMirror(sourceDir, dest)) continue;
            try {
                SidecarPublishHelper.copyDirectoryRecursive(sourceDir, dest);
                SidecarPublishHelper.markTreeWorldReadable(dest);
            } catch (Exception ignored) {}
        }
    }

    /** Skip copy when mirror config is present and at least as new as the source tree. */
    static boolean shouldSkipMirror(File sourceDir, File destDir) {
        if (sourceDir == null || destDir == null) return true;
        File destCfg = new File(destDir, "config.json");
        return destCfg.isFile() && destCfg.length() > 0
                && sourceDir.lastModified() <= destDir.lastModified();
    }
}
