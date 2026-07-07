package com.solar.launcher.navidrome;

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
 * 2026-07-06: Download Navidrome album tracks to local Navidrome/Artist/Album folder.
 */
public final class NavidromeDownloader {

    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(int saved);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private NavidromeDownloader() {}

    public static void downloadAlbum(final Context ctx, final NavidromeArtist artist,
            final NavidromeAlbum album, final List<NavidromeSong> songs, final Callback cb) {
        executor.execute(new Runnable() {
            @Override public void run() {
                if (songs == null || songs.isEmpty()) {
                    postError(cb, "No songs");
                    return;
                }
                File root = new File(DeviceFeatures.getNewMediaRoot(ctx), "Navidrome");
                String artistDir = safeName(artist != null ? artist.name : album.artist);
                String albumDir = safeName(album.name);
                File dir = new File(new File(root, artistDir), albumDir);
                if (!dir.exists()) dir.mkdirs();
                int saved = 0;
                for (int i = 0; i < songs.size(); i++) {
                    NavidromeSong s = songs.get(i);
                    try {
                        String ext = s.suffix != null && !s.suffix.isEmpty() ? s.suffix : "mp3";
                        File out = new File(dir, safeName(s.title) + "." + ext.toLowerCase(Locale.US));
                        if (!out.exists() || out.length() < 1024) {
                            downloadUrl(NavidromeClient.getInstance().getDownloadUrl(s.id), out);
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

    /** 2026-07-06: Same TLS/HTTP stack as NavidromeClient — avoids refused/wrong-port downloads. */
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
