package com.solar.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Debug experiment — format MicroSD with a single partition.
 * Rooted Y1: repartition + mkfs via su; Y2 / non-root Y1: system storage settings UI.
 */
public final class MicroSdFormatTool {

    private static final String TAG = "MicroSdFormat";
    private static final int SECTOR_BYTES = 512;

    private MicroSdFormatTool() {}

    /** Rooted Y1 with working su — low-level format; otherwise system UI. */
    public static boolean shouldUseRootFormat(Context ctx) {
        return DeviceFeatures.isY1() && canRunSu();
    }

    /** Block device basename for the user MicroSD (mmcblkN only — sanitized). */
    public static String defaultBlockDeviceName() {
        if (DeviceFeatures.isY2()) {
            return "mmcblk1";
        }
        return "mmcblk1";
    }

    public static boolean isValidBlockDeviceName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.contains("..") || name.contains("/")) return false;
        return name.matches("mmcblk[0-9]+");
    }

    /** Read capacity from sysfs sector count — O(1), no card scan. */
    public static long readCapacityBytes(String blockDeviceName) {
        if (!isValidBlockDeviceName(blockDeviceName)) return -1L;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(
                    "/sys/block/" + blockDeviceName + "/size"));
            String line = reader.readLine();
            if (line == null) return -1L;
            long sectors = Long.parseLong(line.trim());
            return sectors * (long) SECTOR_BYTES;
        } catch (Exception e) {
            return -1L;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
    }

    public static String formatCapacityLabel(long bytes) {
        if (bytes <= 0L) return "?";
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    /** Mount point to umount before formatting. */
    public static String microSdMountPath() {
        if (DeviceFeatures.isY2()) {
            return "/storage/sdcard1";
        }
        return "/storage/sdcard0";
    }

    /** Shell script run as root: new DOS table, one primary partition, exFAT or FAT32. */
    public static String buildRootFormatShell(String blockDeviceName) {
        if (!isValidBlockDeviceName(blockDeviceName)) return null;
        String dev = "/dev/block/" + blockDeviceName;
        String part = dev + "p1";
        return ""
                + "sync; "
                + "umount " + shQuote(microSdMountPath()) + " 2>/dev/null; "
                + "umount " + shQuote(part) + " 2>/dev/null; "
                + "umount " + shQuote(dev) + " 2>/dev/null; "
                // o=new table, n=partition, p=primary, 1=first, 2048=start, default end, w=write
                + "printf 'o\\nn\\np\\n1\\n2048\\n\\n\\nw\\n' | fdisk " + shQuote(dev) + "; "
                + "sleep 1; "
                + "if command -v mkfs.exfat >/dev/null 2>&1; then "
                + "  mkfs.exfat -n SOLAR " + shQuote(part) + "; "
                + "elif command -v mkfs.exfat-fuse >/dev/null 2>&1; then "
                + "  mkfs.exfat-fuse -n SOLAR " + shQuote(part) + "; "
                + "else "
                + "  mkfs.vfat -F 32 -n SOLAR " + shQuote(part) + "; "
                + "  echo FAT32_FALLBACK; "
                + "fi; "
                + "sync";
    }

    public static boolean canRunSu() {
        for (String su : new String[] {"/system/xbin/su", "su"}) {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[] {su, "-c", "id"});
                if (p.waitFor() == 0) return true;
            } catch (Exception ignored) {
            } finally {
                if (p != null) p.destroy();
            }
        }
        return false;
    }

    /** Run root format on a worker thread; callbacks on UI thread via activity.runOnUiThread. */
    public static void runRootFormat(final Activity activity, final String blockDeviceName,
            final FormatCallback callback) {
        if (activity == null || callback == null) return;
        final String shell = buildRootFormatShell(blockDeviceName);
        if (shell == null) {
            callback.onFailed();
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                boolean fat32Fallback = false;
                try {
                    String output = runSuCapture(shell);
                    ok = output != null;
                    fat32Fallback = output != null && output.contains("FAT32_FALLBACK");
                } catch (Exception e) {
                    Log.w(TAG, "format failed: " + e.getMessage());
                }
                final boolean success = ok;
                final boolean fallback = fat32Fallback;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (success) {
                            callback.onSuccess(fallback);
                        } else {
                            callback.onFailed();
                        }
                    }
                });
            }
        }, "MicroSdFormat").start();
    }

    /** Open stock Android storage UI when low-level format is unavailable. */
    public static boolean launchSystemFormatUi(Activity activity) {
        if (activity == null) return false;
        Intent[] candidates = new Intent[] {
                new Intent("android.settings.INTERNAL_STORAGE_SETTINGS"),
                new Intent().setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$StorageSettingsActivity")),
                new Intent().setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.deviceinfo.Storage")),
        };
        for (Intent intent : candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static boolean isMicroSdDetectable() {
        if (!DeviceFeatures.isMicroSdPresent()) return false;
        long cap = readCapacityBytes(defaultBlockDeviceName());
        return cap > 0L || new File("/dev/block/" + defaultBlockDeviceName()).exists();
    }

    private static String runSuCapture(String command) {
        for (String su : new String[] {"/system/xbin/su", "su"}) {
            Process process = null;
            java.io.InputStream in = null;
            try {
                process = Runtime.getRuntime().exec(new String[] {
                        su, "-c", "sh -c '" + command.replace("'", "'\\''") + "'"});
                in = process.getInputStream();
                byte[] buf = new byte[4096];
                int n;
                StringBuilder sb = new StringBuilder();
                while ((n = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n));
                }
                int code = process.waitFor();
                if (code == 0) return sb.toString();
            } catch (Exception ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignored) {}
                }
                if (process != null) process.destroy();
            }
        }
        return null;
    }

    private static String shQuote(String s) {
        return "'" + (s != null ? s.replace("'", "'\\''") : "") + "'";
    }

    public interface FormatCallback {
        void onSuccess(boolean fat32Fallback);
        void onFailed();
    }
}
