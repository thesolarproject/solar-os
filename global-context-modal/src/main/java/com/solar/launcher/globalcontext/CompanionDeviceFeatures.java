package com.solar.launcher.globalcontext;

import android.os.Build;

/**
 * 2026-07-05 — Minimal Y1/Y2 branch for companion (no Solar DeviceFeatures dep).
 * Layman: tells companion which hardware rules apply.
 * Technical: ro.product.model heuristics match Solar {@code DeviceFeatures}.
 */
public final class CompanionDeviceFeatures {

    private CompanionDeviceFeatures() {}

    /** Y2 (MT6582) has hardware volume + sleep buttons. */
    public static boolean isY2() {
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase() : "";
        if (model.contains("y2") || device.contains("y2")) return true;
        String board = Build.BOARD != null ? Build.BOARD.toLowerCase() : "";
        return board.contains("mt6582");
    }

    public static boolean isY1() {
        return !isY2();
    }
}
