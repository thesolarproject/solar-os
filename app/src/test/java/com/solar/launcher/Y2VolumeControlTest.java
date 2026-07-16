package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Y2 Now Playing volume must step via MediaVolumeControl, not early-return no-op. */
public class Y2VolumeControlTest {

    @Test
    public void powerRowCountUnchangedForSolarOnly() {
        assertEquals(2, PowerMenuRowCatalog.powerRowCountForTest());
    }

    @Test
    public void volumeKeyCodesMatchY2Keylayout() {
        assertFalse(Y1InputKeys.isWheelUp(android.view.KeyEvent.KEYCODE_VOLUME_UP));
        assertFalse(Y1InputKeys.isWheelDown(android.view.KeyEvent.KEYCODE_VOLUME_DOWN));
        assertTrue(Y1InputKeys.isVolumeUpKey(android.view.KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(Y1InputKeys.isVolumeDownKey(android.view.KeyEvent.KEYCODE_VOLUME_DOWN));
    }

    @Test
    public void y2WheelUsesMediaPlayPauseKeycodes() {
        assertTrue(Y1InputKeys.isWheelUp(126));
        assertTrue(Y1InputKeys.isWheelDown(127));
    }

    /** 2026-07-16 — NP pulse always uses 0–100 display max (not raw stream steps). */
    @Test
    public void displayMaxIsAlwaysHundred() {
        assertEquals(100, HearingSafetyVolume.DISPLAY_MAX);
    }
}
