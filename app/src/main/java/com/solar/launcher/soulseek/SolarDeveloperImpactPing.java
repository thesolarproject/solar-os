package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.ConnectivityHelper;
import com.solar.launcher.DeviceFeatures;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-07-16 — Silent one-line Soulseek pings to SolarDev for high-impact events.
 * Hidden from conversation UI via {@link SolarDeveloperAccounts#DIAG_MARKER}.
 * Strong cooldowns + single low-priority worker so media/UI never wait.
 */
public final class SolarDeveloperImpactPing {
    private static final long WIFI_COOLDOWN_MS = 20L * 60L * 1000L;
    private static final long FAIL_COOLDOWN_MS = 90L * 60L * 1000L;
    private static final long OK_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_LINE = 96;

    private static final Map<String, Long> lastSentMs = new ConcurrentHashMap<String, Long>();
    private static final AtomicBoolean workerBusy = new AtomicBoolean(false);
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "SolarDevImpactPing");
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        }
    });

    private SolarDeveloperImpactPing() {}

    public static void wifiConnected(Context context) {
        enqueue(context, "wifi_on", WIFI_COOLDOWN_MS, "connected to Wi-Fi");
    }

    public static void wifiDisconnecting(Context context) {
        enqueue(context, "wifi_off", WIFI_COOLDOWN_MS, "Wi-Fi disconnecting");
    }

    /** High-impact media failure (download / stream). One-liner only. */
    public static void mediaFailed(Context context, String service, String detail) {
        String svc = service != null ? service.trim().toLowerCase(Locale.US) : "media";
        if (svc.isEmpty()) svc = "media";
        String line = svc + " failed: " + clean(detail);
        enqueue(context, "fail_" + svc, FAIL_COOLDOWN_MS, line);
    }

    /** Rare success ping (first play OK of a long window). */
    public static void mediaOk(Context context, String service, String detail) {
        String svc = service != null ? service.trim().toLowerCase(Locale.US) : "media";
        if (svc.isEmpty()) svc = "media";
        String line = svc + " ok: " + clean(detail);
        enqueue(context, "ok_" + svc, OK_COOLDOWN_MS, line);
    }

    private static String clean(String detail) {
        if (detail == null) return "unknown";
        String d = detail.replace('\n', ' ').replace('\r', ' ').trim();
        if (d.isEmpty()) return "unknown";
        // Drop huge exception dumps — keep first clause.
        int cut = d.indexOf(" at ");
        if (cut > 20) d = d.substring(0, cut);
        if (d.length() > MAX_LINE) d = d.substring(0, MAX_LINE - 1) + "…";
        return d;
    }

    private static void enqueue(final Context context, final String key, final long cooldownMs,
            final String oneLine) {
        if (oneLine == null || oneLine.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = lastSentMs.get(key);
        if (last != null && now - last < cooldownMs) return;
        // Reserve slot early so UI threads never queue spam.
        lastSentMs.put(key, now);
        final Context app = resolveApp(context);
        WORKER.execute(new Runnable() {
            @Override
            public void run() {
                if (!workerBusy.compareAndSet(false, true)) {
                    // Another ping in flight — drop (already reserved cooldown).
                    return;
                }
                try {
                    sendNow(app, oneLine);
                } catch (Exception ignored) {
                } finally {
                    workerBusy.set(false);
                }
            }
        });
    }

    private static Context resolveApp(Context context) {
        if (context != null) return context.getApplicationContext();
        try {
            return com.solar.launcher.SolarApplication.getAppContext();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void sendNow(Context app, String oneLine) {
        if (app == null) return;
        if (!ConnectivityHelper.isOnline(app)) return;
        SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        SoulseekAccount acct = SoulseekAccount.load(prefs, app);
        String user = acct != null && acct.username != null && !acct.username.isEmpty()
                ? acct.username : "user";
        String device = DeviceFeatures.deviceModelLabel();
        String body = SolarDeveloperAccounts.formatImpactPing(user, device, oneLine);

        // One recipient only — cuts Soulseek traffic vs full fan-out.
        String primary = SolarDeveloperAccounts.SOLAR_DEV;
        SoulseekClient client = null;
        try {
            client = com.solar.launcher.MainActivity.getActiveSoulseekClient();
        } catch (Throwable ignored) {}
        if (client != null && client.isLoggedIn()) {
            try {
                client.sendPrivateMessageSync(primary, body);
                return;
            } catch (Exception ignored) {}
        }
        // Fallback: diag session if main Reach is offline.
        try {
            SolarDiagSessionManager.sendToRecipients(app, prefs, new String[] { primary }, body);
        } catch (Exception ignored) {}
    }
}
