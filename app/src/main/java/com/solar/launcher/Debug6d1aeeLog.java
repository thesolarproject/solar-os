package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 6d1aee — global overlay volume vs FM radio stream routing.
 * Pull: adb pull /storage/sdcard0/.solar/debug-6d1aee.log .cursor/
 */
public final class Debug6d1aeeLog {

    private static final String TAG = "SolarDbg6d1aee";
    private static final String SESSION = "6d1aee";
    private static final String FILE = "debug-6d1aee.log";
    /** Off by default — enable via adb harness when debugging FM volume routing. */
    public static volatile boolean ENABLED = false;

    private Debug6d1aeeLog() {}

    /** One NDJSON line — FM vs MUSIC volume stream diagnostics. */
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
