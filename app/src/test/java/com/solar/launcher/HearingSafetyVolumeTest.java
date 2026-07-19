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

    /**
     * 2026-07-18 — Temp unlock re-maps same HW index from display 100% → ~80%.
     * Layman: ear unlock keeps loudness, opens headroom above on the bar.
     */
    @Test
    public void tempUnlockRemapsCapIndexToEightyDisplay() {
        HearingSafetyVolume.resetWarnStateForTest();
        int abs = 15;
        int capIdx = HearingSafetyVolume.effectiveMaxForTest(abs, true); // 12
        // Under cap: 12/12 → 100%
        if (HearingSafetyVolume.indexToDisplayForTest(capIdx, capIdx) != 100) {
            throw new AssertionError("at cap with HS on should be 100%");
        }
        // After temp unlock scale is full abs: 12/15 → 80%
        int unlockedDisplay = HearingSafetyVolume.indexToDisplayForTest(capIdx, abs);
        if (unlockedDisplay != 80) {
            throw new AssertionError("temp unlock display expected 80 got " + unlockedDisplay);
        }
    }

    /**
     * 2026-07-18 — Speaker never gets the 80% Hearing Safety cap (pref may still be on).
     */
    @Test
    public void speakerRouteNeverCapsEvenWhenSafetyPrefOn() {
        HearingSafetyVolume.resetWarnStateForTest();
        int abs = 15;
        int speakerEff = HearingSafetyVolume.effectiveMaxForRouteTest(abs, true, false, true);
        if (speakerEff != abs) {
            throw new AssertionError("speaker must use full max, got " + speakerEff);
        }
        int headsetEff = HearingSafetyVolume.effectiveMaxForRouteTest(abs, true, false, false);
        if (headsetEff != 12) {
            throw new AssertionError("headset with HS on should cap at 12, got " + headsetEff);
        }
    }

    /**
     * 2026-07-18 — At live OS max with no cap, display is always 100% (speaker stuck-at-80% fix).
     */
    @Test
    public void liveOsMaxPaintsFullBarWhenNotCapped() {
        // Inflated eff=15 but OS only allows 12 and we're at 12 → still 100%.
        int display = HearingSafetyVolume.displayAtLiveMaxForTest(12, 12, 15, false);
        if (display != 100) {
            throw new AssertionError("at OS max without cap expected 100 got " + display);
        }
        // Cap active: scale to eff 12, index 12 → 100 (ear zone).
        int atCap = HearingSafetyVolume.displayAtLiveMaxForTest(12, 15, 12, true);
        if (atCap != 100) {
            throw new AssertionError("at HS cap expected 100 got " + atCap);
        }
        // Cap active mid: 6/12 → 50
        int mid = HearingSafetyVolume.displayAtLiveMaxForTest(6, 15, 12, true);
        if (mid < 45 || mid > 55) {
            throw new AssertionError("mid cap display " + mid);
        }
    }
}
