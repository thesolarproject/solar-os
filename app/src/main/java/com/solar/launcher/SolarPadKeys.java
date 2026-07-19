package com.solar.launcher;

import android.view.KeyEvent;

/**
 * Key classification for Stem/Mix assign — wheel must never equal Play-pad.
 * Layman: scrollwheel scrolls lists; the center Play glyph binds deck 3.
 * Technical: Y1 wheel = MEDIA_PLAY/PAUSE 126/127; true Play = 85 only.
 * Was: KEYCODE_MEDIA_PLAY treated as Mix deck-3. Reversal: that OR.
 * 2026-07-19
 */
public final class SolarPadKeys {
    private SolarPadKeys() {}

    /** Hardware Play/Pause (center OK on Y1 wheel press) — not scroll ticks. */
    public static boolean isTruePlayPause(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86;
    }

    /** Y1/Y2 scrollwheel codes — never Mix/Stem PLAY pad. */
    public static boolean isScrollWheel(int keyCode) {
        return Y1InputKeys.isWheelKey(keyCode);
    }

    /** Mix deck-3 / Stem melody pad — true Play only (not wheel 126). */
    public static boolean isPadPlayKey(int keyCode) {
        if (isScrollWheel(keyCode)) return false;
        return isTruePlayPause(keyCode);
    }
}
