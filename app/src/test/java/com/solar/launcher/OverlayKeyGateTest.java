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
    public void postOverlayCooldownMsIsNonTrivial() {
        if (OverlayKeyGate.POST_OVERLAY_COOLDOWN_MS < 200L) {
            throw new AssertionError("cooldown too short to absorb dismiss storms");
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
    public void deliverDedupeSkipsRapidRepeat() {
        OverlayKeyGate.resetDeliverDedupeForTest();
        final int[] calls = new int[] {0};
        OverlayKeyGate.arm(new OverlayKeyGate.Handler() {
            @Override
            public boolean onKeyDown(int keyCode) {
                calls[0]++;
                return true;
            }

            @Override
            public boolean onKeyUp(int keyCode) {
                return true;
            }
        });
        OverlayKeyGate.deliver(126);
        OverlayKeyGate.deliver(126);
        if (calls[0] != 1) {
            throw new AssertionError("expected one handler call, got " + calls[0]);
        }
        OverlayKeyGate.deliverUp(126);
        if (calls[0] != 1) {
            throw new AssertionError("key-up should not be deduped against key-down");
        }
        OverlayKeyGate.disarm();
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
