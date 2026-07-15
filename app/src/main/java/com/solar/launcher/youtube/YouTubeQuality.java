package com.solar.launcher.youtube;

import com.solar.launcher.DeviceFeatures;

/**
 * 2026-07-15 — Progressive quality ladder for Solar YouTube playback.
 * Layman: Y2 tries 480p first; if that fails, drop to 360p. Y1 stays at 360p.
 * Technical: was NotPipeClient.preferredVideoQuality / fallbackVideoQuality.
 * Reversal: hardcode "360" at play sites.
 */
public final class YouTubeQuality {

    private YouTubeQuality() {}

    /** Preferred progressive quality for this device. */
    public static String preferredVideoQuality() {
        return DeviceFeatures.isY2() ? "480" : "360";
    }

    /** Next lower quality after a failed resolve / IJK error, or null if none. */
    public static String fallbackVideoQuality(String failedQuality) {
        if ("480".equals(failedQuality)) return "360";
        return null;
    }
}
