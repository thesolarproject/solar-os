package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * SystemUI Xposed hook forwards USB storage enable prompts here — starts the global overlay tier.
 */
public final class UsbStorageOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(intent.getAction())) return;
        routeUsbStorageOverlay(context, intent.getBooleanExtra(
                OverlayTriggers.EXTRA_USB_STORAGE_LOCK, false));
    }

    /** Cable unplug — dismiss global USB overlay without pulling Solar HOME. */
    static void dismissGlobalOverlayIfActive(Context context) {
        if (context == null || !OverlayKeyGate.isOverlayKeysActive()) return;
        OverlayTierScheduler.clearPendingUsbPrompt();
        ExternalInputHandoff.restoreAfterOverlayDismiss(context.getApplicationContext());
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(OverlayTriggers.ACTION_DISMISS_OVERLAY);
        try {
            context.startService(svc);
        } catch (Exception ignored) {}
    }

    /** Honor Solar USB prefs, then start overlay or jump straight to Solar USB lock / enable. */
    static void routeUsbStorageOverlay(Context context, boolean massStorageLockOnly) {
        if (context == null) return;
        if (!massStorageLockOnly && UsbHostSessionPolicy.hasUserDismissedThisSession(context)) {
            return;
        }
        // #region agent log
        try {
            org.json.JSONObject d = Debug266f21Log.usbSnapshot();
            d.put("massStorageLockOnly", massStorageLockOnly);
            d.put("fg", ExternalInputHandoff.getForegroundPackageName(context));
            d.put("useGlobalOverlay", shouldUseGlobalOverlayPrompt(context));
            d.put("autoConnect", UsbStorageSessionFlags.isAutoConnectEnabled(context));
            Debug266f21Log.log(context, "UsbStorageOverlayReceiver.routeUsbStorageOverlay",
                    "route entry", "H3,H7", d);
        } catch (Exception ignored) {}
        // #endregion
        if (!UsbMassStorageExperiment.isEnabled(context)) return;
        if (massStorageLockOnly) {
            if (shouldUseGlobalOverlayPrompt(context)) {
                // UMS exported while user stays in a stock app — no Solar lock handoff.
                return;
            }
            launchSolarUsbHandoff(context, false, true);
            return;
        }
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(context)) {
            return;
        }
        // 2026-07-06 — ANR/crash native tier owns overlay — USB waits for user action first.
        if (OverlayTierScheduler.shouldDeferUsbSpawn()) {
            OverlayTierScheduler.queuePendingUsbPrompt();
            return;
        }
        UsbHostSessionPolicy.markPromptEvaluated(context);
        if (UsbStorageSessionFlags.isAutoConnectEnabled(context)) {
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    UsbMassStorageController.enable(app, "auto.receiver");
                }
            }, "UsbAutoConnectRecv").start();
            launchSolarUsbHandoff(context, false, true);
            return;
        }
        // 2026-07-06 — Solar foreground: in-app USB tier owns keys; global WM overlay steals nothing.
        if (!shouldUseGlobalOverlayPrompt(context)) {
            launchSolarUsbHandoff(context, false, false);
            return;
        }
        // Overlay arms inside SolarOverlayService — never set sys.solar.overlay.active here.
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setComponent(new ComponentName(context.getPackageName(),
                SolarOverlayService.class.getName()));
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE);
        try {
            context.startService(svc);
        } catch (Exception ignored) {}
    }

    /**
     * True when a stock/third-party app is foreground — USB enable prompt belongs in :overlay,
     * not Solar's in-app context menu (which would steal focus via moveTaskToFront).
     */
    static boolean shouldUseGlobalOverlayPrompt(Context context) {
        if (context == null) return false;
        if (OverlayKeyGate.isOverlayKeysActive()) return false;
        return shouldUseGlobalOverlayPromptForTest(
                ExternalInputHandoff.getForegroundPackageName(context));
    }

    /** Test hook — package name to overlay-vs-in-app routing without Robolectric. */
    static boolean shouldUseGlobalOverlayPromptForTest(String foregroundPkg) {
        if (GlobalOverlayPolicy.isSolarForegroundPackage(foregroundPkg)) return false;
        // Unknown / SystemUI / third-party / Rockbox — global overlay; never HOME-steal Solar.
        return true;
    }

    /** Route USB enable prompt through global overlay when user is not already in Solar. */
    static void routeUsbConnectPromptIfNeeded(Context context) {
        if (!shouldUseGlobalOverlayPrompt(context)) return;
        routeUsbStorageOverlay(context, false);
    }

    /** True when the enable prompt should stay in :overlay — UMS not exported yet. */
    static boolean isEnablePromptOverlayPreferred(Context context) {
        return shouldUseGlobalOverlayPrompt(context);
    }

    /** Log + route overlay enable prompt (never pulls Solar HOME until user confirms). */
    static void routeEnablePromptOverlay(Context context, String caller) {
        if (context == null) return;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("caller", caller);
            d.put("fg", ExternalInputHandoff.getForegroundPackageName(context));
            d.put("overlayActive", OverlayKeyGate.isOverlayKeysActive());
            DebugEdc27bLog.log("UsbStorageOverlayReceiver.routeEnablePromptOverlay",
                    "overlay enable prompt", "USB-F1", d);
        } catch (Exception ignored) {}
        // #endregion
        routeUsbStorageOverlay(context, false);
    }

    /** Bring Solar to front for enable or mass-storage lock — skips third-party foreground restore. */
    static void launchSolarUsbHandoff(Context context, boolean enableStorage, boolean lockOnly) {
        if (context == null) return;
        if (!UsbMassStorageExperiment.isEnabled(context)) return;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("enableStorage", enableStorage);
            d.put("lockOnly", lockOnly);
            DebugEdc27bLog.log("UsbStorageOverlayReceiver.launchSolarUsbHandoff",
                    "start MainActivity for USB", "USB-F2", d);
        } catch (Exception ignored) {}
        // #endregion
        OverlayForegroundGuard.markUserRequestedSolarNavigation();
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (enableStorage) {
            launch.putExtra(MainActivity.EXTRA_USB_OVERLAY_ENABLE, true);
        }
        if (lockOnly) {
            launch.putExtra(MainActivity.EXTRA_USB_OVERLAY_LOCK, true);
        }
        context.startActivity(launch);
    }
}
