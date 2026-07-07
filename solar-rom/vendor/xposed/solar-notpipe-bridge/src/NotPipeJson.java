package com.solar.launcher.xposed.notpipe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 2026-07-06 — Minimal JSON for notPipe VideoInfo rows over broadcast IPC.
 * Layman: turns notPipe search results into text Solar can read.
 * Technical: reflection on io.github.gohoski.notpipe.data.VideoInfo getters.
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
            o.put("id", field(v, "id"));
            o.put("title", field(v, "title"));
            o.put("author", field(v, "channel"));
            o.put("length", field(v, "duration"));
            arr.put(o);
        }
        return arr.toString();
    }

    private static String field(Object obj, String name) {
        try {
            Object val = obj.getClass().getField(name).get(obj);
            return val != null ? String.valueOf(val) : "";
        } catch (Exception e) {
            return "";
        }
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
