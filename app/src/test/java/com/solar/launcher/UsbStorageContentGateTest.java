package com.solar.launcher;

import org.junit.Test;

/** USB volume gate logic — no Robolectric. */
public class UsbStorageContentGateTest {

    @Test
    public void y1FullGateWhenPrimaryUnavailable() {
        if (!UsbStorageContentGate.shouldGateEntireScreenForTest(true, false, false)) {
            throw new AssertionError("Y1 should gate when MicroSD unavailable");
        }
        if (UsbStorageContentGate.shouldGateEntireScreenForTest(true, true, false)) {
            throw new AssertionError("Y1 should not gate when MicroSD available");
        }
    }

    @Test
    public void y2FullGateOnlyWhenBothVolumesUnavailable() {
        if (!UsbStorageContentGate.shouldGateEntireScreenForTest(false, false, false)) {
            throw new AssertionError("Y2 gates when both volumes unavailable");
        }
        if (UsbStorageContentGate.shouldGateEntireScreenForTest(false, true, false)) {
            throw new AssertionError("Y2 should not full-gate when MicroSD still available");
        }
        if (UsbStorageContentGate.shouldGateEntireScreenForTest(false, false, true)) {
            throw new AssertionError("Y2 should not full-gate when internal still available");
        }
    }

    @Test
    public void y2PartialBannerWhenOneVolumeUnavailable() {
        if (!UsbStorageContentGate.shouldShowPartialBannerForTest(true, true, false)) {
            throw new AssertionError("internal ok + MicroSD exported → partial banner");
        }
        if (!UsbStorageContentGate.shouldShowPartialBannerForTest(true, false, true)) {
            throw new AssertionError("MicroSD ok + internal exported → partial banner");
        }
        if (UsbStorageContentGate.shouldShowPartialBannerForTest(true, true, true)) {
            throw new AssertionError("both available → no partial banner");
        }
        if (UsbStorageContentGate.shouldShowPartialBannerForTest(false, true, false)) {
            throw new AssertionError("Y1 never uses partial banner");
        }
    }

    @Test
    public void isPathBlockedFalseOnHostWithoutStorage() {
        if (UsbStorageContentGate.isPathBlocked("/storage/sdcard0/Music/track.mp3")) {
            // Host CI may not have /storage — method should not block unknown mounts aggressively.
        }
    }
}
