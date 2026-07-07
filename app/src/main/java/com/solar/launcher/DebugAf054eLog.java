package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2026-07-06 — Debug session af054e: Y1 boot stall, rescue daemon storm, MTP vs UMS.
 * Pull: adb shell su -c 'cat /data/local/tmp/debug-af054e.log'
 * 2026-07-06 — Async single-file append so hot paths (wake, USB gate) do not block on SD I/O.
 */
public final class DebugAf054eLog {

    private static final String TAG = "SolarDbgaf054e";
    private static final String SESSION = "af054e";
    private static final String FILE = "debug-af054e.log";
    /** On for af054e session; flip false after verification. */
    public static volatile boolean ENABLED = false;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    private DebugAf054eLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    /** app_process / root daemon — no Context or DeviceFeatures dependency. */
    public static void logStandalone(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    /** USB + power snapshot for boot stall hypotheses. */
    public static JSONObject bootSnapshot() {
        JSONObject d = new JSONObject();
        try {
            d.put("usbConfig", readProp("sys.usb.config"));
            d.put("kernelFn", readFirstLine("/sys/class/android_usb/android0/functions"));
            d.put("lunBound", lunBound());
            d.put("model", readProp("ro.product.model"));
            d.put("overlayActive", readProp("sys.solar.overlay.active"));
            d.put("imeActive", readProp("sys.solar.ime.active"));
        } catch (Exception ignored) {}
        return d;
    }

    private static void emit(String location, String message, String hypothesisId, JSONObject data) {
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("runId", "pre-fix");
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            if (data != null) o.put("data", data);
            final String line = o.toString();
            Log.i(TAG, line);
            WRITER.execute(new Runnable() {
                @Override
                public void run() {
                    append(new File("/data/local/tmp", FILE), line);
                }
            });
        } catch (Exception ignored) {}
    }

    private static boolean lunBound() {
        String[] paths = new String[]{
                "/sys/class/android_usb/android0/f_mass_storage/lun/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun0/file",
                "/sys/class/android_usb/android0/f_mass_storage/lun1/file"
        };
        for (String path : paths) {
            String line = readFirstLine(path);
            if (line != null && line.trim().length() > 0) return true;
        }
        return false;
    }

    private static String readProp(String key) {
        Process p = null;
        BufferedReader r = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            p.waitFor();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            try {
                if (r != null) r.close();
            } catch (Exception ignored) {}
            if (p != null) p.destroy();
        }
    }

    private static String readFirstLine(String path) {
        BufferedReader r = null;
        try {
            File f = new File(path);
            if (!f.canRead()) return "";
            r = new BufferedReader(new java.io.FileReader(f));
            String line = r.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {}
            }
        }
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
}
