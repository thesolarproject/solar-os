package com.solar.launcher.youtube;

import android.content.Context;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2026-07-16 — Temp files for YouTube <b>Play</b> (not Save).
 * Layman: streaming a search result must not leave tracks forever under Music/YouTube.
 * Tech: app cache/youtube_play; purged when off the music queue (same as Deezer/Reach temps).
 * Reversal: playYouTubeAudio uses YouTubeDownloader.saveAudio to Music/YouTube again.
 */
public final class YouTubePlayCache {

    public static final String DIR_NAME = "youtube_play";

    private YouTubePlayCache() {}

    public static File dir(Context ctx) {
        if (ctx == null) return null;
        File d = new File(ctx.getApplicationContext().getCacheDir(), DIR_NAME);
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    public static File dir(File appCacheRoot) {
        if (appCacheRoot == null) return null;
        File d = new File(appCacheRoot, DIR_NAME);
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Destination for a play-only audio file (never under Music/YouTube). */
    public static File destAudioFile(Context ctx, YouTubeVideo video, String ext) {
        File d = dir(ctx);
        if (d == null) return null;
        String base = YouTubeSavePaths.safeName(video != null ? video.title : "audio");
        if (base.isEmpty()) base = "audio";
        String id = video != null && video.id != null
                ? video.id.replaceAll("[^A-Za-z0-9_-]", "") : "x";
        String e = ext != null && ext.length() > 0 ? ext.toLowerCase(java.util.Locale.US) : "m4a";
        return new File(d, id + "_" + base + "." + e);
    }

    public static boolean isTempFile(File appCacheRoot, File f) {
        if (f == null || appCacheRoot == null) return false;
        File rootDir = dir(appCacheRoot);
        if (rootDir == null) return false;
        String root = rootDir.getAbsolutePath();
        String path = f.getAbsolutePath();
        return path.equals(root) || path.startsWith(root + File.separator);
    }

    public static boolean isTempFile(Context ctx, File f) {
        if (ctx == null || f == null) return false;
        return isTempFile(ctx.getApplicationContext().getCacheDir(), f);
    }

    /** Drop play-cache files not referenced by the current queue. */
    public static void purgeUnreferenced(File appCacheRoot, List<File> keep) {
        File cache = dir(appCacheRoot);
        if (cache == null) return;
        File[] files = cache.listFiles();
        if (files == null) return;
        Set<String> keepPaths = new HashSet<String>();
        if (keep != null) {
            for (File f : keep) {
                if (f != null) keepPaths.add(f.getAbsolutePath());
            }
        }
        for (File f : files) {
            if (f.isFile() && !keepPaths.contains(f.getAbsolutePath())) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}
