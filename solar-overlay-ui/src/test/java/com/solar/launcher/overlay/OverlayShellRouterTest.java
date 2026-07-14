package com.solar.launcher.overlay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-14 — Solar ThemedContextMenu is sole shell by default (matches Xposed forwarder).
 * Host unit tests: SystemProperties missing → readProp returns defaults in OverlayShellRouter.
 */
public class OverlayShellRouterTest {

    @Test
    public void defaultUsesSolarThemedShell() {
        // No android.os.SystemProperties on host JVM → companion_shell default 0 → Solar.
        assertFalse(OverlayShellRouter.useCompanionShell());
        assertEquals(OverlayShellRouter.SOLAR_PKG, OverlayShellRouter.overlayPackage());
        assertEquals(OverlayShellRouter.SOLAR_OVERLAY_SERVICE,
                OverlayShellRouter.overlayServiceClass());
        assertTrue(OverlayShellRouter.overlayComponent() != null);
    }

    @Test
    public void companionPackageConstantsStable() {
        assertFalse(OverlayShellRouter.SOLAR_PKG.equals(OverlayShellRouter.COMPANION_PKG));
        assertTrue(OverlayShellRouter.COMPANION_OVERLAY_SERVICE
                .startsWith(OverlayShellRouter.COMPANION_PKG));
        assertTrue(OverlayShellRouter.SOLAR_OVERLAY_SERVICE
                .startsWith(OverlayShellRouter.SOLAR_PKG));
    }

    @Test
    public void peerIsOtherShellWhenSolarPrimary() {
        // Default Solar → peer is companion Chip (so dismissPeer cannot hit Solar).
        assertEquals(OverlayShellRouter.COMPANION_PKG, OverlayShellRouter.peerOverlayPackage());
        assertEquals(OverlayShellRouter.COMPANION_OVERLAY_SERVICE,
                OverlayShellRouter.peerOverlayServiceClass());
        assertFalse(OverlayShellRouter.peerOverlayPackage()
                .equals(OverlayShellRouter.overlayPackage()));
    }
}
