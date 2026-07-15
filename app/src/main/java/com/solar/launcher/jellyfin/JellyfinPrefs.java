package com.solar.launcher.jellyfin;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 2026-07-14: Jellyfin server URL + username/password in Solar prefs.
 */
public final class JellyfinPrefs {

    private static final String PREF_URL = "jellyfin_url";
    private static final String PREF_USER = "jellyfin_user";
    private static final String PREF_PASS = "jellyfin_pass";

    private JellyfinPrefs() {}

    public static void load(JellyfinClient client, SharedPreferences prefs) {
        if (client == null || prefs == null) return;
        client.applySettings(
                prefs.getString(PREF_URL, ""),
                prefs.getString(PREF_USER, ""),
                prefs.getString(PREF_PASS, ""));
    }

    public static void save(Context ctx, SharedPreferences prefs, String url, String user, String pass) {
        if (prefs == null) return;
        String normUrl = JellyfinClient.normalizeServerUrl(url);
        prefs.edit()
                .putString(PREF_URL, normUrl)
                .putString(PREF_USER, user != null ? user.trim() : "")
                .putString(PREF_PASS, pass != null ? pass.trim() : "")
                .commit();
        JellyfinClient.getInstance().applySettings(normUrl, user, pass);
        JellyfinCacheStore.getInstance(ctx).clearArtists();
    }

    public static boolean isConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        String url = prefs.getString(PREF_URL, "");
        String user = prefs.getString(PREF_USER, "");
        return url != null && !url.trim().isEmpty()
                && user != null && !user.trim().isEmpty();
    }
}
