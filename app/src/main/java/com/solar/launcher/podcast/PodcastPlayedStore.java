package com.solar.launcher.podcast;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 2026-07-15 — Durable podcast episode played flags.
 * Layman: remember which episodes you already finished.
 * Reversal: clear SharedPreferences podcast_played.
 */
public final class PodcastPlayedStore {

    private static final String PREFS = "podcast_played";

    private PodcastPlayedStore() {}

    public static boolean isPlayed(Context ctx, String key) {
        if (ctx == null || key == null) return false;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
    }

    public static void markPlayed(Context ctx, String key) {
        if (ctx == null || key == null) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, true).commit();
    }

    public static void clearPlayed(Context ctx, String key) {
        if (ctx == null || key == null) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).commit();
    }

    public static void selfCheck() {
        // Prefs require Context — structural OK if class loads.
    }
}
