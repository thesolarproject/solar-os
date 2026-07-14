package com.solar.launcher;

import org.junit.Test;

/** Concierge prop cache — no Robolectric / no getprop. */
public class UsbStorageConciergeTest {

    @Test
    public void conciergeTtlIsPositive() {
        if (UsbStorageConcierge.conciergeTtlMsForTest() <= 0L) {
            throw new AssertionError("production concierge TTL should be positive");
        }
    }

    @Test
    public void conciergeCacheFreshnessMatchesTtlWindow() {
        long ttl = UsbStorageConcierge.conciergeTtlMsForTest();
        if (UsbStorageConcierge.isConciergeCacheFreshForTest(0L, 500L, ttl)) {
            throw new AssertionError("zero cacheAt must be stale");
        }
        if (UsbStorageConcierge.isConciergeCacheFreshForTest(10L, 10L + ttl, ttl)) {
            throw new AssertionError("age == TTL must be stale");
        }
        if (!UsbStorageConcierge.isConciergeCacheFreshForTest(10L, 10L + ttl - 1L, ttl)) {
            throw new AssertionError("age < TTL must be fresh");
        }
    }

    @Test
    public void invalidateClearsActiveFlag() {
        UsbStorageConcierge.invalidateConciergeCache();
        // Host JVM has no sys.solar.usb.concierge — active must stay false.
        if (UsbStorageConcierge.isXposedConciergeActive()) {
            throw new AssertionError("host CI should not report concierge active");
        }
    }
}
