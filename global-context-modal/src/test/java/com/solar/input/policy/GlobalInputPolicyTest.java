package com.solar.input.policy;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-05 — Companion module policy unit tests (shared JAR). */
public class GlobalInputPolicyTest {

    @Test
    public void powerTapPassthroughUnder500ms() {
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(100L));
        assertFalse(GlobalInputPolicy.shouldPassthroughPowerTap(400L));
    }

    @Test
    public void solarHomeOffersPowerLongOnY2() {
        assertTrue(GlobalInputPolicy.shouldOfferPowerLongModal(
                GlobalInputPolicy.SOLAR_PKG, true));
    }
}
