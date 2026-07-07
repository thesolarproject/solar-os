package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session 4ae0fd — Get Music search wheel / recent-search focus. Pull via adb from .solar/. */
public final class Debug4ae0fdLog {
    private static final String TAG = "SolarDbg4ae0fd";
    private static final String SESSION = "4ae0fd";
    private static final String FILE = "debug-4ae0fd.log";
    /** ponytail: sync file I/O only for short debug sessions. */
    public static volatile boolean ENABLED = false;

    private Debug4ae0fdLog() {}

    /** One NDJSON line for Get Music browse wheel diagnostics. */
    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
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
            File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
            if (sdRoot != null) {
                try {
                    File dir = new File(sdRoot, ".solar");
                    if (!dir.exists()) dir.mkdirs();
                    append(new File(dir, FILE), line);
                } catch (Exception ignored2) {}
            }
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored3) {}
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
