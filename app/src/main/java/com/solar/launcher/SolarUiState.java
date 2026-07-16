package com.solar.launcher;

/**
 * Lightweight UI flags for Xposed / overlay process — {@link #PROP_NOW_PLAYING_SCREEN} tells
 * volume hooks to skip the global volume HUD while Solar NP / video shows inline transport pulse.
 */
public final class SolarUiState {

    /** 1 while music Now Playing or video player is visible (inline volume pulse). */
    public static final String PROP_NOW_PLAYING_SCREEN = "persist.solar.ui.now_playing";
    /** 1 while Solar has an active music/podcast/radio/video session (readable from :overlay). */
    public static final String PROP_PLAYBACK_ACTIVE = "persist.solar.ui.playback_active";

    private SolarUiState() {}

    /**
     * 2026-07-15 — MainActivity screen change writes this so VolumePanelHooks / :overlay can skip HUD.
     * Covers STATE_PLAYER and STATE_VIDEO_PLAYER. Reversal: never call (old dead prop).
     * 2026-07-16 — Async setprop; sync su on every NP enter blocked the first volume wheel ticks.
     */
    public static void setNowPlayingScreen(boolean active) {
        writePropAsync(PROP_NOW_PLAYING_SCREEN, active ? "1" : "0");
    }

    /** MainActivity playback start/stop — global overlay Now Playing chip. */
    public static void setPlaybackActive(boolean active) {
        writePropAsync(PROP_PLAYBACK_ACTIVE, active ? "1" : "0");
    }

    /** 2026-07-16 — Prefer reflection; root setprop only as background fallback. */
    private static void writePropAsync(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
            return;
        } catch (Exception ignored) {}
        RootShell.runAsync("setprop " + key + " " + value);
    }

    /** Overlay service + in-app checks (readable from {@code :overlay} process). */
    public static boolean isNowPlayingScreen() {
        return "1".equals(readProp(PROP_NOW_PLAYING_SCREEN, "0"));
    }

    /** True when background playback is active — show Go to Now Playing over third-party apps. */
    public static boolean isPlaybackActive() {
        return "1".equals(readProp(PROP_PLAYBACK_ACTIVE, "0"));
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
