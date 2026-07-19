package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Stem Player control math — gain under one wheel turn + Gen1 bar ladder.
 * 2026-07-18
 */
public class StemControlsTest {

    @Test
    public void gainFullScaleWithinOneWheelTurn() {
        float g = 0f;
        for (int i = 0; i < StemControls.GAIN_CLICKS_FULL; i++) {
            g = StemControls.nudgeGain(g, 1);
        }
        assertEquals(1f, g, 0.001f);
        assertTrue("full scale needs ≤16 clicks", StemControls.GAIN_CLICKS_FULL <= 16);
    }

    @Test
    public void gainNudgeDownClamps() {
        assertEquals(0f, StemControls.nudgeGain(0f, -3), 0.001f);
        assertEquals(1f, StemControls.nudgeGain(1f, 5), 0.001f);
    }

    @Test
    public void loopBarLadderCoversGen1Steps() {
        assertEquals(0.25f, StemControls.LOOP_BARS[0], 0.001f);
        assertEquals(8f, StemControls.LOOP_BARS[StemControls.LOOP_BARS.length - 1], 0.001f);
        float b = 0.25f;
        // Six steps up from 0.25 → 8 in ≤6 clicks (under one turn).
        for (int i = 0; i < 5; i++) {
            b = StemControls.nudgeLoopBars(b, 1);
        }
        assertEquals(8f, b, 0.001f);
    }

    @Test
    public void loopIndexFindsClosest() {
        assertEquals(2, StemControls.loopIndexForBars(1f));
        assertEquals(0, StemControls.loopIndexForBars(0.2f));
    }

    @Test
    public void dotsForGainAndLoop() {
        assertEquals(0, StemControls.dotsForGain(0f, 8));
        assertEquals(8, StemControls.dotsForGain(1f, 8));
        assertTrue(StemControls.dotsForLoopBars(1f, 8) >= 1);
        assertEquals(8, StemControls.dotsForLoopBars(8f, 8));
        // Hardware face: 4 LEDs per arm.
        assertEquals(4, StemFaceView.DOTS_PER_ARM);
        assertEquals(4, StemControls.dotsForGain(1f, StemFaceView.DOTS_PER_ARM));
        assertEquals(0, StemControls.dotsForGain(0f, StemFaceView.DOTS_PER_ARM));
    }

    @Test
    public void sessionInactiveByDefault() {
        assertFalse(StemPlayerHost.isSessionActive());
    }

    /** CW (raw wheel-down = -1) raises volume and shortens loop. 2026-07-19 */
    @Test
    public void wheelPolarityCwLouderShorterLoop() {
        assertEquals(1, StemControls.volumeStepsFromWheel(-1));
        assertEquals(-1, StemControls.volumeStepsFromWheel(1));
        assertEquals(-1, StemControls.loopStepsFromWheel(-1));
        assertEquals(1, StemControls.loopStepsFromWheel(1));
        assertEquals(0.5f, StemControls.nudgeLoopBars(1f, StemControls.loopStepsFromWheel(-1)), 0.001f);
        assertEquals(2f, StemControls.nudgeLoopBars(1f, StemControls.loopStepsFromWheel(1)), 0.001f);
        assertEquals(StemControls.GAIN_STEP,
                StemControls.nudgeGain(0f, StemControls.volumeStepsFromWheel(-1)), 0.001f);
    }

    /** Per-stem focus: non-looping stems open in volume; loop-ctrl stems keep loop wheel. 2026-07-19 */
    @Test
    public void wheelModeFollowsPerStemLoopCtrl() {
        assertFalse(StemControls.wheelLoopModeForStem(true, false));
        assertTrue(StemControls.wheelLoopModeForStem(true, true));
        assertFalse(StemControls.wheelLoopModeForStem(false, true));
        assertTrue(StemControls.centerShouldStopLoop(true, true));
        assertFalse(StemControls.centerShouldStopLoop(true, false));
        assertFalse(StemControls.centerShouldStopLoop(false, true));
    }

    /** Hold-Center peeks volume; release returns to loop wheel. 2026-07-19 */
    @Test
    public void holdCenterVolumePeekMath() {
        assertTrue(StemControls.centerHoldVolumeEligible(true, true));
        assertFalse(StemControls.centerHoldVolumeEligible(true, false));
        assertFalse(StemControls.centerHoldVolumeEligible(false, true));
        assertTrue(StemControls.wheelUsesVolume(true, true));
        assertFalse(StemControls.wheelUsesVolume(true, false));
        assertTrue(StemControls.wheelUsesVolume(false, false));
        // Focused arm volume dots while adjusting gain (not loop bars). 2026-07-19
        assertFalse(StemControls.faceShowsLoopBars(false, false));
        assertTrue(StemControls.faceShowsLoopBars(true, false));
        assertFalse(StemControls.faceShowsLoopBars(true, true));
    }
}
