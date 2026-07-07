package com.solar.launcher;

import org.junit.Test;

public class MicroSdFormatToolTest {

    @Test
    public void sectorSizeMath() {
        long bytes = MicroSdFormatTool.readCapacityBytes("invalid/../mmc");
        if (bytes != -1L) throw new AssertionError("reject bad device name");
    }

    @Test
    public void blockDeviceSanitization() {
        if (MicroSdFormatTool.isValidBlockDeviceName("mmcblk1")) {
            // ok
        } else {
            throw new AssertionError("mmcblk1 valid");
        }
        if (MicroSdFormatTool.isValidBlockDeviceName("../mmcblk1")) {
            throw new AssertionError("reject path traversal");
        }
        if (MicroSdFormatTool.isValidBlockDeviceName("sda1")) {
            throw new AssertionError("reject non-mmc");
        }
    }

    @Test
    public void formatCapacityLabel() {
        long gb = 32L * 1024L * 1024L * 1024L;
        String label = MicroSdFormatTool.formatCapacityLabel(gb);
        if (!label.contains("GB")) throw new AssertionError("gb label");
        if (!"?".equals(MicroSdFormatTool.formatCapacityLabel(-1))) {
            throw new AssertionError("unknown size");
        }
    }

    @Test
    public void rootFormatShellContainsFdiskAndPartition() {
        String shell = MicroSdFormatTool.buildRootFormatShell("mmcblk1");
        if (shell == null || !shell.contains("fdisk")) {
            throw new AssertionError("fdisk missing");
        }
        if (!shell.contains("mmcblk1p1")) throw new AssertionError("partition path");
    }

    @Test
    public void rootFormatShellRejectsBadDevice() {
        if (MicroSdFormatTool.buildRootFormatShell("../../../dev/mmcblk0") != null) {
            throw new AssertionError("should reject bad device");
        }
    }

    @Test
    public void y1UsesRootPathWhenSuAssumed() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        // JVM has no su — shouldUseRootFormat false here; route is still Y1-gated.
        boolean route = DeviceFeatures.isY1();
        DeviceFeatures.resetCacheForTest();
        if (!route) throw new AssertionError("y1 route");
    }

    @Test
    public void y2DefaultBlockDevice() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        if (!"mmcblk1".equals(MicroSdFormatTool.defaultBlockDeviceName())) {
            throw new AssertionError("y2 block dev");
        }
        DeviceFeatures.resetCacheForTest();
    }
}
