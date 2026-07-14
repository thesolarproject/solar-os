package com.solar.launcher;

import android.content.Context;

import com.solar.launcher.overlay.OverlayMenuClient;

/**
 * 2026-07-14 — Routes global power quick menu via OverlayShellRouter (Solar ThemedContextMenu).
 * Layman: opens the same decorated system options as Solar Home.
 * Technical: OverlayMenuClient → SolarOverlayService; companion_shell=1 for Chip escape.
 * Was: companion Chip primary. Reversal: companion_shell=1.
 */
public final class CompanionContextMenuLauncher {

    private CompanionContextMenuLauncher() {}

    public static boolean isCompanionInstalled(Context ctx) {
        return OverlayMenuClient.isCompanionInstalled(ctx);
    }

    /**
     * 2026-07-14 — Open power tier on the one Solar ThemedContextMenu shell.
     * Layman: quick menu matches Home decoration and takes the wheel.
     * Was: companion Chip primary. Reversal: companion_shell=1.
     */
    public static boolean openPowerQuickMenu(Context ctx) {
        return OverlayMenuClient.showPowerQuickMenu(ctx);
    }
}
