package com.solar.launcher.video;

import android.content.Context;
import android.content.SharedPreferences;

import tv.danmaku.ijk.media.example.widget.media.IRenderView;

/**
 * Video playback user prefs.
 * 2026-07-15 — Crop mode for 4:3 panels (letterbox vs centre-crop).
 * Layman: black bars around wide videos, or zoom to fill the square-ish screen.
 * Reversal: drop crop prefs; surfaces stay stretch MATCH_PARENT.
 */
public final class VideoSettings {
    public static final String PREF_SLEEP_DURING_PLAYBACK = "sleep_during_playback";
    /** Letterbox — fit entire frame (default on 4:3). */
    public static final String CROP_LETTERBOX = "letterbox";
    /** Zoom / crop edges to fill the panel (TV Zoom analogue). */
    public static final String CROP_FILL = "crop43";

    private static final String PREFS = "video_settings";
    private static final String PREF_CROP = "video_crop_mode";

    private VideoSettings() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** When true, center-hold and context lock may turn the screen off during video playback. */
    public static boolean getSleepDuringPlayback(Context ctx) {
        return prefs(ctx).getBoolean(PREF_SLEEP_DURING_PLAYBACK, true);
    }

    public static void setSleepDuringPlayback(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(PREF_SLEEP_DURING_PLAYBACK, enabled).commit();
    }

    /**
     * 2026-07-15 — Current crop preference ({@link #CROP_LETTERBOX} default).
     * Layman: how the picture fits our nearly-square screen.
     */
    public static String getCropMode(Context ctx) {
        String v = prefs(ctx).getString(PREF_CROP, CROP_LETTERBOX);
        return CROP_FILL.equals(v) ? CROP_FILL : CROP_LETTERBOX;
    }

    /** Cycle letterbox ↔ crop-to-fill; returns the new mode. */
    public static String cycleCropMode(Context ctx) {
        String next = CROP_FILL.equals(getCropMode(ctx)) ? CROP_LETTERBOX : CROP_FILL;
        prefs(ctx).edit().putString(PREF_CROP, next).commit();
        return next;
    }

    public static void setCropMode(Context ctx, String mode) {
        String v = CROP_FILL.equals(mode) ? CROP_FILL : CROP_LETTERBOX;
        prefs(ctx).edit().putString(PREF_CROP, v).commit();
    }

    /**
     * 2026-07-15 — Map pref → IJK {@link IRenderView} aspect constant.
     * Letterbox = fit parent; crop = fill parent (may clip left/right on 16:9).
     */
    public static int ijkAspectRatio(Context ctx) {
        return CROP_FILL.equals(getCropMode(ctx))
                ? IRenderView.AR_ASPECT_FILL_PARENT
                : IRenderView.AR_ASPECT_FIT_PARENT;
    }

    /** Short label for settings row state. */
    public static String cropModeLabel(Context ctx) {
        return CROP_FILL.equals(getCropMode(ctx)) ? "Crop 4:3" : "Letterbox";
    }
}
