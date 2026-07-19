package com.solar.launcher.stem;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-19 — Cloud stem separator contract (LALAL today; other vendors later).
 * Layman: one plug for “make vocals / instrumental from a song in the cloud.”
 * Technical: local find vs network ensure. Reversal: call LalalClient only from UI.
 */
public interface StemSeparatorProvider {

    /** Stable id for cache paths (e.g. {@code lalal}). */
    String providerId();

    /**
     * Local file ready for this mode — no network.
     * @return existing audio file or null
     */
    File findReadySolo(Context ctx, File track, SoloMode mode, File appCache);

    /**
     * Ensure solo file exists (may upload/split/download). Caller must gate Wi‑Fi + opt-in.
     */
    File ensureSolo(Context ctx, File track, SoloMode mode, File appCache,
            LalalClient.Progress progress) throws Exception;

    /** True when full Stem Player pads exist for this track (local). */
    boolean trackFullStemsReady(Context ctx, File track, boolean premix, File appCache);
}
