package com.solar.launcher.globalcontext;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 2026-07-10 — Companion gate dedupe + cooldown policy (host JVM safe). */
public final class CompanionOverlayKeyGateTest {

    @Test
    public void postOverlayCooldownMatchesSolarGate() {
        assertEquals(90L, CompanionOverlayKeyGate.POST_OVERLAY_COOLDOWN_MS);
    }

    @Test
    public void wheelDedupeDisabledForScrollTicks() {
        assertEquals(0L, CompanionOverlayKeyGate.dedupeWindowMsForKey(
                KeyEvent.KEYCODE_MEDIA_PAUSE));
    }

    @Test
    public void nonWheelDedupeCoalescesDualHook() {
        assertEquals(45L, CompanionOverlayKeyGate.dedupeWindowMsForKey(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void wheelDedupeWindowShorterThanNonWheel() {
        assertTrue(CompanionOverlayKeyGate.dedupeWindowMsForKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                < CompanionOverlayKeyGate.dedupeWindowMsForKey(KeyEvent.KEYCODE_DPAD_CENTER));
    }
}
