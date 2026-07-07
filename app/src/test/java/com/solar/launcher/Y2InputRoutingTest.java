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
    public void y2PowerKeyIsDedicatedSleepLockButton() {
        assertTrue(Y1InputKeys.isPowerKey(KeyEvent.KEYCODE_POWER));
        assertFalse(Y1InputKeys.isPowerKey(KeyEvent.KEYCODE_BACK));
        assertFalse(Y1InputKeys.isWheelKey(KeyEvent.KEYCODE_POWER));
    }

    @Test
    public void unifiedSideKeysUseMediaCodesOnly() {
        assertTrue(Y1InputKeys.isTrackPreviousKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        assertTrue(Y1InputKeys.isTrackNextKey(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertFalse(Y1InputKeys.isTrackPreviousKey(KeyEvent.KEYCODE_DPAD_LEFT));
        assertFalse(Y1InputKeys.isTrackNextKey(KeyEvent.KEYCODE_DPAD_RIGHT));
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

    @Test
    public void dispatchInterceptsWheelOnMenuNotPlayer() {
        assertTrue(MainActivity.shouldDispatchInterceptWheelForTest(
                0, STATE_WIFI_KEYBOARD, STATE_PLAYER, STATE_VIDEO_PLAYER, false));
        assertFalse(MainActivity.shouldDispatchInterceptWheelForTest(
                STATE_PLAYER, STATE_WIFI_KEYBOARD, STATE_PLAYER, STATE_VIDEO_PLAYER, false));
    }

    @Test
    public void dispatchInterceptsMediaSideExceptKeyboard() {
        assertTrue(MainActivity.shouldDispatchInterceptMediaSideForTest(0, STATE_WIFI_KEYBOARD));
        assertFalse(MainActivity.shouldDispatchInterceptMediaSideForTest(
                STATE_WIFI_KEYBOARD, STATE_WIFI_KEYBOARD));
    }
}
