package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05 — Debug session ff7a07: Xposed modules menu freeze/crash timing.
 * Pull: adb pull /storage/sdcard0/solar/debug-ff7a07.log .cursor/debug-ff7a07.log
 */
public final class DebugXposedMenuLog {
    private static final String TAG = "SolarDbgff7a07";
    private static final String SESSION = "ff7a07";
    private static final String FILE = "debug-ff7a07.log";
    /** Flip on for Xposed menu freeze investigation only. */
    public static volatile boolean ENABLED = false;

    private DebugXposedMenuLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("thread", Thread.currentThread().getName());
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            writeFile("/storage/sdcard0/solar/" + FILE, line);
            writeFile("/storage/sdcard1/solar/" + FILE, line);
            writeFile("/data/data/com.solar.launcher/files/" + FILE, line);
        } catch (Exception ignored) {}
    }

    private static void writeFile(String path, String line) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
