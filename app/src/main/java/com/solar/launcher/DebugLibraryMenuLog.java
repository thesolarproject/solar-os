package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-05: Debug session f37f3f — music library menu crash instrumentation.
 * Pull: adb shell run-as com.solar.launcher cat files/debug-f37f3f.log
 * Or: adb shell cat /storage/sdcard0/solar/debug-f37f3f.log (Y1 microSD path).
 */
public final class DebugLibraryMenuLog {

    private static final String TAG = "SolarDbgf37f3f";
    private static final String SESSION = "f37f3f";
    private static final String FILE = "debug-f37f3f.log";

    private DebugLibraryMenuLog() {}

    /** Always emits during f37f3f debug — library menu crash hunt. */
    public static void log(String location, String message, String hypothesisId, JSONObject data) {
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
            appendLine(line);
        } catch (Exception ignored) {}
    }

    public static void logError(String location, String message, String hypothesisId,
            Throwable t, JSONObject data) {
        try {
            JSONObject o = data != null ? data : new JSONObject();
            if (t != null) {
                o.put("exception", t.getClass().getSimpleName());
                o.put("exceptionMsg", t.getMessage() != null ? t.getMessage() : "");
                StackTraceElement[] st = t.getStackTrace();
                if (st != null && st.length > 0) {
                    o.put("stack0", st[0].toString());
                }
            }
            log(location, message, hypothesisId, o);
        } catch (Exception ignored) {}
    }

    private static void appendLine(String line) {
        try {
            File sdDir = new File("/storage/sdcard0/solar");
            if (!sdDir.exists()) sdDir.mkdirs();
            FileWriter w = new FileWriter(new File(sdDir, FILE), true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
        try {
            File sd1 = new File("/storage/sdcard1/solar");
            if (!sd1.exists()) sd1.mkdirs();
            FileWriter w = new FileWriter(new File(sd1, FILE), true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
        try {
            File appDir = new File("/data/data/com.solar.launcher/files");
            if (!appDir.exists()) appDir.mkdirs();
            FileWriter w = new FileWriter(new File(appDir, FILE), true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
