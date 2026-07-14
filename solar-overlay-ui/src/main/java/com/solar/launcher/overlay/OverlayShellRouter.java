package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Picks the one global overlay shell (companion by default).
 * Layman: opens the system-wide menu in the helper app, not inside Solar Home.
 * Technical: companion GlobalContextOverlayService primary; persist.solar.overlay.legacy_shell=1
 * keeps Solar :overlay SolarOverlayService for one-release rollback.
 * 2026-07-10 — Defaults must match Xposed OverlayKeyForwarder (legacy default "0").
 * Was: legacy default "1" + companion_shell default "0" → paint always Solar while keys
 * went to companion (or nowhere) → dead input + Solar BACK hold force-quit looked like crash.
 * Reversal: set persist.solar.overlay.legacy_shell=1 for Solar :overlay paint again.
 */
public final class OverlayShellRouter {

    /** Escape hatch — set to "1" to paint with Solar :overlay again (rollback). */
    public static final String LEGACY_SHELL_PROP = "persist.solar.overlay.legacy_shell";
    /** Optional hard-off for companion; default "1" (on). Set "0" only with legacy_shell=1. */
    public static final String COMPANION_SHELL_PROP = "persist.solar.overlay.companion_shell";

    public static final String SOLAR_PKG = "com.solar.launcher";
    public static final String SOLAR_OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";
    public static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    public static final String COMPANION_OVERLAY_SERVICE =
            COMPANION_PKG + ".GlobalContextOverlayService";

    private OverlayShellRouter() {}

    /**
     * Companion is the sole shell unless legacy_shell=1 (matches Xposed key forward default).
     * Empty/unset props → companion. Must stay aligned with OverlayKeyForwarder.readLegacyShellProp.
     */
    public static boolean useCompanionShell() {
        // Explicit rollback: paint + keys stay on Solar :overlay.
        if ("1".equals(readProp(LEGACY_SHELL_PROP, "0"))) return false;
        // Optional hard-off (default on). Only meaningful with a deliberate legacy roll-forward.
        if ("0".equals(readProp(COMPANION_SHELL_PROP, "1"))) return false;
        return true;
    }

    /** Package that owns the live WM shell. */
    public static String overlayPackage() {
        return useCompanionShell() ? COMPANION_PKG : SOLAR_PKG;
    }

    /** Fully-qualified overlay Service class for startService / key forward. */
    public static String overlayServiceClass() {
        return useCompanionShell() ? COMPANION_OVERLAY_SERVICE : SOLAR_OVERLAY_SERVICE;
    }

    /**
     * Package-resolved ComponentName for overlay startService/dismiss.
     * Layman: always points at the same menu process Xposed is talking to.
     */
    public static android.content.ComponentName overlayComponent() {
        return new android.content.ComponentName(overlayPackage(), overlayServiceClass());
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Throwable t) {
            return def;
        }
    }
}
