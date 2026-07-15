package com.solar.launcher.youtube.api;

import android.content.Context;

import com.solar.launcher.youtube.YouTubeComment;
import com.solar.launcher.youtube.YouTubeVideo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 2026-07-15 — Random pick + failover across YouTube backends (notPipe Manager idea).
 * Layman: tries several public frontends until one answers.
 * Technical: shuffle copy of pool per call; HQ stream prefer supportsHqVideo first.
 * Reversal: single hardcoded host; delete failover.
 */
public final class InstancePool {

    private static final Random RANDOM = new Random();

    private final Object lock = new Object();
    private List<YoutubeBackend> all = new ArrayList<YoutubeBackend>();

    public InstancePool(Context ctx) {
        reload(ctx);
    }

    /** Rebuild backends from prefs (after InstancesUpdater). */
    public void reload(Context ctx) {
        if (ctx == null) return;
        InstancesConfig cfg = new InstancesConfig(ctx);
        cfg.ensureSeeds();
        List<YoutubeBackend> next = new ArrayList<YoutubeBackend>();
        List<String> inv = cfg.getInvidious();
        for (int i = 0; i < inv.size(); i++) {
            next.add(new InvidiousBackend(inv.get(i)));
        }
        List<String> piped = cfg.getPiped();
        for (int i = 0; i < piped.size(); i++) {
            next.add(new PipedBackend(piped.get(i)));
        }
        List<String> yt = cfg.getYtApiLegacy();
        for (int i = 0; i < yt.size(); i++) {
            next.add(new YtApiLegacyBackend(yt.get(i)));
        }
        synchronized (lock) {
            all = next;
        }
    }

    public int size() {
        synchronized (lock) {
            return all.size();
        }
    }

    public List<YouTubeVideo> getPopularVideos() throws IOException {
        return runMetadata(new MetaOp() {
            @Override
            public List<YouTubeVideo> run(YoutubeBackend b) throws IOException {
                return b.getPopularVideos();
            }
        });
    }

    public List<YouTubeVideo> search(final String query) throws IOException {
        return runMetadata(new MetaOp() {
            @Override
            public List<YouTubeVideo> run(YoutubeBackend b) throws IOException {
                return b.search(query);
            }
        });
    }

    public List<YouTubeComment> getComments(final String videoId) throws IOException {
        // 2026-07-15 — Empty comments from one host is success; only hard IO fails over.
        IOException last = null;
        for (YoutubeBackend b : shuffled()) {
            try {
                List<YouTubeComment> c = b.getComments(videoId);
                if (c != null) return c;
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return new ArrayList<YouTubeComment>();
    }

    public String getVideoUrl(String videoId, String quality) throws IOException {
        StreamPick pick = getVideoUrlPick(videoId, quality);
        return pick.url;
    }

    /**
     * 2026-07-15 — Resolve stream and say which backend won (debug / fix path).
     * Layman: find a playable link and remember who handed it out.
     */
    public StreamPick getVideoUrlPick(String videoId, String quality) throws IOException {
        // 2026-07-15 — 240/144 are low-tier (A5 fallback); do not prefer HQ backends.
        // Was: quality != null && !"360".equals(quality)
        boolean wantHq = quality != null && !"360".equals(quality) && !"240".equals(quality)
                && !"144".equals(quality);
        List<YoutubeBackend> order = shuffled();
        if (wantHq) {
            List<YoutubeBackend> hq = new ArrayList<YoutubeBackend>();
            List<YoutubeBackend> rest = new ArrayList<YoutubeBackend>();
            for (int i = 0; i < order.size(); i++) {
                YoutubeBackend b = order.get(i);
                if (b.supportsHqVideo()) hq.add(b);
                else if (b.supportsVideo360()) rest.add(b);
            }
            order = new ArrayList<YoutubeBackend>(hq);
            order.addAll(rest);
        }
        IOException last = null;
        for (int i = 0; i < order.size(); i++) {
            YoutubeBackend b = order.get(i);
            if (wantHq && !b.supportsHqVideo() && b.supportsVideo360()) {
                // After HQ exhausted, try 360 backends with quality forced to 360.
                try {
                    String url = b.getVideoUrl(videoId, "360");
                    if (url != null && url.length() > 0
                            && com.solar.launcher.net.SolarHttp.isUrlReachable(url)) {
                        return new StreamPick(url, b.getName(), "360");
                    }
                } catch (IOException e) {
                    last = e;
                }
                continue;
            }
            if (!b.supportsVideo360() && !b.supportsHqVideo()) continue;
            try {
                String url = b.getVideoUrl(videoId, quality);
                if (url != null && url.length() > 0
                        && com.solar.launcher.net.SolarHttp.isUrlReachable(url)) {
                    return new StreamPick(url, b.getName(), quality);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no stream backends");
    }

    /** One resolved stream + backend tag for playback diagnostics. */
    public static final class StreamPick {
        public final String url;
        public final String backend;
        public final String qualityUsed;

        public StreamPick(String url, String backend, String qualityUsed) {
            this.url = url;
            this.backend = backend != null ? backend : "";
            this.qualityUsed = qualityUsed != null ? qualityUsed : "";
        }
    }

    public YoutubeBackend.AudioStream resolveAudio(String videoId) throws IOException {
        IOException last = null;
        for (YoutubeBackend b : shuffled()) {
            try {
                YoutubeBackend.AudioStream a = b.resolveAudio(videoId);
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("backend", b.getName());
                    d.put("host", b.getHost());
                    d.put("nullStream", a == null);
                    d.put("emptyUrl", a == null || a.url == null || a.url.length() == 0);
                    d.put("ext", a != null && a.ext != null ? a.ext : "");
                    com.solar.launcher.Debug88eea4Log.log(null,
                            "InstancePool.resolveAudio", "backend attempt", "A", d);
                } catch (Exception ignored) {}
                // #endregion
                if (a != null && a.url != null && a.url.length() > 0
                        && com.solar.launcher.net.SolarHttp.isUrlReachable(a.url)) return a;
            } catch (IOException e) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("backend", b.getName());
                    d.put("host", b.getHost());
                    d.put("err", e.getMessage() != null ? e.getMessage() : "");
                    com.solar.launcher.Debug88eea4Log.log(null,
                            "InstancePool.resolveAudio", "backend IOException", "A", d);
                } catch (Exception ignored) {}
                // #endregion
                last = e;
            }
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("last", last != null ? last.getMessage() : "none");
            com.solar.launcher.Debug88eea4Log.log(null,
                    "InstancePool.resolveAudio", "all backends failed", "A", d);
        } catch (Exception ignored) {}
        // #endregion
        if (last != null) throw last;
        throw new IOException("no audio stream");
    }

    private List<YouTubeVideo> runMetadata(MetaOp op) throws IOException {
        IOException last = null;
        for (YoutubeBackend b : shuffled()) {
            try {
                List<YouTubeVideo> r = op.run(b);
                if (r != null) return r;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no metadata backends");
    }

    private List<YoutubeBackend> shuffled() {
        List<YoutubeBackend> copy;
        synchronized (lock) {
            copy = new ArrayList<YoutubeBackend>(all);
        }
        Collections.shuffle(copy, RANDOM);
        return copy;
    }

    private interface MetaOp {
        List<YouTubeVideo> run(YoutubeBackend b) throws IOException;
    }
}
