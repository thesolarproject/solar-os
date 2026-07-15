package com.solar.launcher.plex;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Plex server URL + token in Solar prefs.
 */
public final class PlexPrefs {

    private static final String PREF_URL = "plex_url";
    private static final String PREF_TOKEN = "plex_token";

    private PlexPrefs() {}

    public static void load(PlexClient client, SharedPreferences prefs) {
        if (client == null || prefs == null) return;
        client.applySettings(
                prefs.getString(PREF_URL, ""),
                prefs.getString(PREF_TOKEN, ""));
    }

    public static void save(Context ctx, SharedPreferences prefs, String url, String token) {
        if (prefs == null) return;
        String normUrl = PlexClient.normalizeServerUrl(url);
        prefs.edit()
                .putString(PREF_URL, normUrl)
                .putString(PREF_TOKEN, token != null ? token.trim() : "")
                .commit();
        PlexClient.getInstance().applySettings(normUrl, token);
        PlexCacheStore.getInstance(ctx).clearArtists();
    }

    public static boolean isConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        String url = prefs.getString(PREF_URL, "");
        String token = prefs.getString(PREF_TOKEN, "");
        return url != null && !url.trim().isEmpty()
                && token != null && !token.trim().isEmpty();
    }
}
