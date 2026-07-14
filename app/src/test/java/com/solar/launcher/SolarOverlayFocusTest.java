package com.solar.launcher;

import android.view.WindowManager;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * 2026-07-14 — Sole Solar :overlay is FOCUSABLE so wheel/Back hit KeyCapturingOverlayRoot.
 * Was: FLAG_NOT_FOCUSABLE (IPC-only). Companion chip escape hatch is separate.
 */
public class SolarOverlayFocusTest {

    @Test
    public void solarOverlayWindowFlags_areFocusableForWheel() {
        int flags = SolarOverlayService.globalOverlayWindowFlags();
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0);
    }
}
