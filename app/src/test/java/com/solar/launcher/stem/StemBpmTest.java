package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * BPM + chop slice math for hold-stutter. 2026-07-19
 */
public class StemBpmTest {

    @Test
    public void chopSliceEighthAt120() {
        // 120 BPM → 2000 ms/bar → 1/8 = 250 ms. 2026-07-19
        int ms = StemBpm.chopSliceMs(120f, 2);
        assertEquals(250, ms);
    }

    @Test
    public void chopOffIsZero() {
        assertEquals(0, StemBpm.chopSliceMs(120f, 0));
    }

    @Test
    public void rateClamp() {
        assertEquals(1f, StemBpm.rateToMatch(120f, 120f), 0.001f);
        assertTrue(StemBpm.rateToMatch(120f, 200f) >= StemBpm.MIN_RATE);
        assertTrue(StemBpm.rateToMatch(200f, 100f) <= StemBpm.MAX_RATE);
    }

    @Test
    public void stutterHoldMsIsShort() {
        assertTrue(StemControls.STEM_STUTTER_HOLD_MS < 600L);
        assertTrue(StemControls.STEM_STUTTER_HOLD_MS >= 350L);
    }
}
