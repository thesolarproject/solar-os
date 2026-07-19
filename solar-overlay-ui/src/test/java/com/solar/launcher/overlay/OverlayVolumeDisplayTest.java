package com.solar.launcher.overlay;

import org.junit.Test;

/**
 * 2026-07-18 — Pure mapping tests for companion volume 0–100 display
 * (same math as HearingSafetyVolume; no Android runtime).
 */
public class OverlayVolumeDisplayTest {

    @Test
    public void capIndexMapsToFullBar() {
        int display = OverlayVolumeDisplay.indexToDisplay(12, 12);
        if (display != 100) throw new AssertionError("got " + display);
    }

    @Test
    public void fullIndexMapsToFullBar() {
        if (OverlayVolumeDisplay.indexToDisplay(15, 15) != 100) {
            throw new AssertionError("full");
        }
    }

    @Test
    public void displayRoundTrip() {
        int idx = OverlayVolumeDisplay.displayToIndex(100, 15);
        if (idx != 15) throw new AssertionError("100% -> 15");
        int mid = OverlayVolumeDisplay.displayToIndex(50, 12);
        if (mid < 5 || mid > 7) throw new AssertionError("mid " + mid);
    }
}
