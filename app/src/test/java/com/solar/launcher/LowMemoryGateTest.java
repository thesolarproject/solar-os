package com.solar.launcher;

import android.content.ComponentCallbacks2;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-16 — Pure threshold tests for Y1 RAM gate (no Robolectric). */
public class LowMemoryGateTest {

    @Test
    public void roomyDeviceNotPressured() {
        assertFalse(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void lowAvailMemIsPressured() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                20L * 1024L * 1024L,
                80L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void lowMemFreeIsPressured() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                100L * 1024L * 1024L,
                8L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void systemLowMemoryFlag() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                true,
                -1));
    }

    @Test
    public void trimRunningLow() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                false,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
    }

    @Test
    public void floorsArePositive() {
        assertTrue(LowMemoryGate.AVAIL_FLOOR_BYTES > 0);
        assertTrue(LowMemoryGate.MEMFREE_FLOOR_BYTES > 0);
        assertTrue(LowMemoryGate.DEFER_MS > 0);
    }
}
