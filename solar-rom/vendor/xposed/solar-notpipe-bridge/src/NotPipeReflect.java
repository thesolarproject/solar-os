package com.solar.launcher.xposed.notpipe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 2026-07-14 — Resolve notPipe Manager/Metadata across clear + ProGuard 0.3.0 names.
 * Layman: find the YouTube helper's guts whether or not the APK scrambled class names.
 * Technical: try api.Manager first; fall back to release mapping a.e / methods a|c|d.
 * Reversal: delete — only clear-name reflection works (needs unobfuscated notPipe).
 */
final class NotPipeReflect {

    private NotPipeReflect() {}

    /** Load Manager class (clear or obfuscated 0.3.0 release). */
    static Class<?> managerClass(ClassLoader cl) throws ClassNotFoundException {
        try {
            return Class.forName("io.github.gohoski.notpipe.api.Manager", true, cl);
        } catch (ClassNotFoundException ignored) {
            return Class.forName("io.github.gohoski.notpipe.a.e", true, cl);
        }
    }

    /**
     * Ensure Manager singleton exists. Stock NotPipe.onCreate inlines init on release APKs
     * (no public init()), so success = getInstance works.
     */
    static void ensureManagerReady(ClassLoader cl) {
        try {
            getManager(cl);
            return;
        } catch (Throwable ignored) {}
        try {
            Class<?> mc = managerClass(cl);
            try {
                Method init = mc.getDeclaredMethod("init");
                init.invoke(null);
            } catch (NoSuchMethodException noInit) {
                // 2026-07-14 — Release minify inlined init into NotPipe.onCreate; nothing to call.
                SolarNotPipeBridge.log("Manager.init absent (release minify) — rely on app onCreate");
            }
        } catch (Throwable t) {
            SolarNotPipeBridge.log("Manager ready failed: " + t);
        }
    }

    /** Manager.getInstance() — release: static a(). */
    static Object getManager(ClassLoader cl) throws Exception {
        Class<?> mc = managerClass(cl);
        try {
            return mc.getDeclaredMethod("getInstance").invoke(null);
        } catch (NoSuchMethodException e) {
            return mc.getDeclaredMethod("a").invoke(null);
        }
    }

    /** Manager.getMetadata() — release: c(). */
    static Object getMetadata(Object manager) throws Exception {
        try {
            return manager.getClass().getDeclaredMethod("getMetadata").invoke(manager);
        } catch (NoSuchMethodException e) {
            return manager.getClass().getDeclaredMethod("c").invoke(manager);
        }
    }

    /** Metadata.getPopularVideos() — release: c(). */
    static List<?> popularVideos(Object metadata) throws Exception {
        try {
            return (List<?>) metadata.getClass().getMethod("getPopularVideos").invoke(metadata);
        } catch (NoSuchMethodException e) {
            return (List<?>) metadata.getClass().getMethod("c").invoke(metadata);
        }
    }

    /** Metadata.search(q) — release: a(String). */
    static List<?> search(Object metadata, String query) throws Exception {
        try {
            return (List<?>) metadata.getClass().getMethod("search", String.class).invoke(metadata, query);
        } catch (NoSuchMethodException e) {
            return (List<?>) metadata.getClass().getMethod("a", String.class).invoke(metadata, query);
        }
    }

    /** Metadata.getComments(id) — release: d(String). */
    static List<?> comments(Object metadata, String videoId) throws Exception {
        try {
            return (List<?>) metadata.getClass().getMethod("getComments", String.class)
                    .invoke(metadata, videoId);
        } catch (NoSuchMethodException e) {
            return (List<?>) metadata.getClass().getMethod("d", String.class).invoke(metadata, videoId);
        }
    }

    /**
     * Manager.getVideoUrl(id, quality, timeout) — release inlines to
     * a(String,String,int,VideoStream,VideoStream[]) with null preferred.
     */
    static String getVideoUrl(Object manager, ClassLoader cl, String videoId, String quality,
            int timeout) throws Exception {
        Class<?> mc = manager.getClass();
        try {
            Method m = mc.getDeclaredMethod("getVideoUrl", String.class, String.class, int.class);
            return (String) m.invoke(manager, videoId, quality, timeout);
        } catch (NoSuchMethodException ignored) {}
        Class<?> stream = Class.forName("io.github.gohoski.notpipe.a.h", true, cl);
        Class<?> streamArr = Array.newInstance(stream, 0).getClass();
        Method m = mc.getDeclaredMethod("a", String.class, String.class, int.class, stream, streamArr);
        return (String) m.invoke(manager, videoId, quality, timeout, null, null);
    }

    /** Read public field by clear name or known release aliases. */
    static String field(Object obj, String logical) {
        if (obj == null) return "";
        String[] names = aliases(logical);
        for (int i = 0; i < names.length; i++) {
            try {
                Field f = obj.getClass().getField(names[i]);
                Object val = f.get(obj);
                return val != null ? String.valueOf(val) : "";
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String[] aliases(String logical) {
        // VideoInfo release (c.d): h title, i thumb, j id, k channel, m duration
        // Comment release (c.b): a channel, c content
        if ("id".equals(logical)) return new String[]{"id", "j"};
        if ("title".equals(logical)) return new String[]{"title", "h"};
        if ("channel".equals(logical)) return new String[]{"channel", "k", "a"};
        if ("duration".equals(logical)) return new String[]{"duration", "m"};
        if ("content".equals(logical)) return new String[]{"content", "c"};
        return new String[]{logical};
    }
}
