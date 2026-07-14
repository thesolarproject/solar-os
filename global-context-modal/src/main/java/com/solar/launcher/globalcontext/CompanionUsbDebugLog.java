package com.solar.launcher.globalcontext;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * Debug session 62b1bb — USB storage companion shell + UMS enable path.
 * Layman: quiet log file on SD so we can verify lock paint and disk-mode toggle on hardware.
 * Technical: append NDJSON to /.solar/debug-62b1bb.log on sdcard0 then sdcard1.
 * Reversal: delete once USB lock + enable are verified on Y1/Y2.
 */
public final class CompanionUsbDebugLog {

    private static final String TAG = "SolarUsbDbg62b1bb";
    private static final String SESSION = "62b1bb";
    private static final String FILE = "debug-62b1bb.log";
    private static final String[] ROOTS = {
            "/storage/sdcard1",
            "/storage/sdcard0",
            "/mnt/sdcard",
            "/sdcard"
    };

    public static volatile boolean ENABLED = false;

    private CompanionUsbDebugLog() {}

    /** One NDJSON line for USB tier paint and UMS shell results. */
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
            appendLine(line);
        } catch (Exception ignored) {}
    }

    private static void appendLine(String line) {
        for (int i = 0; i < ROOTS.length; i++) {
            try {
                File dir = new File(ROOTS[i], ".solar");
                if (!dir.exists() && !dir.mkdirs()) continue;
                FileWriter w = new FileWriter(new File(dir, FILE), true);
                w.write(line);
                w.write('\n');
                w.close();
                return;
            } catch (Exception ignored) {}
        }
    }
}
