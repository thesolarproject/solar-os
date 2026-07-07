package com.solar.launcher;

import android.content.Context;

/**
 * 2026-07-06 — Tear down kernel UMS when user has not opted into auto-connect or explicit enable.
 * Layman: stops the player from silently becoming a USB disk on boot or cable plug-in.
 * Technical: disable when {@link UsbMassStorageController#isKernelMassStorageMode()} without consent.
 * Reversal: delete callers; rely on boot-only reset in {@link BootReceiver}.
 */
public final class UsbUnauthorizedUmsGuard {

    private UsbUnauthorizedUmsGuard() {}

    /** Background-safe — idempotent disable when UMS mode active without user consent. */
    public static void teardownIfUnauthorizedAsync(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                teardownIfUnauthorizedBlocking(app);
            }
        }, "UsbUmsGuard").start();
    }

    /** Blocking teardown for boot / tests. */
    public static boolean teardownIfUnauthorizedBlocking(Context context) {
        if (context == null) return false;
        if (!UsbMassStorageExperiment.isEnabled(context)) {
            return UsbMassStorageController.disableIfExported(context);
        }
        if (UsbStorageSessionFlags.isAutoConnectEnabled(context)) {
            return false;
        }
        if (!UsbMassStorageController.isKernelMassStorageMode()) {
            return true;
        }
        boolean ok = UsbMassStorageController.disable(context);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("ok", ok);
            d.put("autoConnect", false);
            d.put("usbConfig", UsbMassStorageController.isKernelMassStorageMode());
            Debug531722Log.log("UsbUnauthorizedUmsGuard.teardownIfUnauthorizedBlocking",
                    "cleared kernel UMS without consent", "H3", d);
        } catch (Exception ignored) {}
        // #endregion
        return ok;
    }
}
