package com.solar.launcher.eq;

import java.util.Locale;

/**
 * 2026-07-15 — Build FFmpeg lavfi audio-filter string for ten peaking bands + preamp.
 * Layman: turn slider values into the filter text IJK/FFmpeg understands.
 * Reversal: empty string disables af; player plays un-EQed audio.
 */
public final class EqFilterGraph {

    private EqFilterGraph() {}

    /**
     * FFmpeg equalizer filters chained with volume for preamp.
     * width_type=o (octave) ~ Q≈1.4 graphic feel; gain in dB.
     */
    public static String buildAf(EqBandModel model) {
        if (model == null || !model.needsSoftwareEq()) return null;
        StringBuilder sb = new StringBuilder(256);
        // Preamp first so band boosts sit on top of the trim.
        if (Math.abs(model.getPreampDb()) > 0.01f) {
            sb.append("volume=");
            sb.append(String.format(Locale.US, "%.2fdB", model.getPreampDb()));
        }
        for (int i = 0; i < EqBandModel.BAND_COUNT; i++) {
            float g = model.getGainDb(i);
            if (Math.abs(g) < 0.01f) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append("equalizer=f=");
            sb.append(EqBandModel.CENTERS_HZ[i]);
            sb.append(":width_type=o:width=1:g=");
            sb.append(String.format(Locale.US, "%.2f", g));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
