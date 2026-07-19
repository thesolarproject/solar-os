package com.solar.launcher.stem;

/**
 * 2026-07-19 — Solo Now Playing mode (acapella vs instrumental).
 * Layman: play just the voice, or just the band, on the normal player screen.
 * Technical: enum for ensureSolo / findReadySolo. Reversal: boolean flags at call sites.
 */
public enum SoloMode {
    ACAPELLA,
    INSTRUMENTAL
}
