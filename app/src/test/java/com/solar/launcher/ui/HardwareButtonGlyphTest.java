package com.solar.launcher.ui;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-18 — Smoke checks for HardwareButtonGlyph (no Android Context needed for colour hex / enum paths).
 */
public class HardwareButtonGlyphTest {

    @After
    public void tearDown() {
        HardwareButtonGlyph.clearCacheForTest();
    }

    @Test
    public void boundsForFontHeightPreservesAspect() {
        // Wide Prev-like 92×48 → height 14 → width ~27
        int[] wide = HardwareButtonGlyph.boundsForFontHeight(92, 48, 14);
        if (wide[1] != 14) throw new AssertionError("height locked to font: " + wide[1]);
        if (wide[0] < 20 || wide[0] > 30) throw new AssertionError("wide width: " + wide[0]);
        // Square OK 48×48 → 14×14
        int[] square = HardwareButtonGlyph.boundsForFontHeight(48, 48, 14);
        if (square[0] != 14 || square[1] != 14) {
            throw new AssertionError("square: " + square[0] + "x" + square[1]);
        }
        // Play/pause (chrome-stripped) ~92×48 → height 14 → width ~27 (same family as Prev)
        int[] pp = HardwareButtonGlyph.boundsForFontHeight(92, 48, 14);
        if (pp[1] != 14) throw new AssertionError("pp height");
        if (pp[0] < 20 || pp[0] > 30) throw new AssertionError("pp width: " + pp[0]);
        // Wider source still maps wider (aspect lock)
        int[] ppWide = HardwareButtonGlyph.boundsForFontHeight(133, 48, 14);
        if (ppWide[0] <= wide[0]) throw new AssertionError("wider source must stay wider");
        // Back chevron ~45×48 stays near-square (not squashed to a strip)
        int[] back = HardwareButtonGlyph.boundsForFontHeight(45, 48, 14);
        if (back[1] != 14) throw new AssertionError("back height");
        if (back[0] < 12 || back[0] > 15) throw new AssertionError("back width: " + back[0]);
    }

    @Test
    public void buttonAssetPathsAreY1Pngs() {
        for (HardwareButtonGlyph.Button b : HardwareButtonGlyph.Button.values()) {
            if (b.assetPath == null || !b.assetPath.startsWith("y1/btn_") || !b.assetPath.endsWith(".png")) {
                throw new AssertionError("bad path: " + b + " → " + b.assetPath);
            }
        }
        if (!"y1/btn_back.png".equals(HardwareButtonGlyph.Button.BACK.assetPath)) {
            throw new AssertionError("BACK path");
        }
        if (!"y1/btn_ok.png".equals(HardwareButtonGlyph.Button.OK.assetPath)) {
            throw new AssertionError("OK path");
        }
        if (!"y1/btn_wheel.png".equals(HardwareButtonGlyph.Button.WHEEL.assetPath)) {
            throw new AssertionError("WHEEL path");
        }
    }

    @Test
    public void loadRawNullContextFailsOpen() {
        if (HardwareButtonGlyph.loadRaw(null, HardwareButtonGlyph.Button.OK) != null) {
            throw new AssertionError("null context must fail-open");
        }
        if (HardwareButtonGlyph.tintedDrawable(null, HardwareButtonGlyph.Button.OK, 0xFFFFFFFF, 14) != null) {
            throw new AssertionError("null tint must fail-open");
        }
    }
}
