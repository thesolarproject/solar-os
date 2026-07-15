package com.solar.launcher.youtube.api;

import android.content.Context;

import com.solar.launcher.SolarLog;
import com.solar.launcher.net.SolarHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 2026-07-15 — Refresh Solar YouTube instance pools from notPipe.json shape.
 * Layman: downloads a list of working YouTube frontends so one dead site does not kill browse.
 * Technical: keys invidious / piped / ytapilegacy; fail-open keeps previous seeds.
 * Reversal: delete; InstancesConfig seeds-only forever.
 */
public final class InstancesUpdater {

    private static final String TAG = "YtInstancesUpdater";
    /** Refresh at most once per day unless forced. */
    private static final long MIN_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);

    private InstancesUpdater() {}

    /**
     * Background-safe: may hit the network. Call from a worker thread.
     * @return true when lists were replaced from remote JSON
     */
    public static boolean updateIfStale(Context ctx, boolean force) {
        if (ctx == null) return false;
        InstancesConfig cfg = new InstancesConfig(ctx);
        cfg.ensureSeeds();
        long last = cfg.getLastUpdateMs();
        if (!force && last > 0 && (System.currentTimeMillis() - last) < MIN_INTERVAL_MS) {
            return false;
        }
        return updateNow(ctx, cfg);
    }

    /** Parse notPipe.json body into three string lists (package-private for tests). */
    static ParsedInstances parseNotPipeJson(String body) throws Exception {
        JSONObject obj = new JSONObject(body);
        return new ParsedInstances(
                readStringArray(obj, "invidious"),
                readStringArray(obj, "piped"),
                readStringArray(obj, "ytapilegacy"));
    }

    private static boolean updateNow(Context ctx, InstancesConfig cfg) {
        try {
            String body = SolarHttp.getText(cfg.getUpdateUrl());
            ParsedInstances parsed = parseNotPipeJson(body);
            if (parsed.invidious.isEmpty() && parsed.piped.isEmpty()
                    && parsed.ytapi.isEmpty()) {
                SolarLog.w(TAG, "remote instance JSON empty — keep seeds");
                return false;
            }
            List<String> inv = parsed.invidious.isEmpty()
                    ? cfg.getInvidious() : parsed.invidious;
            List<String> piped = parsed.piped.isEmpty()
                    ? cfg.getPiped() : parsed.piped;
            List<String> yt = parsed.ytapi.isEmpty()
                    ? cfg.getYtApiLegacy() : parsed.ytapi;
            cfg.saveLists(inv, piped, yt);
            SolarLog.i(TAG, "updated instances inv=" + inv.size()
                    + " piped=" + piped.size() + " ytapi=" + yt.size());
            return true;
        } catch (Exception e) {
            SolarLog.w(TAG, "instance update failed: " + e.getMessage());
            return false;
        }
    }

    private static List<String> readStringArray(JSONObject obj, String key) throws Exception {
        List<String> out = new ArrayList<String>();
        if (!obj.has(key)) return out;
        JSONArray arr = obj.getJSONArray(key);
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "");
            if (s.length() > 0) out.add(s);
        }
        return out;
    }

    /** Holder for unit tests / updater. */
    static final class ParsedInstances {
        final List<String> invidious;
        final List<String> piped;
        final List<String> ytapi;

        ParsedInstances(List<String> invidious, List<String> piped, List<String> ytapi) {
            this.invidious = invidious;
            this.piped = piped;
            this.ytapi = ytapi;
        }
    }
}
