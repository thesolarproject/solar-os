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
 * 2026-07-16 — Ships crash/error logs to the solar-diag worker (TLS 1.2).
 * Event-only background ships on low-priority threads so UX stays smooth:
 * startup, Wi‑Fi connect, pre-Wi‑Fi-off flush, crash, power-off/restart,
 * user Report Issue, remote solar_diag pull. No periodic spam.
 */
public final class SolarDiagnosticReporter {
    public static final String PREF_DIAG_AUTO_REPORT = "solar_diag_auto_report";
    public static final String PREF_DIAG_SENT_MANIFEST = "solar_diag_sent_manifest";
    public static final String PREF_FEATURE_COOLDOWN = "solar_diag_feature_cooldown";

    /** Min gap between Wi‑Fi-connect ships (frequent enough, not thrashy). */
    private static final long MIN_WIFI_SHIP_INTERVAL_MS = 45L * 60L * 1000L;
    private static final long SESSION_RETRY_MS = 30L * 60L * 1000L;
    private static final long FEATURE_ERROR_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    /** Max wait for log export before power-off/restart proceeds. */
    private static final long POWER_SHIP_TIMEOUT_MS = 12_000L;
    /** Max wait before Wi‑Fi radio is allowed to drop (keep UX snappy). */
    private static final long WIFI_OFF_SHIP_TIMEOUT_MS = 8_000L;
    private static final int LOGCAT_LINES_FULL = 200;
    private static final int MAX_FILE_BYTES = 48 * 1024;
    private static final int MAX_FILE_BYTES_FULL = 160 * 1024;
    private static final int MAX_TOTAL_BYTES = 200 * 1024;
    private static final int MAX_TOTAL_BYTES_FULL = 750 * 1024;
    /** Boot/startup ship retries while waiting for connectivity. */
    private static final long[] BOOT_RETRY_MS = {90_000L, 240_000L};

    public enum ScanMode {
        STARTUP,
        /** @deprecated unused — kept so old callers compile; no-ops if started. */
        ROUTINE,
        /** @deprecated no longer auto-shipped on thread open. */
        SUPPORT_OPEN,
        REMOTE_PULL,
        /** User typed a Report Issue / Solar Development message. */
        USER_REPORT,
        /** Wi‑Fi association while auto-report is on (light). */
        WIFI,
        /** Flush before radio off (user or auto sleep policy) — light, time-boxed. */
        WIFI_OFF,
        /** User chose Shut Down from on-device menus. */
        POWER_OFF,
        /** User chose Restart from on-device menus. */
        RESTART
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

    /**
     * @deprecated Opening the Solar Development thread no longer ships diagnostics.
     * Prefer {@link #shipUserReport} when the user actually sends a message.
     */
    public static void shipOnDeveloperSupportOpen(final Context context, final SharedPreferences prefs) {
        // No-op: ship-on-open caused routine-like storms when users browsed the thread.
    }

    /**
     * User Report Issue / message to Solar Development — full diagnostics + quoted text.
     * Always allowed (does not require auto-report pref); needs network.
     */
    public static void shipUserReport(final Context context, final SharedPreferences prefs,
            final String userMessage, final RemotePullCallback callback) {
        if (context == null) {
            if (callback != null) callback.onComplete(false, 0, "", "no_context");
            return;
        }
        final Context app = context.getApplicationContext();
        final SharedPreferences p = prefs != null ? prefs
                : app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        startScan(app, p, ScanMode.USER_REPORT, null, callback,
                userMessage != null ? userMessage : "");
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
        startScan(app, p, ScanMode.REMOTE_PULL, replyToDev, callback, null);
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
        // Feature errors only log locally; ships on next event (startup/wifi/power/user report).
        if (context == null) return;
        SolarDiagFeatureLog.event("diag", "feature_error queued " + key);
    }

    public static void onProcessStart(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        SolarDiagFeatureLog.event("app", "process_start sdk=" + Build.VERSION.SDK_INT
                + " model=" + Build.MODEL
                + (hasRecentCrashLog() ? " crash_pending=1" : ""));
        // App / process start → one delayed STARTUP ship when online (crash or clean boot).
        scheduleBootScan(app, prefs);
    }

    public static void onReachInternetAvailable(final Context context, final SharedPreferences prefs) {
        if (context == null) return;
        if (!isBackgroundShippingAllowed(prefs)) return;
        if (!firstInternetScanDone) {
            firstInternetScanDone = true;
            // First online after process start = system/app startup ship.
            startScan(context.getApplicationContext(), prefs, ScanMode.STARTUP, null, null, null);
            return;
        }
        // Later connectivity restores count as Wi‑Fi connection events.
        onWifiAvailable(context, prefs);
    }

    public static void onWifiAvailable(final Context context, final SharedPreferences prefs) {
        if (!isBackgroundShippingAllowed(prefs) || context == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScanMs < MIN_WIFI_SHIP_INTERVAL_MS) return;
        startScan(context.getApplicationContext(), prefs, ScanMode.WIFI, null, null, null);
    }

    /**
     * @deprecated Periodic routine ships removed — use event hooks only.
     * Kept as a no-op so older call sites do not reintroduce spam.
     */
    public static void scheduleIfNeeded(final Context context, final SharedPreferences prefs) {
        // Intentionally empty: diagnostics are event-bundled only.
    }

    public static void scheduleUrgent(final Context context, final SharedPreferences prefs) {
        if (context == null) return;
        startScan(context.getApplicationContext(), prefs, ScanMode.STARTUP, null, null, null);
    }

    /**
     * User chose Shut Down / Restart from menus: toast, optional log ship, silent Soulseek
     * notice to developers (hidden from conversation UI), then {@code powerAction}.
     */
    public static void runWithPowerDiagPrep(final Context context, final boolean restart,
            final Runnable powerAction) {
        if (context == null) {
            if (powerAction != null) powerAction.run();
            return;
        }
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs =
                app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        try {
            android.widget.Toast.makeText(app, com.solar.launcher.R.string.diag_getting_ready,
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ConnectivityHelper.isOnline(app)) {
                        final ScanMode mode = restart ? ScanMode.RESTART : ScanMode.POWER_OFF;
                        if (isBackgroundShippingAllowed(prefs) && SolarDiagClient.isConfigured()) {
                            awaitShip(app, prefs, mode, POWER_SHIP_TIMEOUT_MS);
                        }
                        // Silent notice even when auto-report is off (if online).
                        notifyDevelopersPoweredOff(app, prefs, restart);
                    }
                } catch (Exception e) {
                    SolarDiagFeatureLog.warn("diag", "power_prep " + e.getMessage());
                } finally {
                    if (powerAction != null) {
                        try {
                            powerAction.run();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, restart ? "SolarPowerRestartDiag" : "SolarPowerOffDiag");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /**
     * Before Wi‑Fi radio off (user toggle or auto sleep): light log flush while still online.
     * User path may show {@code disconnecting} toast — never mentions diagnostics.
     * Always runs {@code disableWifi} after a short timeout (or immediately if offline/busy).
     *
     * @param userVisible when true, show a neutral "Disconnecting…" toast
     */
    public static void runBeforeWifiDisable(final Context context, final boolean userVisible,
            final Runnable disableWifi) {
        if (context == null) {
            if (disableWifi != null) disableWifi.run();
            return;
        }
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs =
                app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        if (userVisible) {
            try {
                android.widget.Toast.makeText(app,
                        com.solar.launcher.R.string.toast_wifi_disconnecting,
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        }
        // Offline or auto-report off: drop radio immediately (no hang).
        if (!ConnectivityHelper.isOnline(app)
                || !isBackgroundShippingAllowed(prefs)
                || !SolarDiagClient.isConfigured()) {
            if (disableWifi != null) disableWifi.run();
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    awaitShip(app, prefs, ScanMode.WIFI_OFF, WIFI_OFF_SHIP_TIMEOUT_MS);
                } catch (Exception e) {
                    SolarDiagFeatureLog.warn("diag", "wifi_off_prep " + e.getMessage());
                } finally {
                    if (disableWifi != null) {
                        try {
                            disableWifi.run();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, "SolarWifiOffDiag");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Time-boxed ship for power / wifi-off prep (does not block forever). */
    private static void awaitShip(Context app, SharedPreferences prefs, ScanMode mode,
            long timeoutMs) {
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        startScan(app, prefs, mode, null, new RemotePullCallback() {
            @Override
            public void onComplete(boolean ok, int issueNumber, String htmlUrl, String error) {
                latch.countDown();
            }
        }, null);
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }

    /** Silent Soulseek PMs to developer accounts — never stored in virtual conversation. */
    static void notifyDevelopersPoweredOff(Context context, SharedPreferences prefs,
            boolean restart) {
        if (context == null || prefs == null) return;
        try {
            SoulseekAccount acct = SoulseekAccount.load(prefs, context);
            String username = acct != null ? acct.username : "";
            String body = SolarDeveloperAccounts.formatPoweredOffNotice(username, restart);
            String[] devs = SolarDeveloperAccounts.developerUsernames();
            SoulseekClient client = null;
            try {
                client = com.solar.launcher.MainActivity.getActiveSoulseekClient();
            } catch (Throwable ignored) {}
            if (client != null && client.isLoggedIn()) {
                for (int i = 0; i < devs.length; i++) {
                    if (devs[i] == null || devs[i].isEmpty()) continue;
                    try {
                        client.sendPrivateMessageSync(devs[i], body);
                    } catch (Exception ignored) {}
                    if (i + 1 < devs.length) {
                        try { Thread.sleep(1500L); } catch (InterruptedException ignored) {}
                    }
                }
                return;
            }
            // Fallback: -diag session (no local thread append).
            SolarDiagSessionManager.sendToRecipients(context, prefs, devs, body);
        } catch (Exception e) {
            SolarDiagFeatureLog.warn("diag", "power_notice " + e.getMessage());
        }
    }

    private static void scheduleBootScan(final Context context, final SharedPreferences prefs) {
        if (!bootScanPending) return;
        bootScanPending = false;
        if (!isBackgroundShippingAllowed(prefs)) return;
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
                    // If first-online already shipped STARTUP, skip duplicate.
                    if (firstInternetScanDone && !hasRecentCrashLog()) return;
                    startScan(context, prefs, ScanMode.STARTUP, null, null, null);
                    return;
                }
            }
        }, "SolarDiagBoot").start();
    }

    private static void startScan(final Context context, final SharedPreferences prefs,
            final ScanMode mode, final String replyToDev, final RemotePullCallback callback,
            final String userMessage) {
        if (context == null || prefs == null) {
            if (callback != null) callback.onComplete(false, 0, "", "bad_args");
            return;
        }
        if (!scanRunning.compareAndSet(false, true)) {
            if (callback != null) callback.onComplete(false, 0, "", "busy");
            return;
        }
        lastScanMs = System.currentTimeMillis();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runScan(context, prefs, mode, replyToDev, callback, userMessage);
                } finally {
                    scanRunning.set(false);
                }
            }
        }, "SolarDiagScan");
        // Background event ships yield CPU to UI/audio; user/remote keep default priority.
        if (mode != ScanMode.USER_REPORT && mode != ScanMode.REMOTE_PULL) {
            t.setPriority(Thread.MIN_PRIORITY);
        }
        t.start();
    }

    static void runScan(Context context, SharedPreferences prefs, ScanMode mode,
            String replyToDev, RemotePullCallback callback, String userMessage) {
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
        if ((mode == ScanMode.STARTUP || mode == ScanMode.WIFI || mode == ScanMode.WIFI_OFF
                || mode == ScanMode.POWER_OFF || mode == ScanMode.RESTART)
                && !isEnabled(prefs) && !(mode == ScanMode.STARTUP && hasRecentCrashLog())) {
            if (callback != null) callback.onComplete(false, 0, "", "disabled");
            return;
        }
        // USER_REPORT and REMOTE_PULL always allowed when configured.

        boolean full = mode == ScanMode.REMOTE_PULL
                || mode == ScanMode.USER_REPORT
                || mode == ScanMode.SUPPORT_OPEN
                || mode == ScanMode.POWER_OFF
                || mode == ScanMode.RESTART
                || (mode == ScanMode.STARTUP && hasRecentCrashLog());
        // Never toggle Wi‑Fi: ships only while already online.
        if (!ConnectivityHelper.isOnline(context)) {
            if (callback != null) callback.onComplete(false, 0, "", "offline");
            if (mode == ScanMode.USER_REPORT || mode == ScanMode.REMOTE_PULL) {
                SolarDiagFeatureLog.warn("diag", mode.name() + " offline — ship deferred");
            } else if (mode != ScanMode.WIFI_OFF && mode != ScanMode.POWER_OFF
                    && mode != ScanMode.RESTART) {
                scheduleSessionRetry(context, prefs, mode);
            }
            return;
        }
        runScanOnline(context, prefs, mode, replyToDev, callback, full, userMessage);
    }

    private static void runScanOnline(Context context, SharedPreferences prefs, ScanMode mode,
            String replyToDev, RemotePullCallback callback, boolean full, String userMessage) {
        SoulseekAccount main = SoulseekAccount.load(prefs, context);
        List<LogSource> sources = collectSources(context, prefs, full);
        JSONObject manifest = loadManifest(prefs);
        JSONObject updated = new JSONObject();
        try {
            Iterator<String> keys = manifest.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                updated.put(k, manifest.optLong(k, 0));
            }
        } catch (Exception ignored) {}

        int maxTotal = full ? MAX_TOTAL_BYTES_FULL : MAX_TOTAL_BYTES;
        int maxFile = full ? MAX_FILE_BYTES_FULL : MAX_FILE_BYTES;
        List<SolarDiagClient.FilePart> parts = new ArrayList<SolarDiagClient.FilePart>();
        int budget = maxTotal;

        String userMsg = mode == ScanMode.USER_REPORT ? userMessage : null;
        if (userMsg != null && !userMsg.isEmpty()) {
            parts.add(new SolarDiagClient.FilePart("Diag/user-message.txt", userMsg));
            budget -= userMsg.length();
        }

        String env = full
                ? SolarDiagContextCollector.collectEnvironment(context)
                : SolarDiagContextCollector.collectEnvironmentLight(context);
        parts.add(new SolarDiagClient.FilePart("Diag/environment.txt", env));
        budget -= env.length();
        // Full ARL dump only on user report / remote pull / crash — routine gets redacted.
        String account = full
                ? SolarDiagContextCollector.collectAccountContext(context, prefs)
                : SolarDiagContextCollector.collectAccountContextLight(context, prefs);
        parts.add(new SolarDiagClient.FilePart("Diag/account-context.txt", account));
        budget -= account.length();
        String ring = SolarDiagFeatureLog.dumpRing();
        if (ring != null && !ring.isEmpty()) {
            parts.add(new SolarDiagClient.FilePart("Diag/feature-ring.txt", ring));
            budget -= ring.length();
        }

        boolean forceAll = mode == ScanMode.REMOTE_PULL
                || mode == ScanMode.USER_REPORT
                || mode == ScanMode.SUPPORT_OPEN
                || mode == ScanMode.POWER_OFF
                || mode == ScanMode.RESTART
                || mode == ScanMode.WIFI_OFF;
        int shippedFiles = 0;
        for (LogSource src : sources) {
            if (src == null || src.file == null || !src.file.isFile()) continue;
            long mtime = src.file.lastModified();
            String key = src.file.getAbsolutePath();
            if (!forceAll && !shouldShipSource(src.label, manifest, key, mtime, mode)) continue;
            int cap = Math.min(maxFile, Math.max(0, budget));
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

        // Connect/startup with nothing new: skip HTTPS (env-only issues were flooding solar-diag).
        // WIFI_OFF still ships light env/ring so pre-sleep flush always has a heartbeat.
        if ((mode == ScanMode.ROUTINE || mode == ScanMode.WIFI || mode == ScanMode.STARTUP)
                && shippedFiles == 0 && !hasRecentCrashLog()) {
            SolarDiagFeatureLog.event("diag", mode.name().toLowerCase(Locale.US) + "_skip no_new_logs");
            if (callback != null) callback.onComplete(true, 0, "", "skipped_empty");
            return;
        }

        String type = typeForMode(mode, sources);
        String feature = "";
        String trigger = triggerForMode(mode);
        String usernameForIssue = null;
        String titleHint = null;
        if (mode == ScanMode.REMOTE_PULL) {
            type = "diag_pull";
            trigger = "remote_pull";
            usernameForIssue = main != null ? main.username : null;
        } else if (mode == ScanMode.USER_REPORT) {
            type = "user_report";
            trigger = "user_message";
            usernameForIssue = main != null ? main.username : null;
            titleHint = titleFromUserMessage(userMsg);
        } else if (mode == ScanMode.STARTUP && hasRecentCrashLog()) {
            type = "crash";
            trigger = "crash";
        } else if (mode == ScanMode.WIFI) {
            type = "wifi";
            trigger = "wifi_connect";
        } else if (mode == ScanMode.WIFI_OFF) {
            type = "wifi";
            trigger = "wifi_off";
        } else if (mode == ScanMode.POWER_OFF) {
            type = "power";
            trigger = "power_off";
        } else if (mode == ScanMode.RESTART) {
            type = "power";
            trigger = "restart";
        }

        String summary = "mode=" + mode.name() + " files=" + shippedFiles
                + " sdk=" + Build.VERSION.SDK_INT
                + " model=" + DeviceFeatures.deviceModelLabel()
                + " family=" + DeviceFeatures.deviceFamily();
        if (userMsg != null && !userMsg.isEmpty()) {
            String oneLine = userMsg.replace('\n', ' ').trim();
            if (oneLine.length() > 200) oneLine = oneLine.substring(0, 200) + "…";
            summary = summary + "\nuser_message: " + oneLine;
        }
        JSONObject device = SolarDiagContextCollector.deviceJson(context);
        SolarDiagClient.Result result = SolarDiagClient.submit(
                type, feature, trigger, usernameForIssue, device, summary, titleHint,
                userMsg, parts);

        if (result.ok) {
            prefs.edit().putString(PREF_DIAG_SENT_MANIFEST, updated.toString()).apply();
            SolarDiagFeatureLog.event("diag", "shipped issue=" + result.issueNumber
                    + " mode=" + mode.name());
        } else {
            SolarDiagFeatureLog.warn("diag", "ship_failed mode=" + mode.name()
                    + " err=" + result.error);
            if (mode != ScanMode.REMOTE_PULL && mode != ScanMode.USER_REPORT
                    && mode != ScanMode.WIFI_OFF && mode != ScanMode.POWER_OFF
                    && mode != ScanMode.RESTART) {
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

    private static String titleFromUserMessage(String msg) {
        if (msg == null) return null;
        String t = msg.trim().replace('\n', ' ');
        if (t.isEmpty()) return null;
        if (t.length() > 80) t = t.substring(0, 80) + "…";
        return t;
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
        if (mode == ScanMode.USER_REPORT) return "user_report";
        if (mode == ScanMode.WIFI || mode == ScanMode.WIFI_OFF) return "wifi";
        if (mode == ScanMode.POWER_OFF || mode == ScanMode.RESTART) return "power";
        if (mode == ScanMode.SUPPORT_OPEN) return "other";
        return "other";
    }

    private static String triggerForMode(ScanMode mode) {
        if (mode == ScanMode.REMOTE_PULL) return "remote_pull";
        if (mode == ScanMode.USER_REPORT) return "user_message";
        if (mode == ScanMode.WIFI) return "wifi_connect";
        if (mode == ScanMode.WIFI_OFF) return "wifi_off";
        if (mode == ScanMode.POWER_OFF) return "power_off";
        if (mode == ScanMode.RESTART) return "restart";
        if (mode == ScanMode.STARTUP) return hasRecentCrashLog() ? "crash" : "startup";
        return "event";
    }

    private static boolean hasRecentCrashLog() {
        long window = 48L * 60L * 60L * 1000L; // 48h — recent enough to care, not forever
        long now = System.currentTimeMillis();
        File dir = SolarLogPaths.preferredLogDir(null);
        File crash = new File(dir, "crash.log");
        return crash.isFile() && now - crash.lastModified() < window;
    }

    static boolean shouldShipSource(String label, JSONObject manifest, String path, long mtime,
            ScanMode mode) {
        if (mode == ScanMode.SUPPORT_OPEN || mode == ScanMode.REMOTE_PULL
                || mode == ScanMode.USER_REPORT || mode == ScanMode.WIFI_OFF
                || mode == ScanMode.POWER_OFF || mode == ScanMode.RESTART) {
            return true;
        }
        if (mode == ScanMode.STARTUP && isPriorityStartupSource(label)) return true;
        return manifest.optLong(path, -1) != mtime;
    }

    static boolean isPriorityStartupSource(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.US);
        // Keep startup priority tight — crash/error only (not full logcat every boot).
        if (lower.contains("crash.log") || lower.contains("error.log")) return true;
        if (lower.contains("storage.log")) return true;
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
                startScan(context, prefs, mode, null, null, null);
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
        return collectSources(context, null, false);
    }

    static List<LogSource> collectSources(Context context, SharedPreferences prefs) {
        return collectSources(context, prefs, false);
    }

    static List<LogSource> collectSources(Context context, SharedPreferences prefs, boolean full) {
        List<LogSource> out = new ArrayList<LogSource>();
        // Preferred log dir first (app-private) — avoid walking every volume on light ships.
        File preferred = SolarLogPaths.preferredLogDir(context);
        addIfFile(out, "SolarLog/crash.log", new File(preferred, "crash.log"));
        addIfFile(out, "SolarLog/error.log", new File(preferred, "error.log"));
        addIfFile(out, "SolarLog/storage.log", new File(preferred, "storage.log"));
        addLogTree(new File(preferred, "features"), out, "SolarLog/features");
        if (full) {
            addIfFile(out, "SolarLog/crash.log.old", new File(preferred, "crash.log.old"));
            addIfFile(out, "SolarLog/error.log.old", new File(preferred, "error.log.old"));
            int vol = 1;
            for (File logDir : SolarLogPaths.logDirs(context)) {
                if (logDir.getAbsolutePath().equals(preferred.getAbsolutePath())) continue;
                String prefix = "SolarLog/vol" + vol;
                addIfFile(out, prefix + "/crash.log", new File(logDir, "crash.log"));
                addIfFile(out, prefix + "/error.log", new File(logDir, "error.log"));
                vol++;
            }
            for (File root : DeviceFeatures.getStorageRoots()) {
                if (root == null) continue;
                collectRockboxLogs(new File(root, ".rockbox"), out, "Rockbox/" + root.getName());
            }
            addDeviceSnapshot(out);
            // logcat -d is expensive on KitKat; only full ships (user report / pull / crash).
            addLogcatSnapshot(out, LOGCAT_LINES_FULL);
        }
        // Light/routine: no logcat snapshot — ring + crash/error tails are enough.
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

    private static void addLogcatSnapshot(List<LogSource> out, int lines) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "logcat", "-d", "-t", String.valueOf(lines > 0 ? lines : LOGCAT_LINES)
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
