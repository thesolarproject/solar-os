package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 4881df — power menu / BACK-long / GlobalActions tracing in Solar app.
 * Pull: adb shell cat /storage/sdcard1/.solar/debug-4881df.log (Y2)
 *       adb shell cat /storage/sdcard0/.solar/debug-4881df.log (Y1)
 */
public final class DebugMenuLog {

    private static final String TAG = "SolarDbg4881df";
    private static final String SESSION = "4881df";
    private static final String FILE = "debug-4881df.log";
    /** Active for this debug session only — disable after verification. */
    public static volatile boolean ENABLED = false;

    private DebugMenuLog() {}

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
