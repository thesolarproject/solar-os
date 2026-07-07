package com.solar.launcher.theme;

import android.content.Context;
import android.graphics.Color;

import com.solar.launcher.DeviceFeatures;

import org.json.JSONObject;

import java.io.File;

/**
 * Publishes active theme dialog colors to primary storage for Xposed Holo layout skinning.
 * Fail-open: when publish fails, the theme-font module leaves stock Holo colors unchanged.
 */
public final class ThemeColorBridge {

    /** Sidecar filename read by {@code ThemeColorSidecar} in the Xposed module. */
    public static final String SIDECAR_FILE = "theme-colors.json";

    private ThemeColorBridge() {}

    /** Sidecar on this device's primary user volume — legacy single-root helper for tests. */
    public static File sidecarFile(Context ctx) {
        File root = DeviceFeatures.getPrimaryStorageRoot();
        if (root == null && ctx != null) {
            root = DeviceFeatures.getNewMediaRoot(ctx);
        }
        if (root == null) return new File("/dev/null");
        return new File(new File(root, SystemFontBridge.SIDECAR_DIR), SIDECAR_FILE);
    }

    /** 2026-07-05 — Write panel + dialog text colors to app-private + every storage volume. */
    public static void publish(Context ctx) {
        if (ctx == null) return;
        try {
            JSONObject json = new JSONObject();
            json.put("panelColor", ThemeManager.getContextMenuPanelColor());
            json.put("textColor", ThemeManager.getDialogTextColor());
            json.put("mutedColor", ThemeManager.getDialogTextColor());
            SidecarPublishHelper.publishBytes(ctx, SIDECAR_FILE, json.toString().getBytes("UTF-8"));
        } catch (Throwable ignored) {
            SidecarPublishHelper.deleteFromAllRoots(ctx, SIDECAR_FILE);
        }
    }

    /** Parse sidecar JSON for unit tests and diagnostics. */
    public static int readPanelColor(String json) {
        try {
            return new JSONObject(json).getInt("panelColor");
        } catch (Throwable ignored) {
            return Color.TRANSPARENT;
        }
    }
}
