package com.solar.launcher;

import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * 2026-07-05 — AVRCP-only transport remap; wheel scancodes 105/106 must never change in keylayout.
 * Positive AVRCP device match + mtk-kpd/mtk-tpd denylist — no source-bit fallback (misclassifies wheel).
 * When changing: remaps 126/127/86/87/88 only when isBluetoothTransportKey is true.
 * Reversal: delete guard; Bluetooth and wheel may both fire transport on same keycodes.
 */
public final class Y1BluetoothInput {

    private Y1BluetoothInput() {}

    /**
     * True when KeyEvent came from BT AVRCP uinput, not Y1 wheel (mtk-tpd-kpd / mtk-kpd).
     */
    public static boolean isBluetoothTransportKey(KeyEvent event) {
        if (event == null) return false;
        InputDevice device = event.getDevice();
        if (device == null) return false;
        String name = device.getName();
        if (name == null) return false;
        if (isY1HardwareInputName(name)) return false;
        return name.contains("AVRCP");
    }

    /** Hardware keypad / touchpad names on Y1 — wheel and side buttons. */
    public static boolean isY1HardwareInputName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("mtk-kpd") || lower.contains("mtk-tpd");
    }

    /**
     * {@link Intent#ACTION_MEDIA_BUTTON} deliveries have no {@link InputDevice}; treat discrete
     * transport keycodes as BT when Solar is playing. Foreground wheel still uses {@link KeyEvent}
     * with a hardware device id.
     */
    public static boolean isMediaButtonTransportKeyCode(int keyCode) {
        return Y1InputKeys.isBluetoothMediaButtonKeyCode(keyCode);
    }

    static void selfCheck() {
        if (!isY1HardwareInputName("mtk-tpd-kpd")) throw new AssertionError("mtk-tpd");
        if (!isY1HardwareInputName("mtk-kpd")) throw new AssertionError("mtk-kpd");
        if (isY1HardwareInputName("AVRCP")) throw new AssertionError("avrcp not hardware");
    }
}
