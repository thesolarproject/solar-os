package com.solar.launcher;

import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * 2026-07-11 — Host keyboard/mouse → Solar actions when running on emulator (or A5 lab).
 * Layman: Esc/Backspace/right-click leave screens; arrows scroll; space plays; -/= volume.
 * Tech: remap KeyEvent keycodes before MainActivity handlers; volume opens Solar HUD.
 * Reversal: delete; stock emulator keys only.
 */
public final class EmulatorInputMap {

    private EmulatorInputMap() {}

    /** True on AVD / sdk builds (generic fingerprint or family prop lab). */
    public static boolean isEmulator() {
        try {
            String fp = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase() : "";
            String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
            String product = Build.PRODUCT != null ? Build.PRODUCT.toLowerCase() : "";
            if (fp.contains("generic") || fp.contains("sdk") || fp.contains("emulator")) return true;
            if (model.contains("sdk") || model.contains("emulator") || model.contains("android sdk")) {
                return true;
            }
            if (product.contains("sdk") || product.contains("google_sdk")) return true;
            String hardware = Build.HARDWARE != null ? Build.HARDWARE.toLowerCase() : "";
            if (hardware.contains("ranchu") || hardware.contains("goldfish")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /** Active when emulator or A5 (touch lab keyboards). */
    public static boolean shouldRemap() {
        return isEmulator() || DeviceFeatures.isA5();
    }

    /**
     * 2026-07-11 — Host mouse right-click → Back (previous menu) on emulator/A5 lab.
     * Layman: right-click goes back a screen, same as Esc.
     * Tech: BUTTON_SECONDARY on mouse MotionEvent (API 14+ getButtonState).
     */
    public static boolean isRightClickBack(MotionEvent ev) {
        if (ev == null || !shouldRemap()) return false;
        try {
            int buttons = ev.getButtonState();
            if ((buttons & MotionEvent.BUTTON_SECONDARY) != 0) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Remap host key to Solar keycode without gating (unit tests + table self-check).
     */
    static int mapKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return KeyEvent.KEYCODE_BACK;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return KeyEvent.KEYCODE_DPAD_CENTER;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                return KeyEvent.KEYCODE_DPAD_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case KeyEvent.KEYCODE_SPACE:
                return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case KeyEvent.KEYCODE_COMMA:
            case KeyEvent.KEYCODE_LEFT_BRACKET:
                return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            case KeyEvent.KEYCODE_PERIOD:
            case KeyEvent.KEYCODE_RIGHT_BRACKET:
                return KeyEvent.KEYCODE_MEDIA_NEXT;
            case KeyEvent.KEYCODE_MINUS:
                return KeyEvent.KEYCODE_VOLUME_DOWN;
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_PLUS:
                return KeyEvent.KEYCODE_VOLUME_UP;
            default:
                return -1;
        }
    }

    /**
     * Remap host key to Solar keycode, or -1 if not ours.
     * Esc/DEL → BACK; arrows/WASD → DPAD; space → play/pause; brackets → skip; -/= → volume.
     */
    public static int remapToSolarKeyCode(int keyCode) {
        if (!shouldRemap()) return -1;
        return mapKeyCode(keyCode);
    }

    /** Build remapped event, or null when no change. */
    public static KeyEvent remapEvent(KeyEvent src) {
        if (src == null) return null;
        int mapped = remapToSolarKeyCode(src.getKeyCode());
        if (mapped < 0 || mapped == src.getKeyCode()) return null;
        return new KeyEvent(src.getDownTime(), src.getEventTime(), src.getAction(),
                mapped, src.getRepeatCount(), src.getMetaState(), src.getDeviceId(),
                src.getScanCode(), src.getFlags());
    }

    static void selfCheck() {
        if (mapKeyCode(KeyEvent.KEYCODE_ESCAPE) != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("esc→back");
        }
        if (mapKeyCode(KeyEvent.KEYCODE_DEL) != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("del→back");
        }
        if (mapKeyCode(KeyEvent.KEYCODE_SPACE) != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            throw new AssertionError("space→pp");
        }
        if (mapKeyCode(KeyEvent.KEYCODE_MINUS) != KeyEvent.KEYCODE_VOLUME_DOWN) {
            throw new AssertionError("minus→vol");
        }
        if (MotionEvent.BUTTON_SECONDARY == 0) {
            throw new AssertionError("secondary button const");
        }
    }
}
