package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Wakes Solar when a USB host (PC) connects while the process is not running.
 * 2026-07-10 — Restored July-2 monlith: start MainActivity HOME; USB UI is in-app.
 * ponytail: dynamic receivers in MainActivity miss USB_STATE if SystemUI wins
 * the race and Solar was force-stopped; this manifest receiver starts HOME.
 * Reversal: companion/Xposed-only concierge without MainActivity wake.
 */
public final class UsbHostWakeReceiver extends BroadcastReceiver {

    static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";

    /**
     * 2026-07-15 — Debounce disconnect UMS teardown (mass_storage setprop re-enumerates USB).
     * Layman: flipping into disk mode briefly looks like unplug — wait before Turn Off.
     * Reversal: set to 0L to restore immediate disableIfExported on connected=false.
     */
    private static final long DISCONNECT_DISABLE_DEBOUNCE_MS = 2500L;
    private static Handler sHandler;
    private static Runnable sPendingDisconnectDisable;

    static boolean isUsbHostIntent(Intent intent) {
        if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return false;
        if (!intent.getBooleanExtra("connected", false)) return false;
        // Prefer host signals when present (July-2); fall back to connected-only for stock variance.
        return intent.getBooleanExtra("host_connected", false)
                || intent.getBooleanExtra("mass_storage", false)
                || intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false)
                || intent.getBooleanExtra("connected", false);
    }

    /** Cable unplug — unlock MainActivity flags / tear down UMS without launching Solar. */
    static boolean isUsbDisconnectIntent(Intent intent) {
        return intent != null
                && ACTION_USB_STATE.equals(intent.getAction())
                && !intent.getBooleanExtra("connected", false);
    }

    private static Handler handler() {
        if (sHandler == null) {
            sHandler = new Handler(Looper.getMainLooper());
        }
        return sHandler;
    }

    /** Cancel a pending disconnect disable (USB came back after re-enum). */
    private static void cancelPendingDisconnectDisable() {
        if (sPendingDisconnectDisable != null) {
            handler().removeCallbacks(sPendingDisconnectDisable);
            sPendingDisconnectDisable = null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // #region agent log
        try {
            org.json.JSONObject d = Debug705932Log.usbSnapshot();
            d.put("action", intent != null ? intent.getAction() : null);
            d.put("connected", intent != null && intent.getBooleanExtra("connected", false));
            d.put("hostConnected", intent != null && intent.getBooleanExtra("host_connected", false));
            d.put("massStorageExtra", intent != null && intent.getBooleanExtra("mass_storage", false));
            d.put("userSession", UsbMassStorageController.isUserSessionActive());
            Debug705932Log.log("UsbHostWakeReceiver.onReceive", "usb broadcast", "H1,H4", d);
        } catch (Exception ignored) {}
        // #endregion
        if (isUsbDisconnectIntent(intent)) {
            // 2026-07-15 — mass_storage setprop blips connected=false; do not clear session/LUN yet.
            // Was: immediate dismiss + disableIfExported → empty LUN while kernel stayed mass_storage.
            if (UsbMassStorageController.shouldIgnoreDisconnectDisable()) {
                return;
            }
            UsbStorageOverlayReceiver.dismissGlobalOverlayIfActive(context);
            UsbStorageConcierge.clearOnUsbDisconnect();
            UsbHostSessionPolicy.onUsbHostDisconnected(context);
            final android.content.BroadcastReceiver.PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            cancelPendingDisconnectDisable();
            sPendingDisconnectDisable = new Runnable() {
                @Override
                public void run() {
                    sPendingDisconnectDisable = null;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (UsbMassStorageController.shouldIgnoreDisconnectDisable()) {
                                    return;
                                }
                                // Sticky still disconnected?
                                Intent sticky = app.registerReceiver(null,
                                        new android.content.IntentFilter(ACTION_USB_STATE));
                                if (sticky != null && sticky.getBooleanExtra("connected", false)) {
                                    return;
                                }
                                UsbMassStorageController.disableIfExported(app);
                                UsbMassStorageController.clearUserSession();
                            } finally {
                                pending.finish();
                            }
                        }
                    }, "UsbDisconnectUmsOff").start();
                }
            };
            handler().postDelayed(sPendingDisconnectDisable, DISCONNECT_DISABLE_DEBOUNCE_MS);
            return;
        }
        if (!isUsbHostIntent(intent)) return;
        cancelPendingDisconnectDisable();
        boolean dismissed = UsbHostSessionPolicy.hasUserDismissedThisSession(context);
        boolean evaluated = UsbHostSessionPolicy.hasPromptEvaluatedThisSession(context);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hostIntent", true);
            d.put("dismissed", dismissed);
            d.put("evaluated", evaluated);
            d.put("extraHost", intent.getBooleanExtra("host_connected", false));
            d.put("extraMs", intent.getBooleanExtra("mass_storage", false));
            d.put("extraPcKnow", intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false));
            d.put("connectedOnlyFallback", intent.getBooleanExtra("connected", false)
                    && !intent.getBooleanExtra("host_connected", false)
                    && !intent.getBooleanExtra("mass_storage", false)
                    && !intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false));
            Debug02fc83Log.log(context, "UsbHostWakeReceiver.onReceive",
                    "host path", "H3", d);
        } catch (Exception ignored) {}
        // #endregion
        // 2026-07-14 — Prompt already shown/dismissed this plug: idle until cable unplug (H3 logs).
        // Was: every USB_STATE started MainActivity with evaluate_host while cable stayed in.
        if (dismissed || evaluated) {
            return;
        }
        // Mid-cable USB_STATE storm: session already armed — do not relaunch before evaluated sticks.
        boolean wasActive = UsbHostSessionPolicy.hasActiveHostSession(context);
        UsbHostSessionPolicy.onUsbHostConnected(context);
        if (wasActive) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("wasActive", true);
                d.put("skipRelaunch", true);
                Debug02fc83Log.log(context, "UsbHostWakeReceiver.onReceive",
                        "skip relaunch — session active", "H3", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        try {
            if (!context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0).enabled) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(context)) {
            return;
        }
        // July-2: bring MainActivity once per host session; USB prompt/lock is in-process.
        Intent home = new Intent(context, MainActivity.class);
        home.setAction(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        home.putExtra(MainActivity.EXTRA_USB_EVALUATE_HOST, true);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("startActivity", true);
            d.put("evaluatedBeforeStart", evaluated);
            d.put("wasActive", wasActive);
            Debug02fc83Log.log(context, "UsbHostWakeReceiver.onReceive",
                    "start MainActivity evaluate", "H3", d);
        } catch (Exception ignored) {}
        // #endregion
        try {
            context.startActivity(home);
        } catch (Exception e) {
            android.util.Log.w("UsbHostWake", "start MainActivity failed", e);
            UsbStorageOverlayReceiver.routeToSolar(context, true, false, "UsbHostWakeReceiver");
        }
    }

    /**
     * True when USB host connect should wake MainActivity.
     * Always true for monlith Solar USB UX (except dismissed session / experiment off).
     */
    static boolean shouldLaunchMainActivityForUsbHost(Context context) {
        if (context == null) return false;
        if (UsbHostSessionPolicy.hasUserDismissedThisSession(context)) return false;
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(context)) return false;
        return UsbMassStorageExperiment.isEnabled(context);
    }
}
