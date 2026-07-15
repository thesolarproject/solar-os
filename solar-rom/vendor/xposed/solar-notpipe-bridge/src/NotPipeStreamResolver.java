package com.solar.launcher.xposed.notpipe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-06 — Resolves YouTube audio-only stream URLs inside notPipe process.
 * Layman: asks Piped backends for a music file link instead of a video link.
 * Technical: reflects Manager instance pools; parses Piped /streams audioStreams JSON.
 * Reversal: delete; audio save falls back to error only.
 */
final class NotPipeStreamResolver {

    private static final String STREAM_KIND_AUDIO = "audio";

    private NotPipeStreamResolver() {}

    /** Build RESOLVE_STREAM JSON payload for video or audio kind. */
    static String resolvePayload(Object manager, String videoId, String quality,
            int timeout, String streamKind) throws Exception {
        if (STREAM_KIND_AUDIO.equals(streamKind)) {
            AudioPick pick = resolveAudioPick(manager, videoId);
            if (pick == null || pick.url == null || pick.url.isEmpty()) {
                throw new IOException("no audio stream");
            }
            return "{\"url\":\"" + NotPipeJson.escape(pick.url) + "\",\"videoId\":\""
                    + NotPipeJson.escape(videoId) + "\",\"ext\":\""
                    + NotPipeJson.escape(pick.ext != null ? pick.ext : "m4a") + "\"}";
        }
        // 2026-07-14 — Clear getVideoUrl or release minify a(String,String,int,h,h[]).
        ClassLoader cl = manager.getClass().getClassLoader();
        String url = NotPipeReflect.getVideoUrl(manager, cl, videoId, quality, timeout);
        String ext = guessExtFromUrl(url, "mp4");
        return "{\"url\":\"" + NotPipeJson.escape(url) + "\",\"videoId\":\""
                + NotPipeJson.escape(videoId) + "\",\"ext\":\"" + NotPipeJson.escape(ext) + "\"}";
    }

    private static final class AudioPick {
        final String url;
        final String ext;
        AudioPick(String url, String ext) {
            this.url = url;
            this.ext = ext;
        }
    }

    /** Walk Piped instances and pick best audioStreams entry. */
    private static AudioPick resolveAudioPick(Object manager, String videoId) {
        try {
            List<Object> pools = collectStreamInstances(manager);
            for (int i = 0; i < pools.size(); i++) {
                Object inst = pools.get(i);
                if (inst == null) continue;
                if (!inst.getClass().getName().endsWith(".Piped")) continue;
                AudioPick pick = resolvePipedAudio(inst, videoId);
                if (pick != null) return pick;
            }
        } catch (Throwable t) {
            SolarNotPipeBridge.log("audio resolve failed: " + t.getMessage());
        }
        return null;
    }

    private static List<Object> collectStreamInstances(Object manager) throws Exception {
        List<Object> out = new ArrayList<Object>();
        Class<?> managerClass = manager.getClass();
        for (String fieldName : new String[]{"videoInstances", "hqInstances"}) {
            try {
                Field f = managerClass.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object raw = f.get(manager);
                if (raw instanceof List) {
                    out.addAll((List<?>) raw);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** Piped GET /streams/{id} → audioStreams array. */
    private static AudioPick resolvePipedAudio(Object piped, String videoId) throws Exception {
        Field baseUrlF = piped.getClass().getDeclaredField("baseUrl");
        baseUrlF.setAccessible(true);
        Field proxyUrlF = piped.getClass().getDeclaredField("proxyUrl");
        proxyUrlF.setAccessible(true);
        String baseUrl = (String) baseUrlF.get(piped);
        String proxyUrl = (String) proxyUrlF.get(piped);
        if (baseUrl == null || proxyUrl == null) return null;

        Class<?> httpRequestClass = Class.forName("io.github.gohoski.notpipe.http.HttpRequest");
        Class<?> httpClientClass = Class.forName("io.github.gohoski.notpipe.http.HttpClient");
        Object req = httpRequestClass.getConstructor(String.class, String.class)
                .newInstance(baseUrl, "/streams/" + videoId);
        Method executeToString = httpClientClass.getMethod("executeToString", httpRequestClass);
        String json = (String) executeToString.invoke(null, req);
        if (json == null || json.isEmpty()) return null;

        Class<?> jsonClass = Class.forName("cc.nnproject.json.JSON");
        Method getObject = jsonClass.getMethod("getObject", String.class);
        Object root = getObject.invoke(null, json);
        Method getArray = root.getClass().getMethod("getArray", String.class);
        Object audioStreams = getArray.invoke(root, "audioStreams");
        if (!(audioStreams instanceof List)) return null;
        List<?> streams = (List<?>) audioStreams;
        if (streams.isEmpty()) return null;

        Class<?> utilsClass = Class.forName("io.github.gohoski.notpipe.Utils");
        Method parseUrl = utilsClass.getMethod("parseUrl", String.class, String.class);

        AudioPick best = null;
        int bestScore = -1;
        for (int i = 0; i < streams.size(); i++) {
            Object entry = streams.get(i);
            if (entry == null) continue;
            Method getString = entry.getClass().getMethod("getString", String.class);
            Method getInt = entry.getClass().getMethod("getInt", String.class);
            String relUrl = (String) getString.invoke(entry, "url");
            if (relUrl == null || relUrl.isEmpty()) continue;
            String fullUrl = (String) parseUrl.invoke(null, proxyUrl, relUrl);
            String mime = (String) getString.invoke(entry, "mimeType");
            int bitrate = 0;
            try {
                bitrate = (Integer) getInt.invoke(entry, "bitrate");
            } catch (Throwable ignored) {}
            String ext = extFromMime(mime);
            int score = bitrate;
            if ("m4a".equals(ext) || "opus".equals(ext)) score += 100000;
            if (score > bestScore) {
                bestScore = score;
                best = new AudioPick(fullUrl, ext);
            }
        }
        return best;
    }

    private static String extFromMime(String mime) {
        if (mime == null) return "m4a";
        String lower = mime.toLowerCase();
        if (lower.contains("opus")) return "opus";
        if (lower.contains("mp4") || lower.contains("m4a")) return "m4a";
        if (lower.contains("webm")) return "webm";
        if (lower.contains("ogg")) return "ogg";
        return "m4a";
    }

    private static String guessExtFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) return fallback;
        String ext = path.substring(dot + 1).toLowerCase();
        if (ext.length() > 6) return fallback;
        return ext;
    }
}
