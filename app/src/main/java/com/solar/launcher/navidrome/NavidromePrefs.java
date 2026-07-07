package com.solar.launcher.navidrome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 2026-07-06: Navidrome server URL + credentials in Solar prefs.
 */
public final class NavidromePrefs {

    private static final String PREF_URL = "navidrome_url";
    private static final String PREF_USER = "navidrome_user";
    private static final String PREF_PASS = "navidrome_pass";

    private NavidromePrefs() {}

    public static void load(NavidromeClient client, SharedPreferences prefs) {
        if (client == null || prefs == null) return;
        client.applySettings(
                prefs.getString(PREF_URL, ""),
                prefs.getString(PREF_USER, ""),
                prefs.getString(PREF_PASS, ""));
    }

    public static void save(Context ctx, SharedPreferences prefs, String url, String user, String pass) {
        if (prefs == null) return;
        // 2026-07-06: Persist normalized URL so settings preview matches what the client opens.
        String normUrl = NavidromeClient.normalizeServerUrl(url);
        prefs.edit()
                .putString(PREF_URL, normUrl)
                .putString(PREF_USER, user != null ? user.trim() : "")
                .putString(PREF_PASS, pass != null ? pass.trim() : "")
                .commit();
        NavidromeClient.getInstance().applySettings(normUrl, user, pass);
        NavidromeCacheStore.getInstance(ctx).clearArtists();
    }

    public static boolean isConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        String url = prefs.getString(PREF_URL, "");
        String user = prefs.getString(PREF_USER, "");
        return url != null && !url.trim().isEmpty()
                && user != null && !user.trim().isEmpty();
    }
}
