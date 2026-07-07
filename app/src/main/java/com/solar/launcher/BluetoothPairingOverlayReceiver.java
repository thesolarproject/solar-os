package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-05 — Starts global Bluetooth pairing overlay (PIN / passkey / consent).
 * Layman: Xposed or the pairing coordinator ask :overlay to paint Solar pairing screens.
 * Technical: broadcast → SolarOverlayService → OverlayModalHost BT tiers.
 */
public final class BluetoothPairingOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING.equals(intent.getAction())) return;
        routeBluetoothPairingOverlay(context, intent);
    }

    /** Start :overlay service with pairing extras — same path from coordinator and Xposed. */
    static void routeBluetoothPairingOverlay(Context context, Intent source) {
        if (context == null || source == null) return;
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setComponent(new ComponentName(context.getPackageName(),
                SolarOverlayService.class.getName()));
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING);
        svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_MODE,
                source.getIntExtra(OverlayTriggers.EXTRA_BT_PAIRING_MODE,
                        BluetoothPairingCoordinator.MODE_PIN));
        svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_ADDRESS,
                source.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_ADDRESS));
        svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_NAME,
                source.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_NAME));
        svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_PASSKEY,
                source.getIntExtra(OverlayTriggers.EXTRA_BT_PAIRING_PASSKEY, 0));
        if (source.hasExtra(OverlayTriggers.EXTRA_BT_PAIRING_PIN_PREFILL)) {
            svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_PIN_PREFILL,
                    source.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_PIN_PREFILL));
        }
        try {
            context.startService(svc);
        } catch (Exception ignored) {}
    }
}
