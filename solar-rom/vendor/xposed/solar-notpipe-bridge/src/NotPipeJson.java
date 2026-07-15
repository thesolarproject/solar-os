package com.solar.launcher.xposed.notpipe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 2026-07-06 — Minimal JSON for notPipe VideoInfo rows over broadcast IPC.
 * Layman: turns notPipe search results into text Solar can read.
 * Technical: reflection on VideoInfo/Comment fields (clear or ProGuard aliases).
 * 2026-07-14 — Alias field names for release minify (c.d / c.b).
 * Reversal: delete with bridge — Solar uses its own parser on same JSON shape.
 */
public final class NotPipeJson {

    private NotPipeJson() {}

    static String videosToJson(List<?> videos) throws Exception {
        JSONArray arr = new JSONArray();
        if (videos == null) return arr.toString();
        for (int i = 0; i < videos.size(); i++) {
            Object v = videos.get(i);
            if (v == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", NotPipeReflect.field(v, "id"));
            o.put("title", NotPipeReflect.field(v, "title"));
            o.put("author", NotPipeReflect.field(v, "channel"));
            o.put("length", NotPipeReflect.field(v, "duration"));
            arr.put(o);
        }
        return arr.toString();
    }

    /**
     * Serialize notPipe Comment objects for Solar messaging-style UI.
     * Fields: author, content (channel/content public fields on Comment).
     */
    static String commentsToJson(List<?> comments) throws Exception {
        JSONArray arr = new JSONArray();
        if (comments == null) return arr.toString();
        int n = Math.min(comments.size(), 80);
        for (int i = 0; i < n; i++) {
            Object c = comments.get(i);
            if (c == null) continue;
            JSONObject o = new JSONObject();
            o.put("author", NotPipeReflect.field(c, "channel"));
            o.put("content", NotPipeReflect.field(c, "content"));
            arr.put(o);
        }
        return arr.toString();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
