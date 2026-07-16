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
        // 2026-07-16 — Ladder continues past 360 (240 / 480 / 720) so Y1 can recover.
        String after480 = YouTubeClient.fallbackVideoQuality("480");
        if (after480 == null || after480.length() == 0) {
            throw new AssertionError("480 should have a fallback");
        }
        String after360 = YouTubeClient.fallbackVideoQuality("360");
        // Y1 ladder: 360→240; A5 ladder: 360 is not preferred first but still maps to next.
        if (after360 == null) {
            throw new AssertionError("360 should fall back further (240/480)");
        }
    }
}
