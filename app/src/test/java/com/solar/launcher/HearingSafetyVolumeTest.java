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
}
