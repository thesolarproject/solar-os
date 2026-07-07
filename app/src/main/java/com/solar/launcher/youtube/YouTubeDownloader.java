package com.solar.launcher.youtube;

import android.content.Context;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.net.SolarHttp;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

/**
 * 2026-07-06 — Saves YouTube streams to Videos/ or Music/ on user storage.
 * Layman: downloads a picked video or audio-only track into the right library folder.
 * Technical: RESOLVE_STREAM IPC then SolarHttp.downloadToFile on worker thread.
 * Reversal: delete; context save actions have no backend.
 */
public final class YouTubeDownloader {

    public interface Callback {
        void onProgress(int done, int total);
        void onComplete(File savedFile);
        void onError(String message);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private YouTubeDownloader() {}

    public static void saveVideo(final Context ctx, final YouTubeVideo video, final Callback cb) {
        save(ctx, video, false, cb);
    }

    public static void saveAudio(final Context ctx, final YouTubeVideo video, final Callback cb) {
        save(ctx, video, true, cb);
    }

    private static void save(final Context ctx, final YouTubeVideo video,
            final boolean audioOnly, final Callback cb) {
        if (video == null || video.id == null || video.id.isEmpty()) {
            postError(cb, "no video");
            return;
        }
        File existing = audioOnly
                ? YouTubeSavePaths.findSavedAudio(ctx, video)
                : YouTubeSavePaths.findSavedVideo(ctx, video);
        if (existing != null && existing.length() > 1024L) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (cb != null) cb.onComplete(existing);
                }
            });
            return;
        }
        NotPipeClient client = NotPipeClient.getInstance(ctx);
        NotPipeClient.Callback resolveCb = new NotPipeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                try {
                    YouTubeResultJson.StreamResult stream =
                            YouTubeResultJson.parseStreamResult(payloadJson);
                    if (stream == null || stream.url == null || stream.url.isEmpty()) {
                        postError(cb, "no stream");
                        return;
                    }
                    final File dest = audioOnly
                            ? YouTubeSavePaths.destAudioFile(ctx, video, stream.ext)
                            : YouTubeSavePaths.destVideoFile(ctx, video, stream.ext);
                    downloadResolved(stream.url, dest, cb);
                } catch (Exception e) {
                    postError(cb, e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                postError(cb, message);
            }
        };
        if (audioOnly) {
            client.resolveAudioStream(video.id, resolveCb);
        } else {
            client.resolveStream(video.id, resolveCb);
        }
    }

    private static void downloadResolved(final String url, final File dest, final Callback cb) {
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (cb != null) cb.onProgress(0, 1);
                        }
                    });
                    File parent = dest.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    SolarHttp.downloadToFile(url, dest);
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (cb != null) {
                                cb.onProgress(1, 1);
                                cb.onComplete(dest);
                            }
                        }
                    });
                } catch (Exception e) {
                    postError(cb, e.getMessage());
                }
            }
        });
    }

    private static void postError(final Callback cb, final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                if (cb != null) cb.onError(msg != null ? msg : "error");
            }
        });
    }

    /** Sanitize title for filesystem — shared with save path helpers. */
    static String safeName(String raw) {
        return YouTubeSavePaths.safeName(raw);
    }
}
