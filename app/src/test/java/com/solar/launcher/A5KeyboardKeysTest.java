package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-14 — A5 keyboard volume/Back map while typing tray is open.
 */
public class A5KeyboardKeysTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void selfCheck() {
        A5KeyboardKeys.selfCheck();
    }

    @Test
    public void faceMidRemainsSelectNotEnter() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (A5KeyboardKeys.isEnterBackKey(KeyEvent.KEYCODE_BACK, A5InputKeys.FACE_MIDDLE_SCAN)) {
            throw new AssertionError("face mid must not be enter-back");
        }
        if (A5KeyboardKeys.remapForIme(KeyEvent.KEYCODE_BACK, A5InputKeys.FACE_MIDDLE_SCAN)
                != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("face mid → center for IME");
        }
    }

    @Test
    public void sidePowerAndScan0BackAreEnter() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!A5KeyboardKeys.isEnterBackKey(KeyEvent.KEYCODE_BACK, 0)) {
            throw new AssertionError("scan0 back = enter");
        }
        if (!A5KeyboardKeys.isEnterBackKey(A5InputKeys.SIDE_POWER, A5InputKeys.SIDE_POWER_SCAN)) {
            throw new AssertionError("side power = enter before remap");
        }
    }

    @Test
    public void volumeMapsOnlyOnA5() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!A5KeyboardKeys.isSpaceKey(KeyEvent.KEYCODE_VOLUME_UP)
                || !A5KeyboardKeys.isDeleteKey(KeyEvent.KEYCODE_VOLUME_DOWN)) {
            throw new AssertionError("a5 vol map");
        }
        DeviceFeatures.setCachedFamilyForTest("y2");
        if (A5KeyboardKeys.isSpaceKey(KeyEvent.KEYCODE_VOLUME_UP)
                || A5KeyboardKeys.isDeleteKey(KeyEvent.KEYCODE_VOLUME_DOWN)) {
            throw new AssertionError("y2 must keep volume for loudness");
        }
    }
}
