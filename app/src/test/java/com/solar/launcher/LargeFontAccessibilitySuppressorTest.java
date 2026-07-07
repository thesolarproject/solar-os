package com.solar.launcher;

import org.junit.Test;

/** Large-font accessibility guard — Y1/Y2 JB/KK only. */
public class LargeFontAccessibilitySuppressorTest {

    @Test
    public void detectsLargeFontScaleAboveThreshold() {
        if (!LargeFontAccessibilitySuppressor.isLargeFontScaleForTest(1.3f)) {
            throw new AssertionError("1.3 should count as large font");
        }
        if (LargeFontAccessibilitySuppressor.isLargeFontScaleForTest(1.0f)) {
            throw new AssertionError("1.0 is normal font");
        }
    }

    @Test
    public void resetIfLarge_noOpOnNullContext() {
        LargeFontAccessibilitySuppressor.resetIfLarge(null);
    }
}
