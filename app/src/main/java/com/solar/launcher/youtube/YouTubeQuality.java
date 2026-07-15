package com.solar.launcher.youtube;

import com.solar.launcher.DeviceFeatures;

/**
 * 2026-07-15 — Progressive quality ladder for Solar YouTube playback.
 * Layman: all devices prefer 360p (near native on Y1/Y2/A5); less decode work.
 * Technical: was NotPipeClient.preferredVideoQuality / fallbackVideoQuality.
 * Reversal: hardcode "360" at play sites; restore isY2 480 preferred if needed.
 */
public final class YouTubeQuality {

    private YouTubeQuality() {}

    /** Preferred progressive quality for this device. */
    public static String preferredVideoQuality() {
        // 2026-07-15 — Cap at 360p on Y1/Y2/A5 (closer to native; less processing).
        // Was: DeviceFeatures.isY2() ? "480" : "360".
        return "360";
    }

    /** Next lower quality after a failed resolve / IJK error, or null if none. */
    public static String fallbackVideoQuality(String failedQuality) {
        // 2026-07-15 — Ladder: 720/480 → 360; A5 can drop 360 → 240.
        if ("480".equals(failedQuality) || "720".equals(failedQuality)) return "360";
        if ("360".equals(failedQuality) && DeviceFeatures.isA5()) return "240";
        return null;
    }
}
