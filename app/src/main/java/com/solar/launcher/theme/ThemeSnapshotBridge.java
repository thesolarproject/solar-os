package com.solar.launcher.theme;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * 2026-07-05 — Compact active-theme snapshot for instant overlay RAM restore at process start.
 * Layman: a tiny JSON cheat sheet so global modals paint the right theme before heavy I/O finishes.
 */
public final class ThemeSnapshotBridge {

    /** Sidecar filename — read by Solar main + :overlay processes (not Xposed). */
    public static final String SNAPSHOT_FILE = "theme-snapshot.json";
    private static final int SNAPSHOT_VERSION = 1;

    private ThemeSnapshotBridge() {}

    /** Write current theme folder + internal cache path to all sidecar roots. */
    public static void publish(Context ctx) {
        if (ctx == null) return;
        ThemeManager.ThemeEntry active = ThemeManager.getCurrentTheme();
        if (active == null || active.folderName == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("version", SNAPSHOT_VERSION);
            json.put("themeFolder", active.folderName);
            json.put("internalPath", resolveInternalPath(ctx, active));
            File mirror = ThemeMirrorHelper.findMirroredThemeDir(ctx, active.folderName);
            if (mirror != null) {
                json.put("mirrorPath", mirror.getAbsolutePath());
            }
            json.put("panelColor", ThemeManager.getContextMenuPanelColor());
            json.put("dialogTextColor", ThemeManager.getDialogTextColor());
            json.put("rowSelectionFillColor", ThemeManager.getRowSelectionFillColor());
            json.put("savedAt", System.currentTimeMillis());
            SidecarPublishHelper.publishBytes(ctx, SNAPSHOT_FILE, json.toString().getBytes("UTF-8"));
        } catch (Throwable ignored) {
            SidecarPublishHelper.deleteFromAllRoots(ctx, SNAPSHOT_FILE);
        }
    }

    /**
     * 2026-07-05 — Synchronous RAM warm from snapshot — call on Application main thread at boot.
     * Returns true when a theme entry was restored into {@link ThemeManager}.
     */
    public static boolean loadIntoThemeManager(Context ctx) {
        if (ctx == null) return false;
        Context app = ctx.getApplicationContext();
        ActiveThemeEngine.init(app);
        File sidecar = SidecarPublishHelper.readFirstSidecar(app, SNAPSHOT_FILE);
        if (sidecar == null) return false;
        try {
            JSONObject json = readJson(sidecar);
            if (json == null) return false;
            String folder = json.optString("themeFolder", "");
            String internalPath = json.optString("internalPath", "");
            String mirrorPath = json.optString("mirrorPath", "");
            if (folder.length() == 0) return false;
            ThemeManager.ThemeEntry entry = ThemeManager.parseFolderForOverlay(
                    resolveLoadDir(folder, internalPath, mirrorPath, app));
            if (entry == null) {
                entry = buildSnapshotStubEntry(folder, internalPath, mirrorPath, json);
            }
            if (entry == null) return false;
            ThemeManager.installOverlayRamEntry(entry, folder);
            ThemeManager.preloadOverlayFontFromRam();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Parse sidecar JSON for unit tests. */
    public static String readThemeFolder(String json) {
        try {
            return new JSONObject(json).optString("themeFolder", "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static File resolveLoadDir(String folder, String internalPath, String mirrorPath, Context app) {
        if (mirrorPath != null && mirrorPath.length() > 0) {
            File mirror = new File(mirrorPath);
            if (new File(mirror, "config.json").isFile()) return mirror;
        }
        File mirrored = ThemeMirrorHelper.findMirroredThemeDir(app, folder);
        if (mirrored != null) return mirrored;
        if (internalPath != null && internalPath.length() > 0) {
            File dir = new File(internalPath);
            if (new File(dir, "config.json").isFile()) return dir;
        }
        File mmc = new File(ThemeManager.internalThemesDir(app), folder);
        if (new File(mmc, "config.json").isFile()) return mmc;
        return mmc;
    }

    /** Minimal theme entry from snapshot colors when full config.json is not readable yet. */
    static ThemeManager.ThemeEntry buildSnapshotStubEntry(String folder, String internalPath,
            String mirrorPath, JSONObject json) {
        if (json == null || folder == null || folder.length() == 0) return null;
        try {
            JSONObject root = new JSONObject();
            JSONObject dialog = new JSONObject();
            if (json.has("dialogTextColor")) {
                dialog.put("dialogTextColor", colorHex(json.getInt("dialogTextColor")));
            }
            root.put("dialogConfig", dialog);
            if (json.has("rowSelectionFillColor")) {
                String rowHex = colorHex(json.getInt("rowSelectionFillColor"));
                JSONObject item = new JSONObject();
                item.put("itemSelectedTextColor", rowHex);
                root.put("itemConfig", item);
                JSONObject player = new JSONObject();
                player.put("progressColor", rowHex);
                root.put("playerConfig", player);
            }
            String path = internalPath != null && internalPath.length() > 0 ? internalPath : mirrorPath;
            if (path == null) path = "";
            return new ThemeManager.ThemeEntry(path, folder, folder, root);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** ARGB int → #AARRGGBB for Y1 config.json colour fields. */
    private static String colorHex(int color) {
        return String.format(Locale.US, "#%08X", color);
    }

    private static String resolveInternalPath(Context ctx, ThemeManager.ThemeEntry active) {
        if (active.folderName != null) {
            File cacheDir = new File(ThemeManager.internalThemesDir(ctx), active.folderName);
            if (new File(cacheDir, "config.json").isFile()) {
                return cacheDir.getAbsolutePath();
            }
        }
        if (active.folderPath != null && !active.folderPath.startsWith("asset://")) {
            return active.folderPath;
        }
        return "";
    }

    private static JSONObject readJson(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
