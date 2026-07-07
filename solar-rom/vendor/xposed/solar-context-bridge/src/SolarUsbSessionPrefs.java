package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — Cross-process USB prompt prefs for SystemUI hooks (Rockbox / third-party fg).
 * Layman: honors Connections → USB → Skip plug-in prompt from any process.
 * Tech: reads {@code sys.solar.usb.skip_prompt} + Y2 {@code sys.solar.ums.experiment} set by Solar app.
 * Reversal: delete and rely on SharedPreferences only (breaks :overlay / systemui staleness).
 */
final class SolarUsbSessionPrefs {

    private static final String SYSPROP_SKIP_PROMPT = "sys.solar.usb.skip_prompt";
    private static final String SYSPROP_AUTO_CONNECT = "sys.solar.usb.auto_connect";
    private static final String SYSPROP_Y2_UMS_EXPERIMENT = "sys.solar.ums.experiment";
    private static final String SYSPROP_BOOT_HOST_AT_BOOT = "sys.solar.usb.boot_host_at_boot";
    private static final String SYSPROP_BOOT_SETTLE_READY = "sys.solar.usb.boot_settle_ready";
    /** 1 while user dismissed enable prompt this host session (2026-07-06). */
    private static final String SYSPROP_SESSION_DISMISSED = "sys.solar.usb.session_dismissed";

    private SolarUsbSessionPrefs() {}

    /** False when user enabled Skip plug-in prompt in Solar USB settings. */
    static boolean shouldOfferConnectPrompt() {
        return !"1".equals(readSysProp(SYSPROP_SKIP_PROMPT, "0"));
    }

    /** Y1 always; Y2 needs Debug experiment sysprop before UMS prompt or hooks (2026-07-05). */
    static boolean shouldShowUsbStoragePrompt() {
        if (!shouldOfferConnectPrompt()) return false;
        if (isSessionDismissed()) return false;
        if (!isPromptAllowedAfterBootSettle()) return false;
        return isUmsFeatureEnabled();
    }

    /**
     * 2026-07-06 — No prompt while rebooted with PC cable until settle or unplug cycle.
     * Layman: wait after boot-with-USB before asking about disk mode.
     * Tech: reads {@code sys.solar.usb.boot_settle_ready} set by Solar app on boot.
     */
    static boolean isPromptAllowedAfterBootSettle() {
        if (!"1".equals(readSysProp(SYSPROP_BOOT_HOST_AT_BOOT, "0"))) {
            return true;
        }
        return "1".equals(readSysProp(SYSPROP_BOOT_SETTLE_READY, "0"));
    }

    /** Y1 always; Y2 gated on debug experiment — matches {@code UsbMassStorageExperiment}. */
    static boolean isUmsFeatureEnabled() {
        if (android.os.Build.VERSION.SDK_INT >= 19
                && !"1".equals(readSysProp(SYSPROP_Y2_UMS_EXPERIMENT, "0"))) {
            return false;
        }
        return true;
    }

    /**
     * Settings → auto-connect — only when user explicitly toggled ON (2026-07-06).
     * Unset sysprop defaults off on Y1 and Y2 — no silent UMS without consent.
     */
    static boolean isAutoConnectEnabled() {
        String val = readSysProp(SYSPROP_AUTO_CONNECT, "");
        return "1".equals(val);
    }

    /** User dismissed USB enable tier — no concierge re-prompt this plug (2026-07-06). */
    static boolean isSessionDismissed() {
        return "1".equals(readSysProp(SYSPROP_SESSION_DISMISSED, "0"));
    }

    private static String readSysProp(String key, String def) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            return (String) XposedHelpers.callStaticMethod(sp, "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
