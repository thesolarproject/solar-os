package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debug session 62b1bb — Y2 volume + UI responsiveness on Now Playing.
 * Pull: adb pull /storage/sdcard1/.solar/debug-62b1bb.log .cursor/debug-62b1bb.log
 */
public final class Debug62b1bbLog {

    private static final String TAG = "SolarDbg62b1bb";
    private static final String SESSION = "62b1bb";
    private static final String FILE = "debug-62b1bb.log";
    private static final ExecutorService FILE_EXEC = Executors.newSingleThreadExecutor();

    /** Off by default — flip true only for focused volume/UI debug (2026-07-11). */
    public static volatile boolean ENABLED = false;

    private Debug62b1bbLog() {}

    /** One NDJSON line — volume routing and overlay key-swallow diagnostics. */
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
            final String line = o.toString();
            Log.i(TAG, line);
            FILE_EXEC.execute(new Runnable() {
                @Override
                public void run() {
                    appendLine(line);
                }
            });
        } catch (Exception ignored) {}
    }

    private static void appendLine(String line) {
        try {
            File sd = DeviceFeatures.getPrimaryStorageRoot();
            if (sd != null) {
                File dir = new File(sd, ".solar");
                if (!dir.exists()) dir.mkdirs();
                FileWriter w = new FileWriter(new File(dir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
            }
        } catch (Exception ignored) {}
    }
}
