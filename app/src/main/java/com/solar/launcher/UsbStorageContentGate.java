package com.solar.launcher;

import java.io.File;
import java.util.List;

/**
 * USB mass-storage volume availability — gates library UI when SD paths are unmounted (2026-07-05).
 * 2026-07-15 — Gate only when every browsable volume is gone; Internal-only devices stay usable.
 * Layman: while the PC has the card, Solar warns you to unplug before browsing folders or hides
 * tracks on volumes the computer still owns — but keeps working if Internal Storage is free.
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

    /**
     * True when every user volume is unmounted — show disconnect-only gate.
     * 2026-07-15 — Was Y1-primary-only (bricked no-card / Internal-only). Now: all families.
     */
    public static boolean shouldGateEntireScreen() {
        List<File> roots = DeviceFeatures.getStorageRoots();
        if (roots.isEmpty()) return true;
        for (File root : roots) {
            if (!isVolumeUnavailable(root)) return false;
        }
        return true;
    }

    /** Dual storage — one volume still readable while the other is exported to the PC. */
    public static boolean shouldShowPartialBanner() {
        boolean anyAvail = false;
        boolean anyUnavail = false;
        for (File root : DeviceFeatures.getStorageRoots()) {
            if (isVolumeUnavailable(root)) anyUnavail = true;
            else anyAvail = true;
        }
        return anyAvail && anyUnavail;
    }

    /** Test hook — partial banner from explicit root availability flags. */
    static boolean shouldShowPartialBannerForTest(boolean dualCapable, boolean primaryAvail,
            boolean secondaryAvail) {
        if (!dualCapable) return false;
        if (primaryAvail && !secondaryAvail) return true;
        if (!primaryAvail && secondaryAvail) return true;
        return false;
    }

    /** Test hook — full gate only when every simulated volume is down. */
    static boolean shouldGateEntireScreenForTest(boolean y1, boolean primaryAvail,
            boolean secondaryAvail) {
        // 2026-07-15 — Y1 with Internal up must not full-gate (secondaryAvail true → no gate).
        if (primaryAvail || secondaryAvail) return false;
        return true;
    }
}
