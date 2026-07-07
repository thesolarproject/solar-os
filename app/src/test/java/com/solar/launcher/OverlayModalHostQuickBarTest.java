package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Global quick bar — Now Playing chip policy over third-party / Rockbox. */
public class OverlayModalHostQuickBarTest {

    @Test
    public void nowPlayingChip_visibleOverThirdPartyWhenPlaying() {
        assertTrue(OverlayModalHost.isNowPlayingChipVisible(false, true));
    }

    @Test
    public void nowPlayingChip_hiddenWhenIdle() {
        assertFalse(OverlayModalHost.isNowPlayingChipVisible(false, false));
    }

    @Test
    public void nowPlayingChip_hiddenOverRockboxEvenWhenPlaying() {
        assertFalse(OverlayModalHost.isNowPlayingChipVisible(true, true));
    }
}
