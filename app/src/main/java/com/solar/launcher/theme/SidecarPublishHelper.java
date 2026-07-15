package com.solar.launcher.theme;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-05 — Writes Solar sidecar files to app-private storage and every mounted user volume.
 * Layman: theme font/colors/snapshot live in two places so overlays and Xposed find them fast.
 */
public final class SidecarPublishHelper {

    /** Hidden dir — matches Xposed {@code FontSidecar} / {@code SystemFontBridge}. */
    public static final String SIDECAR_DIR = ".solar";

    private SidecarPublishHelper() {}

    /**
     * 2026-07-10 — World-readable flat mirror for data-installed companion (no sdcard group).
     * ThemeReader FLAT_SIDECAR_DIRS reads this path; mode 0644 files + 0755 dir.
     */
    public static final String COMPANION_THEME_MIRROR = "/data/local/tmp/solar-theme";

    /** All sidecar parent dirs — app files first (UMS-safe), then primary + secondary storage. */
    public static List<File> sidecarDirs(Context ctx) {
        List<File> dirs = new ArrayList<File>();
        if (ctx != null) {
            dirs.add(new File(ctx.getApplicationContext().getFilesDir(), SIDECAR_DIR));
        }
        for (File root : DeviceFeatures.getStorageRoots()) {
            if (root != null) {
                dirs.add(new File(root, SIDECAR_DIR));
            }
        }
        // Companion ThemeReader — flat dir (files not nested under .solar/).
        dirs.add(new File(COMPANION_THEME_MIRROR));
        return dirs;
    }

    /** First readable sidecar — prefers app-private copy, then MicroSD, then Y2 internal. */
    public static File readFirstSidecar(Context ctx, String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        for (File dir : sidecarDirs(ctx)) {
            File candidate = new File(dir, fileName);
            if (candidate.isFile() && candidate.length() > 0) {
                return candidate;
            }
        }
        return null;
    }

    /** Copy bytes to every sidecar root — world-readable on external volumes for Xposed. */
    public static void publishBytes(Context ctx, String fileName, byte[] data) {
        if (ctx == null || fileName == null || data == null || data.length == 0) return;
        for (File dir : sidecarDirs(ctx)) {
            writeOne(dir, fileName, data);
        }
    }

    /** Mirror a source file to all sidecar roots (font sidecar, etc.). */
    public static void publishFile(Context ctx, String fileName, File source) {
        if (ctx == null || fileName == null || source == null || !source.isFile()) return;
        for (File dir : sidecarDirs(ctx)) {
            File dest = new File(dir, fileName);
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                continue;
            }
            try {
                copyFile(source, dest);
                dest.setReadable(true, false);
            } catch (Exception ignored) {}
        }
    }

    /** Remove a sidecar from every root — fail-open when publish is invalid. */
    public static void deleteFromAllRoots(Context ctx, String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        for (File dir : sidecarDirs(ctx)) {
            File f = new File(dir, fileName);
            if (f.isFile()) {
                f.delete();
            }
        }
    }

    private static void writeOne(File dir, String fileName, byte[] data) {
        if (dir == null) return;
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        // Companion + Xposed need o+rx on dirs under /data/local/tmp/solar-theme.
        try {
            dir.setReadable(true, false);
            dir.setExecutable(true, false);
        } catch (Exception ignored) {}
        File out = new File(dir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            fos.write(data);
            fos.flush();
            out.setReadable(true, false);
        } catch (Exception ignored) {
            if (out.isFile()) out.delete();
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }
    }

    static void copyFile(File source, File dest) throws java.io.IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    /** 2026-07-05 — Recursive tree copy for theme mirror — skips unchanged files by mtime/size. */
    static void copyDirectoryRecursive(File source, File target) throws java.io.IOException {
        if (source == null || !source.exists()) return;
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new java.io.IOException("mkdir " + target);
            }
            String[] children = source.list();
            if (children != null) {
                for (String child : children) {
                    copyDirectoryRecursive(new File(source, child), new File(target, child));
                }
            }
            return;
        }
        if (target.isFile()
                && target.length() == source.length()
                && target.lastModified() >= source.lastModified()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("mkdir " + parent);
        }
        copyFile(source, target);
    }

    /** World-readable mirror on external volumes so Xposed and :overlay can mmap without delay. */
    static void markTreeWorldReadable(File root) {
        if (root == null || !root.exists()) return;
        root.setReadable(true, false);
        if (root.isDirectory()) {
            String[] children = root.list();
            if (children != null) {
                for (String child : children) {
                    markTreeWorldReadable(new File(root, child));
                }
            }
        }
    }
}
