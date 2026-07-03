package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Y2InputRoutingTest {

    private static final int STATE_FLOW = 50;
    private static final int STATE_WIFI_KEYBOARD = 7;
    private static final int STATE_PLAYER = 2;
    private static final int STATE_VIDEO_PLAYER = 60;
    private static final int STATE_SETTINGS = 5;

    @Test
    public void y2DpadMapsToMediaPrevNext() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        assertTrue(Y1InputKeys.isY2TrackPreviousKey(KeyEvent.KEYCODE_DPAD_LEFT));
        assertTrue(Y1InputKeys.isY2TrackNextKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void shouldRouteSkipOnSettingsWithQueueAndMusic() {
        assertTrue(MainActivity.shouldRouteMediaSkipKeysForTest(
                true, STATE_SETTINGS, STATE_FLOW, STATE_WIFI_KEYBOARD,
                STATE_PLAYER, STATE_VIDEO_PLAYER,
                false, true, false, false));
    }

    @Test
    public void shouldRouteSkipOnKeyboardWithQueue() {
        assertTrue(MainActivity.shouldRouteMediaSkipKeysForTest(
                true, STATE_WIFI_KEYBOARD, STATE_FLOW, STATE_WIFI_KEYBOARD,
                STATE_PLAYER, STATE_VIDEO_PLAYER,
                false, false, false, false));
    }

    @Test
    public void shouldNotRouteSkipOnFlow() {
        assertFalse(MainActivity.shouldRouteMediaSkipKeysForTest(
                true, STATE_FLOW, STATE_FLOW, STATE_WIFI_KEYBOARD,
                STATE_PLAYER, STATE_VIDEO_PLAYER,
                true, true, false, false));
    }

    @Test
    public void shouldNotRouteSkipWithoutQueue() {
        assertFalse(MainActivity.shouldRouteMediaSkipKeysForTest(
                false, STATE_SETTINGS, STATE_FLOW, STATE_WIFI_KEYBOARD,
                STATE_PLAYER, STATE_VIDEO_PLAYER,
                false, true, false, false));
    }

    @Test
    public void shouldRouteSkipOnNowPlaying() {
        assertTrue(MainActivity.shouldRouteMediaSkipKeysForTest(
                true, STATE_PLAYER, STATE_FLOW, STATE_WIFI_KEYBOARD,
                STATE_PLAYER, STATE_VIDEO_PLAYER,
                false, false, false, false));
    }
}
