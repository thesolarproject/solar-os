package com.solar.launcher.xposed.bridge;

import android.view.KeyEvent;

/** Y1/Y2 key codes for bridge hooks — keep aligned with {@link com.solar.launcher.Y1InputKeys}. */
final class Y1InputKeysBridge {

    private Y1InputKeysBridge() {}

    static boolean isWheelKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == 19 || keyCode == 20
                || keyCode == 126 || keyCode == 127;
    }

    /** Physical OK / enter — not wheel play/pause scancodes. */
    static boolean isCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == 66 || keyCode == 23;
    }

    static boolean isBackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == 4;
    }

    /** Side previous/next — mtk-kpd 165/163 → 88/87. */
    static boolean isTrackSkipKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88;
    }

    /** Dedicated play/pause — not wheel 126/127. */
    static boolean isPlayPauseKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == 79;
    }
}
