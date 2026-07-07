package com.solar.launcher;

import java.io.File;
import java.util.List;

/**
 * USB mass-storage volume availability — gates library UI when SD paths are unmounted (2026-07-05).
 * Layman: while the PC has the card, Solar warns you to unplug before browsing folders or hides
 * tracks on volumes the computer still owns.
 * Tech: {@code listFiles()==null} on a storage root means vold unshared that volume for UMS.
 * Reversal: drop gate rows and use {@link MainActivity#isUsbMassStorageUiLocked()} only if redundant.
 */
public final class UsbStorageContentGate {

    private UsbStorageContentGate() {}

    /** True when {@code root} is missing or vold has unmounted it (UMS share). */
    public static boolean isVolumeUnavailable(File root) {
        if (root == null || !root.isDirectory()) return true;
        try {
            return root.listFiles() == null;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** True when {@code path} lives on a storage root that is currently unavailable. */
    public static boolean isPathBlocked(String path) {
        if (path == null || path.length() == 0) return false;
        for (File root : DeviceFeatures.getStorageRoots()) {
            String rp = root.getAbsolutePath();
            if (path.equals(rp) || path.startsWith(rp + "/")) {
                return isVolumeUnavailable(root);
            }
        }
        return false;
    }

    /** Y1 or Y2 with every user volume unmounted — show disconnect-only gate like folder browser. */
    public static boolean shouldGateEntireScreen() {
        if (DeviceFeatures.isY1()) {
            return isVolumeUnavailable(DeviceFeatures.getPrimaryStorageRoot());
        }
        List<File> roots = DeviceFeatures.getStorageRoots();
        if (roots.isEmpty()) return true;
        for (File root : roots) {
            if (!isVolumeUnavailable(root)) return false;
        }
        return true;
    }

    /** Y2 dual storage — one volume still readable while the other is exported to the PC. */
    public static boolean shouldShowPartialBanner() {
        if (!DeviceFeatures.isY2()) return false;
        boolean anyAvail = false;
        boolean anyUnavail = false;
        for (File root : DeviceFeatures.getStorageRoots()) {
            if (isVolumeUnavailable(root)) anyUnavail = true;
            else anyAvail = true;
        }
        return anyAvail && anyUnavail;
    }

    /** Test hook — partial banner from explicit root availability flags. */
    static boolean shouldShowPartialBannerForTest(boolean y2, boolean primaryAvail,
            boolean secondaryAvail) {
        if (!y2) return false;
        if (primaryAvail && !secondaryAvail) return true;
        if (!primaryAvail && secondaryAvail) return true;
        return false;
    }

    /** Test hook — full gate when Y1 primary down or Y2 both down. */
    static boolean shouldGateEntireScreenForTest(boolean y1, boolean primaryAvail,
            boolean secondaryAvail) {
        if (y1) return !primaryAvail;
        return !primaryAvail && !secondaryAvail;
    }
}
