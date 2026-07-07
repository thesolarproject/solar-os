package com.solar.input.policy;

/**
 * 2026-07-06 — Authoritative power/BACK hold eligibility for Solar + companion + Xposed.
 * Layman: one rulebook so sleep button, context menu, and Rockbox BACK never fight each other.
 * Technical: pure Java policy (~150 lines); compiled into bridge, companion, and Solar delegate.
 * Reversal: delete JAR; restore duplicated shouldOffer* in SystemServerHooks + GlobalOverlayPolicy.
 */
public final class GlobalInputPolicy {    public static final String POLICY_REV_PROPERTY = "sys.solar.input.policy_rev";
    /** Bump when hold tiers or fail-open paths change — adb: getprop sys.solar.input.policy_rev */
    public static final int POLICY_REV = 18;
 
    /** Cross-process — infinite list wrap opt-in (overlay :overlay, companion, IME read-only). */
    public static final String PROP_INFINITE_SCROLL = "persist.solar.nav.infinite_scroll";
 
    public static final long POWER_TAP_MAX_MS = 380L;
    /**
     * 2026-07-06 — Fast global modal tier (~300ms): SystemUI shells, stock apps, third-party apps.
     * Layman: same snappy feel as the USB/SystemUI overlay concierge across the whole OS.
     * Technical: matches warmOverlayProcess + postDelayed in SystemServerHooks BACK/POWER paths.
     */
    public static final long GLOBAL_MODAL_HOLD_MS = 130L;
    /** @deprecated use {@link #GLOBAL_MODAL_HOLD_MS}. */
    public static final long THIRD_PARTY_MODAL_HOLD_MS = GLOBAL_MODAL_HOLD_MS;
    /**
     * 2026-07-07 — Nav-owned HOME (Rockbox/JJ) BACK/POWER passthrough + JJ modal tier (~200 ms).
     * Layman: short Back/Power still reaches Rockbox/JJ; ~0.2 s opens quick menu on JJ (Rockbox BACK never).
     * Reversal: restore 840 ms THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS for slower JJ accidental-hold guard.
     */
    public static final long THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS = 200L;
    /** @deprecated use {@link #THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS}. */
    public static final long NAV_OWNED_LAUNCHER_MODAL_HOLD_MS = THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
    /** @deprecated use {@link #THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS}. */
    public static final long NAV_OWNED_BACK_MODAL_MS = THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
    /** @deprecated use {@link #backModalHoldMsForPackage}. */
    public static final long MODAL_HOLD_MS = GLOBAL_MODAL_HOLD_MS;
    /** OK/center long-press in stock apps — opens row context menu (~300ms). */
    public static final long CENTER_MENU_HOLD_MS = 300L;
    /** 2026-07-06 — Solar HOME BACK hold — in-app quick/options menu (~300ms). */
    public static final long SOLAR_BACK_CONTEXT_HOLD_MS = 130L;
    /** 2026-07-06 — Overlay + HUD show 3..2..1 while finger still down (~7s total from hold start). */
    public static final long HUD_COUNTDOWN_START_MS = 4900L;
    /** Alias — rescue countdown HUD tier (~4.9s from hold DOWN). */
    public static final long RESCUE_COUNTDOWN_START_MS = HUD_COUNTDOWN_START_MS;
    public static final long RESCUE_EXECUTE_MS = 7000L;
    /** Alias — total BACK/POWER hold before solar-rescue-exec. */
    public static final long RESCUE_HOLD_MS = RESCUE_EXECUTE_MS;
    public static final long EMERGENCY_ROCKBOX_MODAL_MS = 1400L;
    /** Rockbox/JJ pass BACK through until this ms — Rockbox never opens BACK modal below. */
    public static final long ROCKBOX_BACK_PASSTHROUGH_MS = THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;

    public static final String SOLAR_PKG = "com.solar.launcher";
    public static final String ROCKBOX_PKG = "org.rockbox";
    /** JJ Launcher (MO-ON) — third-party HOME; longer hold tier, BACK-long modal allowed. */
    public static final String JJ_PKG = "com.themoon.y1";
    public static final String INNIOASIS_PREFIX = "com.innioasis.";
    /** android.view.KeyEvent.KEYCODE_BACK — avoid android import in policy-only javac. */
    public static final int KEYCODE_BACK = 4;

    /** android.view.KeyEvent.KEYCODE_POWER — avoid android import in policy-only javac. */
    public static final int KEYCODE_POWER = 26;

    /** JJ only — nav-owned extended tier (300 ms); Rockbox uses {@link #isNavOwnedHomeLauncher} passthrough. */
    private static final String[] SPECIAL_INPUT_HOLD_LAUNCHER_PKGS = { JJ_PKG };
    /** @deprecated use {@link #SPECIAL_INPUT_HOLD_LAUNCHER_PKGS}. */
    private static final String[] THIRD_PARTY_LAUNCHER_PKGS = SPECIAL_INPUT_HOLD_LAUNCHER_PKGS;

    private GlobalInputPolicy() {}

    /** Y2 short POWER tap — never consume; stock sleep/wake owns the gesture. */
    public static boolean shouldPassthroughPowerTap(long holdMs) {
        return holdMs < POWER_TAP_MAX_MS;
    }

    /**
     * 2026-07-07 — BACK-long modal delay: ~420 ms apps + SystemUI; ~300 ms Rockbox/JJ passthrough ceiling.
     * Reversal: restore 840 ms nav-owned tier for slower accidental-hold guard on JJ.
     */
    public static long backModalHoldMsForPackage(String pkg) {
        if (isNavOwnedHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        if (isThirdPartyHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        return GLOBAL_MODAL_HOLD_MS;
    }

    /**
     * 2026-07-07 — Y2 POWER long-hold — ~300 ms passthrough on Rockbox/JJ; ~420 ms elsewhere.
     */
    public static long powerModalHoldMsForPackage(String pkg) {
        if (isNavOwnedHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        if (isThirdPartyHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        return GLOBAL_MODAL_HOLD_MS;
    }

    /**
     * 2026-07-07 — Finger-lift grace after modal open — match hold tier so BACK UP won't dismiss early.
     */
    public static long overlayDismissGraceMsForPackage(String pkg) {
        if (isNavOwnedHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        if (isThirdPartyHomeLauncher(pkg)) return THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
        return 150L;
    }

    /**
     * 2026-07-06 — Y2 POWER long-hold modal — Solar HOME via companion overlay.
     * Fail-open when task probe returns systemui/null/android over Rockbox.
     */
    public static boolean shouldOfferPowerLongModal(String pkg, boolean y2Device) {
        if (!y2Device) return false;
        if (pkg != null && pkg.startsWith(INNIOASIS_PREFIX)) return false;
        if (SOLAR_PKG.equals(pkg)) return true;
        if (ROCKBOX_PKG.equals(pkg)) return true;
        if (JJ_PKG.equals(pkg)) return true;
        if (shouldFailOpenPowerFg(pkg)) return true;
        if (pkg == null || pkg.length() == 0) return false;
        if (isSystemShellPackage(pkg)) return false;
        return true;
    }

    /** Y2 systemui/null/android fg — PWM probe lie; still offer POWER modal. */
    public static boolean shouldFailOpenPowerFg(String pkg) {
        if (pkg == null || pkg.length() == 0) return true;
        return isSystemShellPackage(pkg);
    }

    /**
     * 2026-07-06 — SystemUI/keyguard fg on BACK-long — same fail-open as POWER (USB dialog fast path).
     * Reversal: remove; BACK-long blocked when WM reports com.android.systemui.
     */
    public static boolean shouldFailOpenBackFg(String pkg) {
        return shouldFailOpenPowerFg(pkg);
    }

    /**
     * 2026-07-06 — BACK/POWER hold opens full global context modal (launcher switch is a chip inside).
     * Reversal: restore 600ms {@code SHOW_OVERLAY_LAUNCHER_PICKER}-only path in bridge hooks.
     */
    public static boolean shouldOfferLauncherPickerOnBackHold(String pkg, boolean y2Device,
            boolean imeActive, boolean emergencyMode) {
        return shouldOfferBackLongModal(pkg, y2Device, imeActive, emergencyMode);
    }

    /** 2026-07-06 — Y2 POWER hold opens global quick menu (includes Solar, Rockbox, JJ fg). */
    public static boolean shouldOfferLauncherPickerOnPowerHold(String pkg, boolean y2Device) {
        return shouldOfferPowerLongModal(pkg, y2Device);
    }

    /**
     * 2026-07-06 — BACK-long global modal; Rockbox excluded; SystemUI fail-open included.
     * Reversal: drop shouldFailOpenBackFg branch — stock USB dialog loses BACK-long quick menu.
     */
    public static boolean shouldOfferBackLongModal(String pkg, boolean y2Device,
            boolean imeActive, boolean emergencyMode) {
        if (imeActive) {
            if (pkg == null || pkg.length() == 0) return false;
            if (SOLAR_PKG.equals(pkg)) return false;
            if (pkg.startsWith(INNIOASIS_PREFIX)) return false;
            if (isSystemShellPackage(pkg)) return false;
            return true;
        }
        // 2026-07-06 — Rockbox owns BACK nav; JJ launcher still gets modal at longer tier.
        if (ROCKBOX_PKG.equals(pkg)) return false;
        if (JJ_PKG.equals(pkg)) return true;
        // 2026-07-06 — SystemUI USB/ANR shells: fast modal like direct overlay concierge tier.
        if (shouldFailOpenBackFg(pkg)) return true;
        if (pkg == null || pkg.length() == 0) return false;
        if (SOLAR_PKG.equals(pkg)) return false;
        if (pkg.startsWith(INNIOASIS_PREFIX)) return false;
        if (isSystemShellPackage(pkg)) return false;
        return true;
    }

    /** Rockbox/JJ BACK passthrough under normal mode — Y1 under 7s; Y2 always for BACK. */
    public static boolean shouldPassthroughRockboxBack(long holdMs, boolean y2Device,
            boolean emergencyMode, boolean imeActive) {
        return shouldPassthroughNavOwnedLauncherBack(holdMs, y2Device, emergencyMode, imeActive);
    }

    /** 2026-07-06 — JJ/Rockbox BACK pass through until modal tier; overlay owns keys after. */
    public static boolean shouldPassthroughNavOwnedLauncherBack(long holdMs, boolean y2Device,
            boolean emergencyMode, boolean imeActive) {
        if (imeActive) return false;
        if (emergencyMode && holdMs < EMERGENCY_ROCKBOX_MODAL_MS) return true;
        return holdMs < THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
    }

    /** Y2 POWER on nav-owned HOME — Rockbox/JJ passthrough until 300 ms tier. */
    public static boolean shouldPassthroughNavOwnedLauncherPower(long holdMs, String pkg,
            boolean imeActive) {
        if (imeActive) return false;
        long ceiling = isNavOwnedHomeLauncher(pkg)
                ? THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS
                : GLOBAL_MODAL_HOLD_MS;
        return holdMs < ceiling;
    }

    /** @deprecated use {@link #shouldPassthroughNavOwnedLauncherPower(long, String, boolean)}. */
    public static boolean shouldPassthroughNavOwnedLauncherPower(long holdMs, boolean imeActive) {
        return holdMs < THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS;
    }

    /** True for Rockbox or JJ — overlay key capture + BACK passthrough ceiling. */
    public static boolean isNavOwnedHomeLauncher(String pkg) {
        return ROCKBOX_PKG.equals(pkg) || JJ_PKG.equals(pkg);
    }

    /**
     * 2026-07-07 — Nav-owned hold tier (300 ms) — JJ BACK-long modal; Rockbox/JJ passthrough ceiling.
     * Generic Android CATEGORY_HOME launchers use {@link #GLOBAL_MODAL_HOLD_MS} (420 ms) like stock apps.
     * Rockbox uses separate {@link #isNavOwnedHomeLauncher} BACK rules — never BACK-long modal.
     */
    public static boolean isThirdPartyHomeLauncher(String pkg) {
        return isSpecialInputHoldLauncher(pkg);
    }

    /** PM scan cache — comma-separated HOME packages (see LauncherDiscovery.sync). */
    public static final String PROP_HOME_LAUNCHER_PKGS = "persist.solar.home.launch_pkgs";

    /** JJ only — not generic PM-discovered HOME launchers. */
    public static boolean isSpecialInputHoldLauncher(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        for (int i = 0; i < SPECIAL_INPUT_HOLD_LAUNCHER_PKGS.length; i++) {
            if (SPECIAL_INPUT_HOLD_LAUNCHER_PKGS[i].equals(pkg)) return true;
        }
        return false;
    }

    /**
     * 2026-07-07 — True when package appears in PM-synced HOME list — uses 420 ms modal tier.
     * Layman: generic alternate launchers behave like Settings, not like JJ/Rockbox.
     */
    public static boolean isPersistedHomeLauncher(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        if (isNavOwnedHomeLauncher(pkg) || SOLAR_PKG.equals(pkg)) return false;
        String raw = readSysProp(PROP_HOME_LAUNCHER_PKGS, "");
        if (raw.length() == 0) return false;
        String base = basePackageName(pkg);
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            if (base.equals(basePackageName(parts[i].trim()))) return true;
        }
        return false;
    }

    private static String basePackageName(String procOrPkg) {
        if (procOrPkg == null) return "";
        int colon = procOrPkg.indexOf(':');
        if (colon > 0) return procOrPkg.substring(0, colon);
        return procOrPkg;
    }

    private static String readSysProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? String.valueOf(v) : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** Ultra-long rescue HUD — any fg except Solar on BACK; Y2 POWER includes Solar fg; null fg OK at boot. */
    public static boolean shouldArmRescueHoldForPackage(String pkg, int keyCode) {
        if (SOLAR_PKG.equals(pkg)) {
            return keyCode == KEYCODE_POWER;
        }
        return true;
    }

    public static boolean isSystemShellPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return true;
        if ("android".equals(pkg)) return true;
        if (pkg.startsWith("com.android.systemui")) return true;
        if (pkg.startsWith("com.android.keyguard")) return true;
        if (pkg.startsWith("com.android.inputmethod")) return true;
        return false;
    }
}
