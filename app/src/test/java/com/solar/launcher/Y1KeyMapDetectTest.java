package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Y1KeyMapDetectTest {

    @Test
    public void classifyStockMtkKpd() {
        assertEquals(Y1KeyMap.LAYOUT_STOCK,
                Y1KeyMap.classifyMtkLines("key 103   DPAD_LEFT", "key 105   MEDIA_PREVIOUS    WAKE"));
    }

    @Test
    public void classifyRockboxRomMtkKpd() {
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_ROM,
                Y1KeyMap.classifyMtkLines("key 103   DPAD_LEFT           WAKE",
                        "key 105   DPAD_UP             WAKE"));
    }

    @Test
    public void classifyRockboxSideloadSwap() {
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_SIDELoad,
                Y1KeyMap.classifyMtkLines("key 103   MEDIA_PREVIOUS    WAKE",
                        "key 105   DPAD_LEFT           WAKE"));
    }

    @Test
    public void classifyRockboxClassicBase() {
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC,
                Y1KeyMap.classifyMtkLines("key 103   DPAD_UP", "key 105   DPAD_LEFT"));
    }
}
