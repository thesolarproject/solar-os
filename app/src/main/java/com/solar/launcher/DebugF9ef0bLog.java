package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/** Debug session f9ef0b — FM radio / OK routing; pull from SD .solar/ or app files via adb. */
public final class DebugF9ef0bLog {
    private static final String TAG = "SolarDbgF9ef0b";
    private static final String FILE = "debug-f9ef0b.log";
    private static final String SESSION = "f9ef0b";
    /** Off by default — FM session tracing must be opt-in so playback paths stay lightweight. */
    public static volatile boolean ENABLED = false;

    private DebugF9ef0bLog() {}

    public static void log(Context ctx, String location, String message, String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            long ts = System.currentTimeMillis();
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", ts);
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
                    FileWriter w = new FileWriter(new File(dir, FILE), true);
                    w.write(line);
                    w.write('\n');
                    w.close();
                } catch (Exception ignored2) {}
            }
            if (ctx != null) {
                try {
                    FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE), true);
                    w.write(line);
                    w.write('\n');
                    w.close();
                } catch (Exception ignored3) {}
            }
        } catch (Exception ignored) {}
    }
}
