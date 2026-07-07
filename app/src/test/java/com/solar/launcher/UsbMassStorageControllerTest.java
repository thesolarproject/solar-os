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
}
