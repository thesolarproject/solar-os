package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session e47f4c — FM MTK fallback input handoff from Solar radio journey.
 * Pull: adb -s SERIAL shell cat /storage/sdcard0/.solar/debug-e47f4c.log
 */
public final class DebugE47f4cLog {

    private static final String TAG = "SolarDbgE47f4c";
    private static final String SESSION = "e47f4c";
    private static final String FILE = "debug-e47f4c.log";
    /** Off by default — FM fallback tracing should not tax normal wheel handoff. */
    public static volatile boolean ENABLED = false;

    private DebugE47f4cLog() {}

    public static void log(String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
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
            append(new File("/data/data/com.solar.launcher/files", FILE), line);
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                append(new File(dir, FILE), line);
            }
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
