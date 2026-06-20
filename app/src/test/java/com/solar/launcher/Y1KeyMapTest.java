package com.solar.launcher;

import android.view.InputDevice;
import android.view.KeyEvent;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Y1KeyMapTest {

    @After
    public void tearDown() {
        Y1KeyMap.resetLayoutCacheForTest();
    }

    @Test
    public void stockWheelAndMediaKeys() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_STOCK);
        // Without scancode, DPAD_LEFT/RIGHT map to media previous/next:
        assertFalse(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_LEFT, false));
        assertFalse(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_RIGHT, false));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, false));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_DPAD_RIGHT, false));

        // With scancode, scroll wheel works on Stock:
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, false));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_RIGHT, Y1KeyMap.SCAN_WHEEL_CW, false));
        assertFalse(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, false));

        // Trackpad buttons work:
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_MEDIA_NEXT, false));
        assertFalse(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PLAY, false));
    }

    @Test
    public void rockboxRomWheelAndSkip() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_ROM);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PLAY, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_MEDIA_PAUSE, true));
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PLAY, Y1KeyMap.SCAN_WHEEL_CCW, true));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_MEDIA_NEXT, true));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Y1KeyMap.SCAN_PREV, true));
    }

    @Test
    public void rockboxSideloadSwappedKeys() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_SIDELoad);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_MEDIA_NEXT, true));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_DPAD_RIGHT, true));
    }

    @Test
    public void rockboxClassicBaseKeys() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_UP, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_DOWN, true));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_DPAD_RIGHT, true));
    }

    @Test
    public void rockboxHardwareWorksWhenPrefOff() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_ROM);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PLAY, false));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_MEDIA_PAUSE, false));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_DPAD_LEFT, false));
    }

    @Test
    public void scancodeWheelWorksWhenGenericKlWrong() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_RIGHT, Y1KeyMap.SCAN_WHEEL_CW, true));
        assertFalse(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, true));
    }

    @Test
    public void scancodeSkipStockAndRockbox() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_STOCK);
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Y1KeyMap.SCAN_PREV, false));
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_ROM);
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Y1KeyMap.SCAN_PREV, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_MEDIA_NEXT, Y1KeyMap.SCAN_NEXT, true));
    }

    @Test
    public void runtimeHintOverridesStockFileDetect() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_STOCK);
        Y1KeyMap.setRuntimeLayoutHintForTest(Y1KeyMap.LAYOUT_ROCKBOX_ROM);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_MEDIA_PLAY, false));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, false));
    }

    @Test
    public void noteHardwareKeyWheel126SetsRockboxHint() {
        assertTrue(Y1KeyMap.noteHardwareKeyInput(InputDevice.SOURCE_KEYBOARD,
                Y1KeyMap.SCAN_WHEEL_CCW, KeyEvent.KEYCODE_MEDIA_PLAY));
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_ROM, Y1KeyMap.getRuntimeLayoutHintForTest());
    }

    @Test
    public void noteHardwareKeyWheelLeftSetsStockHint() {
        assertTrue(Y1KeyMap.noteHardwareKeyInput(InputDevice.SOURCE_KEYBOARD,
                Y1KeyMap.SCAN_WHEEL_CCW, KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(Y1KeyMap.LAYOUT_STOCK, Y1KeyMap.getRuntimeLayoutHintForTest());
    }

    @Test
    public void noteHardwareKeyIgnoresBtMediaSource() {
        Y1KeyMap.setRuntimeLayoutHintForTest(-1);
        final int sourceMedia = 0x00000400;
        assertFalse(Y1KeyMap.noteHardwareKeyInput(sourceMedia,
                Y1KeyMap.SCAN_NEXT, KeyEvent.KEYCODE_MEDIA_NEXT));
        assertEquals(-1, Y1KeyMap.getRuntimeLayoutHintForTest());
    }
}
