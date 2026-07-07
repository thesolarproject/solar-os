package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 2026-07-05 — Y2-only opt-in for USB mass storage (UMS still broken on MT6582 KitKat).
 * Layman: hides USB disk mode on Y2 until you flip the experiment in Debug settings.
 * Tech: prefs gate UI + {@link UsbMassStorageController}; {@link #SYSPROP} gates Xposed server hooks.
 * Reversal: delete class and always call UMS on Y2 when ROM hooks are stable.
 */
public final class UsbMassStorageExperiment {

    public static final String PREF_USB_MASS_STORAGE_EXPERIMENT = "solar_y2_usb_mass_storage_experiment";
    /** Read by SolarContextBridgeY2 {@code UsbMassStorageServerHooks} — 1 = hooks active (2026-07-05). */
    public static final String SYSPROP = "sys.solar.ums.experiment";

    private UsbMassStorageExperiment() {}

    /** UMS experiment toggle is shown and enforced on Y2 only; Y1 keeps stock UMS path. */
    public static boolean isAvailableOnDevice() {
        return DeviceFeatures.isY2();
    }

    /** Off by default on Y2; always true on Y1 and when experiment unavailable. */
    public static boolean isEnabled(SharedPreferences prefs) {
        if (!isAvailableOnDevice()) return true;
        return prefs != null && prefs.getBoolean(PREF_USB_MASS_STORAGE_EXPERIMENT, false);
    }

    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return !isAvailableOnDevice();
        return isEnabled(ctx.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE));
    }

    /** Root setprop so system_server hooks match the Debug experiment toggle (2026-07-05). */
    public static void syncExperimentSysprop(Context ctx) {
        if (!isAvailableOnDevice() || ctx == null) return;
        if (!RootShell.canRun()) return;
        String val = isEnabled(ctx) ? "1" : "0";
        RootShell.run("setprop " + SYSPROP + " " + val);
    }

    /** Persist toggle + sync sysprop — call after user flips the Debug row. */
    public static void setEnabled(Context ctx, boolean enable) {
        if (ctx == null || !isAvailableOnDevice()) return;
        ctx.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_USB_MASS_STORAGE_EXPERIMENT, enable)
                .apply();
        if (enable) {
            XposedModuleConfigStore.writeBoolean(
                    "com.solar.launcher.xposed.bridge.y2",
                    "y2_usb_mass_storage_hooks",
                    true);
        }
        syncExperimentSysprop(ctx);
        if (!enable) {
            UsbMassStorageController.ensureStockMtpWhenExperimentOff(ctx);
        }
    }

    /** Test hook — family + pref without Android context (2026-07-05). */
    static boolean isEnabledForFamily(String family, boolean experimentPref) {
        if (family == null || !"y2".equals(family)) return true;
        return experimentPref;
    }

    /** Test hook — USB submenu visible when Y1 always, Y2 only if experiment on. */
    static boolean connectionsUsbMenuVisibleForFamily(String family, boolean experimentPref) {
        if (family == null || !"y2".equals(family)) return true;
        return experimentPref;
    }
}
