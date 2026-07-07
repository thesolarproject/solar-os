package com.solar.launcher;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Manifest receiver — PAIRING_REQUEST and bond events for all apps (Settings, Rockbox, Solar). */
public class BluetoothAudioRepairReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        BluetoothDevice device = null;
        try {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        } catch (Exception ignored) {}
        if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
            int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                    BluetoothDevice.ERROR);
            int passkey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, 0);
            if (BluetoothPairingCoordinator.onPairingRequest(context, device, variant, passkey, false)) {
                abortBroadcast();
            }
            return;
        }
        if (BluetoothAudioRepair.isBondAuthFailure(intent)) {
            BluetoothPairingCoordinator.onAuthFailure(context, device);
            return;
        }
        if (!BluetoothAudioRepair.shouldRepairEvent(intent)) return;
        if (device != null) {
            BluetoothAudioRepair.rememberLastAudioDevice(context, device);
        }
        BluetoothAudioRepair.requestRepair(context, device);
    }
}
