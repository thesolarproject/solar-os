package com.solar.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 2026-07-15 — Debug session b2d21b: USB Connection prompt / wake molasses.
 * Layman: counts how often slow probes and leftover debug writers run while USB UI is up.
 * Tech: async NDJSON only (never block UI); aggregates call counts + ms.
 * Reversal: delete this class and {@code // #region agent log} call sites after verification.
 */
public final class DebugB2d21bLog {

    private static final String TAG = "SolarDbgB2d21b";
    private static final String SESSION = "b2d21b";
    private static final String FILE = "debug-b2d21b.log";
    private static final String HOST_LOG =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-b2d21b.log";
    private static final String INGEST =
            "http://127.0.0.1:7642/ingest/033bd1a9-8f26-4d94-8b67-6b91f340fc87";

    /** On for this session — disable after post-fix confirmation. */
    public static volatile boolean ENABLED = true;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final AtomicInteger probeCalls = new AtomicInteger();
    private static final AtomicLong probeMs = new AtomicLong();
    private static final AtomicInteger dbgIoCalls = new AtomicInteger();
    private static final AtomicLong dbgIoMs = new AtomicLong();
    private static final AtomicInteger conciergeCalls = new AtomicInteger();
    private static final AtomicLong conciergeMs = new AtomicLong();
    private static volatile long lastFlushAtMs = 0L;

    private DebugB2d21bLog() {}

    /** Record one UMS/kernel probe duration (hypothesis H1). */
    public static void noteProbe(String location, long elapsedMs) {
        if (!ENABLED) return;
        probeCalls.incrementAndGet();
        probeMs.addAndGet(Math.max(0L, elapsedMs));
        emitSample(null, location, "ums probe", "H1", elapsedMs, "probe");
    }

    /** Record leftover Debug* ENABLED FileWriter/HTTP cost (hypothesis H2). */
    public static void noteDebugIo(String location, long elapsedMs, String loggerName) {
        if (!ENABLED) return;
        dbgIoCalls.incrementAndGet();
        dbgIoMs.addAndGet(Math.max(0L, elapsedMs));
        emitSample(null, location, "debug io " + loggerName, "H2", elapsedMs, "dbgIo");
    }

    /** Record concierge getprop/SystemProperties cost (hypothesis H3). */
    public static void noteConcierge(String location, long elapsedMs) {
        if (!ENABLED) return;
        conciergeCalls.incrementAndGet();
        conciergeMs.addAndGet(Math.max(0L, elapsedMs));
        emitSample(null, location, "concierge prop", "H3", elapsedMs, "concierge");
    }

    /** Mark USB prompt / wake landmarks (H4/H5 context). */
    public static void noteEvent(Context ctx, String location, String message, String hypothesisId,
            JSONObject extra) {
        if (!ENABLED) return;
        emitFull(ctx, location, message, hypothesisId, extra);
    }

    /** Start timing helper for call sites. */
    public static long begin() {
        return SystemClock.elapsedRealtime();
    }

    /** Elapsed ms since {@link #begin()}. */
    public static long since(long startMs) {
        return SystemClock.elapsedRealtime() - startMs;
    }

    private static void emitSample(Context ctx, String location, String message,
            String hypothesisId, long elapsedMs, String kind) {
        long now = SystemClock.elapsedRealtime();
        // Throttle spam: one sample per kind burst, plus periodic aggregate every 750ms.
        boolean flushAgg = (now - lastFlushAtMs) >= 750L;
        if (flushAgg) {
            lastFlushAtMs = now;
            try {
                JSONObject agg = new JSONObject();
                agg.put("probeCalls", probeCalls.get());
                agg.put("probeMs", probeMs.get());
                agg.put("dbgIoCalls", dbgIoCalls.get());
                agg.put("dbgIoMs", dbgIoMs.get());
                agg.put("conciergeCalls", conciergeCalls.get());
                agg.put("conciergeMs", conciergeMs.get());
                emitFull(ctx, "DebugB2d21bLog.aggregate", "usb-perf totals", "H1,H2,H3", agg);
            } catch (Exception ignored) {}
        }
        try {
            JSONObject d = new JSONObject();
            d.put("kind", kind);
            d.put("elapsedMs", elapsedMs);
            d.put("probeCalls", probeCalls.get());
            d.put("dbgIoCalls", dbgIoCalls.get());
            d.put("conciergeCalls", conciergeCalls.get());
            emitFull(ctx, location, message, hypothesisId, d);
        } catch (Exception ignored) {}
    }

    private static void emitFull(final Context ctx, final String location, final String message,
            final String hypothesisId, final JSONObject data) {
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject o = new JSONObject();
                    o.put("sessionId", SESSION);
                    o.put("timestamp", System.currentTimeMillis());
                    o.put("location", location);
                    o.put("message", message);
                    o.put("hypothesisId", hypothesisId);
                    o.put("runId", "pre-fix");
                    if (data != null) o.put("data", data);
                    String line = o.toString();
                    Log.i(TAG, line);
                    append(new File(HOST_LOG), line);
                    if (ctx != null) {
                        try {
                            append(new File(ctx.getFilesDir(), FILE), line);
                        } catch (Exception ignored) {}
                    }
                    postIngest(line);
                } catch (Exception ignored) {}
            }
        });
    }

    private static void append(File f, String line) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }

    private static void postIngest(String line) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(INGEST);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(200);
            conn.setReadTimeout(200);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Debug-Session-Id", SESSION);
            byte[] body = line.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(body.length);
            OutputStream os = new BufferedOutputStream(conn.getOutputStream());
            os.write(body);
            os.flush();
            os.close();
            conn.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
