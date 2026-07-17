package com.solar.launcher;

import org.junit.Test;

/** UMS root command builder — no Robolectric. */
public class UsbMassStorageControllerTest {

    @Test
    public void enableCommandUsesUmsEnablerOneAndVolumePath() {
        String cmd = UsbMassStorageController.buildUmsCommand(
                "/data/app/solar.apk", true, "/storage/sdcard1");
        if (!cmd.contains("UmsEnabler 1")) {
            throw new AssertionError("expected enable flag 1 in: " + cmd);
        }
        if (!cmd.contains("CLASSPATH='/data/app/solar.apk'")) {
            throw new AssertionError("expected quoted classpath in: " + cmd);
        }
        if (!cmd.contains("'/storage/sdcard1'")) {
            throw new AssertionError("expected volume path in: " + cmd);
        }
    }

    @Test
    public void disableCommandUsesUmsEnablerZero() {
        String cmd = UsbMassStorageController.buildUmsCommand(
                "/data/app/solar.apk", false, "/storage/sdcard0");
        if (!cmd.contains("UmsEnabler 0")) {
            throw new AssertionError("expected disable flag 0 in: " + cmd);
        }
        if (!cmd.contains("'/storage/sdcard0'")) {
            throw new AssertionError("expected volume path in: " + cmd);
        }
    }

    @Test
    public void y1ShellEnableCommandQuotesScriptPath() {
        String cmd = UsbMassStorageController.buildUmsShellCommand(
                "/data/data/solar-enable-ums.sh", true);
        if (!cmd.contains("solar-enable-ums.sh")) {
            throw new AssertionError("expected enable script in: " + cmd);
        }
        if (cmd.contains("volume mount")) {
            throw new AssertionError("enable must not remount: " + cmd);
        }
    }

    @Test
    public void disableShellCommandAlwaysRemountsExportVolumes() {
        // Stale /system disable scripts only unshare → Idle-Unmounted (empty All Songs).
        String cmd = UsbMassStorageController.buildUmsShellCommand(
                "/system/etc/solar/solar-disable-ums.sh", false);
        if (!cmd.contains("solar-disable-ums.sh")) {
            throw new AssertionError("expected disable script in: " + cmd);
        }
        if (!cmd.contains("vdc volume mount /storage/sdcard0")) {
            throw new AssertionError("expected sdcard0 remount in: " + cmd);
        }
        if (!cmd.contains("vdc volume mount /storage/sdcard1")) {
            throw new AssertionError("expected sdcard1 remount in: " + cmd);
        }
    }

    @Test
    public void disableIfExportedReturnsFalseForNullContext() {
        if (UsbMassStorageController.disableIfExported(null)) {
            throw new AssertionError("null context should not report success");
        }
    }

    @Test
    public void isMassStorageActiveStateRequiresKernelMassStorageMode() {
        if (!UsbMassStorageController.isMassStorageActiveState("mtp,adb", "mtp,adb", true)) {
            // stale lun on mtp must not count as exported
        } else {
            throw new AssertionError("mtp mode with lun set should not be exported");
        }
        if (!UsbMassStorageController.isMassStorageActiveState("mass_storage,adb", "mass_storage,adb", false)) {
            // mass_storage without lun is not exported
        } else {
            throw new AssertionError("mass_storage without lun should not be exported");
        }
        if (!UsbMassStorageController.isMassStorageActiveState("mass_storage,adb", "mass_storage,adb", true)) {
            throw new AssertionError("mass_storage with lun should be exported");
        }
    }

    @Test
    public void kernelMassStorageDetectedFromConfigString() {
        if (!UsbMassStorageController.isKernelMassStorageModeFromStrings("mass_storage,adb", "mtp,adb")) {
            throw new AssertionError("mass_storage in sys.usb.config should count as kernel UMS mode");
        }
        if (UsbMassStorageController.isKernelMassStorageModeFromStrings("mtp,adb", "mtp,adb")) {
            throw new AssertionError("mtp,adb should not count as kernel UMS mode");
        }
    }

    @Test
    public void isMassStorageExportedFalseOnHostWithoutSysfs() {
        if (UsbMassStorageController.isMassStorageExported()) {
            throw new AssertionError("host CI should not have bound mass-storage LUN");
        }
    }

    @Test
    public void disconnectGraceHelperAcceptsWindow() {
        if (!UsbMassStorageController.isWithinDisconnectGraceForTest(true, 100L, 500L, 4000L)) {
            throw new AssertionError("expected within grace");
        }
        if (UsbMassStorageController.isWithinDisconnectGraceForTest(true, 100L, 5000L, 4000L)) {
            throw new AssertionError("expected outside grace");
        }
        if (UsbMassStorageController.isWithinDisconnectGraceForTest(false, 100L, 200L, 4000L)) {
            throw new AssertionError("inactive session never in grace");
        }
    }

    @Test
    public void disconnectConfirmMsIsPositive() {
        if (UsbMassStorageController.DISCONNECT_CONFIRM_MS < 500L) {
            throw new AssertionError("confirm window too short");
        }
    }

    @Test
    public void disconnectConfirmMsSurvivesMassStorageReenum() {
        // Y1 re-enum often exceeds 2s; teardown at 2s cleared eject UI mid-enable (2026-07-16).
        if (UsbMassStorageController.DISCONNECT_CONFIRM_MS < 8000L) {
            throw new AssertionError("confirm window must outlast mass_storage re-enum; was "
                    + UsbMassStorageController.DISCONNECT_CONFIRM_MS);
        }
    }

    @Test
    public void invalidateProbeCacheIsSafe() {
        UsbMassStorageController.invalidateProbeCache();
        // Host CI has no mass_storage LUN
        if (UsbMassStorageController.isMassStorageExported()) {
            throw new AssertionError("host CI should not report UMS exported");
        }
        UsbMassStorageController.clearUserSession();
        if (UsbMassStorageController.isUserSessionActive()) {
            throw new AssertionError("session should clear without device");
        }
    }
}
