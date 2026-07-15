package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * 2026-07-05 — Debug session 9870a6: sampled perf counters for input/overlay/USB slowdown.
 * Layman: counts hot-path work every few seconds instead of logging every keypress.
 * Technical: atomic counters flushed as NDJSON to SD .solar/debug-9870a6.log for adb pull.
 */
public final class DebugPerfLog {

    private static final String TAG = "SolarDbg9870a6";
    private static final String SESSION = "9870a6";
    private static final String FILE = "debug-9870a6.log";
    /** Remove WM overlay. */
    public static volatile boolean ENABLED = false;

    private static volatile long startedAtMs;
    private static volatile long lastFlushMs;
    private static volatile int fgProbe;
    private static volatile int handoffInject;
    private static volatile int handoffCacheHit;
    private static volatile int suPropThread;
    private static volatile int usbPoll;
    private static volatile int overlayKeyExec;
    private static volatile int overlayDebugIo;

    private DebugPerfLog() {}

    public static void markStart() {
        if (!ENABLED) return;
        startedAtMs = System.currentTimeMillis();
        lastFlushMs = startedAtMs;
    }

    public static void incFgProbe() { if (ENABLED) fgProbe++; }
    public static void incHandoffInject() { if (ENABLED) handoffInject++; }
    public static void incHandoffCacheHit() { if (ENABLED) handoffCacheHit++; }
    public static void incSuPropThread() { if (ENABLED) suPropThread++; }
    public static void incUsbPoll() { if (ENABLED) usbPoll++; }
    public static void incOverlayKeyExec() { if (ENABLED) overlayKeyExec++; }
    public static void incOverlayDebugIo() { if (ENABLED) overlayDebugIo++; }

    /** Flush counter deltas — call from main thread Handler every ~3s. */
    public static void flushSample(String source) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        long interval = now - lastFlushMs;
        if (interval < 2500L && source != null && !source.contains("force")) return;
        lastFlushMs = now;
        try {
            JSONObject d = new JSONObject();
            d.put("source", source != null ? source : "");
            d.put("elapsedMs", now - startedAtMs);
            d.put("intervalMs", interval);
            d.put("fgProbe", fgProbe);
            d.put("handoffInject", handoffInject);
            d.put("handoffCacheHit", handoffCacheHit);
            d.put("suPropThread", suPropThread);
            d.put("usbPoll", usbPoll);
            d.put("overlayKeyExec", overlayKeyExec);
            d.put("overlayDebugIo", overlayDebugIo);
            d.put("loadavg", readLoadAvg());
            emit("DebugPerfLog.flushSample", "sample", "H-ALL", d);
            fgProbe = handoffInject = handoffCacheHit = suPropThread = 0;
            usbPoll = overlayKeyExec = overlayDebugIo = 0;
        } catch (Exception ignored) {}
    }

    /** Root daemon / app_process — append one line to SD without Context. */
    public static void emitStandalone(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    private static void emit(String location, String message, String hypothesisId, JSONObject data) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) append(new File(new File(sd, ".solar"), FILE), line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
        } catch (Exception ignored) {}
    }

    private static String readLoadAvg() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/loadavg"));
            String line = r.readLine();
            r.close();
            return line != null ? line.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static void append(File f, String line) {
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
