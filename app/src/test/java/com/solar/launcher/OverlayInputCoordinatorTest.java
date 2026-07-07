package com.solar.launcher;

import android.view.KeyEvent;

/** Unit checks for tier-2 overlay root key shell and coordinator gates. */
public final class OverlayInputCoordinatorTest {

    public static void main(String[] args) {
        if (!OverlayKeyGate.isOverlayNavigationKey(KeyEvent.KEYCODE_DPAD_LEFT)) {
            throw new AssertionError("wheel nav key");
        }
        if (GlobalOverlayTriggerMain.scancodeToKeyCode(105) != KeyEvent.KEYCODE_MEDIA_PLAY) {
            throw new AssertionError("wheel up scancode");
        }
        System.out.println("OverlayInputCoordinatorTest ok");
    }
}
