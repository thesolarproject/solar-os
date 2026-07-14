package com.solar.launcher.globalcontext;

import com.solar.input.policy.GlobalInputPolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-10 — Documents {@link GlobalInputCoordinatorService} fg re-resolve at modal fire.
 * Layman: when the menu opens, trust the real app on screen — not a stale SystemUI probe.
 * Mirrors private {@code resolveForegroundAtFire} without Android ActivityManager.
 */
public final class ForegroundResolutionContractTest {

    /** Same precedence as GlobalInputCoordinatorService.resolveForegroundAtFire. */
    private static String resolveAtFire(String cached, String live) {
        if (live != null && live.length() > 0
                && !GlobalInputPolicy.isSystemShellPackage(live)) {
            return live;
        }
        if (GlobalInputPolicy.shouldFailOpenPowerFg(live)) {
            return live != null ? live : cached;
        }
        if (cached != null && cached.length() > 0
                && !GlobalInputPolicy.isSystemShellPackage(cached)) {
            return cached;
        }
        return live != null ? live : cached;
    }

    @Test
    public void liveNonShellWinsOverCachedSystemUi() {
        assertEquals("com.android.settings",
                resolveAtFire("com.android.systemui", "com.android.settings"));
    }

    @Test
    public void systemUiLiveFailsOpenButPowerModalIsSolarOnly() {
        // Coordinator may still resolve systemui; POWER quick menu no longer offered there.
        assertEquals("com.android.systemui",
                resolveAtFire("org.rockbox", "com.android.systemui"));
        assertFalse(GlobalInputPolicy.shouldOfferPowerLongModal(
                resolveAtFire("org.rockbox", "com.android.systemui"), true));
        assertTrue(GlobalInputPolicy.shouldLaunchSolarOnBackLong(
                resolveAtFire("org.rockbox", "com.android.systemui"), false, false));
    }

    @Test
    public void cachedRockboxUsedWhenLiveProbeNull() {
        assertEquals("org.rockbox", resolveAtFire("org.rockbox", null));
    }
}
