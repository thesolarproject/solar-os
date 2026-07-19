package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-18 — USB host vs charger and Power-menu Enable USB gate.
 */
public class UsbHostPresenceTest {

    @Test
    public void chargerOnlyIsNotHost() {
        assertFalse(UsbHostPresence.isUsbHostForTest(true, false, false, false, false));
    }

    @Test
    public void configuredConnectedIsHost() {
        assertTrue(UsbHostPresence.isUsbHostForTest(true, false, false, false, true));
    }

    @Test
    public void hostConnectedFlagIsHost() {
        assertTrue(UsbHostPresence.isUsbHostForTest(true, true, false, false, false));
    }

    @Test
    public void disconnectedNeverHost() {
        assertFalse(UsbHostPresence.isUsbHostForTest(false, true, true, true, true));
    }

    @Test
    public void powerOfferNeedsHostAndIdleUms() {
        assertTrue(UsbHostPresence.shouldOfferEnableForTest(true, true, false, false, false));
        assertFalse(UsbHostPresence.shouldOfferEnableForTest(true, false, false, false, false));
        assertFalse(UsbHostPresence.shouldOfferEnableForTest(false, true, false, false, false));
        assertFalse(UsbHostPresence.shouldOfferEnableForTest(true, true, true, false, false));
        assertFalse(UsbHostPresence.shouldOfferEnableForTest(true, true, false, true, false));
        assertFalse(UsbHostPresence.shouldOfferEnableForTest(true, true, false, false, true));
    }
}
