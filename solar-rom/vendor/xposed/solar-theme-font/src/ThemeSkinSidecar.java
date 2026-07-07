package com.solar.launcher.xposed.themefont;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.SoftReference;

/**
 * 2026-07-05 — Reads Solar-published theme skin from /.solar/ sidecars.
 * Layman: other apps learn wallpaper, highlight colors, and row art from these files.
 */
final class ThemeSkinSidecar {

    static final String SIDECAR_JSON = "theme-skin.json";
    static final String SIDECAR_WALLPAPER = "theme-skin-wallpaper.png";
    static final String SIDECAR_SELECTION = "theme-skin-selection.png";

    private static final String[] CANDIDATE_ROOTS = {
            "/storage/sdcard1",
            "/storage/sdcard0",
            "/mnt/sdcard",
            "/sdcard"
    };

    private static volatile SkinData cached;
    private static volatile long cachedJsonMtime;
    private static volatile long lastCheckMs;
    private static final long CHECK_INTERVAL_MS = 5000L;

    private static SoftReference<android.graphics.Bitmap> wallpaperRef;
    private static SoftReference<android.graphics.Bitmap> selectionRef;
    private static volatile long wallpaperMtime;
    private static volatile long selectionMtime;

    /** Parsed skin contract — immutable snapshot per mtime refresh. */
    static final class SkinData {
        final boolean enabled;
        final int backgroundColor;
        final int rowSelectionFillColor;
        final int selectedTextColor;
        final int textPrimary;
        final int textMuted;
        final int statusBarColor;
        final int statusBarTextColor;
        final boolean hasWallpaper;
        final boolean hasSelectionBitmap;

        SkinData(boolean enabled, int backgroundColor, int rowSelectionFillColor,
                 int selectedTextColor, int textPrimary, int textMuted,
                 int statusBarColor, int statusBarTextColor,
                 boolean hasWallpaper, boolean hasSelectionBitmap) {
            this.enabled = enabled;
            this.backgroundColor = backgroundColor;
            this.rowSelectionFillColor = rowSelectionFillColor;
            this.selectedTextColor = selectedTextColor;
            this.textPrimary = textPrimary;
            this.textMuted = textMuted;
            this.statusBarColor = statusBarColor;
            this.statusBarTextColor = statusBarTextColor;
            this.hasWallpaper = hasWallpaper;
            this.hasSelectionBitmap = hasSelectionBitmap;
        }

        static SkinData disabled() {
            return new SkinData(false, 0, 0, 0, 0, 0, 0, 0, false, false);
        }
    }

    private ThemeSkinSidecar() {}

    /** True when JSON sidecar exists and enabled flag is set. */
    static boolean isEnabled() {
        SkinData data = get();
        return data != null && data.enabled;
    }

    static SkinData get() {
        refreshIfNeeded();
        return cached;
    }

    /** Decode wallpaper once per process — soft-referenced, reloaded on mtime change. */
    static android.graphics.Bitmap wallpaperBitmap() {
        refreshIfNeeded();
        SkinData data = cached;
        if (data == null || !data.enabled || !data.hasWallpaper) return null;
        File file = resolveSidecarFile(SIDECAR_WALLPAPER);
        if (file == null) return null;
        long mtime = file.lastModified();
        android.graphics.Bitmap held = wallpaperRef != null ? wallpaperRef.get() : null;
        if (held != null && !held.isRecycled() && mtime == wallpaperMtime) {
            return held;
        }
        try {
            android.graphics.Bitmap decoded =
                    android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            wallpaperMtime = mtime;
            wallpaperRef = new SoftReference<android.graphics.Bitmap>(decoded);
            return decoded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Decode row selection strip bitmap — same cache rules as wallpaper. */
    static android.graphics.Bitmap selectionBitmap() {
        refreshIfNeeded();
        SkinData data = cached;
        if (data == null || !data.enabled || !data.hasSelectionBitmap) return null;
        File file = resolveSidecarFile(SIDECAR_SELECTION);
        if (file == null) return null;
        long mtime = file.lastModified();
        android.graphics.Bitmap held = selectionRef != null ? selectionRef.get() : null;
        if (held != null && !held.isRecycled() && mtime == selectionMtime) {
            return held;
        }
        try {
            android.graphics.Bitmap decoded =
                    android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            selectionMtime = mtime;
            selectionRef = new SoftReference<android.graphics.Bitmap>(decoded);
            return decoded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_INTERVAL_MS && cached != null) return;
        lastCheckMs = now;
        File jsonFile = resolveSidecarFile(SIDECAR_JSON);
        if (jsonFile == null || !jsonFile.isFile()) {
            cached = SkinData.disabled();
            cachedJsonMtime = 0;
            return;
        }
        long mtime = jsonFile.lastModified();
        if (cached != null && mtime == cachedJsonMtime) return;
        cached = parseJson(jsonFile);
        cachedJsonMtime = mtime;
        wallpaperRef = null;
        selectionRef = null;
    }

    static File resolveSidecarFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        for (String root : CANDIDATE_ROOTS) {
            File f = new File(new File(root, FontSidecar.SIDECAR_DIR), fileName);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    private static SkinData parseJson(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            if (!json.optBoolean("enabled", false)) {
                return SkinData.disabled();
            }
            return new SkinData(
                    true,
                    json.optInt("backgroundColor", 0),
                    json.optInt("rowSelectionFillColor", 0),
                    json.optInt("selectedTextColor", 0),
                    json.optInt("textPrimary", 0),
                    json.optInt("textMuted", 0),
                    json.optInt("statusBarColor", 0),
                    json.optInt("statusBarTextColor", 0),
                    json.optBoolean("hasWallpaper", false),
                    json.optBoolean("hasSelectionBitmap", false));
        } catch (Throwable ignored) {
            return SkinData.disabled();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        }
    }
}
