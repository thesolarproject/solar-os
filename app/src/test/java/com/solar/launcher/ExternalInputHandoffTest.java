package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalInputHandoffTest {

    @Test
    public void androidModeMapsY1MediaKeysToDpad() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PAUSE, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_NEXT, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_ANDROID));
    }

    @Test
    public void offModeDoesNotMapKeys() {
        assertEquals(0, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_OFF));
    }

    @Test
    public void fmModeKeepsRockboxY1SpecialMapping() {
        assertEquals(KeyEvent.KEYCODE_VOLUME_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PAUSE, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1, ExternalInputHandoff.MODE_FM));
    }

    @Test
    public void onlyMediaNavigationKeysAreEligible() {
        assertTrue(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_MEDIA_PLAY));
        assertTrue(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertFalse(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_DPAD_UP));
        assertFalse(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_BACK));
    }

    /** Handoff mapping is device-agnostic — Y1 and Y2 share the same MEDIA→DPAD table. */
    @Test
    public void androidHandoffWorksForBothDeviceFamilies() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_ANDROID));
        DeviceFeatures.setCachedFamilyForTest("y1");
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, ExternalInputHandoff.MODE_ANDROID));
        DeviceFeatures.resetCacheForTest();
    }
}
