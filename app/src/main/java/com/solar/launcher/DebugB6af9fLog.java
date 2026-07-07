package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session b6af9f — Y1 USB mass storage enable path (MTP vs UMS desync).
 * Pull: adb shell su -c 'cat /storage/sdcard0/.solar/debug-b6af9f.log'
 */
public final class DebugB6af9fLog {

    private static final String TAG = "SolarDbgB6af9f";
    private static final String SESSION = "b6af9f";
    private static final String FILE = "debug-b6af9f.log";
    /** ponytail: on only for short Y1 UMS debug sessions. */
    public static volatile boolean ENABLED = false;

    private DebugB6af9fLog() {}

    /** One NDJSON line for Y1 UMS enable diagnostics. */
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
