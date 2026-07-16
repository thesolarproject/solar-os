package com.solar.launcher.youtube;

import com.solar.launcher.DeviceFeatures;

/**
 * 2026-07-16 — Progressive quality ladder for Solar YouTube playback.
 * Layman: Y1/Y2 prefer 360p (near native); A5 prefers 240p (landscape panel).
 * Can climb to 480/720 when lower tiers fail; never stuck on one broken CDN link.
 * Reversal: hardcode "360" at play sites.
 */
public final class YouTubeQuality {

    private YouTubeQuality() {}

    /**
     * Preferred progressive quality for this device.
     * Y1/Y2 → 360; A5 → 240 (ideal when video shows landscape on small panel).
     */
    public static String preferredVideoQuality() {
        if (DeviceFeatures.isA5()) return "240";
        return "360";
    }

    /**
     * Next quality after a failed resolve / IJK error.
     * Ladder: preferred → lower → higher (480 then 720 as last resorts).
     */
    public static String fallbackVideoQuality(String failedQuality) {
        String[] ladder = qualityLadder();
        if (failedQuality == null || failedQuality.length() == 0) {
            return ladder.length > 1 ? ladder[1] : null;
        }
        String fail = failedQuality.trim();
        int idx = -1;
        for (int i = 0; i < ladder.length; i++) {
            if (ladder[i].equals(fail)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            // Unknown — try preferred, then rest of ladder.
            return preferredVideoQuality().equals(fail) ? nextAfter(ladder, 0) : preferredVideoQuality();
        }
        return nextAfter(ladder, idx);
    }

    /** Ordered try list for this device (ideal first). */
    public static String[] qualityLadder() {
        if (DeviceFeatures.isA5()) {
            // 240 ideal; 360 ok; 480 last-resort progressive (720 often too heavy on A5).
            return new String[] { "240", "360", "480" };
        }
        // Y1/Y2: 360 ideal; 240 if 360 broken; 480/720 when only HQ progressive works.
        return new String[] { "360", "240", "480", "720" };
    }

    private static String nextAfter(String[] ladder, int idx) {
        if (idx + 1 < ladder.length) return ladder[idx + 1];
        return null;
    }

    /** Parse height from quality token like "360", "360p", "720p". */
    public static int qualityHeight(String quality) {
        if (quality == null || quality.length() == 0) return 360;
        String digits = quality.replaceAll("[^0-9]", "");
        if (digits.length() == 0) return 360;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 360;
        }
    }

    /**
     * Score a candidate stream for the requested quality (higher = better match).
     * Prefers exact height, then nearest lower, then nearest higher; mp4 beats webm.
     */
    public static int scoreStream(int height, boolean muxed, boolean isMp4, String wantQuality) {
        int want = qualityHeight(wantQuality);
        if (height <= 0) height = want;
        int score = 0;
        if (muxed) score += 100000;
        if (isMp4) score += 50000;
        else score += 10000; // webm/vp9 last on API 17
        int delta = height - want;
        if (delta == 0) score += 20000;
        else if (delta < 0) score += 10000 + delta; // prefer slightly lower
        else score += 5000 - delta; // higher than asked is worse
        // Cap absurd heights
        if (height > 1080) score -= 30000;
        return score;
    }
}
