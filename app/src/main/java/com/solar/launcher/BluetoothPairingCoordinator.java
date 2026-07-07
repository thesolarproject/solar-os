package com.solar.launcher;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 2026-07-05 — Single owner for Bluetooth pairing UI and API calls (Y1 + Y2).
 * Layman: when a gadget asks to pair, Solar shows our wheel-friendly screens or uses a saved PIN.
 * Technical: routes PAIRING_REQUEST variants to :overlay tiers or silent setPin; de-dupes sessions.
 * Reversal: delete class; receivers fall back to stock Holo pairing dialogs.
 */
public final class BluetoothPairingCoordinator {

    private static final String TAG = "SolarBtPair";

    /** Overlay tier — wheel digit keyboard for PIN entry. */
    public static final int MODE_PIN = 1;
    /** Overlay tier — show passkey; user types it on the remote device. */
    public static final int MODE_PASSKEY_DISPLAY = 2;
    /** Overlay tier — passkey on screen; user confirms it matches the remote. */
    public static final int MODE_PASSKEY_CONFIRM = 3;
    /** Overlay tier — Just Works / consent confirm. */
    public static final int MODE_CONSENT = 4;

    /** {@link BluetoothDevice#PAIRING_VARIANT_PIN} = 0, passkey display = 1 (API 17 lacks named constant). */
    private static final int PAIRING_VARIANT_PASSKEY = 1;
    private static final int PAIRING_VARIANT_CONSENT = 3;
    private static volatile String activeSessionAddress;
    private static volatile long activeSessionAt;

    private BluetoothPairingCoordinator() {}

    /**
     * Entry from PAIRING_REQUEST broadcast — returns true when Solar owns the request
     * (silent PIN, overlay shown, or de-duped); caller should abortBroadcast().
     */
    @SuppressLint("MissingPermission")
    public static boolean onPairingRequest(Context context, BluetoothDevice device,
            int variant, int passkey, boolean forcePinUi) {
        if (context == null || device == null) return false;
        String address = safeAddress(device);
        if (address == null) return false;
        if (isDuplicateSession(address)) {
            Log.d(TAG, "pairing de-dupe " + address);
            return true;
        }
        markSession(address);
        if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
            if (!forcePinUi) {
                String pin = BluetoothAudioRepair.pairingPinForDevice(context, device);
                if (submitPin(context, device, pin)) {
                    BluetoothAudioRepair.rememberLastAudioDevice(context, device);
                    clearSession();
                    return true;
                }
            }
            return showPinOverlay(context, address, safeName(device),
                    BluetoothAudioRepair.pairingPinForDevice(context, device));
        }
        if (variant == PAIRING_VARIANT_PASSKEY) {
            return showPasskeyOverlay(context, address, safeName(device), passkey, MODE_PASSKEY_DISPLAY);
        }
        if (variant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
            return showPasskeyOverlay(context, address, safeName(device), passkey, MODE_PASSKEY_CONFIRM);
        }
        if (variant == PAIRING_VARIANT_CONSENT) {
            return showPasskeyOverlay(context, address, safeName(device), passkey, MODE_CONSENT);
        }
        Log.d(TAG, "unknown variant=" + variant + " deferring to stock");
        clearSession();
        return false;
    }

    /** Auth failure during bond — force PIN keyboard even when a default PIN exists. */
    @SuppressLint("MissingPermission")
    public static void onAuthFailure(Context context, BluetoothDevice device) {
        if (context == null || device == null) return;
        String address = safeAddress(device);
        if (address == null) return;
        markSession(address);
        showPinOverlay(context, address, safeName(device),
                BluetoothAudioRepair.pairingPinForDevice(context, device));
    }

    /** User finished PIN entry on overlay keyboard — save and submit to stack. */
    @SuppressLint("MissingPermission")
    public static void submitPinFromOverlay(Context context, String address, String pin) {
        if (context == null) return;
        BluetoothDevice device = BluetoothAudioRepair.deviceForAddress(address);
        if (device == null) return;
        String cleaned = BluetoothAudioRepair.normalizePairingPin(pin);
        BluetoothAudioRepair.savePairingPin(context, address, cleaned);
        submitPin(context, device, cleaned);
        BluetoothAudioRepair.rememberLastAudioDevice(context, device);
        clearSession();
    }

    /** Passkey match / consent row picked on overlay. */
    @SuppressLint("MissingPermission")
    public static void submitConfirmationFromOverlay(Context context, String address, boolean accept) {
        if (context == null) return;
        BluetoothDevice device = BluetoothAudioRepair.deviceForAddress(address);
        if (device == null) return;
        submitConfirmation(device, accept);
        if (accept) {
            BluetoothAudioRepair.rememberLastAudioDevice(context, device);
        }
        clearSession();
    }

    /** Back / Cancel on pairing overlay — reject bond attempt. */
    @SuppressLint("MissingPermission")
    public static void cancelPairing(Context context, String address) {
        BluetoothDevice device = BluetoothAudioRepair.deviceForAddress(address);
        if (device != null) {
            submitConfirmation(device, false);
            try {
                Method cancel = device.getClass().getMethod("cancelPairingUserInput");
                cancel.invoke(device);
            } catch (Exception ignored) {}
        }
        clearSession();
    }

    /** Informational passkey display dismissed — session ends; stack waits for remote. */
    public static void dismissPasskeyDisplaySession() {
        clearSession();
    }

    static String formatPasskey(int passkey) {
        String raw = String.valueOf(Math.max(0, passkey));
        while (raw.length() < 6) raw = "0" + raw;
        if (raw.length() > 6) raw = raw.substring(raw.length() - 6);
        return raw;
    }

    static int overlayModeForVariant(int variant) {
        if (variant == BluetoothDevice.PAIRING_VARIANT_PIN) return MODE_PIN;
        if (variant == PAIRING_VARIANT_PASSKEY) return MODE_PASSKEY_DISPLAY;
        if (variant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) return MODE_PASSKEY_CONFIRM;
        if (variant == PAIRING_VARIANT_CONSENT) return MODE_CONSENT;
        return MODE_CONSENT;
    }

    private static boolean showPinOverlay(Context context, String address, String name, String prefill) {
        return startPairingOverlay(context, address, name, 0, MODE_PIN, prefill);
    }

    private static boolean showPasskeyOverlay(Context context, String address, String name,
            int passkey, int mode) {
        return startPairingOverlay(context, address, name, passkey, mode, null);
    }

    private static boolean startPairingOverlay(Context context, String address, String name,
            int passkey, int mode, String pinPrefill) {
        try {
            Intent svc = new Intent(context, SolarOverlayService.class);
            svc.setComponent(new ComponentName(context.getPackageName(),
                    SolarOverlayService.class.getName()));
            svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING);
            svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_MODE, mode);
            svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_ADDRESS, address);
            svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_NAME, name != null ? name : "");
            svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_PASSKEY, passkey);
            if (pinPrefill != null) {
                svc.putExtra(OverlayTriggers.EXTRA_BT_PAIRING_PIN_PREFILL, pinPrefill);
            }
            context.startService(svc);
            Log.i(TAG, "pairing overlay mode=" + mode + " addr=" + address);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "pairing overlay failed", e);
            clearSession();
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean submitPin(Context context, BluetoothDevice device, String pin) {
        try {
            byte[] bytes = BluetoothAudioRepair.bluetoothPinBytes(pin);
            Object ok = device.getClass().getMethod("setPin", byte[].class).invoke(device, bytes);
            Log.i(TAG, "setPin ok=" + ok + " addr=" + device.getAddress());
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (Exception e) {
            Log.w(TAG, "setPin failed " + device.getAddress(), e);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private static void submitConfirmation(BluetoothDevice device, boolean accept) {
        try {
            device.getClass().getMethod("setPairingConfirmation", boolean.class)
                    .invoke(device, accept);
            Log.i(TAG, "setPairingConfirmation(" + accept + ") addr=" + device.getAddress());
        } catch (Exception e) {
            Log.w(TAG, "setPairingConfirmation failed", e);
        }
    }

    private static boolean isDuplicateSession(String address) {
        if (activeSessionAddress == null || !activeSessionAddress.equals(address)) return false;
        return System.currentTimeMillis() - activeSessionAt < 30000L;
    }

    private static void markSession(String address) {
        activeSessionAddress = address;
        activeSessionAt = System.currentTimeMillis();
    }

    static void clearSession() {
        activeSessionAddress = null;
        activeSessionAt = 0L;
    }

    private static String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name != null ? name : device.getAddress();
        } catch (Exception ignored) {
            return safeAddress(device);
        }
    }

    /** Unit-test guard — variant → overlay mode mapping. */
    static void selfCheck() {
        if (overlayModeForVariant(BluetoothDevice.PAIRING_VARIANT_PIN) != MODE_PIN) {
            throw new AssertionError("pin mode");
        }
        if (!"012345".equals(formatPasskey(12345))) throw new AssertionError("passkey pad");
        if (!"000042".equals(formatPasskey(42))) throw new AssertionError("passkey pad2");
    }
}
