package com.solar.launcher.globalcontext;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * 2026-07-08 — Root UMS enable/disable from companion without launching Solar Home.
 * Layman: turns PC disk mode on or off from the system menu helper.
 * Technical: runs /system/etc/solar/solar-*-ums.sh via su; probes mass_storage sysfs.
 * Reversal: delete; companion launches MainActivity EXTRA_USB_OVERLAY_* again.
 */
public final class CompanionUmsShell {

    private static final String TAG = "CompanionUmsShell";
    private static final String ENABLE_SYSTEM = "/system/etc/solar/solar-enable-ums.sh";
    private static final String DISABLE_SYSTEM = "/system/etc/solar/solar-disable-ums.sh";
    private static final String ENABLE_LEGACY = "/system/etc/solar/y1-enable-ums.sh";
    private static final String DISABLE_LEGACY = "/system/etc/solar/y1-disable-ums.sh";
    private static final String ENABLE_DATA = "/data/data/solar-enable-ums.sh";
    private static final String DISABLE_DATA = "/data/data/solar-disable-ums.sh";

    private CompanionUmsShell() {}

    /** True when kernel USB config still lists mass_storage. */
    public static boolean isKernelMassStorageMode() {
        String cfg = readProp("sys.usb.config");
        if (cfg != null && cfg.contains("mass_storage")) return true;
        return readFileContains("/sys/class/android_usb/android0/functions", "mass_storage")
                || readFileContains("/config/usb_gadget/g1/configs/b.1/strings/0x409/functions",
                "mass_storage");
    }

    /** True when a mass-storage LUN has a non-empty backing file (PC can see a disk). */
    public static boolean isLunBound() {
        String[] paths = new String[]{
                "/sys/class/android_usb/android0/f_mass_storage/lun/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun0/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun1/file"
        };
        for (int i = 0; i < paths.length; i++) {
            if (readFileNonEmpty(paths[i])) return true;
        }
        return false;
    }

    /** Kernel mass_storage + bound LUN. */
    public static boolean isMassStorageExported() {
        return isKernelMassStorageMode() && isLunBound();
    }

    /** Enable UMS — returns true when script exit is success and LUN is exported. */
    public static boolean enable() {
        boolean ok = runScript(true);
        return ok && isMassStorageExported();
    }

    /** Disable UMS / restore MTP — always attempted so Turn Off works while cable plugged. */
    public static boolean disable() {
        if (!isKernelMassStorageMode()) return true;
        return runScript(false);
    }

    private static boolean runScript(boolean enable) {
        String path = resolveScript(enable);
        if (path == null) {
            Log.w(TAG, "ums script missing enable=" + enable);
            return false;
        }
        String cmd = "sh " + path;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int code = p.waitFor();
            boolean ok = code == 0;
            if (!enable) {
                ok = ok && !isKernelMassStorageMode();
            }
            Log.i(TAG, "ums " + (enable ? "enable" : "disable") + " code=" + code + " ok=" + ok);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("enable", enable);
                d.put("script", path);
                d.put("exitCode", code);
                d.put("ok", ok);
                d.put("kernelUms", isKernelMassStorageMode());
                CompanionUsbDebugLog.log("CompanionUmsShell.runScript",
                        "ums script finished", "H-USB-ENABLE", d);
            } catch (Exception ignored) {}
            // #endregion
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "ums shell failed", e);
            return false;
        }
    }

    private static String resolveScript(boolean enable) {
        String[] candidates = enable
                ? new String[]{ENABLE_SYSTEM, ENABLE_LEGACY, ENABLE_DATA}
                : new String[]{DISABLE_SYSTEM, DISABLE_LEGACY, DISABLE_DATA};
        for (String c : candidates) {
            if (new File(c).isFile()) return c;
        }
        return null;
    }

    private static String readProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v != null ? v.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean readFileContains(String path, String needle) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(path), "UTF-8"));
            String line = br.readLine();
            return line != null && line.contains(needle);
        } catch (Exception e) {
            return false;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static boolean readFileNonEmpty(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(path), "UTF-8"));
            String line = br.readLine();
            return line != null && line.trim().length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
        }
    }
}
