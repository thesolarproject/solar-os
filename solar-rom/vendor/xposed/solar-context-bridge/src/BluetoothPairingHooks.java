package com.solar.launcher.xposed.bridge;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Replace stock Bluetooth pairing dialogs with Solar global overlay tiers.
 * Layman: when Settings or Bluetooth app asks for a PIN, Solar shows our wheel-friendly screens.
 * Technical: hook BluetoothPairingDialog.show; fail-open to stock Holo when Solar missing.
 * Reversal: remove install calls; stock pairing dialogs return on Y1/Y2.
 */
final class BluetoothPairingHooks {

    private static final String SETTINGS_PAIRING_DIALOG =
            "com.android.settings.bluetooth.BluetoothPairingDialog";
    private static final String BLUETOOTH_PAIRING_DIALOG =
            "com.android.bluetooth.BluetoothPairingDialog";

    private BluetoothPairingHooks() {}

    /** Install in Settings and Bluetooth packages (API 17 + 19). */
    static void install(LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        if ("com.android.settings".equals(lpparam.packageName)) {
            hookPairingDialog(lpparam, SETTINGS_PAIRING_DIALOG);
        }
        if ("com.android.bluetooth".equals(lpparam.packageName)) {
            hookPairingDialog(lpparam, BLUETOOTH_PAIRING_DIALOG);
        }
    }

    private static void hookPairingDialog(LoadPackageParam lpparam, String className) {
        try {
            Class<?> dialogClass = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedHookKit.hookAll(dialogClass, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Dialog)) return;
                    Dialog dialog = (Dialog) param.thisObject;
                    Context ctx = dialog.getContext();
                    if (ctx == null || !SolarOverlayClient.canDeliverOverlay(ctx)) return;
                    PairingExtract extract = PairingExtract.fromDialog(dialog);
                    if (extract == null || extract.address == null) return;
                    try {
                        dialog.dismiss();
                    } catch (Throwable ignored) {}
                    boolean ok = SolarOverlayClient.showBluetoothPairing(ctx, extract.mode,
                            extract.address, extract.name, extract.passkey, extract.pinPrefill);
                    if (ok) {
                        XposedHookKit.skipMethod(param);
                        SolarContextBridge.log("BluetoothPairingDialog replaced mode="
                                + extract.mode + " addr=" + extract.address);
                    }
                }
            });
            SolarContextBridge.log("hooked " + className);
        } catch (Throwable t) {
            SolarContextBridge.log("BluetoothPairing hook skip " + className + ": "
                    + t.getClass().getSimpleName());
        }
    }

    /** Reflect pairing dialog fields — mDevice / mType / mPairingKey on AOSP 4.2/4.4. */
    private static final class PairingExtract {
        final int mode;
        final String address;
        final String name;
        final int passkey;
        final String pinPrefill;

        PairingExtract(int mode, String address, String name, int passkey, String pinPrefill) {
            this.mode = mode;
            this.address = address;
            this.name = name;
            this.passkey = passkey;
            this.pinPrefill = pinPrefill;
        }

        static PairingExtract fromDialog(Dialog dialog) {
            if (dialog == null) return null;
            Object self = dialog;
            BluetoothDevice device = (BluetoothDevice) readField(self, "mDevice");
            if (device == null) {
                device = (BluetoothDevice) readField(self, "mBluetoothDevice");
            }
            if (device == null) return null;
            int variant = readIntField(self, "mType", -1);
            if (variant < 0) variant = readIntField(self, "mPairingVariant", -1);
            int passkey = readIntField(self, "mPairingKey", 0);
            if (passkey == 0) passkey = readIntField(self, "mPasskey", 0);
            String address = safeAddress(device);
            String name = safeName(device);
            int mode = mapVariantToMode(variant);
            String prefill = mode == 1 /* MODE_PIN */ ? null : null;
            return new PairingExtract(mode, address, name, passkey, prefill);
        }

        private static int mapVariantToMode(int variant) {
            // Align with com.solar.launcher.BluetoothPairingCoordinator overlay modes.
            if (variant == 0) return 1; // PAIRING_VARIANT_PIN → MODE_PIN
            if (variant == 1) return 2; // PAIRING_VARIANT_PASSKEY → MODE_PASSKEY_DISPLAY
            if (variant == 2) return 3; // PAIRING_VARIANT_PASSKEY_CONFIRMATION → MODE_PASSKEY_CONFIRM
            return 4; // CONSENT / Just Works → MODE_CONSENT
        }

        private static Object readField(Object obj, String name) {
            try {
                return XposedHelpers.getObjectField(obj, name);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static int readIntField(Object obj, String name, int fallback) {
            try {
                Object v = XposedHelpers.getObjectField(obj, name);
                if (v instanceof Integer) return (Integer) v;
            } catch (Throwable ignored) {}
            return fallback;
        }

        private static String safeAddress(BluetoothDevice device) {
            try {
                return device.getAddress();
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static String safeName(BluetoothDevice device) {
            try {
                String n = device.getName();
                return n != null ? n : device.getAddress();
            } catch (Throwable ignored) {
                return safeAddress(device);
            }
        }
    }
}
