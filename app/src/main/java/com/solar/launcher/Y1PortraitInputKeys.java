package com.solar.launcher;

import android.content.Context;
import android.view.KeyEvent;

/**
 * 2026-07-14 — Y1/Y2 portrait-experiment key remap (app-only; never edit Rockbox .kl).
 * Layman: track prev/next become Back / Play-Pause; scroll wheel still scrolls menus (skips on NP).
 * Tech: side 163/165 (+ Y2 105/106) → BACK / PLAY_PAUSE; wheel PLAY/PAUSE → PREV/NEXT on NP only.
 * Was: treated Y2 105/106 as wheel and remapped to PLAY/PAUSE — track buttons scrolled menus.
 * Reversal: drop helper; stock side-skip + wheel-scroll everywhere.
 */
public final class Y1PortraitInputKeys {

    /** mtk-kpd side prev / next scancodes (Y1 + Y2). */
    public static final int SIDE_PREV_SCAN = 165;
    public static final int SIDE_NEXT_SCAN = 163;
    /**
     * Y2-Rockbox.kl — second prev/next pair (docs: side skip), NOT the scroll wheel.
     * Wheel on Y2 is 103/108 → MEDIA_PLAY/PAUSE. Do not treat 105/106 as wheel.
     */
    public static final int Y2_TRACK_PREV_SCAN = 105;
    public static final int Y2_TRACK_NEXT_SCAN = 106;
    /** Y1+Y2 wheel axis (Y1 also uses 105/106 for the same PLAY/PAUSE). */
    public static final int WHEEL_UP_SCAN_Y2 = 103;
    public static final int WHEEL_DOWN_SCAN_Y2 = 108;

    private static final ThreadLocal<Boolean> REMAP_PASSTHROUGH = new ThreadLocal<Boolean>();

    private Y1PortraitInputKeys() {}

    /** Call around re-dispatch of a remapped KeyEvent — one hop only. */
    public static void beginRemapPassthrough() {
        REMAP_PASSTHROUGH.set(Boolean.TRUE);
    }

    /** Clear one-shot remap gate after nested dispatch returns. */
    public static void endRemapPassthrough() {
        REMAP_PASSTHROUGH.remove();
    }

    public static boolean isRemapPassthrough() {
        return Boolean.TRUE.equals(REMAP_PASSTHROUGH.get());
    }

    /**
     * 2026-07-14 — True when portrait experiment must rewrite this key.
     * Never remaps Bluetooth AVRCP (positive BT match only).
     */
    public static boolean needsRemap(Context ctx, KeyEvent event, boolean nowPlaying) {
        if (ctx == null || event == null) return false;
        if (!Y1PortraitExperiment.isEnabled(ctx)) return false;
        if (isRemapPassthrough()) return false;
        if (Y1BluetoothInput.isBluetoothTransportKey(event)) return false;
        int code = event.getKeyCode();
        int scan = event.getScanCode();
        // Track / side prev-next → Back or Play/Pause (not menu scroll).
        if (isTrackNext(code, scan)) return true;
        if (isTrackPrev(code, scan)) return true;
        // NP only: wheel PLAY/PAUSE → skip.
        if (nowPlaying && isWheelVertical(code, scan)) return true;
        return false;
    }

    /**
     * 2026-07-14 — Physical track-next (side).
     * Layman: the next-song button, not the scroll dial.
     * Tech: scan 163; Y2 also 106. Keycode MEDIA_NEXT only when scan is not a Y1 wheel.
     */
    public static boolean isTrackNext(int keyCode, int scan) {
        if (scan == SIDE_NEXT_SCAN) return true;
        if (DeviceFeatures.isY2() && scan == Y2_TRACK_NEXT_SCAN) return true;
        // Y1 tests / no-scan: MEDIA_NEXT is side only (wheel is PLAY/PAUSE).
        if (scan == 0 && !DeviceFeatures.isY2()
                && (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87)) {
            return true;
        }
        // Y2 no-scan: MEDIA_NEXT is always track (wheel is 103/108 → PLAY/PAUSE).
        if (scan == 0 && DeviceFeatures.isY2()
                && (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87)) {
            return true;
        }
        return false;
    }

    /** Physical track-prev (side) — scan 165; Y2 also 105. */
    public static boolean isTrackPrev(int keyCode, int scan) {
        if (scan == SIDE_PREV_SCAN) return true;
        if (DeviceFeatures.isY2() && scan == Y2_TRACK_PREV_SCAN) return true;
        if (scan == 0 && (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88)) {
            // Y1: side only. Y2: PREVIOUS is always track (wheel is PLAY/PAUSE).
            return true;
        }
        return false;
    }

    /**
     * 2026-07-14 — Scroll wheel vertical ticks.
     * Layman: the dial. Tech: MEDIA_PLAY/PAUSE; Y2 scans 103/108; Y1 scans 105/106.
     */
    public static boolean isWheelVertical(int keyCode, int scan) {
        if (scan == WHEEL_UP_SCAN_Y2 || scan == WHEEL_DOWN_SCAN_Y2) return true;
        // Y1 wheel shares 105/106 with Y2's track pair — only count as wheel on Y1.
        if (!DeviceFeatures.isY2()
                && (scan == Y2_TRACK_PREV_SCAN || scan == Y2_TRACK_NEXT_SCAN)) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == 19
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == 20;
    }

    /** @deprecated use {@link #isTrackNext(int, int)} */
    public static boolean isSideNext(int keyCode, int scan) {
        return isTrackNext(keyCode, scan);
    }

    /** @deprecated use {@link #isTrackPrev(int, int)} */
    public static boolean isSidePrev(int keyCode, int scan) {
        return isTrackPrev(keyCode, scan);
    }

    /** @deprecated use {@link #isWheelVertical(int, int)} */
    public static boolean isY1WheelVertical(int keyCode) {
        return isWheelVertical(keyCode, 0);
    }

    /**
     * 2026-07-14 — Rewrite event for portrait experiment; null = leave unchanged.
     * Track next → Back scan 0; track prev → PLAY_PAUSE; NP wheel → skip.
     */
    public static KeyEvent remapEvent(Context ctx, KeyEvent src, boolean nowPlaying) {
        if (src == null) return null;
        int code = src.getKeyCode();
        int scan = src.getScanCode();
        int outCode = code;
        int outScan = scan;
        if (isTrackNext(code, scan)) {
            outCode = KeyEvent.KEYCODE_BACK;
            outScan = 0;
        } else if (isTrackPrev(code, scan)) {
            outCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            outScan = 0;
        } else if (nowPlaying && isWheelVertical(code, scan)) {
            boolean up = scan == WHEEL_UP_SCAN_Y2
                    || (!DeviceFeatures.isY2() && scan == Y2_TRACK_PREV_SCAN)
                    || code == KeyEvent.KEYCODE_MEDIA_PLAY || code == 126
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == 19;
            outCode = up ? KeyEvent.KEYCODE_MEDIA_PREVIOUS : KeyEvent.KEYCODE_MEDIA_NEXT;
            // Keep wheel scancode so logs stay honest.
        } else {
            return null;
        }
        return new KeyEvent(src.getDownTime(), src.getEventTime(), src.getAction(),
                outCode, src.getRepeatCount(), src.getMetaState(),
                src.getDeviceId(), outScan, src.getFlags());
    }

    /**
     * 2026-07-14 — Pure matrix check for unit tests (no Context).
     * Layman: track next → Back; track prev → PP; NP wheel → skip; menus leave wheel alone.
     * @param y2 when true, scans 105/106 are track (not wheel).
     */
    static int remapKeyCodeForTest(int keyCode, int scan, boolean nowPlaying, boolean y2) {
        // Track next → Back
        if (scan == SIDE_NEXT_SCAN || (y2 && scan == Y2_TRACK_NEXT_SCAN)
                || (scan == 0 && (keyCode == 87 || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT))) {
            return KeyEvent.KEYCODE_BACK;
        }
        // Track prev → Play/Pause
        if (scan == SIDE_PREV_SCAN || (y2 && scan == Y2_TRACK_PREV_SCAN)
                || (scan == 0 && (keyCode == 88 || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS))) {
            return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        // NP wheel skip — Y1 105/106 or PLAY/PAUSE; Y2 103/108 or PLAY/PAUSE
        if (nowPlaying) {
            boolean wheelUp = keyCode == 126 || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                    || scan == WHEEL_UP_SCAN_Y2 || (!y2 && scan == Y2_TRACK_PREV_SCAN);
            boolean wheelDown = keyCode == 127 || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || scan == WHEEL_DOWN_SCAN_Y2 || (!y2 && scan == Y2_TRACK_NEXT_SCAN);
            if (wheelUp) return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            if (wheelDown) return KeyEvent.KEYCODE_MEDIA_NEXT;
        }
        // Menus: wheel unchanged (already PLAY/PAUSE on both families).
        return keyCode;
    }
}
