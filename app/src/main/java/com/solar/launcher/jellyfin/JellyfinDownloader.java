package com.solar.launcher.jellyfin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.net.SolarHttp;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2026-07-06: Download Jellyfin album tracks to local Jellyfin/Artist/Album folder.
 */
public final class JellyfinDownloader {

    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(int saved);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private JellyfinDownloader() {}

    public static void downloadAlbum(final Context ctx, final JellyfinArtist artist,
            final JellyfinAlbum album, final List<JellyfinSong> songs, final Callback cb) {
        executor.execute(new Runnable() {
            @Override public void run() {
                if (songs == null || songs.isEmpty()) {
                    postError(cb, "No songs");
                    return;
                }
                File root = new File(DeviceFeatures.getNewMediaRoot(ctx), "Jellyfin");
                String artistDir = safeName(artist != null ? artist.name : album.artist);
                String albumDir = safeName(album.name);
                File dir = new File(new File(root, artistDir), albumDir);
                if (!dir.exists()) dir.mkdirs();
                int saved = 0;
                for (int i = 0; i < songs.size(); i++) {
                    JellyfinSong s = songs.get(i);
                    try {
                        String ext = s.suffix != null && !s.suffix.isEmpty() ? s.suffix : "mp3";
                        File out = new File(dir, safeName(s.title) + "." + ext.toLowerCase(Locale.US));
                        if (!out.exists() || out.length() < 1024) {
                            downloadUrl(JellyfinClient.getInstance().getDownloadUrl(s), out);
                        }
                        saved++;
                        final int done = i + 1;
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (cb != null) cb.onProgress(done, songs.size());
                            }
                        });
                    } catch (Exception e) {
                        postError(cb, e.getMessage());
                        return;
                    }
                }
                final int count = saved;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (cb != null) cb.onComplete(count);
                    }
                });
            }
        });
    }

    /** 2026-07-06: Same TLS/HTTP stack as JellyfinClient — avoids refused/wrong-port downloads. */
    private static void downloadUrl(String urlStr, File out) throws Exception {
        SolarHttp.downloadToFile(urlStr, out);
    }

    static String safeName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Unknown";
        return raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static void postError(final Callback cb, final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                if (cb != null) cb.onError(msg != null ? msg : "error");
            }
        });
    }
}
