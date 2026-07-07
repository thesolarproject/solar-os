package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** input_event layout must match {@code 99Y1ButtonScript} / mtk-kpd on Y1. */
public class GlobalOverlayTriggerMainTest {

    @Test
    public void backScancodeAtOffset10() {
        byte[] ev = new byte[16];
        ev[8] = 1; // EV_KEY
        ev[10] = (byte) 158; // KEY_BACK scancode
        ev[12] = 1; // value down
        assertEquals(158, GlobalOverlayTriggerMain.readEventCode(ev));
        assertEquals(1, GlobalOverlayTriggerMain.readEventValue(ev));
    }

    /** Y1 center/back scancodes map to overlay key codes. */
    @Test
    public void scancodeToKeyCode_mapsBackAndCenter() {
        assertEquals(4, GlobalOverlayTriggerMain.scancodeToKeyCode(158));
        assertEquals(85, GlobalOverlayTriggerMain.scancodeToKeyCode(164));
    }

    /** Wheel scancodes 105/106 → media play/pause for NOT_FOCUSABLE overlay navigation. */
    @Test
    public void scancodeToKeyCode_mapsWheelToMediaKeys() {
        assertEquals(126, GlobalOverlayTriggerMain.scancodeToKeyCode(105));
        assertEquals(127, GlobalOverlayTriggerMain.scancodeToKeyCode(106));
    }

    /** Y2 power scancode — matches Y2-Rockbox.kl key 116. */
    @Test
    public void powerScancodeAtOffset10() {
        byte[] ev = new byte[16];
        ev[8] = 1;
        ev[10] = (byte) 116;
        ev[12] = 1;
        assertEquals(116, GlobalOverlayTriggerMain.readEventCode(ev));
    }

    /** Side keys map to DPAD for horizontal quick-bar focus. */
    @Test
    public void scancodeToKeyCode_mapsSideKeysToDpad() {
        assertEquals(21, GlobalOverlayTriggerMain.scancodeToKeyCode(165));
        assertEquals(22, GlobalOverlayTriggerMain.scancodeToKeyCode(163));
        assertEquals(0, GlobalOverlayTriggerMain.scancodeToKeyCode(999));
    }
}
