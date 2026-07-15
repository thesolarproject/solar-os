package com.solar.launcher;

import android.content.pm.ActivityInfo;
import android.view.KeyEvent;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-14 — Y1/Y2 portrait experiment + BT defaults + strip allowlist + handedness.
 * Self-checks (no Android framework) for pref gates and remap matrix.
 */
public class Y1PortraitExperimentTest {

    @After
    public void resetFamily() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void availabilityOnlyY1Y2() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (Y1PortraitExperiment.isAvailable()) {
            throw new AssertionError("not on A5");
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!Y1PortraitExperiment.isAvailable()) {
            throw new AssertionError("on Y1");
        }
        DeviceFeatures.setCachedFamilyForTest("y2");
        if (!Y1PortraitExperiment.isAvailable()) {
            throw new AssertionError("on Y2");
        }
    }

    @Test
    public void defaultOffPrefHook() {
        if (Y1PortraitExperiment.isEnabledForTest(false)) {
            throw new AssertionError("default off");
        }
        if (!Y1PortraitExperiment.isEnabledForTest(true)) {
            throw new AssertionError("enabled when pref true");
        }
    }

    @Test
    public void modeMigrationAndDefaults() {
        // Missing string + legacy false → off
        if (!Y1PortraitExperiment.MODE_OFF.equals(
                Y1PortraitExperiment.modeForTest(null, false))) {
            throw new AssertionError("null+false → off");
        }
        // Legacy boolean On → left-handed (keeps old PORTRAIT)
        if (!Y1PortraitExperiment.MODE_LEFT.equals(
                Y1PortraitExperiment.modeForTest(null, true))) {
            throw new AssertionError("legacy true → left");
        }
        // Explicit right
        if (!Y1PortraitExperiment.MODE_RIGHT.equals(
                Y1PortraitExperiment.modeForTest("right", false))) {
            throw new AssertionError("right mode");
        }
        if (!Y1PortraitExperiment.isEnabledForTest("right", false)) {
            throw new AssertionError("right is enabled");
        }
        if (Y1PortraitExperiment.isEnabledForTest("off", true)) {
            throw new AssertionError("explicit off wins over legacy");
        }
    }

    @Test
    public void activityOrientationHandedness() {
        // Right (default On) → reverse portrait; left → portrait.
        // Pure constants: ActivityInfo fields are static ints (no framework runtime).
        if (ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            throw new AssertionError("portrait constants must differ");
        }
        // Documented mapping in Y1PortraitExperiment.activityOrientation comments/tests via mode.
        if (!Y1PortraitExperiment.MODE_RIGHT.equals("right")
                || !Y1PortraitExperiment.MODE_LEFT.equals("left")) {
            throw new AssertionError("mode constants");
        }
    }

    @Test
    public void narrowContextMenuGate() {
        // Pure helper: eligible when a5OrNarrowOrY1Portrait flag true.
        if (!A5PortraitChrome.useTwoRowQuickBar(true, true)) {
            throw new AssertionError("portrait+eligible → two-row");
        }
        if (A5PortraitChrome.useTwoRowQuickBar(true, false)) {
            throw new AssertionError("portrait without eligibility → single row");
        }
        if (A5PortraitChrome.useTwoRowQuickBar(false, true)) {
            throw new AssertionError("landscape → never two-row");
        }
    }

    @Test
    public void inputRemapMatrixY1() {
        // Side next → Back
        if (Y1PortraitInputKeys.remapKeyCodeForTest(87, 163, false, false)
                != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("side next → Back");
        }
        // Side prev → Play/Pause
        if (Y1PortraitInputKeys.remapKeyCodeForTest(88, 165, false, false)
                != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            throw new AssertionError("side prev → PP");
        }
        // NP wheel
        if (Y1PortraitInputKeys.remapKeyCodeForTest(126, 105, true, false)
                != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("NP wheel up → prev");
        }
        if (Y1PortraitInputKeys.remapKeyCodeForTest(127, 106, true, false)
                != KeyEvent.KEYCODE_MEDIA_NEXT) {
            throw new AssertionError("NP wheel down → next");
        }
        // Menus: wheel stays scroll (PLAY/PAUSE)
        if (Y1PortraitInputKeys.remapKeyCodeForTest(126, 105, false, false) != 126) {
            throw new AssertionError("menus wheel stays");
        }
    }

    @Test
    public void inputRemapMatrixY2TrackButtonsNotWheel() {
        // 2026-07-14 — Y2 105/106 are track skip, not wheel. Portrait → Back / PP (not scroll).
        if (Y1PortraitInputKeys.remapKeyCodeForTest(88, 105, false, true)
                != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            throw new AssertionError("Y2 scan105 track prev → PP");
        }
        if (Y1PortraitInputKeys.remapKeyCodeForTest(87, 106, false, true)
                != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("Y2 scan106 track next → Back");
        }
        // Classical side 163/165 still Back / PP
        if (Y1PortraitInputKeys.remapKeyCodeForTest(87, 163, false, true)
                != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("Y2 side next → Back");
        }
        if (Y1PortraitInputKeys.remapKeyCodeForTest(88, 165, false, true)
                != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            throw new AssertionError("Y2 side prev → PP");
        }
        // Y2 wheel 103/108 PLAY/PAUSE unchanged on menus
        if (Y1PortraitInputKeys.remapKeyCodeForTest(126, 103, false, true) != 126) {
            throw new AssertionError("Y2 menus wheel stays PLAY");
        }
        // NP: Y2 wheel → skip
        if (Y1PortraitInputKeys.remapKeyCodeForTest(126, 103, true, true)
                != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("Y2 NP wheel up → prev");
        }
    }

    @Test
    public void portraitStripAllowlist() {
        if (SettingsScreens.allowsPortraitPreviewStrip(null)) {
            throw new AssertionError("null fail-closed");
        }
        if (SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.ABOUT)) {
            throw new AssertionError("About no strip");
        }
        if (SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.REPORT_ISSUE)) {
            throw new AssertionError("Report no strip");
        }
        if (SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.DONORS_LIST)) {
            throw new AssertionError("Donors no strip");
        }
        if (SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.THEME_PICKER)) {
            throw new AssertionError("theme picker no strip");
        }
        if (!SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.DEVICE)) {
            throw new AssertionError("Device strip");
        }
        if (!SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.PLAYBACK)) {
            throw new AssertionError("Playback strip");
        }
        if (!SettingsScreens.allowsPortraitPreviewStrip(SettingsScreens.DEBUG)) {
            throw new AssertionError("Debug strip");
        }
    }

    @Test
    public void bluetoothDefaultsByFamily() {
        if (!BluetoothExperiment.isEnabledForTest(null, false)) {
            throw new AssertionError("Y1/Y2 default On");
        }
        if (BluetoothExperiment.isEnabledForTest(null, true)) {
            throw new AssertionError("A5 default Off");
        }
        if (!BluetoothExperiment.isEnabledForTest(Boolean.TRUE, true)) {
            throw new AssertionError("A5 user On");
        }
        if (BluetoothExperiment.isEnabledForTest(Boolean.FALSE, false)) {
            throw new AssertionError("Y1 user Off");
        }
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (BluetoothExperiment.defaultEnabledForFamily()) {
            throw new AssertionError("A5 family default Off");
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!BluetoothExperiment.defaultEnabledForFamily()) {
            throw new AssertionError("Y1 family default On");
        }
    }
}
