package com.solar.launcher.overlay;

/**
 * 2026-07-14 — Picks the one global overlay shell: Solar ThemedContextMenu by default.
 * Layman: one system menu that matches Solar Home decoration and takes the scroll wheel.
 * Technical: SolarOverlayService + ThemedContextMenu sole paint/key target; companion Chip
 * only if persist.solar.overlay.companion_shell=1 (escape hatch).
 * Was: companion ChipOverlayHost primary (parallel chrome, incomplete parity / dead keys).
 * Reversal: set persist.solar.overlay.companion_shell=1 for chip path again.
 */
public final class OverlayShellRouter {

    /**
     * Escape hatch pairing — with companion_shell=1, set this to "1" to force Solar again.
     * Unused when companion_shell is off (Solar is already default).
     */
    public static final String LEGACY_SHELL_PROP = "persist.solar.overlay.legacy_shell";
    /**
     * Opt-in chip companion. Default "0" (off) — Solar ThemedContextMenu is the one shell.
     * Set "1" to revive ChipOverlayHost (incomplete parity — escape hatch only).
     */
    public static final String COMPANION_SHELL_PROP = "persist.solar.overlay.companion_shell";

    public static final String SOLAR_PKG = "com.solar.launcher";
    public static final String SOLAR_OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";
    public static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    public static final String COMPANION_OVERLAY_SERVICE =
            COMPANION_PKG + ".GlobalContextOverlayService";

    private OverlayShellRouter() {}

    /**
     * 2026-07-14 — Solar ThemedContextMenu is sole shell unless companion_shell=1 is forced.
     * Empty/unset → Solar. Must stay aligned with OverlayKeyForwarder.readLegacyShellProp.
     */
    public static boolean useCompanionShell() {
        // Explicit chip opt-in (old companion primary path).
        if ("1".equals(readProp(COMPANION_SHELL_PROP, "0"))) {
            // companion_shell=1 still honors legacy_shell=1 as Solar rollback.
            if ("1".equals(readProp(LEGACY_SHELL_PROP, "0"))) return false;
            return true;
        }
        // Default: Solar ThemedContextMenu (Home parity + themed decoration).
        return false;
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
