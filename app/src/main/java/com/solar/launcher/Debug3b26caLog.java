package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-06: Debug session 3b26ca — library years + Flow album art ingest.
 * Pull: adb shell cat /storage/sdcard0/solar/debug-3b26ca.log
 * Or Y2: adb shell cat /storage/sdcard1/solar/debug-3b26ca.log
 */
public final class Debug3b26caLog {

    private static final String TAG = "SolarDbg3b26ca";
    private static final String SESSION = "3b26ca";
    private static final String FILE = "debug-3b26ca.log";

    private Debug3b26caLog() {}

    /** Always emits during 3b26ca debug — years + album art hunt. */
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

    private static void appendLine(String line) {
        try {
            File sd0 = new File("/storage/sdcard0/solar");
            if (!sd0.exists()) sd0.mkdirs();
            FileWriter w = new FileWriter(new File(sd0, FILE), true);
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
