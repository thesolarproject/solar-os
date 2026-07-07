package com.solar.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-05 — Full-screen digit PIN keyboard inside the global :overlay process.
 * Layman: type a Bluetooth PIN with the scroll wheel over any app (Rockbox, Settings, etc.).
 * Technical: SolarKeyboardShellHost on overlayRoot; keys via OverlayModalHost routing.
 * Reversal: remove; PIN entry falls back to MainActivity or stock Holo dialog.
 */
final class OverlayBluetoothPinKeyboard {

    private final Context context;
    private final ViewGroup parent;
    private final Runnable onDismiss;

    private View shellRoot;
    private SolarKeyboardShellHost shellHost;
    private SolarWheelKeyboardController controller;
    private String deviceAddress;
    private String title;

    OverlayBluetoothPinKeyboard(Context context, ViewGroup parent, Runnable onDismiss) {
        this.context = context.getApplicationContext();
        this.parent = parent;
        this.onDismiss = onDismiss;
    }

    boolean isShowing() {
        return shellRoot != null;
    }

    /** Paint digit keyboard — prefill from saved/default PIN when provided. */
    void show(String title, String address, String prefill) {
        dismiss();
        this.title = title;
        this.deviceAddress = address;
        controller = new SolarWheelKeyboardController();
        controller.setDigitOnlyMode(true);
        if (prefill != null && prefill.length() > 0) {
            controller.setBuffer(prefill);
        }
        controller.setListener(new SolarWheelKeyboardController.Listener() {
            @Override
            public void onStateChanged() {
                refreshUi();
            }

            @Override
            public void onEnterRequested() {
                String pin = controller.getBuffer();
                if (pin == null || pin.trim().length() == 0) {
                    pin = BluetoothAudioRepair.normalizePairingPin(null);
                }
                BluetoothPairingCoordinator.submitPinFromOverlay(context, deviceAddress, pin);
                dismissAndCloseOverlay();
            }
        });

        LayoutInflater inflater = LayoutInflater.from(context);
        shellRoot = inflater.inflate(R.layout.layout_solar_keyboard_shell, parent, false);
        shellHost = new SolarKeyboardShellHost(context, shellRoot,
                context.getString(R.string.keyboard_enter));
        parent.addView(shellRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshUi();
        ThemeManager.ensureOverlayPaintableMinimum(context);
    }

    /** Route wheel / OK / Back from OverlayModalHost while PIN tier is up. */
    boolean handleKeyDown(int keyCode) {
        if (controller == null || shellRoot == null) return false;
        if (Y1InputKeys.isBackKey(keyCode)) {
            BluetoothPairingCoordinator.cancelPairing(context, deviceAddress);
            dismissAndCloseOverlay();
            return true;
        }
        if (Y1InputKeys.isWheelUp(keyCode)) {
            controller.wheelUp();
            return true;
        }
        if (Y1InputKeys.isWheelDown(keyCode)) {
            controller.wheelDown();
            return true;
        }
        if (Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isPlayPauseKey(keyCode)) {
            controller.centerPress();
            return true;
        }
        if (Y1InputKeys.isTrackPreviousKey(keyCode)) {
            controller.mediaDelete();
            return true;
        }
        return false;
    }

    boolean handleKeyUp(int keyCode) {
        return isShowing() && (Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isBackKey(keyCode)
                || Y1InputKeys.isWheelKey(keyCode) || Y1InputKeys.isPlayPauseKey(keyCode));
    }

    void dismiss() {
        if (shellRoot != null && parent != null) {
            try {
                parent.removeView(shellRoot);
            } catch (Exception ignored) {}
        }
        shellRoot = null;
        shellHost = null;
        controller = null;
        deviceAddress = null;
    }

    private void dismissAndCloseOverlay() {
        dismiss();
        if (onDismiss != null) onDismiss.run();
    }

    private void refreshUi() {
        if (shellHost == null || controller == null) return;
        String statusTitle = title != null && title.length() > 0
                ? title : context.getString(R.string.settings_bluetooth_pairing_pin);
        shellHost.applyShellTheme(statusTitle, true);
        String buffer = controller.getBuffer();
        String placeholder = "0000";
        String display = buffer.length() == 0 ? placeholder : buffer;
        shellHost.getKeyboardUi().refresh(controller, statusTitle, display, buffer.length() == 0);
    }
}
