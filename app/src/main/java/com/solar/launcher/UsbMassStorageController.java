package com.solar.launcher;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-05 — Root UMS toggle; Y1 and Y2 use setprop+vdc shell scripts (not MountService IPC).
 * When changing: solar-enable/disable-ums.sh assets + ROM /system/etc/solar/ staging for both families.
 * Reversal: restore MountService app_process path if a future ROM fixes stability.
 */
public final class UsbMassStorageController {

    private static final String ENABLE_ASSET = "y1/solar-enable-ums.sh";
    private static final String DISABLE_ASSET = "y1/solar-disable-ums.sh";
    private static final String ENABLE_SYSTEM = "/system/etc/solar/solar-enable-ums.sh";
    private static final String DISABLE_SYSTEM = "/system/etc/solar/solar-disable-ums.sh";
    private static final String ENABLE_SYSTEM_LEGACY = "/system/etc/solar/y1-enable-ums.sh";
    private static final String DISABLE_SYSTEM_LEGACY = "/system/etc/solar/y1-disable-ums.sh";
    private static final String ENABLE_DATA = "/data/data/solar-enable-ums.sh";
    private static final String DISABLE_DATA = "/data/data/solar-disable-ums.sh";

    private UsbMassStorageController() {}

    /** Enable USB mass storage (verified LUN bind) as root — Y2 gated on experiment (2026-07-05). */
    public static boolean enable(Context context) {
        return enable(context, "unknown");
    }

    /**
     * 2026-07-06 — UMS only with user consent or explicit Settings → Auto-Connect.
     * Callers starting with {@code user.} are manual UI confirms; {@code auto.} needs auto-connect pref.
     */
    public static boolean enable(Context context, String caller) {
        if (!UsbMassStorageExperiment.isEnabled(context)) return false;
        if (!isEnablePermitted(context, caller)) {
            logEnableDenied(context, caller);
            // #region agent log
            try {
                org.json.JSONObject d = Debug266f21Log.usbSnapshot();
                d.put("caller", caller);
                d.put("autoConnect", context != null && UsbStorageSessionFlags.isAutoConnectEnabled(context));
                Debug266f21Log.log(context, "UsbMassStorageController.enable", "denied", "H1,H4", d);
            } catch (Exception ignored) {}
            // #endregion
            return false;
        }
        logEnablePermitted(context, caller);
        // #region agent log
        try {
            org.json.JSONObject d = Debug266f21Log.usbSnapshot();
            d.put("caller", caller);
            d.put("autoConnect", context != null && UsbStorageSessionFlags.isAutoConnectEnabled(context));
            Debug266f21Log.log(context, "UsbMassStorageController.enable", "permitted", "H1,H4", d);
        } catch (Exception ignored) {}
        // #endregion
        return runUmsToggle(context, true);
    }

    /** User tapped Turn on / overlay confirm, or auto-connect pref is explicitly on. */
    private static boolean isEnablePermitted(Context context, String caller) {
        if (caller != null && caller.startsWith("user.")) return true;
        if (context != null && UsbStorageSessionFlags.isAutoConnectEnabled(context)) {
            return caller != null && caller.startsWith("auto.");
        }
        return false;
    }

    private static void logEnablePermitted(Context context, String caller) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("caller", caller);
            d.put("autoConnect", context != null && UsbStorageSessionFlags.isAutoConnectEnabled(context));
            d.put("usbConfig", readSysUsbConfig());
            DebugAf054eLog.log(context, "UsbMassStorageController.enable",
                    "enable permitted", "USB-CONSENT", d);
        } catch (Exception ignored) {}
    }

    private static void logEnableDenied(Context context, String caller) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("caller", caller);
            d.put("autoConnect", context != null && UsbStorageSessionFlags.isAutoConnectEnabled(context));
            d.put("usbConfig", readSysUsbConfig());
            DebugAf054eLog.log(context, "UsbMassStorageController.enable",
                    "enable denied — no consent", "USB-CONSENT", d);
        } catch (Exception ignored) {}
    }

    /** Disable USB mass storage and return to MTP+adb — always allowed so Y2 can recover stock MTP. */
    public static boolean disable(Context context) {
        if (context == null) return false;
        if (!isKernelMassStorageMode()) return true;
        return runUmsToggle(context, false);
    }

    /**
     * Y2 with UMS experiment off — force MTP if a prior UMS session left mass_storage,adb (2026-07-05).
     * Layman: when disk mode is disabled in Debug, USB goes back to normal phone-to-PC file transfer.
     * Tech: boot + experiment-off call {@link #disable} when kernel config still lists mass_storage.
     * Reversal: remove if stock UsbDeviceManager always restores mtp,adb on its own.
     */
    public static void ensureStockMtpWhenExperimentOff(Context context) {
        if (context == null || !DeviceFeatures.isY2()) return;
        if (UsbMassStorageExperiment.isEnabled(context)) return;
        if (!isKernelMassStorageMode()) return;
        disable(context);
    }

    /**
     * Cable unplug teardown — drop kernel UMS when LUN is still bound (2026-07-05).
     * Layman: unplugging the cable should turn off disk mode so the next plug asks again.
     * Tech: idempotent no-op when sysfs LUN nodes are already empty.
     * Reversal: remove callers and rely on stock UsbDeviceManager disconnect if regressions appear.
     */
    public static boolean disableIfExported(Context context) {
        if (context == null) return false;
        if (!isKernelMassStorageMode()) return true;
        return disable(context);
    }

    /**
     * True when USB is in mass_storage mode and a LUN is bound (2026-07-05).
     * Layman: PC can see the SD as a disk — not just a stale sysfs path after MTP restore.
     * Tech: requires {@code sys.usb.config}/kernel functions mass_storage plus non-empty lun/file.
     * Reversal: revert to lun-only probe if a future ROM clears lun nodes synchronously on disable.
     */
    public static boolean isMassStorageExported() {
        if (!isKernelMassStorageMode()) return false;
        return probeLunBackingBound();
    }

    /** Test hook — kernel USB mode from config + functions strings without sysfs. */
    static boolean isKernelMassStorageModeFromStrings(String usbConfig, String kernelFunctions) {
        if (usbConfig != null && usbConfig.contains("mass_storage")) return true;
        return kernelFunctions != null && kernelFunctions.contains("mass_storage");
    }

    /** Test hook — full export state without device I/O. */
    static boolean isMassStorageActiveState(String usbConfig, String kernelFunctions,
            boolean lunBackingBound) {
        return isKernelMassStorageModeFromStrings(usbConfig, kernelFunctions) && lunBackingBound;
    }

    /** Build the legacy Y2 UmsEnabler su command — test hook only. */
    static String buildUmsCommand(String apkPath, boolean enable, String volumePath) {
        if (apkPath == null || apkPath.length() == 0) return "";
        String escaped = apkPath.replace("'", "'\\''");
        StringBuilder cmd = new StringBuilder();
        cmd.append("export CLASSPATH='").append(escaped).append("'");
        cmd.append(" && app_process /system/bin com.solar.launcher.UmsEnabler ");
        cmd.append(enable ? "1" : "0");
        if (volumePath != null && volumePath.length() > 0) {
            cmd.append(" '").append(volumePath.replace("'", "'\\''")).append("'");
        }
        return cmd.toString();
    }

    /** Shell UMS toggle command — test hook without running root shell. */
    static String buildUmsShellCommand(String scriptPath, boolean enable) {
        if (scriptPath == null || scriptPath.length() == 0) return "";
        return "sh " + shellQuote(scriptPath);
    }

    /** Legacy alias for Y1 shell command tests. */
    static String buildY1UmsShellCommand(String scriptPath, boolean enable, String volumePath) {
        return buildUmsShellCommand(scriptPath, enable);
    }

    /** Y1/Y2 both use solar-enable/disable shell scripts (2026-07-05). */
    private static boolean runUmsToggle(Context context, boolean enable) {
        if (context == null) return false;
        String script = resolveUmsScript(context, enable);
        if (script == null) return false;
        String cmd = buildUmsShellCommand(script, enable);
        if (cmd.length() == 0) return false;
        logUmsAttempt(enable, script);
        boolean rootOk = RootShell.run(cmd);
        boolean result;
        if (!enable) {
            result = rootOk && !isKernelMassStorageMode();
        } else {
            // Enable script exits non-zero unless LUN bind succeeded — trust root exit (2026-07-05).
            result = rootOk;
        }
        logUmsResult(enable, rootOk, result);
        return result;
    }

    /** Resolve baked, cached, or asset-extracted UMS script path. */
    private static String resolveUmsScript(Context context, boolean enable) {
        String system = enable ? ENABLE_SYSTEM : DISABLE_SYSTEM;
        if (new File(system).isFile()) return system;
        String legacy = enable ? ENABLE_SYSTEM_LEGACY : DISABLE_SYSTEM_LEGACY;
        if (new File(legacy).isFile()) return legacy;
        String data = enable ? ENABLE_DATA : DISABLE_DATA;
        if (new File(data).isFile()) return data;
        String asset = enable ? ENABLE_ASSET : DISABLE_ASSET;
        String name = enable ? "solar-enable-ums.sh" : "solar-disable-ums.sh";
        File cached = new File(context.getCacheDir(), name);
        if (!extractAsset(context, asset, cached)) return null;
        RootShell.run("cp " + shellQuote(cached.getAbsolutePath()) + " " + shellQuote(data)
                + " && chmod 755 " + shellQuote(data));
        if (new File(data).isFile()) return data;
        RootShell.run("chmod 755 " + shellQuote(cached.getAbsolutePath()));
        return cached.getAbsolutePath();
    }

    /** Copy one asset shell helper to cache for su execution. */
    private static boolean extractAsset(Context context, String assetPath, File out) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = context.getAssets().open(assetPath);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) fos.write(buf, 0, n);
            }
            fos.flush();
            return out.length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
        }
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    // #region agent log
    private static void logUmsAttempt(boolean enable, String script) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("enable", enable);
            d.put("script", script);
            d.put("y1", DeviceFeatures.isY1());
            d.put("y2", DeviceFeatures.isY2());
            d.put("exportVolumes", joinPaths(DeviceFeatures.getUmsExportVolumePaths()));
            d.put("usbConfigBefore", readSysUsbConfig());
            DebugB6af9fLog.log("UsbMassStorageController.runUmsToggle", "before root", "E", d);
            DebugA3510dLog.log("UsbMassStorageController.runUmsToggle", "before root", "H2", d);
            Debug705932Log.log("UsbMassStorageController.runUmsToggle", "before root", "H2", d);
        } catch (Exception ignored) {}
    }

    private static void logUmsResult(boolean enable, boolean rootOk, boolean result) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("enable", enable);
            d.put("rootOk", rootOk);
            d.put("result", result);
            d.put("lunBound", probeLunBackingBound());
            d.put("kernelUms", isKernelMassStorageMode());
            d.put("usbConfigAfter", readSysUsbConfig());
            DebugB6af9fLog.log("UsbMassStorageController.runUmsToggle", "after root", "E", d);
            DebugA3510dLog.log("UsbMassStorageController.runUmsToggle", "after root", "H2,H3", d);
            Debug705932Log.log("UsbMassStorageController.runUmsToggle", "after root", "H2,H3", d);
        } catch (Exception ignored) {}
    }

    private static String joinPaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) out.append(',');
            out.append(paths.get(i));
        }
        return out.toString();
    }
    // #endregion

    /** Read {@code sys.usb.config} for debug session b6af9f. */
    private static String readSysUsbConfig() {
        java.io.BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "sys.usb.config"});
            br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /** True when {@code sys.usb.config} or kernel functions include mass_storage (2026-07-05). */
    static boolean isKernelMassStorageMode() {
        String config = readSysUsbConfig();
        if (config.contains("mass_storage")) return true;
        return readSysfsFirstLine("/sys/class/android_usb/android0/functions").contains("mass_storage");
    }

    private static String readSysfsFirstLine(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) return "";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /** Non-empty LUN backing path on any mass-storage lun node. */
    private static boolean probeLunBackingBound() {
        String[] paths = new String[]{
                "/sys/class/android_usb/android0/f_mass_storage/lun/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun0/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun1/file"
        };
        for (String path : paths) {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) continue;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                if (line != null && line.trim().length() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {}
                }
            }
        }
        return false;
    }
}
