package com.solar.launcher;

import org.junit.Test;

/** UmsEnabler LUN probe helpers — no device sysfs in unit tests. */
public class UmsEnablerTest {

    @Test
    public void readLunBackingPathReturnsEmptyWhenSysfsMissing() {
        if (UmsEnabler.readLunBackingPath().length() != 0) {
            throw new AssertionError("expected empty lun on host without android_usb sysfs");
        }
        if (UmsEnabler.isLunBound()) {
            throw new AssertionError("expected lun unbound on host without android_usb sysfs");
        }
    }
}
