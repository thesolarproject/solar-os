package com.solar.launcher;

import org.junit.Test;

/** Hearing safety display mapping — no Robolectric. */
public class HearingSafetyVolumeTest {

    @Test
    public void displayMaxIsAlways100() {
        if (HearingSafetyVolume.DISPLAY_MAX != 100) {
            throw new AssertionError("display max");
        }
    }

    @Test
    public void safetyOnCapsEffectiveMaxAt80Percent() {
        HearingSafetyVolume.resetWarnStateForTest();
        int eff = HearingSafetyVolume.effectiveMaxForTest(15, true);
        if (eff != 12) {
            throw new AssertionError("expected 12 got " + eff);
        }
    }

    @Test
    public void safetyOffUsesFullHardwareMax() {
        int eff = HearingSafetyVolume.effectiveMaxForTest(15, false);
        if (eff != 15) {
            throw new AssertionError("expected 15");
        }
    }

    @Test
    public void indexMapsToFullDisplayAtEffectiveMax() {
        HearingSafetyVolume.resetWarnStateForTest();
        int eff = 12;
        if (HearingSafetyVolume.indexToDisplayForTest(12, eff) != 100) {
            throw new AssertionError("top of capped range should read 100%");
        }
        if (HearingSafetyVolume.indexToDisplayForTest(15, 15) != 100) {
            throw new AssertionError("top of full range should read 100%");
        }
    }

    @Test
    public void displayRoundTrip() {
        int eff = 15;
        int idx = HearingSafetyVolume.displayToIndexForTest(100, eff);
        if (idx != 15) throw new AssertionError("100% -> max index");
        int display = HearingSafetyVolume.indexToDisplayForTest(idx, eff);
        if (display != 100) throw new AssertionError("max index -> 100%");
    }

    /** 2026-07-16 — Hearing Safety: HW index at 80% cap must paint as 100% on the bar. */
    @Test
    public void safetyOnEightyPercentHardwareIsFullBar() {
        HearingSafetyVolume.resetWarnStateForTest();
        int abs = 15;
        int eff = HearingSafetyVolume.effectiveMaxForTest(abs, true);
        // Cap index == 80% of 15 → 12
        if (eff != 12) throw new AssertionError("cap index " + eff);
        int displayAtCap = HearingSafetyVolume.indexToDisplayForTest(eff, eff);
        if (displayAtCap != 100) {
            throw new AssertionError("safety bar must read 100% at HW cap, got " + displayAtCap);
        }
        // Mid-cap ≈ 50% bar
        int mid = HearingSafetyVolume.indexToDisplayForTest(6, eff);
        if (mid < 45 || mid > 55) {
            throw new AssertionError("mid-cap display out of range: " + mid);
        }
    }

    /** 2026-07-16 — Safety off: full HW top is 100%; 80% of HW is only 80% of the bar. */
    @Test
    public void safetyOffEightyPercentHardwareIsNotFullBar() {
        int abs = 15;
        int eff = HearingSafetyVolume.effectiveMaxForTest(abs, false);
        int at80 = HearingSafetyVolume.indexToDisplayForTest(12, eff);
        if (at80 != 80) {
            throw new AssertionError("expected 80% display at index 12/15, got " + at80);
        }
        if (HearingSafetyVolume.indexToDisplayForTest(15, eff) != 100) {
            throw new AssertionError("full HW must be 100% when safety off");
        }
    }
}
