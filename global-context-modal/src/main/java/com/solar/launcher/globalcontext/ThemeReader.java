package com.solar.launcher.globalcontext;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.SoftReference;

/**
 * 2026-07-10 — Theme colors for companion overlay shell from Solar-published sidecars.
 * Layman: match Solar’s themed quick menu (panel, text, selection) instead of gray fallback.
 * Technical: reads {@code /.solar/theme-skin.json}, {@code theme-snapshot.json},
 * {@code theme-colors.json} (same roots as Xposed ThemeSkinSidecar / ThemeColorSidecar).
 * Was: fake {@code theme_prefs}/{@code bg_color} SharedPreferences that Solar never writes.
 * Reversal: restore hard-coded FALLBACK_* only if sidecars are retired.
 */
public final class ThemeReader {

    private static final String TAG = "ThemeReader";

    /** Bundled fallback when Solar prefs/sidecars unreadable (no theme yet / first boot). */
    public static final int FALLBACK_BG = 0xFF1A1A1A;
    public static final int FALLBACK_FG = 0xFFFFFFFF;
    public static final int FALLBACK_ACCENT = 0xFF4A90D9;
    public static final int FALLBACK_PANEL = 0xEE252528;

    private static final String SIDECAR_DIR = ".solar";
    private static final String SKIN_JSON = "theme-skin.json";
    private static final String SNAPSHOT_JSON = "theme-snapshot.json";
    private static final String COLORS_JSON = "theme-colors.json";
    private static final String SELECTION_PNG = "theme-skin-selection.png";

    /** Same volume order as Xposed FontSidecar / ThemeSkinSidecar. */
    private static final String[] CANDIDATE_ROOTS = {
            "/storage/sdcard1",
            "/storage/sdcard0",
            "/mnt/sdcard",
            "/sdcard"
    };
    /**
     * 2026-07-10 — Flat dirs (files live here, not under {@code .solar/}) for world-readable mirrors.
     * Solar may publish here when companion cannot read group-sdcard_r MicroSD.
     */
    private static final String[] FLAT_SIDECAR_DIRS = {
            "/data/local/tmp/solar-theme",
            "/data/media/0/.solar"
    };

    private static volatile int cachedBg = FALLBACK_BG;
    private static volatile int cachedFg = FALLBACK_FG;
    private static volatile int cachedAccent = FALLBACK_ACCENT;
    private static volatile int cachedPanel = FALLBACK_PANEL;
    private static volatile int cachedSelectedText = FALLBACK_BG;
    private static volatile int cachedMuted = 0xFFAAAAAA;
    private static volatile boolean loadedFromSidecar = false;

    private static SoftReference<Bitmap> selectionRef;
    private static volatile long selectionMtime;
    private static volatile File selectionFile;

    private ThemeReader() {}

    /** Load theme once per overlay open — cheap file read. */
    public static void refresh(Context ctx) {
        int bg = FALLBACK_BG;
        int fg = FALLBACK_FG;
        int accent = FALLBACK_ACCENT;
        int panel = FALLBACK_PANEL;
        int selectedText = FALLBACK_BG;
        int muted = 0xFFAAAAAA;
        boolean any = false;
        File selPng = null;

        // 1) theme-skin.json — richest colors for system paint (ThemeSkinBridge).
        File skin = resolveSidecar(SKIN_JSON);
        if (skin != null) {
            JSONObject json = readJson(skin);
            if (json != null && json.optBoolean("enabled", true)) {
                int skinBg = json.optInt("backgroundColor", 0);
                int skinSel = json.optInt("rowSelectionFillColor", 0);
                int skinFg = json.optInt("textPrimary", 0);
                int skinMuted = json.optInt("textMuted", 0);
                int skinSelText = json.optInt("selectedTextColor", 0);
                if (isMeaningfulColor(skinBg)) {
                    bg = skinBg;
                    panel = withPanelAlpha(skinBg);
                    any = true;
                }
                // 2026-07-10 — Skin JSON uses -1 as “inherit”; treat 0 and -1 as unset.
                if (isMeaningfulColor(skinSel)) {
                    accent = skinSel | 0xFF000000;
                    any = true;
                }
                if (isMeaningfulColor(skinFg)) {
                    fg = skinFg | 0xFF000000;
                    any = true;
                }
                if (isMeaningfulColor(skinMuted)) {
                    muted = skinMuted | 0xFF000000;
                    any = true;
                }
                if (isMeaningfulColor(skinSelText)) {
                    selectedText = skinSelText | 0xFF000000;
                    any = true;
                }
                if (json.optBoolean("hasSelectionBitmap", false)) {
                    selPng = resolveSidecar(SELECTION_PNG);
                }
            }
        }

        // 2) theme-snapshot.json — panel + dialog text (ThemeSnapshotBridge).
        File snap = resolveSidecar(SNAPSHOT_JSON);
        if (snap != null) {
            JSONObject json = readJson(snap);
            if (json != null) {
                int p = json.optInt("panelColor", 0);
                int t = json.optInt("dialogTextColor", 0);
                int r = json.optInt("rowSelectionFillColor", 0);
                if (p != 0) {
                    panel = p;
                    if (!any || bg == FALLBACK_BG) {
                        bg = p | 0xFF000000;
                    }
                    any = true;
                }
                if (t != 0) {
                    fg = t | 0xFF000000;
                    any = true;
                }
                if (r != 0) {
                    accent = r | 0xFF000000;
                    any = true;
                }
            }
        }

        // 3) theme-colors.json — panel + dialog text (ThemeColorBridge / Xposed Holo).
        File colors = resolveSidecar(COLORS_JSON);
        if (colors != null) {
            JSONObject json = readJson(colors);
            if (json != null) {
                int p = json.optInt("panelColor", 0);
                int t = json.optInt("textColor", 0);
                if (p != 0 && !any) {
                    panel = p;
                    bg = p | 0xFF000000;
                    any = true;
                } else if (p != 0 && panel == FALLBACK_PANEL) {
                    panel = p;
                    any = true;
                }
                if (t != 0 && fg == FALLBACK_FG) {
                    fg = t | 0xFF000000;
                    any = true;
                }
            }
        }

        // 4) Legacy: wrong prefs keys (never written by ThemeManager) — kept as last-ditch.
        if (!any && ctx != null) {
            try {
                Context solar = ctx.createPackageContext(
                        com.solar.input.policy.GlobalInputPolicy.SOLAR_PKG,
                        Context.CONTEXT_IGNORE_SECURITY);
                SharedPreferences prefs = solar.getSharedPreferences(
                        "SOLAR_SETTINGS", Context.MODE_PRIVATE);
                // ThemeManager stores booleans/ints here — no color hex strings today.
                // Prefer ignore; leave fallbacks if nothing useful.
                prefs.getAll();
            } catch (Throwable ignored) {}
        }

        cachedBg = bg;
        cachedFg = fg;
        cachedAccent = accent;
        cachedPanel = panel;
        cachedSelectedText = selectedText;
        cachedMuted = muted;
        loadedFromSidecar = any;
        selectionFile = selPng;
        if (selPng == null) {
            selectionRef = null;
            selectionMtime = 0;
        }

        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "refresh any=" + any
                        + " panel=#" + Integer.toHexString(panel)
                        + " fg=#" + Integer.toHexString(fg)
                        + " accent=#" + Integer.toHexString(accent)
                        + " skin=" + (skin != null)
                        + " snap=" + (snap != null)
                        + " colors=" + (colors != null));
            }
        } catch (Throwable ignored) {
            // Host unit tests: android.util.Log may be a stub that throws.
        }
    }

    /** True when at least one Solar sidecar contributed colors. */
    public static boolean hasSidecarTheme() {
        return loadedFromSidecar;
    }

    public static int backgroundColor() {
        return cachedBg;
    }

    public static int foregroundColor() {
        return cachedFg;
    }

    public static int accentColor() {
        return cachedAccent;
    }

    /** Context-menu panel fill (may include alpha). */
    public static int panelColor() {
        return cachedPanel;
    }

    public static int selectedTextColor() {
        return cachedSelectedText;
    }

    public static int mutedTextColor() {
        return cachedMuted;
    }

    /**
     * Theme row-selection strip when published; null when missing.
     * Soft-referenced; reloads on file mtime change.
     */
    public static Bitmap selectionBitmap() {
        File file = selectionFile;
        if (file == null || !file.isFile()) return null;
        long mtime = file.lastModified();
        Bitmap held = selectionRef != null ? selectionRef.get() : null;
        if (held != null && !held.isRecycled() && mtime == selectionMtime) {
            return held;
        }
        try {
            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (decoded == null) return null;
            selectionMtime = mtime;
            selectionRef = new SoftReference<Bitmap>(decoded);
            return decoded;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File resolveSidecar(String fileName) {
        if (fileName == null || fileName.length() == 0) return null;
        for (String root : CANDIDATE_ROOTS) {
            File f = new File(new File(root, SIDECAR_DIR), fileName);
            // Prefer canRead so we skip MicroSD paths the companion UID cannot open.
            if (f.isFile() && f.length() > 0 && f.canRead()) return f;
        }
        for (String dir : FLAT_SIDECAR_DIRS) {
            File f = new File(dir, fileName);
            if (f.isFile() && f.length() > 0 && f.canRead()) return f;
        }
        return null;
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
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Opaque theme bg → translucent panel (matches Solar context chrome ~0xEE). */
    private static int withPanelAlpha(int color) {
        int rgb = color & 0x00FFFFFF;
        return 0xEE000000 | rgb;
    }

    /**
     * 2026-07-10 — Skin JSON uses 0 or -1 as “inherit / unset” (not a real paint color).
     * Layman: skip junk color tokens so selection/text stay readable.
     */
    private static boolean isMeaningfulColor(int argb) {
        return argb != 0 && argb != -1;
    }
}
