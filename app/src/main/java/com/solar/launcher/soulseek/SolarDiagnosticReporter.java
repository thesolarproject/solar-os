package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.solar.launcher.ConnectivityHelper;
import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.SolarLog;
import com.solar.launcher.SolarLogPaths;
import com.solar.launcher.diag.SolarDiagClient;
import com.solar.launcher.diag.SolarDiagContextCollector;
import com.solar.launcher.diag.SolarDiagFeatureLog;
import com.solar.launcher.diag.SolarDiagWifiWake;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 2026-07-16 — Collects crash/error/feature logs and ships them to the solar-diag worker.
 * Rate-limited; uses SolarHttp (TLS 1.2). Remote pull via Soulseek command from developer accounts.
 */
public final class SolarDiagnosticReporter {
    public static final String PREF_DIAG_AUTO_REPORT = "solar_diag_auto_report";
    public static final String PREF_DIAG_SENT_MANIFEST = "solar_diag_sent_manifest";
    public static final String PREF_FEATURE_COOLDOWN = "solar_diag_feature_cooldown";

    private static final long MIN_SCAN_INTERVAL_MS = 5L * 60L * 1000L;
    private static final long MIN_RECONNECT_INTERVAL_MS = 5L * 60L * 1000L;
    private static final long SESSION_RETRY_MS = 120_000L;
    private static final long FEATURE_ERROR_COOLDOWN_MS = 45L * 60L * 1000L;
    private static final int LOGCAT_LINES = 400;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TOTAL_BYTES = 1200 * 1024;
    private static final long[] BOOT_RETRY_MS = {3000L, 15000L, 60000L, 180000L};

    public enum ScanMode {
        STARTUP,
        ROUTINE,
        SUPPORT_OPEN,
        REMOTE_PULL
    }

    public interface RemotePullCallback {
        void onComplete(boolean ok, int issueNumber, String htmlUrl, String error);
    }

    private static final AtomicBoolean scanRunning = new AtomicBoolean(false);
    private static volatile long lastScanMs;
    private static volatile boolean bootScanPending = true;
    private static volatile boolean firstInternetScanDone;
    private static final AtomicInteger retryGeneration = new AtomicInteger();
    private static final Map<String, Long> featureCooldown = new HashMap<String, Long>();

    private SolarDiagnosticReporter() {}

    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_DIAG_AUTO_REPORT, true);
    }

    private static boolean isBackgroundShippingAllowed(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    public static void shipOnDeveloperSupportOpen(final Context context, final SharedPreferences prefs) {
        if (!isEnabled(prefs) || context == null) return;
        startScan(context.getApplicationContext(), prefs, ScanMode.SUPPORT_OPEN, null, null);
    }

    public static void shipOnRemoteDiagCommand(final Context context, final SharedPreferences prefs,
            final String replyToDev, final RemotePullCallback callback) {
        if (context == null) {
            if (callback != null) callback.onComplete(false, 0, "", "no_context");
            return;
        }
        final Context app = context.getApplicationContext();
        final SharedPreferences p = prefs != null ? prefs
                : app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        startScan(app, p, ScanMode.REMOTE_PULL, replyToDev, callback);
    }

    public static void reportFeatureError(Context context, String feature, String message,
            Throwable t) {
        if (feature == null || feature.trim().isEmpty()) feature = "app";
        final String key = feature.trim().toLowerCase(Locale.US);
        long now = System.currentTimeMillis();
        synchronized (featureCooldown) {
            Long last = featureCooldown.get(key);
            if (last != null && now - last < FEATURE_ERROR_COOLDOWN_MS) return;
            featureCooldown.put(key, now);
        }
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        if (!isEnabled(prefs) && !hasRecentCrashLog()) return;
        final String feat = key;
        final String summary = message != null ? message : "";
        final String errDetail = t != null
                ? (t.getClass().getSimpleName() + ": " + (t.getMessage() != null ? t.getMessage() : ""))
                : summary;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SolarDiagWifiWake.Session wake =
                            SolarDiagWifiWake.ensureOnlineForShip(app, prefs, false);
                    try {
                        if (!wake.online && !ConnectivityHelper.isOnline(app)) return;
                        runFeatureErrorShip(app, prefs, feat, errDetail);
                    } finally {
                        SolarDiagWifiWake.restoreAfterShip(app, prefs, wake);
                    }
                } catch (Exception ignored) {}
            }
        }, "SolarDiagFeatureErr").start();
    }

    public static void onProcessStart(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        SolarDiagFeatureLog.event("app", "process_start sdk=" + Build.VERSION.SDK_INT
                + " model=" + Build.MODEL);
        scheduleBootScan(app, prefs);
    }

    public static void onReachInternetAvailable(final Context context, final SharedPreferences prefs) {
        if (context == null) return;
        if (!firstInternetScanDone) {
            firstInternetScanDone = true;
            startScan(context.getApplicationContext(), prefs, ScanMode.STARTUP, null, null);
            return;
        }
        if (!isBackgroundShippingAllowed(prefs)) return;
        scheduleIfNeeded(context, prefs);
    }

    public static void onWifiAvailable(final Context context, final SharedPreferences prefs) {
        if (!isBackgroundShippingAllowed(prefs) || context == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanMs < MIN_RECONNECT_INTERVAL_MS) return;
        scheduleIfNeeded(context, prefs);
    }

    public static void scheduleIfNeeded(final Context context, final SharedPreferences prefs) {
        if (!isBackgroundShippingAllowed(prefs) || context == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanMs < MIN_SCAN_INTERVAL_MS) return;
        startScan(context.getApplicationContext(), prefs, ScanMode.ROUTINE, null, null);
    }

    public static void scheduleUrgent(final Context context, final SharedPreferences prefs) {
        if (context == null) return;
        startScan(context.getApplicationContext(), prefs, ScanMode.STARTUP, null, null);
    }

    private static void scheduleBootScan(final Context context, final SharedPreferences prefs) {
        if (!bootScanPending) return;
        bootScanPending = false;
        final int gen = retryGeneration.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (long delay : BOOT_RETRY_MS) {
                    if (gen != retryGeneration.get()) return;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (!ConnectivityHelper.isOnline(context)) continue;
                    startScan(context, prefs, ScanMode.STARTUP, null, null);
                    return;
                }
            }
        }, "SolarDiagBoot").start();
    }

    private static void startScan(final Context context, final SharedPreferences prefs,
            final ScanMode mode, final String replyToDev, final RemotePullCallback callback) {
        if (context == null || prefs == null) {
            if (callback != null) callback.onComplete(false, 0, "", "bad_args");
            return;
        }
        if (!scanRunning.compareAndSet(false, true)) {
            if (callback != null) callback.onComplete(false, 0, "", "busy");
            return;
        }
        lastScanMs = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runScan(context, prefs, mode, replyToDev, callback);
                } finally {
                    scanRunning.set(false);
                }
            }
        }, "SolarDiagScan").start();
    }

    static void runScan(Context context, SharedPreferences prefs, ScanMode mode,
            String replyToDev, RemotePullCallback callback) {
        if (!SolarDiagClient.isConfigured()) {
            if (callback != null) callback.onComplete(false, 0, "", "not_configured");
            return;
        }
        if (mode == ScanMode.ROUTINE || mode == ScanMode.SUPPORT_OPEN) {
            if (!isEnabled(prefs)) {
                if (callback != null) callback.onComplete(false, 0, "", "disabled");
                return;
            }
        }
        if (mode == ScanMode.STARTUP && !isEnabled(prefs) && !hasRecentCrashLog()) {
            if (callback != null) callback.onComplete(false, 0, "", "disabled");
            return;
        }

        boolean remote = mode == ScanMode.REMOTE_PULL;
        SolarDiagWifiWake.Session wake =
                SolarDiagWifiWake.ensureOnlineForShip(context, prefs, remote);
        try {
            if (!wake.online && !ConnectivityHelper.isOnline(context)) {
                SolarDiagFeatureLog.warn("diag", "offline after wifi_wake mode=" + mode.name());
                if (callback != null) callback.onComplete(false, 0, "", "offline");
                if (mode != ScanMode.REMOTE_PULL) {
                    scheduleSessionRetry(context, prefs, mode);
                }
                return;
            }
            runScanOnline(context, prefs, mode, replyToDev, callback);
        } finally {
            SolarDiagWifiWake.restoreAfterShip(context, prefs, wake);
        }
    }

    private static void runScanOnline(Context context, SharedPreferences prefs, ScanMode mode,
            String replyToDev, RemotePullCallback callback) {
        SoulseekAccount main = SoulseekAccount.load(prefs, context);
        List<LogSource> sources = collectSources(context, prefs);
        JSONObject manifest = loadManifest(prefs);
        JSONObject updated = new JSONObject();
        try {
            Iterator<String> keys = manifest.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                updated.put(k, manifest.optLong(k, 0));
            }
        } catch (Exception ignored) {}

        List<SolarDiagClient.FilePart> parts = new ArrayList<SolarDiagClient.FilePart>();
        int budget = MAX_TOTAL_BYTES;

        String env = SolarDiagContextCollector.collectEnvironment(context);
        parts.add(new SolarDiagClient.FilePart("Diag/environment.txt", env));
        budget -= env.length();
        String account = SolarDiagContextCollector.collectAccountContext(context, prefs);
        parts.add(new SolarDiagClient.FilePart("Diag/account-context.txt", account));
        budget -= account.length();
        String ring = SolarDiagFeatureLog.dumpRing();
        if (ring != null && !ring.isEmpty()) {
            parts.add(new SolarDiagClient.FilePart("Diag/feature-ring.txt", ring));
            budget -= ring.length();
        }

        boolean forceAll = mode == ScanMode.REMOTE_PULL || mode == ScanMode.SUPPORT_OPEN;
        int shippedFiles = 0;
        for (LogSource src : sources) {
            if (src == null || src.file == null || !src.file.isFile()) continue;
            long mtime = src.file.lastModified();
            String key = src.file.getAbsolutePath();
            if (!forceAll && !shouldShipSource(src.label, manifest, key, mtime, mode)) continue;
            int cap = Math.min(MAX_FILE_BYTES, Math.max(0, budget));
            if (cap < 1024) break;
            String content;
            try {
                content = readFileTail(src.file, cap);
            } catch (Exception e) {
                continue;
            }
            if (content == null || content.isEmpty()) {
                try {
                    updated.put(key, mtime);
                } catch (Exception ignored) {}
                continue;
            }
            content = SolarLog.scrub(context, content);
            parts.add(new SolarDiagClient.FilePart(src.label, content));
            budget -= content.length();
            shippedFiles++;
            try {
                updated.put(key, mtime);
            } catch (Exception ignored) {}
        }

        String type = typeForMode(mode, sources);
        String feature = "";
        String trigger = triggerForMode(mode);
        String usernameForIssue = null;
        if (mode == ScanMode.REMOTE_PULL) {
            type = "diag_pull";
            trigger = "remote_pull";
            usernameForIssue = main != null ? main.username : null;
        } else if (mode == ScanMode.STARTUP && hasRecentCrashLog()) {
            type = "crash";
            trigger = "crash";
        }

        String summary = "mode=" + mode.name() + " files=" + shippedFiles
                + " sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL;
        JSONObject device = SolarDiagContextCollector.deviceJson(context);
        SolarDiagClient.Result result = SolarDiagClient.submit(
                type, feature, trigger, usernameForIssue, device, summary, null, parts);

        if (result.ok) {
            prefs.edit().putString(PREF_DIAG_SENT_MANIFEST, updated.toString()).apply();
            SolarDiagFeatureLog.event("diag", "shipped issue=" + result.issueNumber
                    + " mode=" + mode.name());
        } else {
            SolarDiagFeatureLog.warn("diag", "ship_failed mode=" + mode.name()
                    + " err=" + result.error);
            if (mode != ScanMode.REMOTE_PULL) {
                scheduleSessionRetry(context, prefs, mode);
            }
        }

        if (callback != null) {
            callback.onComplete(result.ok, result.issueNumber, result.htmlUrl, result.error);
        }

        if (mode == ScanMode.REMOTE_PULL && replyToDev != null && !replyToDev.isEmpty()) {
            sendDiagConfirmation(replyToDev, result);
        }
    }

    private static void runFeatureErrorShip(Context context, SharedPreferences prefs,
            String feature, String summary) {
        if (!SolarDiagClient.isConfigured()) return;
        List<SolarDiagClient.FilePart> parts = new ArrayList<SolarDiagClient.FilePart>();
        parts.add(new SolarDiagClient.FilePart("Diag/environment.txt",
                SolarDiagContextCollector.collectEnvironment(context)));
        parts.add(new SolarDiagClient.FilePart("Diag/account-context.txt",
                SolarDiagContextCollector.collectAccountContext(context, prefs)));
        String ring = SolarDiagFeatureLog.dumpRing();
        if (ring != null && !ring.isEmpty()) {
            parts.add(new SolarDiagClient.FilePart("Diag/feature-ring.txt", ring));
        }
        File preferred = SolarLogPaths.preferredLogDir(context);
        File err = new File(preferred, "error.log");
        if (err.isFile()) {
            try {
                parts.add(new SolarDiagClient.FilePart("SolarLog/error.log",
                        SolarLog.scrub(context, readFileTail(err, MAX_FILE_BYTES))));
            } catch (Exception ignored) {}
        }
        File crash = new File(preferred, "crash.log");
        if (crash.isFile()) {
            try {
                parts.add(new SolarDiagClient.FilePart("SolarLog/crash.log",
                        SolarLog.scrub(context, readFileTail(crash, MAX_FILE_BYTES))));
            } catch (Exception ignored) {}
        }
        File storage = new File(preferred, "storage.log");
        if (storage.isFile()) {
            try {
                parts.add(new SolarDiagClient.FilePart("SolarLog/storage.log",
                        SolarLog.scrub(context, readFileTail(storage, MAX_FILE_BYTES))));
            } catch (Exception ignored) {}
        }
        JSONObject device = SolarDiagContextCollector.deviceJson(context);
        SolarDiagClient.submit("error", feature, "error", null, device, summary, null, parts);
    }

    private static void sendDiagConfirmation(String replyToDev, SolarDiagClient.Result result) {
        try {
            SoulseekClient client = null;
            try {
                client = com.solar.launcher.MainActivity.getActiveSoulseekClient();
            } catch (Throwable ignored) {}
            if (client == null || !client.isLoggedIn()) return;
            boolean ok = result != null && result.ok;
            int num = result != null ? result.issueNumber : 0;
            String text = SolarDeveloperAccounts.formatDiagConfirmation(ok, num);
            client.sendPrivateMessageSync(replyToDev, text);
        } catch (Exception ignored) {}
    }

    private static String typeForMode(ScanMode mode, List<LogSource> sources) {
        if (mode == ScanMode.STARTUP) return hasRecentCrashLog() ? "crash" : "startup";
        if (mode == ScanMode.REMOTE_PULL) return "diag_pull";
        if (mode == ScanMode.SUPPORT_OPEN) return "other";
        return "other";
    }

    private static String triggerForMode(ScanMode mode) {
        if (mode == ScanMode.REMOTE_PULL) return "remote_pull";
        if (mode == ScanMode.STARTUP) return hasRecentCrashLog() ? "crash" : "routine";
        return "routine";
    }

    private static boolean hasRecentCrashLog() {
        long window = 7L * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        for (File dir : SolarLogPaths.logDirs(null)) {
            File crash = new File(dir, "crash.log");
            if (crash.isFile() && now - crash.lastModified() < window) return true;
        }
        return false;
    }

    static boolean shouldShipSource(String label, JSONObject manifest, String path, long mtime,
            ScanMode mode) {
        if (mode == ScanMode.SUPPORT_OPEN || mode == ScanMode.REMOTE_PULL) return true;
        if (mode == ScanMode.STARTUP && isPriorityStartupSource(label)) return true;
        return manifest.optLong(path, -1) != mtime;
    }

    static boolean isPriorityStartupSource(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.US);
        if (lower.contains("crash.log") || lower.contains("error.log")) return true;
        if (lower.contains("logcat") || lower.contains("snapshot")) return true;
        if (lower.startsWith("solar/debug-")) return true;
        if (lower.startsWith("features/") || lower.contains("feature")) return true;
        if (lower.startsWith("diag/")) return true;
        return false;
    }

    private static void scheduleSessionRetry(final Context context, final SharedPreferences prefs,
            final ScanMode mode) {
        final int gen = retryGeneration.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(SESSION_RETRY_MS);
                } catch (InterruptedException ignored) {}
                if (gen != retryGeneration.get()) return;
                if (mode == ScanMode.ROUTINE && !isEnabled(prefs)) return;
                if (!ConnectivityHelper.isOnline(context)) return;
                startScan(context, prefs, mode, null, null);
            }
        }, "SolarDiagRetry").start();
    }

    static List<String> splitContent(String content, int maxChars) {
        List<String> out = new ArrayList<String>();
        if (content == null || content.isEmpty()) return out;
        if (content.length() <= maxChars) {
            out.add(content);
            return out;
        }
        for (int i = 0; i < content.length(); i += maxChars) {
            out.add(content.substring(i, Math.min(content.length(), i + maxChars)));
        }
        return out;
    }

    private static JSONObject loadManifest(SharedPreferences prefs) {
        try {
            return new JSONObject(prefs.getString(PREF_DIAG_SENT_MANIFEST, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String readFileTail(File f, int maxBytes) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            long len = f.length();
            long skip = len > maxBytes ? len - maxBytes : 0;
            if (skip > 0) in.skip(skip);
            byte[] buf = new byte[(int) Math.min(maxBytes, len > 0 ? len : maxBytes)];
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n, "UTF-8");
        } finally {
            in.close();
        }
    }

    static final class LogSource {
        final String label;
        final File file;

        LogSource(String label, File file) {
            this.label = label;
            this.file = file;
        }
    }

    static List<LogSource> collectSources(Context context) {
        return collectSources(context, null);
    }

    static List<LogSource> collectSources(Context context, SharedPreferences prefs) {
        List<LogSource> out = new ArrayList<LogSource>();
        // 2026-07-16 — Canonical mirrored solar/logs only (plus one-shot snapshots).
        // Avoid scattering new files; still collect any leftover debug-* under solar/ for old builds.
        int vol = 0;
        for (File logDir : SolarLogPaths.logDirs(context)) {
            String prefix = "SolarLog" + (vol == 0 ? "" : "/vol" + vol);
            addIfFile(out, prefix + "/crash.log", new File(logDir, "crash.log"));
            addIfFile(out, prefix + "/crash.log.old", new File(logDir, "crash.log.old"));
            addIfFile(out, prefix + "/error.log", new File(logDir, "error.log"));
            addIfFile(out, prefix + "/error.log.old", new File(logDir, "error.log.old"));
            addIfFile(out, prefix + "/storage.log", new File(logDir, "storage.log"));
            addLogTree(new File(logDir, "features"), out, prefix + "/features");
            addDebugLogs(logDir, out);
            // Legacy agent debug files nested under solar/ only (not volume root clutter).
            File solarRoot = logDir.getParentFile();
            if (solarRoot != null) addDebugLogs(solarRoot, out);
            vol++;
        }
        for (File root : DeviceFeatures.getStorageRoots()) {
            if (root == null) continue;
            collectRockboxLogs(new File(root, ".rockbox"), out, "Rockbox/" + root.getName());
        }
        addDeviceSnapshot(out);
        addLogcatSnapshot(out);
        if (context != null) {
            addIfFile(out, "Platform/prep.log",
                    new File(context.getFilesDir(), "platform-prep.log"));
        }
        return out;
    }

    private static void addLogTree(File dir, List<LogSource> out, String prefix) {
        if (dir == null || !dir.exists()) return;
        if (dir.isFile() && dir.getName().endsWith(".log")) {
            out.add(new LogSource(prefix + "/" + dir.getName(), dir));
            return;
        }
        if (!dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f == null) continue;
            if (f.isDirectory()) {
                addLogTree(f, out, prefix + "/" + f.getName());
            } else if (f.isFile() && (f.getName().endsWith(".log")
                    || f.getName().endsWith(".log.old")
                    || f.getName().endsWith(".txt"))) {
                out.add(new LogSource(prefix + "/" + f.getName(), f));
            }
        }
    }

    private static void addDebugLogs(File dir, List<LogSource> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isFile() && f.getName().startsWith("debug-") && f.getName().endsWith(".log")) {
                out.add(new LogSource("Solar/" + f.getName(), f));
            }
        }
    }

    private static void collectRockboxLogs(File dir, List<LogSource> out, String prefix) {
        if (dir == null || !dir.exists()) return;
        if (dir.isFile() && dir.getName().endsWith(".log")) {
            out.add(new LogSource(prefix + "/" + dir.getName(), dir));
            return;
        }
        if (!dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            collectRockboxLogs(f, out, prefix + "/" + f.getName());
        }
    }

    private static void addIfFile(List<LogSource> out, String label, File f) {
        if (f != null && f.isFile()) out.add(new LogSource(label, f));
    }

    private static void addDeviceSnapshot(List<LogSource> out) {
        StringBuilder sb = new StringBuilder();
        sb.append("model: ").append(Build.MODEL).append('\n');
        sb.append("device: ").append(Build.DEVICE).append('\n');
        sb.append("brand: ").append(Build.BRAND).append('\n');
        sb.append("sdk: ").append(Build.VERSION.SDK_INT).append('\n');
        sb.append("release: ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("fingerprint: ").append(Build.FINGERPRINT).append('\n');
        sb.append("proc: ").append(readOneLine("/proc/version")).append('\n');
        File tmp = new File("/data/data/com.solar.launcher/cache/solar_device_snapshot.txt");
        try {
            java.io.FileWriter w = new java.io.FileWriter(tmp);
            w.write(SolarLog.scrub(sb.toString()));
            w.close();
            out.add(new LogSource("Device/snapshot.txt", tmp));
        } catch (Exception ignored) {}
    }

    private static void addLogcatSnapshot(List<LogSource> out) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "logcat", "-d", "-t", String.valueOf(LOGCAT_LINES)
            });
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            br.close();
            File tmp = new File("/data/data/com.solar.launcher/cache/solar_logcat_snapshot.txt");
            java.io.FileWriter w = new java.io.FileWriter(tmp);
            w.write(SolarLog.scrub(sb.toString()));
            w.close();
            out.add(new LogSource("Android/logcat.txt", tmp));
        } catch (Exception ignored) {}
    }

    private static String readOneLine(String path) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            String line = br.readLine();
            br.close();
            return line != null ? line : "";
        } catch (Exception e) {
            return "";
        }
    }
}
