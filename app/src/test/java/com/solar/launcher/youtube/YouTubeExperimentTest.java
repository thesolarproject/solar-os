package com.solar.launcher.youtube;

import com.solar.launcher.media.MediaSuiteHost;

import org.junit.Test;

/** Host JVM — YouTube kill switch defaults on; explicit false blocks hub screens. */
public class YouTubeExperimentTest {

    @Test
    public void defaultsOnWhenPrefMissing() {
        if (!YouTubeExperiment.isEnabledForTest(null)) {
            throw new AssertionError("missing pref should enable YouTube");
        }
    }

    @Test
    public void enabledWhenPrefOn() {
        if (!YouTubeExperiment.isEnabledForTest(Boolean.TRUE)) {
            throw new AssertionError("pref true should enable");
        }
    }

    @Test
    public void disabledWhenPrefOff() {
        if (YouTubeExperiment.isEnabledForTest(Boolean.FALSE)) {
            throw new AssertionError("pref false should disable");
        }
    }

    @Test
    public void nullPrefsDoNotBlockBrowse() {
        // 2026-07-14 — null prefs treat as default-on (kill switch never written).
        if (YouTubeExperiment.isBlockedScreenState(
                MediaSuiteHost.STATE_YOUTUBE_BROWSE, null)) {
            throw new AssertionError("null prefs should not block YouTube browse");
        }
    }

    @Test
    public void qualityFallbackLadder() {
        // 2026-07-15 — Native YouTubeClient owns quality ladder (was NotPipeClient).
        if (!"360".equals(YouTubeClient.fallbackVideoQuality("480"))) {
            throw new AssertionError("480 should fall back to 360");
        }
        if (YouTubeClient.fallbackVideoQuality("360") != null) {
            throw new AssertionError("360 should have no further fallback");
        }
    }
}
