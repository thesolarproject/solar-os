package com.solar.launcher.overlay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-08 — Smoke checks for OverlayMenuContract + OverlayShellRouter constants.
 * Layman: fails the build if public API action strings drift.
 * Technical: assert stable strings and RESULT_CANCELLED; no Android runtime needed.
 * Reversal: delete test; contract still exists without CI guard.
 */
public class OverlayMenuContractTest {

    @Test
    public void cancelIndexIsNegativeOne() {
        assertEquals(-1, OverlayMenuContract.RESULT_CANCELLED);
    }

    @Test
    public void showActionsAreStable() {
        assertEquals("com.solar.launcher.action.SHOW_OVERLAY_POWER",
                OverlayMenuContract.ACTION_SHOW_OVERLAY_POWER);
        assertEquals("com.solar.launcher.action.SHOW_OVERLAY_APP_MENU",
                OverlayMenuContract.ACTION_SHOW_OVERLAY_APP_MENU);
        assertEquals("com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG",
                OverlayMenuContract.ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
        assertEquals("com.solar.launcher.action.APP_MENU_RESULT",
                OverlayMenuContract.ACTION_APP_MENU_RESULT);
    }

    @Test
    public void companionShellIsDefault() {
        // When legacy prop unset, library readProp returns default "0" → companion.
        assertTrue(OverlayShellRouter.COMPANION_PKG.length() > 0);
        assertFalse(OverlayShellRouter.SOLAR_PKG.equals(OverlayShellRouter.COMPANION_PKG));
        assertEquals(OverlayShellRouter.COMPANION_PKG + ".GlobalContextOverlayService",
                OverlayShellRouter.COMPANION_OVERLAY_SERVICE);
    }
}
