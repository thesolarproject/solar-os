package com.solar.launcher.youtube;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;

import java.io.File;
import java.util.List;

/**
 * 2026-07-06 — Destination paths for saved YouTube files on user media storage.
 * Layman: video saves go under Videos/YouTube; audio-only under Music/YouTube.
 * Technical: uses DeviceFeatures.getNewMediaRoot for Y1/Y2 write policy.
 * Reversal: delete; downloader has no stable paths.
 */
public final class YouTubeSavePaths {

    private YouTubeSavePaths() {}

    /** Preferred-volume write path under Videos/YouTube. */
    public static File destVideoFile(Context ctx, YouTubeVideo video, String ext) {
        return destUnder(ctx, "Videos", video, ext != null ? ext : "mp4");
    }

    /** Preferred-volume write path under Music/YouTube. */
    public static File destAudioFile(Context ctx, YouTubeVideo video, String ext) {
        return destUnder(ctx, "Music", video, ext != null ? ext : "m4a");
    }

    /** 2026-07-15 — Find existing video on any Healthy Videos/ volume, not only Primary. */
    public static File findSavedVideo(Context ctx, YouTubeVideo video) {
        return findExisting(ctx, "Videos", video);
    }

    /** 2026-07-15 — Find existing audio on any Healthy Music/ volume, not only Primary. */
    public static File findSavedAudio(Context ctx, YouTubeVideo video) {
        return findExisting(ctx, "Music", video);
    }

    private static File destUnder(Context ctx, String library, YouTubeVideo video, String ext) {
        String base = safeName(video != null ? video.title : "video");
        if (base.isEmpty()) base = "video";
        File dir = new File(new File(DeviceFeatures.getNewMediaRoot(ctx), library), "YouTube");
        return new File(dir, base + "." + ext.toLowerCase(java.util.Locale.US));
    }

    /**
     * 2026-07-15 — Read-side search across every Music/ or Videos/ root for YouTube/ saves.
     * Layman: if you saved while Primary pointed at the other card, we still find it.
     * Technical: union getMusicRoots/getVideoRoots + YouTube/ basename match.
     * Reversal: old findExisting only checked getNewMediaRoot.
     */
    private static File findExisting(Context ctx, String library, YouTubeVideo video) {
        if (video == null) return null;
        String base = safeName(video.title);
        if (base.isEmpty()) return null;
        String prefix = base.toLowerCase(java.util.Locale.US);
        List<File> roots = libraryRoots(library);
        for (int r = 0; r < roots.size(); r++) {
            File dir = new File(roots.get(r), "YouTube");
            File hit = matchInDir(dir, prefix);
            if (hit != null) return hit;
        }
        // Fail-open: preferred volume even when roots helper returned empty (tests / odd mounts).
        File preferred = new File(new File(DeviceFeatures.getNewMediaRoot(ctx), library), "YouTube");
        return matchInDir(preferred, prefix);
    }

    /** Music/ or Videos/ folders on every healthy volume. */
    private static List<File> libraryRoots(String library) {
        if ("Music".equals(library)) return DeviceFeatures.getMusicRoots();
        if ("Videos".equals(library)) return DeviceFeatures.getVideoRoots();
        java.util.ArrayList<File> empty = new java.util.ArrayList<File>();
        return empty;
    }

    /** First non-tiny file whose name matches the safe title prefix. */
    private static File matchInDir(File dir, String prefixLower) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (!f.isFile() || f.length() <= 1024L) continue;
            String name = f.getName().toLowerCase(java.util.Locale.US);
            if (name.startsWith(prefixLower + ".") || name.equals(prefixLower)) return f;
        }
        return null;
    }

    /** Tests — build path without Context. */
    static File destUnderRoot(File mediaRoot, String library, String title, String ext) {
        String base = safeName(title);
        if (base.isEmpty()) base = "video";
        File dir = new File(new File(mediaRoot, library), "YouTube");
        return new File(dir, base + "." + ext.toLowerCase(java.util.Locale.US));
    }

    /** Tests — basename match under one YouTube/ dir. */
    static File matchInDirForTest(File youtubeDir, String title) {
        String base = safeName(title);
        if (base.isEmpty()) return null;
        return matchInDir(youtubeDir, base.toLowerCase(java.util.Locale.US));
    }

    static String safeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        return raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
