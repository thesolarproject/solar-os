package com.solar.launcher.globalcontext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-10 — Host-side smoke for ThemeReader constants / resolve helpers.
 * Full sidecar parse needs device storage; keep contract guards here.
 */
public class ThemeReaderTest {

    @Test
    public void fallbacksMatchAuraishPalette() {
        assertEquals(0xFF1A1A1A, ThemeReader.FALLBACK_BG);
        assertEquals(0xFFFFFFFF, ThemeReader.FALLBACK_FG);
        assertEquals(0xFF4A90D9, ThemeReader.FALLBACK_ACCENT);
        assertEquals(0xEE252528, ThemeReader.FALLBACK_PANEL);
    }

    @Test
    public void resolveSidecarNullNameIsNull() {
        assertNull(ThemeReader.resolveSidecar(null));
        assertNull(ThemeReader.resolveSidecar(""));
    }

    @Test
    public void resolveSidecarMissingFileIsNullOnHost() {
        // Host JVM has no /storage/sdcard* sidecars — expect miss.
        assertNull(ThemeReader.resolveSidecar("theme-skin-does-not-exist-xyz.json"));
    }

    @Test
    public void hasSidecarThemeFalseUntilRefreshFindsFiles() {
        // Without Android storage, refresh cannot load sidecars on host.
        ThemeReader.refresh(null);
        assertFalse(ThemeReader.hasSidecarTheme());
        assertEquals(ThemeReader.FALLBACK_BG, ThemeReader.backgroundColor());
        assertEquals(ThemeReader.FALLBACK_FG, ThemeReader.foregroundColor());
        assertEquals(ThemeReader.FALLBACK_ACCENT, ThemeReader.accentColor());
        assertTrue(ThemeReader.panelColor() != 0);
    }
}
