package com.solar.launcher.video;

import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Owns one {@link IjkMediaPlayer}, binds {@link SurfaceHolder} lifecycle, play/pause/seek/release.
 * Call {@link #attachHolder(SurfaceHolder)} from a {@link SurfaceRenderView} or SurfaceView host.
 */
public final class VideoPlayerController implements SurfaceHolder.Callback,
        IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnVideoSizeChangedListener {

    /**
     * 2026-07-14 — Host learns when stream fails or ends (YouTube IJK path).
     * 2026-07-15 — Also video size for aspect / landscape session.
     * Layman: Solar can toast and leave the player instead of a black stuck screen.
     * Technical: wraps IJK OnError / OnCompletion / OnVideoSizeChanged.
     * Reversal: leave listener null — same silent-fail behaviour as pre-harden.
     */
    public interface PlaybackListener {
        /** Decode / network failure — what / why for toast. */
        void onError(int what, int extra);

        /** Natural end of stream. */
        void onCompletion();

        /**
         * 2026-07-15 — Decoded frame size ready (for letterbox/crop + orientation).
         * Default no-op via empty impl in older anonymous listeners not updating size.
         */
        void onVideoSize(int width, int height);
    }

    private final IjkMediaPlayer player;
    private SurfaceHolder boundHolder;
    private boolean prepared;
    private boolean playWhenSurfaceReady;
    private boolean pausedForSurfaceLoss;
    private String pendingPath;
    private PlaybackListener playbackListener;
    /** 2026-07-14 — Guard double error callbacks after release / retry. */
    private boolean released;

    public VideoPlayerController() {
        player = SolarIjkPlayerFactory.create();
        wirePlayerListeners();
    }

    /** Host callback for error / end — YouTube uses this to retry quality or leave. */
    public void setPlaybackListener(PlaybackListener listener) {
        playbackListener = listener;
    }

    public IjkMediaPlayer getPlayer() {
        return player;
    }

    /** Open a local file; prepares async once a surface is available. */
    public void open(File file) throws IOException {
        if (file == null) throw new IOException("no file");
        resetForNewSource();
        pendingPath = file.getAbsolutePath();
        player.setDataSource(pendingPath);
        maybePrepare();
    }

    /**
     * 2026-07-06 — HTTP stream (YouTube IJK path).
     * 2026-07-14 — Shares factory HTTP options; errors go to {@link PlaybackListener}.
     */
    public void openUrl(String url) throws IOException {
        if (url == null || url.isEmpty()) throw new IOException("no url");
        resetForNewSource();
        pendingPath = url;
        player.setDataSource(url);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("urlPrefix", url.length() > 96 ? url.substring(0, 96) : url);
            d.put("hasHolder", boundHolder != null);
            d.put("isDirectUrlApi", url.indexOf("/direct_url") >= 0);
            d.put("isVideoplayback", url.indexOf("videoplayback") >= 0);
            com.solar.launcher.Debug9d82a5Log.log(null,
                    "VideoPlayerController.openUrl", "setDataSource", "B", d);
        } catch (Exception ignored) {}
        // #endregion
        maybePrepare();
    }

    public boolean isPrepared() {
        return prepared;
    }

    public boolean isPlaying() {
        try {
            return prepared && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public long getCurrentPosition() {
        try {
            return prepared ? player.getCurrentPosition() : 0L;
        } catch (IllegalStateException e) {
            return 0L;
        }
    }

    public long getDuration() {
        try {
            return prepared ? player.getDuration() : 0L;
        } catch (IllegalStateException e) {
            return 0L;
        }
    }

    public void play() {
        if (!prepared) {
            playWhenSurfaceReady = true;
            return;
        }
        try {
            player.start();
            playWhenSurfaceReady = false;
            pausedForSurfaceLoss = false;
        } catch (IllegalStateException ignored) {}
    }

    public void pause() {
        playWhenSurfaceReady = false;
        if (!prepared) return;
        try {
            if (player.isPlaying()) player.pause();
        } catch (IllegalStateException ignored) {}
    }

    public void togglePlayPause() {
        if (isPlaying()) pause();
        else play();
    }

    public void seekTo(long positionMs) {
        if (!prepared) return;
        try {
            player.seekTo(positionMs);
        } catch (IllegalStateException ignored) {}
    }

    /** Wire surface callbacks from the hosting view. */
    public void attachHolder(SurfaceHolder holder) {
        if (holder == null) return;
        holder.addCallback(this);
        boundHolder = holder;
        player.setDisplay(holder);
        maybePrepare();
    }

    public void detachHolder(SurfaceHolder holder) {
        if (holder != null) holder.removeCallback(this);
        if (boundHolder == holder) boundHolder = null;
    }

    /** Stop playback and release native player — call on Back / screen exit. */
    public void release() {
        released = true;
        playWhenSurfaceReady = false;
        pausedForSurfaceLoss = false;
        prepared = false;
        pendingPath = null;
        playbackListener = null;
        try {
            player.stop();
        } catch (IllegalStateException ignored) {}
        try {
            player.setDisplay(null);
        } catch (IllegalStateException ignored) {}
        player.release();
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        if (released) return;
        prepared = true;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("playWhenReady", playWhenSurfaceReady);
            d.put("urlPrefix", pendingPath != null && pendingPath.length() > 80
                    ? pendingPath.substring(0, 80) : pendingPath);
            com.solar.launcher.Debug9d82a5Log.log(null,
                    "VideoPlayerController.onPrepared", "prepared ok", "C", d);
        } catch (Exception ignored) {}
        // #endregion
        if (playWhenSurfaceReady) play();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        if (released) return true;
        prepared = false;
        playWhenSurfaceReady = false;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("what", what);
            d.put("extra", extra);
            d.put("urlPrefix", pendingPath != null && pendingPath.length() > 80
                    ? pendingPath.substring(0, 80) : pendingPath);
            d.put("isDirectUrlApi", pendingPath != null
                    && pendingPath.indexOf("/direct_url") >= 0);
            com.solar.launcher.Debug9d82a5Log.log(null,
                    "VideoPlayerController.onError", "ijk decode/net error", "A", d);
        } catch (Exception ignored) {}
        // #endregion
        PlaybackListener l = playbackListener;
        if (l != null) {
            l.onError(what, extra);
        }
        // Consumed — avoid IJK default completion-on-error path.
        return true;
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        if (released) return;
        PlaybackListener l = playbackListener;
        if (l != null) {
            l.onCompletion();
        }
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
        if (released) return;
        PlaybackListener l = playbackListener;
        if (l != null) {
            l.onVideoSize(width, height);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (released) return;
        boundHolder = holder;
        player.setDisplay(holder);
        maybePrepare();
        if (pausedForSurfaceLoss && prepared) {
            pausedForSurfaceLoss = false;
            play();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (released) return;
        boundHolder = holder;
        player.setDisplay(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (released) return;
        if (prepared) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    pausedForSurfaceLoss = true;
                }
            } catch (IllegalStateException ignored) {}
        }
        player.setDisplay(null);
        if (boundHolder == holder) boundHolder = null;
    }

    private void maybePrepare() {
        if (released || prepared || pendingPath == null || boundHolder == null) {
            // #region agent log
            // Only note waits for surface (have URL, no holder yet).
            if (!released && !prepared && pendingPath != null && boundHolder == null) {
                try {
                    com.solar.launcher.Debug9d82a5Log.log(null,
                            "VideoPlayerController.maybePrepare",
                            "waiting for surface", "C", null);
                } catch (Exception ignored) {}
            }
            // #endregion
            return;
        }
        // #region agent log
        try {
            com.solar.launcher.Debug9d82a5Log.log(null,
                    "VideoPlayerController.maybePrepare", "prepareAsync", "C", null);
        } catch (Exception ignored) {}
        // #endregion
        try {
            player.prepareAsync();
        } catch (IllegalStateException ignored) {}
    }

    private void resetForNewSource() {
        prepared = false;
        playWhenSurfaceReady = false;
        pausedForSurfaceLoss = false;
        try {
            player.reset();
            SolarIjkPlayerFactory.applyY1Options(player);
            wirePlayerListeners();
        } catch (IllegalStateException ignored) {}
    }

    /** Re-attach prepared / error / completion after reset (reset clears them). */
    private void wirePlayerListeners() {
        player.setOnPreparedListener(this);
        player.setOnErrorListener(this);
        player.setOnCompletionListener(this);
        player.setOnVideoSizeChangedListener(this);
    }
}
