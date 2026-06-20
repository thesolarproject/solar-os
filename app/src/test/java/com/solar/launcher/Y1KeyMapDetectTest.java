package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Y1KeyMapDetectTest {

    private static Y1KeyMap.KlLines lines(String l103, String l105) {
        return lines(l103, l105, null);
    }

    private static Y1KeyMap.KlLines lines(String l103, String l105, String l114) {
        Y1KeyMap.KlLines k = new Y1KeyMap.KlLines();
        k.l103 = l103;
        k.l105 = l105;
        k.l114 = l114;
        return k;
    }

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

    @Test
    public void classifyRockboxKlGeneric() {
        Y1KeyMap.KlLines mtk = lines("key 103   DPAD_UP", "key 105   DPAD_LEFT");
        Y1KeyMap.KlLines gen = lines("key 103   MEDIA_PLAY", "key 105   MEDIA_PREVIOUS",
                "key 114   DPAD_UP");
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_ROM, Y1KeyMap.classifyLayoutFromKlFiles(mtk, gen));
    }

    @Test
    public void classifyStockWhenGenericHasMediaPrevious() {
        Y1KeyMap.KlLines mtk = lines("key 103   DPAD_UP", "key 105   DPAD_LEFT");
        Y1KeyMap.KlLines gen = lines("key 103   DPAD_LEFT", "key 105   MEDIA_PREVIOUS");
        assertEquals(Y1KeyMap.LAYOUT_STOCK, Y1KeyMap.classifyLayoutFromKlFiles(mtk, gen));
    }

    @Test
    public void classifyRockboxWhenBothAgree() {
        Y1KeyMap.KlLines mtk = lines("key 103   DPAD_UP", "key 105   DPAD_LEFT");
        Y1KeyMap.KlLines gen = lines("key 103   DPAD_UP", "key 105   DPAD_LEFT");
        assertEquals(Y1KeyMap.LAYOUT_ROCKBOX_CLASSIC,
                Y1KeyMap.classifyLayoutFromKlFiles(mtk, gen));
    }
}
