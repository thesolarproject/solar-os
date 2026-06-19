package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.After;
import org.junit.Test;

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
}
