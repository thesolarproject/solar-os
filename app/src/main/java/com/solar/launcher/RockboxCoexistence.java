package com.solar.launcher;

import android.content.Context;

/**
 * 2026-07-05 — Rockbox handoff prep: unified keymap + native lib sync before launcher switch.
 * Rockbox calls switch-to-stock.sh with no reboot — see LauncherSwitch handoff contract.
 * When changing: Y1KeymapSync + RockboxLibSync; sync-y1-assets.sh for ROM/asset parity.
 * 2026-07-16 — A5 has no Rockbox handoff; still runs Y1KeymapSync so A5-mtk.kl is restored.
 * Reversal: stop prepareForRockboxSwitch; Rockbox switch may use stale keylayout or codecs.
 */
public final class RockboxCoexistence {
    private RockboxCoexistence() {}

    /** Run before Settings → Switch to Rockbox (keymap + native libs). */
    public static void prepareForRockboxSwitch(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        // 2026-07-16 — A5 has no Rockbox product path; only repair keymaps.
        if (DeviceFeatures.isA5()) {
            Y1KeymapSync.ensureUnified(app);
            return;
        }
        Y1KeymapSync.ensureUnified(app);
        RockboxLibSync.syncBeforeRockboxSwitch(app);
    }

    /** Solar boot: seed ROM scripts from assets and sync keymap/libs if needed. */
    public static void ensureOnSolarStart(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        // Always restore family keylayout (A5 needs this after a Y1 map mangle).
        Y1KeymapSync.ensureUnified(app);
        if (DeviceFeatures.isA5()) {
            // No Rockbox codec/lib tree on A5 Solar builds.
            return;
        }
        RockboxLibSync.ensureOnSolarStart(app);
    }
}
