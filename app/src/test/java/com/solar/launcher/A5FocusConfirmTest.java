package com.solar.launcher;

import android.view.View;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-14 — A5FocusConfirm wrap: key path unfocused = focus only; focused = activate.
 * 2026-07-15 — Touch path is short-tap activate (see attachTouchConfirm).
 */
public class A5FocusConfirmTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void wrapPassthroughWhenNotA5() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        final boolean[] hit = new boolean[] { false };
        View.OnClickListener activate = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hit[0] = true;
            }
        };
        View.OnClickListener wrapped = A5FocusConfirm.wrap(null, activate);
        if (wrapped != activate) {
            throw new AssertionError("Y1 must passthrough listener");
        }
        wrapped.onClick(null);
        if (!hit[0]) throw new AssertionError("activate must run");
    }

    @Test
    public void enabledOnA5() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!A5FocusConfirm.enabled()) {
            throw new AssertionError("A5 must enable focus-confirm");
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (A5FocusConfirm.enabled()) {
            throw new AssertionError("Y1 must not enable");
        }
    }
}
