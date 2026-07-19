package com.solar.launcher;

/**
 * Exclusive Stem Player / Mix session gate — sole heavy audio jam owner.
 * Layman: while Stem or Mix is open, Solar pauses library scans and other busywork
 * so the pads stay responsive.
 * Technical: volatile flag set by hosts on attach/detach; LowMemoryGate + MainActivity
 * input/scan/art/clock consult this. Was: StemPlayerHost.isSessionActive only for a few callers.
 * Reversal: ignore isActive() (old free-running scans under Stem).
 * 2026-07-19
 */
public final class StemOrMixSession {
    private static volatile boolean active;

    private StemOrMixSession() {}

    /** True while Stem or Mix UI owns the device. */
    public static boolean isActive() {
        return active;
    }

    /** Enter exclusive jam — cancel competing prep work at call sites. 2026-07-19 */
    public static void setActive(boolean on) {
        active = on;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("active", on);
            Debug8b0481Log.log("StemOrMixSession.setActive", "exclusive gate", "H3", d);
        } catch (Exception ignored) {}
        // #endregion
    }
}
