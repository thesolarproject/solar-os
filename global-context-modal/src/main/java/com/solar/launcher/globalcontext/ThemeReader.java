package com.solar.launcher.globalcontext;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.io.File;

/**
 * 2026-07-05 — Theme colors for companion overlay shell.
 * Layman: tries to match Solar's look from shared prefs; falls back to bundled Aura-like palette.
 * Technical: reads {@code /data/data/com.solar.launcher/shared_prefs/} when permitted.
 * Reversal: delete; overlay uses hard-coded gray only.
 */
public final class ThemeReader {

    /** Bundled fallback when Solar prefs unreadable (no root / first boot). */
    public static final int FALLBACK_BG = 0xFF1A1A1A;
    public static final int FALLBACK_FG = 0xFFFFFFFF;
    public static final int FALLBACK_ACCENT = 0xFF4A90D9;

    private static volatile int cachedBg = FALLBACK_BG;
    private static volatile int cachedFg = FALLBACK_FG;
    private static volatile int cachedAccent = FALLBACK_ACCENT;

    private ThemeReader() {}

    /** Load theme once per overlay open — cheap file read. */
    public static void refresh(Context ctx) {
        if (ctx == null) return;
        try {
            Context solar = ctx.createPackageContext(
                    com.solar.input.policy.GlobalInputPolicy.SOLAR_PKG, Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences prefs = solar.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
            cachedBg = parseColor(prefs.getString("bg_color", null), FALLBACK_BG);
            cachedFg = parseColor(prefs.getString("fg_color", null), FALLBACK_FG);
            cachedAccent = parseColor(prefs.getString("accent_color", null), FALLBACK_ACCENT);
            return;
        } catch (Throwable ignored) {}
        // SD-side theme JSON — Phase 3 IPC may replace this path.
        try {
            File sd = new File("/storage/sdcard0/Themes/active.json");
            if (!sd.isFile() && CompanionDeviceFeatures.isY2()) {
                sd = new File("/storage/sdcard1/Themes/active.json");
            }
            if (sd.isFile()) {
                // ponytail: full JSON parse deferred — bundled fallback sufficient for skeleton.
            }
        } catch (Throwable ignored) {}
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

    private static int parseColor(String hex, int fallback) {
        if (hex == null || hex.length() == 0) return fallback;
        try {
            if (!hex.startsWith("#")) hex = "#" + hex;
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
