package com.solar.launcher.stem;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-19 — LALAL adapter for {@link StemSeparatorProvider}.
 * Layman: Solar’s first cloud service for Instrumental / Acapella / full stems.
 * Technical: wraps {@link LalalClient} solo + multistem readiness. Reversal: inline LalalClient in UI.
 */
public final class LalalStemSeparator implements StemSeparatorProvider {

    private final String licenseKey;

    public LalalStemSeparator(String licenseKey) {
        this.licenseKey = licenseKey != null ? licenseKey.trim() : "";
    }

    @Override
    public String providerId() {
        return StemFeatures.PROVIDER_LALAL;
    }

    @Override
    public File findReadySolo(Context ctx, File track, SoloMode mode, File appCache) {
        return LalalClient.findReadySoloFile(ctx, track, mode, appCache);
    }

    @Override
    public File ensureSolo(Context ctx, File track, SoloMode mode, File appCache,
            LalalClient.Progress progress) throws Exception {
        File cache = appCache != null ? appCache : (ctx != null ? ctx.getCacheDir() : null);
        File hit = LalalClient.findReadySoloFile(ctx, track, mode, cache);
        if (hit != null) return hit;

        // Prefer bake from full pads when instrumental (no network).
        if (mode == SoloMode.INSTRUMENTAL
                && LalalClient.findReadyStemDir(ctx, track, false, cache) != null) {
            return LalalClient.bakeInstrumentalFromFullStems(ctx, track, cache, progress);
        }
        if (mode == SoloMode.INSTRUMENTAL && ctx != null) {
            try {
                android.content.SharedPreferences prefs =
                        ctx.getSharedPreferences(LalalAccount.PREFS_NAME, 0);
                if (LalalClient.findReadyStemDir(ctx, track,
                        LalalAccount.isPremixExperimental(prefs), cache) != null) {
                    return LalalClient.bakeInstrumentalFromFullStems(ctx, track, cache, progress);
                }
            } catch (Exception ignored) {}
        }
        if (mode == SoloMode.ACAPELLA) {
            File fromFull = LalalClient.findSoloFromFullStems(ctx, track, mode, cache);
            if (fromFull != null) return fromFull;
        }

        LalalClient client = new LalalClient(licenseKey);
        File solo = LalalClient.soloDir(cache, track);
        client.separateSoloToFiles(track, solo, progress);
        File out = mode == SoloMode.ACAPELLA
                ? new File(solo, "vocals.mp3")
                : LalalClient.resolveInstrumentalFile(solo);
        if (out == null || !out.isFile() || out.length() < 100) {
            throw new java.io.IOException("Solo file missing after split");
        }
        return out;
    }

    @Override
    public boolean trackFullStemsReady(Context ctx, File track, boolean premix, File appCache) {
        return LalalClient.trackStemsReady(ctx, track, premix, appCache);
    }
}
