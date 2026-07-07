package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;

/**
 * 2026-07-05 — Debug session 705932: Y1 MTP vs UMS regression (plug-in + enable paths).
 * Pull: adb shell su -c 'cat /data/local/tmp/debug-705932.log'
 */
public final class Debug705932Log {

    private static final String TAG = "SolarDbg705932";
    private static final String SESSION = "705932";
    private static final String FILE = "debug-705932.log";
    /** 2026-07-05 — Off in release; flip true manually for short adb debug sessions. */
    public static volatile boolean ENABLED = BuildConfig.DEBUG;

    private Debug705932Log() {}

    /** One NDJSON line — app, app_process, and shell helpers can call this. */
    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    /** app_process-safe — no DeviceFeatures dependency. */
    public static void logStandalone(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        emit(location, message, hypothesisId, data);
    }

    /** Snapshot kernel + property USB mode for hypothesis tagging. */
    public static JSONObject usbSnapshot() {
        JSONObject d = new JSONObject();
        try {
            d.put("usbConfig", readProp("sys.usb.config"));
            d.put("kernelFn", readFirstLine("/sys/class/android_usb/android0/functions"));
            d.put("lunBound", lunBound());
            d.put("model", readProp("ro.product.model"));
        } catch (Exception ignored) {}
        return d;
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
            append(new File("/data/local/tmp", FILE), line);
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                append(new File(dir, FILE), line);
            }
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
