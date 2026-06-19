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
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_LEFT, false));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_RIGHT, false));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_MEDIA_NEXT, false));
        assertFalse(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, false));
        assertFalse(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_UP, false));
    }

    @Test
    public void rockboxRomWheelAndSkip() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_ROM);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_UP, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_DOWN, true));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_DPAD_RIGHT, true));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_DPAD_LEFT, true));
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
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_UP, false));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_DOWN, false));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_DPAD_LEFT, false));
    }

    @Test
    public void scancodeWheelWorksWhenGenericKlWrong() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, true));
        assertTrue(Y1KeyMap.isWheelDown(KeyEvent.KEYCODE_DPAD_RIGHT, Y1KeyMap.SCAN_WHEEL_CW, true));
        assertFalse(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_WHEEL_CCW, true));
        assertFalse(Y1KeyMap.isWheelKey(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_PREV, true));
    }

    @Test
    public void scancodeSkipStockAndRockbox() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_STOCK);
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Y1KeyMap.SCAN_PREV, false));
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, Y1KeyMap.SCAN_PREV, true));
        assertTrue(Y1KeyMap.isMediaNext(KeyEvent.KEYCODE_DPAD_RIGHT, Y1KeyMap.SCAN_NEXT, true));
    }

    @Test
    public void runtimeHintOverridesStockFileDetect() {
        Y1KeyMap.setLayoutForTest(Y1KeyMap.LAYOUT_STOCK);
        Y1KeyMap.setRuntimeLayoutHintForTest(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC);
        assertTrue(Y1KeyMap.isWheelUp(KeyEvent.KEYCODE_DPAD_UP, false));
        assertTrue(Y1KeyMap.isMediaPrevious(KeyEvent.KEYCODE_DPAD_LEFT, false));
    }

    @Test
    public void noteHardwareKeyWheelUpSetsRockboxHint() {
        KeyEvent ev = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP,
                0, 0, 0, Y1KeyMap.SCAN_WHEEL_CCW, 0, InputDevice.SOURCE_KEYBOARD);
        assertTrue(Y1KeyMap.noteHardwareKey(ev));
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC, Y1KeyMap.getRuntimeLayoutHintForTest());
    }

    @Test
    public void noteHardwareKeyWheelLeftSetsStockHint() {
        KeyEvent ev = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                0, 0, 0, Y1KeyMap.SCAN_WHEEL_CCW, 0, InputDevice.SOURCE_KEYBOARD);
        assertTrue(Y1KeyMap.noteHardwareKey(ev));
        assertEquals(Y1KeyMap.LAYOUT_STOCK, Y1KeyMap.getRuntimeLayoutHintForTest());
    }

    @Test
    public void noteHardwareKeyIgnoresBtMediaSource() {
        Y1KeyMap.setRuntimeLayoutHintForTest(-1);
        KeyEvent ev = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT,
                0, 0, 0, 0, 0, InputDevice.SOURCE_MEDIA);
        assertFalse(Y1KeyMap.noteHardwareKey(ev));
        assertEquals(-1, Y1KeyMap.getRuntimeLayoutHintForTest());
    }
}
