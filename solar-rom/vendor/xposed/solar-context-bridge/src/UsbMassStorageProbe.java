package com.solar.launcher.xposed.bridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 2026-07-05 — Shared sysfs/property probes for UMS export state (SystemUI + system_server).
 * Layman: tells hooks whether the PC can already see the player as a USB disk.
 * Tech: {@code sys.usb.config}/kernel functions mass_storage plus non-empty lun/file node.
 * Reversal: delete and inline back into UsbStorageHooks if this helper is retired.
 */
final class UsbMassStorageProbe {

    private static final String[] LUN_FILE_PATHS = new String[]{
            "/sys/class/android_usb/android0/f_mass_storage/lun/file",
            "/sys/class/android_usb/android0/f_mass_storage/lun0/file",
            "/sys/class/android_usb/android0/f_mass_storage/lun1/file"
    };

    private UsbMassStorageProbe() {}

    /** True when USB config or kernel functions include mass_storage. */
    static boolean probeKernelMassStorageMode() {
        try {
            String config = readFirstLineFromCommand(new String[]{"getprop", "sys.usb.config"});
            if (config.contains("mass_storage")) return true;
            String functions = readFirstLine("/sys/class/android_usb/android0/functions");
            return functions.contains("mass_storage");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when kernel is on mass_storage and a LUN lists a backing block device. */
    static boolean probeMassStorageExported() {
        if (!probeKernelMassStorageMode()) return false;
        return probeLunBackingBound();
    }

    /** Non-empty backing path on any mass-storage LUN sysfs node. */
    static boolean probeLunBackingBound() {
        for (String path : LUN_FILE_PATHS) {
            if (readFirstLine(path).length() > 0) return true;
        }
        return false;
    }

    static String readUsbConfig() {
        return readFirstLineFromCommand(new String[]{"getprop", "sys.usb.config"});
    }

    static String readKernelFunctions() {
        return readFirstLine("/sys/class/android_usb/android0/functions");
    }

    private static String readFirstLineFromCommand(String[] cmd) {
        Process p = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            reader = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return line != null ? line.trim() : "";
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
            if (p != null) p.destroy();
        }
    }

    private static String readFirstLine(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return "";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        }
    }
}
