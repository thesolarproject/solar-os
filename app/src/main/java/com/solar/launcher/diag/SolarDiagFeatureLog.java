package com.solar.launcher.diag;

import android.content.Context;
import android.util.Log;

import com.solar.launcher.SolarLog;
import com.solar.launcher.SolarLogPaths;
import com.solar.launcher.soulseek.SolarDiagnosticReporter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 2026-07-16 — Per-feature breadcrumb logs (ring buffer + mirrored solar/logs/features/).
 * Also fed from SolarLog.e/w so crash bundles include recent feature activity.
 */
public final class SolarDiagFeatureLog {
    private static final String TAG = "SolarDiagFeature";
    /** @deprecated use {@link SolarLogPaths#preferredFeatureLogDir} — legacy sdcard0 path. */
    public static final String FEATURE_LOG_DIR = SolarLogPaths.LEGACY_LOG_DIR + "/features";
    private static final int MAX_RING = 400;
    private static final long MAX_FILE_BYTES = 256 * 1024;
    private static final Object LOCK = new Object();
    private static final ConcurrentLinkedQueue<String> RING = new ConcurrentLinkedQueue<String>();
    private static volatile Context appCtx;

    private SolarDiagFeatureLog() {}

    public static void init(Context context) {
        if (context != null) appCtx = context.getApplicationContext();
    }

    /** Feature lifecycle / success path. */
    public static void event(String feature, String message) {
        write("INFO", feature, message, null);
    }

    /** Non-fatal feature problem — still useful in a diag pull. */
    public static void warn(String feature, String message) {
        write("WARN", feature, message, null);
    }

    /** Feature-level failure; also mirrors to SolarLog.error.log. */
    public static void error(String feature, String message, Throwable t) {
        write("ERROR", feature, message, t);
        // Append to SolarLog without re-entering feature ring (see SolarLog.e → trail).
        SolarLog.eQuiet("Diag/" + (feature != null ? feature : "unknown"), message, t);
        SolarDiagnosticReporter.reportFeatureError(null, feature, message, t);
    }

    public static void error(Context context, String feature, String message, Throwable t) {
        write("ERROR", feature, message, t);
        SolarLog.eQuiet("Diag/" + (feature != null ? feature : "unknown"), message, t);
        SolarDiagnosticReporter.reportFeatureError(context, feature, message, t);
    }

    /**
     * 2026-07-15 — Called from SolarLog.e/w so every app error becomes a feature breadcrumb.
     * Does not call back into SolarLog (avoids recursion).
     */
    public static void trailFromSolarLog(String level, String tag, String message) {
        String feature = featureFromTag(tag);
        write(level != null ? level : "INFO", feature, message, null);
    }

    private static String featureFromTag(String tag) {
        if (tag == null || tag.isEmpty()) return "app";
        String t = tag.trim();
        if (t.startsWith("Diag/")) t = t.substring(5);
        // Map common tags onto stable feature keys for filtering on the server.
        String lower = t.toLowerCase(Locale.US);
        if (lower.contains("soulseek") || lower.contains("reach")) return "reach";
        if (lower.contains("deezer")) return "deezer";
        if (lower.contains("podcast")) return "podcasts";
        if (lower.contains("rockbox")) return "rockbox";
        if (lower.contains("wifi") || lower.contains("wlan")) return "wifi";
        if (lower.contains("playback") || lower.contains("media") || lower.contains("player")) {
            return "playback";
        }
        if (lower.contains("theme")) return "theme";
        if (lower.contains("radio") || lower.contains("fm")) return "radio";
        if (lower.contains("youtube") || lower.contains("yt")) return "youtube";
        if (lower.contains("ota") || lower.contains("update")) return "ota";
        if (lower.contains("overlay")) return "overlay";
        if (lower.contains("platform") || lower.contains("xposed")) return "platform";
        if (t.length() > 24) return t.substring(0, 24);
        return t;
    }

    private static void write(String level, String feature, String message, Throwable t) {
        String feat = feature != null ? feature.trim() : "app";
        if (feat.isEmpty()) feat = "app";
        String msg = message != null ? message : "";
        // Local files scrub secrets; ARL is re-added intentionally only in Diag/account-context.
        msg = SolarLog.scrub(msg);
        String line = ts() + " " + level + " [" + feat + "] " + msg;
        if (t != null) {
            String em = t.getMessage();
            if (em != null) line += " :: " + SolarLog.scrub(em);
            line += " :: " + t.getClass().getSimpleName();
        }
        Log.i(TAG, line);
        pushRing(line);
        appendFile(feat, line);
        if (t != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                t.printStackTrace(new java.io.PrintWriter(sw));
                String stack = SolarLog.scrub(sw.toString());
                appendFile(feat, stack);
                pushRing(stack.length() > 500 ? stack.substring(0, 500) : stack);
            } catch (Exception ignored) {}
        }
    }

    private static void pushRing(String line) {
        RING.add(line);
        while (RING.size() > MAX_RING) {
            RING.poll();
        }
    }

    /** In-memory recent events for the snapshot (even if files failed). */
    public static String dumpRing() {
        StringBuilder sb = new StringBuilder();
        for (String s : RING) {
            if (s != null) sb.append(s).append('\n');
        }
        return sb.toString();
    }

    public static List<File> listFeatureLogFiles() {
        List<File> out = new ArrayList<File>();
        for (File logDir : SolarLogPaths.logDirs(appCtx)) {
            File dir = new File(logDir, "features");
            if (!dir.isDirectory()) continue;
            File[] kids = dir.listFiles();
            if (kids == null) continue;
            for (File f : kids) {
                if (f != null && f.isFile() && f.getName().endsWith(".log")) out.add(f);
            }
        }
        return out;
    }

    private static void appendFile(String feature, String line) {
        synchronized (LOCK) {
            try {
                String safe = feature.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (safe.length() > 40) safe = safe.substring(0, 40);
                SolarLogPaths.appendFeatureMirrored(appCtx, safe + ".log", line, MAX_FILE_BYTES);
            } catch (Exception ex) {
                Log.w(TAG, "append failed: " + ex.getMessage());
            }
        }
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
