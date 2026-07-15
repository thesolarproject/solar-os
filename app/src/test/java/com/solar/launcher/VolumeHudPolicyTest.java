package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Y2/A5 compact volume HUD vs inline NP/video pulse.
 */
public class VolumeHudPolicyTest {

    @Test
    public void y2A5_skipsHudOnNowPlayingWithoutModal() {
        if (VolumeHudPolicy.shouldShowCompactVolumeHud(true, true, false)) {
            throw new AssertionError("NP without modal should skip HUD");
        }
    }

    @Test
    public void y2A5_showsHudWhenModalOpenOnNowPlaying() {
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(true, true, true)) {
            throw new AssertionError("modal open should show/replace volume HUD");
        }
    }

    @Test
    public void y2A5_showsHudOnBrowse() {
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(true, false, false)) {
            throw new AssertionError("browse should keep compact HUD");
        }
    }

    @Test
    public void nonHwVolumeDevice_alwaysShowsWhenAsked() {
        // Y1 / non-family: policy does not suppress (caller may still gate via shouldUseSolarVolumeHud).
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(false, true, false)) {
            throw new AssertionError("non-hw device should not suppress via this policy");
        }
    }

    @Test
    public void inlineVolumeScreen_playerAndVideo() {
        if (!VolumeHudPolicy.isInlineVolumeScreen(3, 3, 21)) {
            throw new AssertionError("player");
        }
        if (!VolumeHudPolicy.isInlineVolumeScreen(21, 3, 21)) {
            throw new AssertionError("video");
        }
        if (VolumeHudPolicy.isInlineVolumeScreen(0, 3, 21)) {
            throw new AssertionError("menu");
        }
    }
}
