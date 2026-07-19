package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Wheel must never classify as Mix/Stem PLAY pad.
 * 2026-07-19
 */
public class SolarPadKeysTest {

    @Test
    public void wheelMediaPlay_isNotPadPlay() {
        if (SolarPadKeys.isPadPlayKey(KeyEvent.KEYCODE_MEDIA_PLAY)) {
            throw new AssertionError("126 must not bind deck 3");
        }
        if (SolarPadKeys.isPadPlayKey(KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            throw new AssertionError("127 must not bind deck 3");
        }
    }

    @Test
    public void centerPlayPause_isPadPlay() {
        if (!SolarPadKeys.isPadPlayKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                && !SolarPadKeys.isPadPlayKey(85)) {
            throw new AssertionError("true Play must bind");
        }
        if (!SolarPadKeys.isPadPlayKey(85)) {
            throw new AssertionError("85 is pad play");
        }
    }
}
