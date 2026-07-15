package com.solar.launcher.eq;

import java.util.Arrays;
import java.util.Locale;

/**
 * 2026-07-15 — Ten fixed graphic-EQ band gains + preamp for Solar software EQ.
 * Layman: ten volume knobs across bass→treble, plus an overall gain trim.
 * Reversal: delete package; Playback can fall back to OEM Equalizer presets.
 */
public final class EqBandModel {

    /** ISO-ish AutoEQ / Rockbox graphic centres (Hz). */
    public static final int[] CENTERS_HZ = {
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
    };

    public static final int BAND_COUNT = CENTERS_HZ.length;
    public static final float MIN_GAIN_DB = -12f;
    public static final float MAX_GAIN_DB = 12f;
    public static final float MIN_PREAMP_DB = -12f;
    public static final float MAX_PREAMP_DB = 12f;

    private final float[] gainsDb = new float[BAND_COUNT];
    private float preampDb;
    private boolean enabled;

    public EqBandModel() {
        resetFlat();
    }

    /** Copy constructor — stash for A/B compare. */
    public EqBandModel(EqBandModel other) {
        if (other != null) {
            System.arraycopy(other.gainsDb, 0, gainsDb, 0, BAND_COUNT);
            preampDb = other.preampDb;
            enabled = other.enabled;
        } else {
            resetFlat();
        }
    }

    /** All bands 0 dB, preamp 0, enabled. */
    public void resetFlat() {
        Arrays.fill(gainsDb, 0f);
        preampDb = 0f;
        enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getPreampDb() {
        return preampDb;
    }

    public void setPreampDb(float db) {
        preampDb = clamp(db, MIN_PREAMP_DB, MAX_PREAMP_DB);
    }

    public float getGainDb(int band) {
        if (band < 0 || band >= BAND_COUNT) return 0f;
        return gainsDb[band];
    }

    public void setGainDb(int band, float db) {
        if (band < 0 || band >= BAND_COUNT) return;
        gainsDb[band] = clamp(db, MIN_GAIN_DB, MAX_GAIN_DB);
    }

    public void adjustGainDb(int band, float delta) {
        setGainDb(band, getGainDb(band) + delta);
    }

    public float[] copyGains() {
        return Arrays.copyOf(gainsDb, BAND_COUNT);
    }

    public void setGains(float[] src) {
        if (src == null) return;
        int n = Math.min(BAND_COUNT, src.length);
        for (int i = 0; i < n; i++) {
            gainsDb[i] = clamp(src[i], MIN_GAIN_DB, MAX_GAIN_DB);
        }
    }

    /** True when software DSP must run (enabled and not all zeros). */
    public boolean needsSoftwareEq() {
        if (!enabled) return false;
        if (Math.abs(preampDb) > 0.01f) return true;
        for (int i = 0; i < BAND_COUNT; i++) {
            if (Math.abs(gainsDb[i]) > 0.01f) return true;
        }
        return false;
    }

    public boolean isFlat() {
        if (Math.abs(preampDb) > 0.01f) return false;
        for (int i = 0; i < BAND_COUNT; i++) {
            if (Math.abs(gainsDb[i]) > 0.01f) return false;
        }
        return true;
    }

    /** Short label for a band centre (31 / 1k / 16k). */
    public static String centerLabel(int band) {
        if (band < 0 || band >= BAND_COUNT) return "?";
        int hz = CENTERS_HZ[band];
        if (hz >= 1000) return String.format(Locale.US, "%dk", hz / 1000);
        return String.valueOf(hz);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
