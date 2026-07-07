package com.solar.launcher.theme;

import org.junit.Test;

/** 2026-07-05 — Theme snapshot JSON parsing for overlay RAM warm. */
public class ThemeSnapshotBridgeTest {

    @Test
    public void readThemeFolder_fromJson() {
        String folder = ThemeSnapshotBridge.readThemeFolder(
                "{\"version\":1,\"themeFolder\":\"Melody\",\"internalPath\":\"/data/Melody\"}");
        if (!"Melody".equals(folder)) {
            throw new AssertionError("expected Melody, got " + folder);
        }
    }

    @Test
    public void readThemeFolder_invalidJsonReturnsEmpty() {
        if (ThemeSnapshotBridge.readThemeFolder("not-json").length() > 0) {
            throw new AssertionError("invalid json should return empty folder");
        }
    }

    @Test
    public void buildSnapshotStubEntry_restoresDialogAndRowColors() throws Exception {
        ThemeManager.resetOverlayThemeBootstrapForTest();
        org.json.JSONObject json = new org.json.JSONObject();
        json.put("dialogTextColor", 0xFF112233);
        json.put("rowSelectionFillColor", 0xFF445566);
        ThemeManager.ThemeEntry entry = ThemeSnapshotBridge.buildSnapshotStubEntry(
                "Melody", "/data/Themes/Melody", "", json);
        if (entry == null || !"Melody".equals(entry.folderName)) {
            throw new AssertionError("stub entry missing");
        }
        ThemeManager.installOverlayRamEntry(entry, "Melody");
        if (ThemeManager.getDialogTextColor() != 0xFF112233) {
            throw new AssertionError("dialog color from stub");
        }
        if (ThemeManager.getRowSelectionFillColor() != 0xFF445566) {
            throw new AssertionError("row fill from stub");
        }
    }
}
