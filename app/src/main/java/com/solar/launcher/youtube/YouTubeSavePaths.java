package com.solar.launcher.youtube;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;

import java.io.File;

/**
 * 2026-07-06 — Destination paths for saved YouTube files on user media storage.
 * Layman: video saves go under Videos/YouTube; audio-only under Music/YouTube.
 * Technical: uses DeviceFeatures.getNewMediaRoot for Y1/Y2 write policy.
 * Reversal: delete; downloader has no stable paths.
 */
public final class YouTubeSavePaths {

    private YouTubeSavePaths() {}

    public static File destVideoFile(Context ctx, YouTubeVideo video, String ext) {
        return destUnder(ctx, "Videos", video, ext != null ? ext : "mp4");
    }

    public static File destAudioFile(Context ctx, YouTubeVideo video, String ext) {
        return destUnder(ctx, "Music", video, ext != null ? ext : "m4a");
    }

    public static File findSavedVideo(Context ctx, YouTubeVideo video) {
        return findExisting(ctx, "Videos", video);
    }

    public static File findSavedAudio(Context ctx, YouTubeVideo video) {
        return findExisting(ctx, "Music", video);
    }

    private static File destUnder(Context ctx, String library, YouTubeVideo video, String ext) {
        String base = safeName(video != null ? video.title : "video");
        if (base.isEmpty()) base = "video";
        File dir = new File(new File(DeviceFeatures.getNewMediaRoot(ctx), library), "YouTube");
        return new File(dir, base + "." + ext.toLowerCase(java.util.Locale.US));
    }

    private static File findExisting(Context ctx, String library, YouTubeVideo video) {
        if (video == null) return null;
        File dir = new File(new File(DeviceFeatures.getNewMediaRoot(ctx), library), "YouTube");
        if (!dir.isDirectory()) return null;
        String base = safeName(video.title);
        if (base.isEmpty()) return null;
        String prefix = base.toLowerCase(java.util.Locale.US);
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (!f.isFile() || f.length() <= 1024L) continue;
            String name = f.getName().toLowerCase(java.util.Locale.US);
            if (name.startsWith(prefix + ".") || name.equals(prefix)) return f;
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

    static String safeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        return raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
