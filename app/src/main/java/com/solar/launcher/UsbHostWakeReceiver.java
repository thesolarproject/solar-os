package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Wakes Solar when a USB host (PC) connects while the process is not running.
 * ponytail: dynamic receivers in MainActivity miss USB_STATE if SystemUI wins
 * the race and Solar was force-stopped; this manifest receiver starts HOME.
 */
public final class UsbHostWakeReceiver extends BroadcastReceiver {

    static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";

    static boolean isUsbHostIntent(Intent intent) {
        if (intent == null || !ACTION_USB_STATE.equals(intent.getAction())) return false;
        return intent.getBooleanExtra("connected", false);
    }

    /** Cable unplug — dismiss global USB overlay without launching Solar. */
    static boolean isUsbDisconnectIntent(Intent intent) {
        return intent != null
                && ACTION_USB_STATE.equals(intent.getAction())
                && !intent.getBooleanExtra("connected", false);
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
            Debug705932Log.log("UsbHostWakeReceiver.onReceive", "usb broadcast", "H1,H4", d);
        } catch (Exception ignored) {}
        // #endregion
        if (isUsbDisconnectIntent(intent)) {
            UsbStorageOverlayReceiver.dismissGlobalOverlayIfActive(context);
            UsbStorageConcierge.clearOnUsbDisconnect();
            UsbHostSessionPolicy.onUsbHostDisconnected(context);
            // Manifest path — tear down stale kernel UMS even when MainActivity is not alive (2026-07-05).
            final android.content.BroadcastReceiver.PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        UsbMassStorageController.disableIfExported(app);
                    } finally {
                        pending.finish();
                    }
                }
            }, "UsbDisconnectUmsOff").start();
            return;
        }
        if (!isUsbHostIntent(intent)) return;
        final android.content.BroadcastReceiver.PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        // 2026-07-06 — Defer to Xposed UsbStorageHooks; fallback if concierge sysprop never set.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    routeUsbHostConnectFallback(app);
                } finally {
                    pending.finish();
                }
            }
        }, UsbStorageConcierge.fallbackDelayMs());
    }

    /** Tier-2 wake when Solar process was dead and Xposed concierge did not route (2026-07-06). */
    private static void routeUsbHostConnectFallback(Context context) {
        if (UsbStorageConcierge.isXposedConciergeActive()) {
            UsbStorageConcierge.logFallbackDecision("UsbHostWakeReceiver", true);
            return;
        }
        UsbStorageConcierge.logFallbackDecision("UsbHostWakeReceiver", false);
        if (UsbHostSessionPolicy.hasUserDismissedThisSession(context)) {
            return;
        }
        UsbHostSessionPolicy.onUsbHostConnected(context);
        try {
            if (!context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), 0).enabled) {
                return;
            }
        } catch (Exception e) {
            return;
        }
        if (!UsbHostSessionPolicy.shouldEvaluatePromptThisSession(context)) {
            return;
        }
        if (UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(context)
                && !UsbStorageSessionFlags.isAutoConnectEnabled(context)
                && UsbStorageOverlayReceiver.shouldUseGlobalOverlayPrompt(context)) {
            UsbStorageOverlayReceiver.routeEnablePromptOverlay(context, "UsbHostWakeReceiver.fallback");
            return;
        }
        if (!shouldLaunchMainActivityForUsbHost(context)) {
            return;
        }
        Intent home = new Intent(context, MainActivity.class);
        home.setAction(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(home);
    }

    /**
     * True when USB host connect should wake {@link MainActivity} (Solar foreground or auto-connect).
     * False when a stock app is foreground and the enable prompt belongs in :overlay.
     */
    static boolean shouldLaunchMainActivityForUsbHost(Context context) {
        if (context == null) return false;
        if (UsbHostSessionPolicy.hasUserDismissedThisSession(context)) return false;
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(context)) return false;
        if (UsbStorageSessionFlags.isAutoConnectEnabled(context)) return true;
        return !UsbStorageOverlayReceiver.shouldUseGlobalOverlayPrompt(context);
    }
}
