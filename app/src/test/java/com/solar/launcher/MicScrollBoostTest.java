package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.*;

/** Mic multiplies impulse only — never invents direction; never blocks live KEY. */
public class MicScrollBoostTest {

    @Test
    public void noBoostWithoutFreshKey() {
        MicScrollBoost b = new MicScrollBoost();
        b.onScratch(2f);
        assertEquals(1f, b.boost(1000L), 0.001f);
    }

    @Test
    public void boostWhenKeyFreshAndScratchFirm() {
        MicScrollBoost b = new MicScrollBoost();
        b.onHardwareNotch(1000L);
        b.onScratch(2f);
        float boost = b.boost(1050L);
        assertTrue(boost > 1f);
        assertTrue(boost <= MicScrollBoost.MAX_BOOST + 0.001f);
    }

    @Test
    public void wheelPhysicsMicBoostDoesNotFlipDirection() {
        WheelPhysics p = new WheelPhysics();
        WheelPhysics.Result r = new WheelPhysics.Result();
        p.setMicBoost(1.5f);
        p.tick(1_000_000_000L, 1, r);
        int stepsCw = r.rowSteps;
        p.reset();
        p.setMicBoost(1.5f);
        p.tick(2_000_000_000L, -1, r);
        assertTrue(stepsCw >= 1);
        assertEquals(stepsCw, r.rowSteps);
        int signed = p.signedMenuSteps(3_000_000_000L, -1, r);
        assertTrue(signed < 0);
    }

    @Test
    public void liveKeyNeverDropsGhostEvenWhenQuiet() {
        MicScrollBoost b = new MicScrollBoost();
        long t0 = 20_000L;
        b.onHardwareNotch(t0);
        b.onFeatures(0.01f, 0.01f, 0.01f);
        // Quiet room, fresh KEY — must not block
        assertFalse(b.shouldDropGhostScroll(t0 + 10));
        assertFalse(b.shouldDropGhostScroll(t0 + MicScrollBoost.LIVE_KEY_MS - 1));
    }

    @Test
    public void quietRoomWithoutContactNeverDropsGhost() {
        MicScrollBoost b = new MicScrollBoost();
        long t0 = 30_000L;
        b.onHardwareNotch(t0);
        b.onFeatures(0.01f, 0.01f, 0.01f);
        // No scrape ever — KEY-only scroll must keep working after 100ms
        assertFalse(b.shouldDropGhostScroll(t0 + 100));
        assertFalse(b.shouldDropGhostScroll(t0 + 200));
        assertFalse(b.shouldDropGhostScroll(t0 + MicScrollBoost.GHOST_WATCH_MS + 50));
    }

    @Test
    public void ghostScrollDropsOnlyAfterContactThenQuiet() {
        MicScrollBoost b = new MicScrollBoost();
        long t0 = 10_000L;
        b.onHardwareNotch(t0);
        b.onFeatures(0.8f, 0.6f, 0.7f);
        assertTrue(b.isFingerContact());
        assertFalse(b.shouldDropGhostScroll(t0 + 10));
        b.onFeatures(0.02f, 0.02f, 0.02f);
        assertFalse(b.isFingerContact());
        // After LIVE_KEY grace: first quiet poll arms timer, second after LIFT_QUIET drops.
        long tQuiet = t0 + MicScrollBoost.LIVE_KEY_MS + 10;
        assertFalse(b.shouldDropGhostScroll(tQuiet));
        assertTrue(b.shouldDropGhostScroll(tQuiet + MicScrollBoost.LIFT_QUIET_MS + 5));
    }

    @Test
    public void boostDecelsToOneWhenScratchDropsWhileKeyFresh() {
        MicScrollBoost b = new MicScrollBoost();
        long t0 = 50_000L;
        b.onHardwareNotch(t0);
        b.onFeatures(1.5f, 1.2f, 1.4f);
        float firm = b.boost(t0 + 20);
        assertTrue(firm > 1.1f);
        // Lift / quiet pad — decel: boost returns 1 even though KEY is still "fresh".
        b.onFeatures(0.01f, 0.01f, 0.01f);
        assertEquals(1f, b.boost(t0 + 40), 0.001f);
    }

    @Test
    public void liftQuietIsSnappyAfterLongSpin() {
        assertTrue(MicScrollBoost.LIFT_QUIET_MS <= 50L);
        assertTrue(MicScrollBoost.LIVE_KEY_MS <= 60L);
    }

    @Test
    public void outsideWatchWindowNeverDrops() {
        MicScrollBoost b = new MicScrollBoost();
        long t0 = 40_000L;
        b.onHardwareNotch(t0);
        b.onFeatures(0.8f, 0.6f, 0.7f);
        b.onFeatures(0.01f, 0.01f, 0.01f);
        assertFalse(b.shouldDropGhostScroll(t0 + MicScrollBoost.GHOST_WATCH_MS + 10));
    }
}
