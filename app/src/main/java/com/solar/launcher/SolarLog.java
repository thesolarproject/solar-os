package com.solar.launcher;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 2026-07-16 — App-wide error/crash logs under preferred {@code solar/logs} (app-private first).
 * Hot path: single-directory append (no dual-SD mirror per line). Crash still multi-mirrors once.
 */
public final class SolarLog {

    private static final String TAG = "SolarLog";
    /** @deprecated use {@link SolarLogPaths#preferredLogDir} — kept for older call sites. */
    public static final String LOG_DIR = SolarLogPaths.LEGACY_LOG_DIR;
    private static final String CRASH_FILE = "crash.log";
    private static final String ERROR_FILE = "error.log";
    private static final long MAX_BYTES = 256 * 1024;
    private static final Object LOCK = new Object();
    private static volatile android.content.Context appCtx;

    private SolarLog() {}

    public static void installUncaughtHandler() {
        installUncaughtHandler(null);
    }

    public static void installUncaughtHandler(android.content.Context context) {
        updateContext(context);
        final android.content.Context appCtx = context != null
                ? context.getApplicationContext() : null;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                logCrash(thread, e);
                if (appCtx != null) {
                    SolarRecoveryCoordinator.onUncaughtCrash(appCtx);
                }
                if (previous != null) {
                    previous.uncaughtException(thread, e);
                } else {
                    System.exit(1);
                }
            }
        });
    }

    public static void e(String tag, String message, Throwable t) {
        message = scrub(message);
        if (t != null) {
            Log.e(tag, message, t);
        } else {
            Log.e(tag, message);
        }
        appendPrimary(ERROR_FILE, formatLine("ERROR", tag, message, t));
        // Memory ring only — no second disk write storm on every error.
        trailFeatureRingOnly("ERROR", tag, message);
    }

    /**
     * Same as {@link #e} without feature-trail re-entry (used by SolarDiagFeatureLog).
     */
    public static void eQuiet(String tag, String message, Throwable t) {
        message = scrub(message);
        if (t != null) {
            Log.e(tag, message, t);
        } else {
            Log.e(tag, message);
        }
        appendPrimary(ERROR_FILE, formatLine("ERROR", tag, message, t));
    }

    public static void w(String tag, String message) {
        message = scrub(message);
        Log.w(tag, message);
        appendPrimary(ERROR_FILE, formatLine("WARN", tag, message, null));
        // WARN: logcat + error.log only — no feature-disk trail (hot path on devices).
    }

    /** WARN without feature trail and without recursive trails (storage probe, etc.). */
    public static void wQuiet(String tag, String message) {
        message = scrub(message);
        Log.w(tag, message);
        appendPrimary(ERROR_FILE, formatLine("WARN", tag, message, null));
    }

    public static void i(String tag, String message) {
        message = scrub(message);
        Log.i(tag, message);
    }

    private static void trailFeatureRingOnly(String level, String tag, String message) {
        try {
            com.solar.launcher.diag.SolarDiagFeatureLog.trailFromSolarLog(level, tag, message);
        } catch (Throwable ignored) {}
    }

    private static void logCrash(Thread thread, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String body = sw.toString();
            Log.e("SolarCrash", scrub(body));
            String header = "\n--- CRASH " + ts() + " thread=" + thread.getName() + " ---\n";
            // Crash: mirror once to all volumes so UMS/card-loss still has a copy.
            appendMirrored(CRASH_FILE, header + body);
            appendPrimary(ERROR_FILE, header + body);
            trailFeatureRingOnly("ERROR", "crash", "uncaught " + e.getClass().getSimpleName()
                    + " thread=" + thread.getName());
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("thread", thread.getName());
                d.put("exception", e.getClass().getName());
                d.put("message", scrub(e.getMessage() != null ? e.getMessage() : ""));
                d.put("stack", body.length() > 2000 ? scrub(body.substring(0, 2000)) : scrub(body));
                DebugSessionLog.log("SolarLog.logCrash", "uncaught", "H1", d);
                DebugB8b871Log.log(null, "SolarLog.logCrash", "uncaught", "H-E", d);
                // #region agent log
                Debug9d82a5Log.log(appCtx, "SolarLog.logCrash", "uncaught", "F", d);
                // #endregion
            } catch (Exception ignoredDbg) {}
        } catch (Exception ignored) {}
    }

    private static String formatLine(String level, String tag, String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(ts()).append(' ').append(level).append(' ').append(tag).append(": ").append(message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw.toString().trim());
        }
        return sb.toString();
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static void appendPrimary(String fileName, String text) {
        text = scrub(text);
        synchronized (LOCK) {
            try {
                SolarLogPaths.appendPrimary(appCtx, fileName, text, MAX_BYTES);
            } catch (Exception ex) {
                Log.w(TAG, "append failed: " + ex.getMessage());
            }
        }
    }

    private static void appendMirrored(String fileName, String text) {
        text = scrub(text);
        synchronized (LOCK) {
            try {
                SolarLogPaths.appendMirrored(appCtx, fileName, text, MAX_BYTES);
            } catch (Exception ex) {
                Log.w(TAG, "append mirror failed: " + ex.getMessage());
            }
        }
    }

    /** Preferred-dir append without feature trails (storage probe, etc.). */
    public static void appendPrimaryRaw(android.content.Context context, String fileName,
            String text, long maxBytes) {
        updateContext(context);
        if (text == null) return;
        synchronized (LOCK) {
            SolarLogPaths.appendPrimary(appCtx, fileName, text, maxBytes > 0 ? maxBytes : MAX_BYTES);
        }
    }

    /** @deprecated use {@link #appendPrimaryRaw} — name kept for older call sites */
    public static void appendMirroredRaw(android.content.Context context, String fileName,
            String text, long maxBytes) {
        appendPrimaryRaw(context, fileName, text, maxBytes);
    }

    static String formatStorageLine(String severity, File path, long total, long free) {
        return ts() + " STORAGE " + severity
                + " path=" + (path != null ? path.getAbsolutePath() : "?")
                + " total=" + SolarLogPaths.formatBytes(total)
                + " free=" + SolarLogPaths.formatBytes(free);
    }

    private static volatile String[] cachedSensitive = new String[0];
    private static volatile long lastSensitiveUpdate = 0;

    private static String[] getSensitiveStrings() {
        long now = System.currentTimeMillis();
        if (now - lastSensitiveUpdate < 5000 && cachedSensitive.length > 0) {
            return cachedSensitive;
        }
        java.util.ArrayList<String> list = new java.util.ArrayList<String>();
        addSensitive(list, com.solar.launcher.deezer.DeezerAccount.bundledDemoArl());
        addSensitive(list, com.solar.launcher.deezer.DeezerAccount.bundledFreeArl());
        if (appCtx != null) {
            try {
                android.content.SharedPreferences prefs = appCtx.getSharedPreferences("SOLAR_SETTINGS", android.content.Context.MODE_PRIVATE);
                addSensitive(list, prefs.getString("deezer_arl", ""));
                addSensitive(list, prefs.getString("deezer_user_name", ""));
                addSensitive(list, prefs.getString("soulseek_user", ""));
                addSensitive(list, prefs.getString("solar_diag_user", ""));
                addSensitive(list, com.solar.launcher.soulseek.SoulseekAccount.generateUsername(appCtx, false));
                android.content.SharedPreferences leg = appCtx.getSharedPreferences("solar_prefs", android.content.Context.MODE_PRIVATE);
                addSensitive(list, leg.getString("deezer_arl", ""));
                addSensitive(list, leg.getString("deezer_user_name", ""));
            } catch (Exception ignored) {}
        }
        java.util.Collections.sort(list, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        String[] arr = list.toArray(new String[0]);
        cachedSensitive = arr;
        lastSensitiveUpdate = now;
        return arr;
    }

    private static void addSensitive(java.util.ArrayList<String> list, String s) {
        if (s != null) {
            s = s.trim();
            if (s.length() >= 3 && !list.contains(s)) {
                list.add(s);
            }
        }
    }

    public static String scrub(android.content.Context context, String text) {
        updateContext(context);
        return scrub(text);
    }

    public static void updateContext(android.content.Context context) {
        if (context != null && appCtx == null) {
            appCtx = context.getApplicationContext();
        }
    }

    public static String scrub(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] s = getSensitiveStrings();
        for (String str : s) {
            if (text.contains(str)) {
                text = text.replace(str, "***");
            }
        }
        return text;
    }

    public static void scrubExistingLogs(android.content.Context context) {
        updateContext(context);
        scrubExistingLogs();
    }

    public static void scrubExistingLogs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 2026-07-16 — Only tidy solar/logs trees (mirrored volumes + app-private).
                    // Do not walk entire volume roots (avoids thrashing user media).
                    for (File logDir : SolarLogPaths.logDirs(appCtx)) {
                        scrubDir(logDir, 0);
                    }
                    if (appCtx != null) {
                        scrubDir(new File(appCtx.getFilesDir(), SolarLogPaths.REL_LOGS), 0);
                    }
                } catch (Exception ignored) {}
            }
        }, "SolarLogScrub").start();
    }

    private static void scrubDir(File f, int depth) {
        if (f == null || !f.exists() || depth > 3) return;
        if (f.isFile()) {
            String name = f.getName().toLowerCase(Locale.US);
            if (name.endsWith(".log") || name.endsWith(".old") || name.endsWith(".txt")
                    || name.startsWith("debug-") || name.contains("log")) {
                scrubFile(f);
            }
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                scrubDir(kid, depth + 1);
            }
        }
    }

    private static void scrubFile(File f) {
        try {
            if (f.length() == 0 || f.length() > 5 * 1024 * 1024) return;
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = 0;
            while (read < buf.length) {
                int n = fis.read(buf, read, buf.length - read);
                if (n <= 0) break;
                read += n;
            }
            fis.close();
            if (read <= 0) return;
            String content = new String(buf, 0, read, "UTF-8");
            String[] sensitive = getSensitiveStrings();
            boolean changed = false;
            for (String str : sensitive) {
                if (content.contains(str)) {
                    content = content.replace(str, "***");
                    changed = true;
                }
            }
            if (changed) {
                synchronized (LOCK) {
                    File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
                    FileOutputStream fos = new FileOutputStream(tmp);
                    fos.write(content.getBytes("UTF-8"));
                    fos.flush();
                    fos.close();
                    if (tmp.exists() && tmp.length() > 0) {
                        tmp.renameTo(f);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    static void selfCheck() {
        if (!"crash.log".equals(CRASH_FILE)) throw new AssertionError("crash file");
        if (quotedSsidForTest("a").equals("\"a\"")) { /* ok */ } else throw new AssertionError("quote");
        if (!"abc***xyz".equals(scrub("abc" + com.solar.launcher.deezer.DeezerAccount.bundledDemoArl() + "xyz"))) throw new AssertionError("scrub arl");
    }

    static String quotedSsidForTest(String ssid) {
        return ssid == null ? "\"\"" : "\"" + ssid + "\"";
    }
}
