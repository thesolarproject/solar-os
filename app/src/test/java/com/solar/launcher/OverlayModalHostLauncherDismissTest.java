package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** 2026-07-06 — Launcher picker / power tier must dismiss overlay on every selection path. */
public class OverlayModalHostLauncherDismissTest {

    @Test
    public void pickerModeSourceReferencesDismissHelper() throws Exception {
        String text = readRepoFile("app/src/main/java/com/solar/launcher/OverlayModalHost.java");
        assertTrue(text.contains("dismissOverlayForLauncherSelection"));
        assertTrue(text.contains("overlay_picker_restart"));
        assertTrue(text.contains("overlay_power"));
    }

    @Test
    public void powerTierLauncherRowsUseSwitchScriptGate() throws Exception {
        String text = readRepoFile("app/src/main/java/com/solar/launcher/OverlayModalHost.java");
        assertTrue(text.contains("if (LauncherSwitch.isSwitchScriptAvailable()) {"));
        assertTrue(text.contains("appendPowerLauncherRow(labels, headers, powerRowActions, homeTarget, marker,"));
    }

    private static String readRepoFile(String rel) throws Exception {
        java.io.File root = new java.io.File(System.getProperty("user.dir"));
        java.io.File f = new java.io.File(root, rel);
        if (!f.isFile()) {
            f = new java.io.File(root.getParentFile(), rel);
        }
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
    }
}
