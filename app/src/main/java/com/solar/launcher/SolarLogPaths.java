package com.solar.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-16 — Canonical Solar log layout on every mounted volume (and app-private storage).
 * Layman: keep logs in one tidy folder tree, mirrored like themes so a missing card still has a copy.
 * Path: {@code <volume>/solar/logs/…} plus {@code filesDir/solar/logs/} (UMS-safe).
 */
public final class SolarLogPaths {
    public static final String REL_ROOT = "solar";
    public static final String REL_LOGS = "solar/logs";
    public static final String REL_FEATURES = "solar/logs/features";
    /** Legacy default path (Y1 MicroSD) — prefer {@link #preferredLogDir}. */
    public static final String LEGACY_LOG_DIR = "/storage/sdcard0/solar/logs";

    /**
     * MicroSD total capacity below this may indicate a bad card/partition (not “disk full”).
     * 0.5 GiB — real cards are almost always multi‑GB; Y1 often shows ~0.4 GB when the slot fails.
     */
    public static final long MICROSD_CAPACITY_WARN_BYTES = 512L * 1024L * 1024L;

    private SolarLogPaths() {}

    /** App-private log dir — survives MicroSD unmount / USB mass-storage export. */
    public static File appPrivateLogDir(Context ctx) {
        if (ctx == null) {
            return new File("/data/data/com.solar.launcher/files/" + REL_LOGS);
        }
        return new File(ctx.getApplicationContext().getFilesDir(), REL_LOGS);
    }

    public static File appPrivateFeatureLogDir(Context ctx) {
        return new File(appPrivateLogDir(ctx), "features");
    }

    /**
     * Every place Solar should keep logs — app-private first, then each healthy user volume.
     * Mirrors theme dual-storage: MMC/internal + MicroSD when both exist.
     */
    public static List<File> logDirs(Context ctx) {
        List<File> dirs = new ArrayList<File>();
        addUnique(dirs, appPrivateLogDir(ctx));
        for (File root : DeviceFeatures.getStorageRoots()) {
            if (root == null) continue;
            addUnique(dirs, new File(root, REL_LOGS));
        }
        // Fail-open legacy path if roots were empty/stub.
        addUnique(dirs, new File(LEGACY_LOG_DIR));
        return dirs;
    }

    /** Preferred writable log directory (private first, then volumes). */
    public static File preferredLogDir(Context ctx) {
        for (File dir : logDirs(ctx)) {
            if (ensureWritableDir(dir)) return dir;
        }
        File fallback = appPrivateLogDir(ctx);
        ensureWritableDir(fallback);
        return fallback;
    }

    public static File preferredFeatureLogDir(Context ctx) {
        File base = preferredLogDir(ctx);
        File features = new File(base, "features");
        ensureWritableDir(features);
        return features;
    }

    /**
     * Append one line (or block) to the same relative file under every log root.
     * Rotates each file independently when larger than maxBytes.
     */
    public static void appendMirrored(Context ctx, String fileName, String text, long maxBytes) {
        if (fileName == null || fileName.isEmpty() || text == null) return;
        byte[] bytes;
        try {
            bytes = (text.endsWith("\n") ? text : text + "\n").getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }
        for (File dir : logDirs(ctx)) {
            if (!ensureWritableDir(dir)) continue;
            File log = new File(dir, fileName);
            try {
                if (maxBytes > 0 && log.isFile() && log.length() > maxBytes) {
                    File rotated = new File(dir, fileName + ".old");
                    //noinspection ResultOfMethodCallIgnored
                    log.renameTo(rotated);
                }
                FileOutputStream fos = new FileOutputStream(log, true);
                fos.write(bytes);
                fos.close();
            } catch (Exception ignored) {}
        }
    }

    /** Append under {@code features/<safeName>.log} on every log root. */
    public static void appendFeatureMirrored(Context ctx, String featureFileName, String text,
            long maxBytes) {
        if (featureFileName == null || featureFileName.isEmpty() || text == null) return;
        byte[] bytes;
        try {
            bytes = (text.endsWith("\n") ? text : text + "\n").getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }
        for (File logDir : logDirs(ctx)) {
            File features = new File(logDir, "features");
            if (!ensureWritableDir(features)) continue;
            File log = new File(features, featureFileName);
            try {
                if (maxBytes > 0 && log.isFile() && log.length() > maxBytes) {
                    File rotated = new File(features, featureFileName + ".old");
                    //noinspection ResultOfMethodCallIgnored
                    log.renameTo(rotated);
                }
                FileOutputStream fos = new FileOutputStream(log, true);
                fos.write(bytes);
                fos.close();
            } catch (Exception ignored) {}
        }
    }

    public static boolean ensureWritableDir(File dir) {
        if (dir == null) return false;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            return dir.canWrite() || dir.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Probe MicroSD total capacity (not free space). Sub‑512 MB totals often mean a failed
     * card/partition on Y1; log as a warning. Lower severity wording on A5/Y2 (user-swappable card).
     *
     * @return true when a capacity anomaly was logged
     */
    public static boolean probeMicroSdCapacityAndLog(Context ctx) {
        File micro = resolveMicroSdProbeRoot();
        if (micro == null) return false;
        long total;
        long free;
        try {
            total = micro.getTotalSpace();
            free = micro.getUsableSpace();
        } catch (Exception e) {
            SolarLog.w("Storage", "MicroSD probe failed path=" + micro.getAbsolutePath()
                    + " err=" + e.getMessage());
            return false;
        }
        if (total <= 0L) {
            SolarLog.w("Storage", "MicroSD total capacity unreadable path="
                    + micro.getAbsolutePath() + " free=" + free);
            return true;
        }
        if (total >= MICROSD_CAPACITY_WARN_BYTES) {
            return false;
        }
        // Total capacity tiny — not “almost full” (that would be free << total with large total).
        String family = DeviceFeatures.deviceFamily();
        String severity = DeviceFeatures.isY1() ? "Y1_ALERT" : "NOTE";
        SolarLog.w("Storage", severity
                + " MicroSD total capacity " + formatBytes(total)
                + " < 512MB (possible card/partition failure; free=" + formatBytes(free)
                + ") path=" + micro.getAbsolutePath()
                + " family=" + family);
        try {
            SolarLog.appendMirroredRaw(ctx, "storage.log",
                    SolarLog.formatStorageLine(severity, micro, total, free), 256 * 1024);
        } catch (Throwable ignored) {}
        return true;
    }

    /** Prefer healthy MicroSD root; else primary path if it exists as a directory. */
    public static File resolveMicroSdProbeRoot() {
        File micro = DeviceFeatures.getMicroSdRoot();
        if (micro != null) return micro;
        try {
            File primary = new File(DeviceFeatures.primaryStoragePath());
            if (primary.isDirectory()) return primary;
        } catch (Exception ignored) {}
        return null;
    }

    /** Unit-testable: capacity anomaly when total is positive and under the 512 MB floor. */
    public static boolean isSuspiciousMicroSdCapacity(long totalBytes) {
        return totalBytes > 0L && totalBytes < MICROSD_CAPACITY_WARN_BYTES;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024L) return bytes + "B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static void addUnique(List<File> dirs, File dir) {
        if (dir == null) return;
        String path = dir.getAbsolutePath();
        for (int i = 0; i < dirs.size(); i++) {
            if (path.equals(dirs.get(i).getAbsolutePath())) return;
        }
        dirs.add(dir);
    }
}
