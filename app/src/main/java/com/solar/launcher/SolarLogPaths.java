package com.solar.launcher;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-16 — Canonical Solar log layout (app-private + optional volume mirrors).
 * Hot path writes only to the preferred dir (app-private first) to avoid dual-SD I/O stalls.
 * Full mirror is for crashes / explicit flush / diagnostic ship — not every log line.
 */
public final class SolarLogPaths {
    public static final String REL_ROOT = "solar";
    public static final String REL_LOGS = "solar/logs";
    public static final String REL_FEATURES = "solar/logs/features";
    /** Legacy default path (Y1 MicroSD) — prefer {@link #preferredLogDir}. */
    public static final String LEGACY_LOG_DIR = "/storage/sdcard0/solar/logs";

    /**
     * MicroSD total capacity below this may indicate a bad card/partition (not “disk full”).
     */
    public static final long MICROSD_CAPACITY_WARN_BYTES = 512L * 1024L * 1024L;

    private static final long DIRS_CACHE_TTL_MS = 60_000L;
    private static final long PROBE_COOLDOWN_MS = 6L * 60L * 60L * 1000L; // 6h

    private static volatile List<File> cachedLogDirs;
    private static volatile long cachedLogDirsAt;
    private static volatile File cachedPreferred;
    private static volatile long cachedPreferredAt;
    private static volatile long lastMicroSdProbeMs;

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
     * Every place Solar may keep logs — app-private first, then each healthy user volume.
     * Cached ~60s so hot-path logging does not re-probe storage every line.
     */
    public static List<File> logDirs(Context ctx) {
        long now = System.currentTimeMillis();
        List<File> hit = cachedLogDirs;
        if (hit != null && now - cachedLogDirsAt < DIRS_CACHE_TTL_MS) {
            return hit;
        }
        List<File> dirs = new ArrayList<File>();
        addUnique(dirs, appPrivateLogDir(ctx));
        try {
            for (File root : DeviceFeatures.getStorageRoots()) {
                if (root == null) continue;
                addUnique(dirs, new File(root, REL_LOGS));
            }
        } catch (Throwable ignored) {}
        addUnique(dirs, new File(LEGACY_LOG_DIR));
        cachedLogDirs = dirs;
        cachedLogDirsAt = now;
        return dirs;
    }

    /** Preferred writable log directory — app-private when possible (fast, UMS-safe). */
    public static File preferredLogDir(Context ctx) {
        long now = System.currentTimeMillis();
        File hit = cachedPreferred;
        if (hit != null && now - cachedPreferredAt < DIRS_CACHE_TTL_MS && hit.isDirectory()) {
            return hit;
        }
        File priv = appPrivateLogDir(ctx);
        if (ensureWritableDir(priv)) {
            cachedPreferred = priv;
            cachedPreferredAt = now;
            return priv;
        }
        for (File dir : logDirs(ctx)) {
            if (ensureWritableDir(dir)) {
                cachedPreferred = dir;
                cachedPreferredAt = now;
                return dir;
            }
        }
        ensureWritableDir(priv);
        cachedPreferred = priv;
        cachedPreferredAt = now;
        return priv;
    }

    public static File preferredFeatureLogDir(Context ctx) {
        File base = preferredLogDir(ctx);
        File features = new File(base, "features");
        ensureWritableDir(features);
        return features;
    }

    /**
     * Hot path: append to preferred dir only (no multi-volume walk).
     * 2026-07-16 — Stops dual-SD write storms that stalled Y1/Y2 UI.
     */
    public static void appendPrimary(Context ctx, String fileName, String text, long maxBytes) {
        if (fileName == null || fileName.isEmpty() || text == null) return;
        byte[] bytes = toBytes(text);
        if (bytes == null) return;
        File dir = preferredLogDir(ctx);
        writeOne(dir, fileName, bytes, maxBytes);
    }

    /**
     * Full mirror to all log roots — use sparingly (crash, storage health, pre-ship flush).
     */
    public static void appendMirrored(Context ctx, String fileName, String text, long maxBytes) {
        if (fileName == null || fileName.isEmpty() || text == null) return;
        byte[] bytes = toBytes(text);
        if (bytes == null) return;
        for (File dir : logDirs(ctx)) {
            writeOne(dir, fileName, bytes, maxBytes);
        }
    }

    /** Feature log hot path — preferred features/ only. */
    public static void appendFeaturePrimary(Context ctx, String featureFileName, String text,
            long maxBytes) {
        if (featureFileName == null || featureFileName.isEmpty() || text == null) return;
        byte[] bytes = toBytes(text);
        if (bytes == null) return;
        File features = preferredFeatureLogDir(ctx);
        writeOne(features, featureFileName, bytes, maxBytes);
    }

    /** @deprecated prefer {@link #appendFeaturePrimary} on hot paths */
    public static void appendFeatureMirrored(Context ctx, String featureFileName, String text,
            long maxBytes) {
        appendFeaturePrimary(ctx, featureFileName, text, maxBytes);
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
     * Probe MicroSD total capacity at most once every 6 hours (background-safe).
     * @return true when a capacity anomaly was logged
     */
    public static boolean probeMicroSdCapacityAndLog(Context ctx) {
        long now = System.currentTimeMillis();
        if (lastMicroSdProbeMs > 0 && now - lastMicroSdProbeMs < PROBE_COOLDOWN_MS) {
            return false;
        }
        lastMicroSdProbeMs = now;
        File micro = resolveMicroSdProbeRoot();
        if (micro == null) return false;
        long total;
        long free;
        try {
            total = micro.getTotalSpace();
            free = micro.getUsableSpace();
        } catch (Exception e) {
            return false;
        }
        if (total <= 0L) {
            SolarLog.wQuiet("Storage", "MicroSD total capacity unreadable path="
                    + micro.getAbsolutePath() + " free=" + free);
            return true;
        }
        if (total >= MICROSD_CAPACITY_WARN_BYTES) {
            return false;
        }
        String family = DeviceFeatures.deviceFamily();
        String severity = DeviceFeatures.isY1() ? "Y1_ALERT" : "NOTE";
        SolarLog.wQuiet("Storage", severity
                + " MicroSD total capacity " + formatBytes(total)
                + " < 512MB (possible card/partition failure; free=" + formatBytes(free)
                + ") path=" + micro.getAbsolutePath()
                + " family=" + family);
        try {
            SolarLog.appendPrimaryRaw(ctx, "storage.log",
                    SolarLog.formatStorageLine(severity, micro, total, free), 128 * 1024);
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

    private static byte[] toBytes(String text) {
        try {
            return (text.endsWith("\n") ? text : text + "\n").getBytes("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeOne(File dir, String fileName, byte[] bytes, long maxBytes) {
        if (dir == null || !ensureWritableDir(dir)) return;
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

    private static void addUnique(List<File> dirs, File dir) {
        if (dir == null) return;
        String path = dir.getAbsolutePath();
        for (int i = 0; i < dirs.size(); i++) {
            if (path.equals(dirs.get(i).getAbsolutePath())) return;
        }
        dirs.add(dir);
    }
}
