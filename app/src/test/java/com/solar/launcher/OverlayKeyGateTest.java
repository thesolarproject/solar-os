package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Post-dismiss cooldown + dedupe guards for global overlay key routing. */
public class OverlayKeyGateTest {

    @Test
    public void overlayUiPropertyNameIsStableForStaleRecovery() {
        if (!"sys.solar.overlay.ui".equals(OverlayKeyGate.UI_PROPERTY)) {
            throw new AssertionError("UI property name drift");
        }
        if (!"sys.solar.overlay.opening".equals(OverlayKeyGate.OPENING_PROPERTY)) {
            throw new AssertionError("opening property name drift");
        }
    }

    @Test
    public void postOverlayCooldownMatchesSharedPolicy() {
        // 2026-07-10 — 90ms matches CompanionOverlayKeyGate + Xposed cooldown prop.
        if (OverlayKeyGate.POST_OVERLAY_COOLDOWN_MS != 90L) {
            throw new AssertionError("cooldown drift — align with GlobalInputPolicy / companion gate");
        }
    }

    @Test
    public void isOverlayNavigationKey_includesWheelAndBack() {
        if (!OverlayKeyGate.isOverlayNavigationKey(KeyEvent.KEYCODE_MEDIA_PLAY)) {
            throw new AssertionError("wheel up");
        }
        if (!OverlayKeyGate.isOverlayNavigationKey(KeyEvent.KEYCODE_BACK)) {
            throw new AssertionError("back");
        }
        if (OverlayKeyGate.isOverlayNavigationKey(KeyEvent.KEYCODE_HOME)) {
            throw new AssertionError("home excluded");
        }
    }

    @Test
    public void wheelDedupeWindowIsShorterThanNonWheel() {
        // Host JVM cannot mock SystemClock — assert dedupe policy constants instead.
        long wheel = OverlayKeyGate.dedupeWindowMsForTest(KeyEvent.KEYCODE_MEDIA_PLAY);
        long back = OverlayKeyGate.dedupeWindowMsForTest(KeyEvent.KEYCODE_BACK);
        if (wheel >= back) {
            throw new AssertionError("wheel dedupe must be tighter than non-wheel");
        }
        if (wheel != 12L) {
            throw new AssertionError("wheel dedupe drift");
        }
        if (back != 45L) {
            throw new AssertionError("non-wheel dedupe drift");
        }
    }

    @Test
    public void shouldSwallowBackAfterOverlayDismiss_onlyBackDuringCooldown() {
        assertFalse(OverlayKeyGate.shouldSwallowBackAfterOverlayDismiss(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(OverlayKeyGate.shouldSwallowBackAfterOverlayDismiss(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void isInPostOverlayCooldownFalseWhenUnset() {
        assertFalse(OverlayKeyGate.isInPostOverlayCooldown());
    }
}
