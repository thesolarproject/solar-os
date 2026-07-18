package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.ConnectivityHelper;
import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.ReachPolicy;
import com.solar.launcher.SolarLog;
import com.solar.launcher.diag.SolarDiagFeatureLog;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 2026-07-16 — Silent one-line Soulseek pings to SolarDev for high-impact events.
 *
 * <h3>Hallmark / performance design (non-invasive diagnostics)</h3>
 * <ul>
 *   <li><b>Piggyback, don't poke</b> — pings fire only at natural wait moments
 *       (Wi‑Fi transition, stream resolve after buffering, download already failed).
 *       Never on every keypress, frame, or RSSI tick.</li>
 *   <li><b>In-memory metadata only</b> — never open MediaMetadataRetriever / disk ID3
 *       on the ping path. Call sites pass title/artist/id already held for UI.</li>
 *   <li><b>MIN_PRIORITY single worker</b> — UI/audio never block; at most one send in flight.</li>
 *   <li><b>Dampening</b> — per-content cooldowns + hourly budget so spam cannot melt battery
 *       or fill SolarDev inboxes.</li>
 *   <li><b>Small payload</b> — one Soulseek PM to primary SolarDev only; bodies start with
 *       {@code solar diag - } so any conversation UI can hide them; same line mirrors into
 *       the feature ring for GH bundles.</li>
 *   <li><b>Scrub first</b> — SolarLog.scrub before wire so tokens/paths stay private.</li>
 * </ul>
 */
public final class SolarDeveloperImpactPing {
    private static final long WIFI_COOLDOWN_MS = 20L * 60L * 1000L;
    private static final long FAIL_COOLDOWN_MS = 90L * 60L * 1000L;
    private static final long OK_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    /** Soft cap — prevents runaway when many distinct tracks fail. */
    private static final int MAX_PINGS_PER_HOUR = 16;
    private static final int MAX_LINE = 180;

    private static final Map<String, Long> lastSentMs = new ConcurrentHashMap<String, Long>();
    private static final AtomicInteger hourBudget = new AtomicInteger(0);
    private static volatile long hourBudgetWindowStartMs;
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

    /**
     * Compact media identity for pings + feature logs — built from fields already in memory.
     * Do not populate by re-reading ID3 from disk here.
     */
    public static final class MediaInfo {
        public String service;
        public String id;
        public String title;
        public String artist;
        public String album;
        public String quality;
        public String peer;
        public String file;
        public String reason;
        public boolean ok;

        public MediaInfo service(String s) { this.service = s; return this; }
        public MediaInfo id(String v) { this.id = v; return this; }
        public MediaInfo title(String v) { this.title = v; return this; }
        public MediaInfo artist(String v) { this.artist = v; return this; }
        public MediaInfo album(String v) { this.album = v; return this; }
        public MediaInfo quality(String v) { this.quality = v; return this; }
        public MediaInfo peer(String v) { this.peer = v; return this; }
        public MediaInfo file(String v) { this.file = v; return this; }
        public MediaInfo reason(String v) { this.reason = v; return this; }
        public MediaInfo ok(boolean v) { this.ok = v; return this; }

        public static MediaInfo of(String service) {
            return new MediaInfo().service(service);
        }

        /** Short content key for cooldown (id preferred). */
        String contentKey() {
            if (id != null && !id.trim().isEmpty()) return id.trim();
            String t = title != null ? title.trim() : "";
            String a = artist != null ? artist.trim() : "";
            if (!t.isEmpty() || !a.isEmpty()) {
                return (a + "|" + t).toLowerCase(Locale.US);
            }
            if (file != null && !file.isEmpty()) return file;
            return "generic";
        }

        /**
         * Compact diagnostic line:
         * {@code youtube fail id=abc title="Song" by Artist q=720p: 404}
         */
        String formatLine() {
            String svc = service != null ? service.trim().toLowerCase(Locale.US) : "media";
            StringBuilder sb = new StringBuilder(96);
            sb.append(svc).append(ok ? " ok" : " fail");
            if (id != null && !id.isEmpty()) sb.append(" id=").append(clip(id, 24));
            if (title != null && !title.isEmpty()) {
                sb.append(" \"").append(clip(title, 40)).append('"');
            }
            if (artist != null && !artist.isEmpty()) {
                sb.append(" by ").append(clip(artist, 28));
            }
            if (album != null && !album.isEmpty()) {
                sb.append(" album=").append(clip(album, 24));
            }
            if (quality != null && !quality.isEmpty()) {
                sb.append(" q=").append(clip(quality, 12));
            }
            if (peer != null && !peer.isEmpty()) {
                sb.append(" peer=").append(clip(peer, 20));
            }
            if (file != null && !file.isEmpty()) {
                sb.append(" file=").append(clip(basename(file), 36));
            }
            if (reason != null && !reason.isEmpty()) {
                sb.append(": ").append(clip(reason.replace('\n', ' '), 60));
            }
            String out = sb.toString();
            if (out.length() > MAX_LINE) out = out.substring(0, MAX_LINE - 1) + "…";
            return out;
        }

        private static String clip(String s, int max) {
            if (s == null) return "";
            String t = s.trim();
            if (t.length() <= max) return t;
            return t.substring(0, max - 1) + "…";
        }

        private static String basename(String path) {
            if (path == null) return "";
            int s = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return s >= 0 && s + 1 < path.length() ? path.substring(s + 1) : path;
        }
    }

    public static void wifiConnected(Context context) {
        enqueue(context, "wifi_on", WIFI_COOLDOWN_MS, "connected to Wi-Fi", "wifi", true);
    }

    public static void wifiDisconnecting(Context context) {
        enqueue(context, "wifi_off", WIFI_COOLDOWN_MS, "Wi-Fi disconnecting", "wifi", false);
    }

    /** High-impact media failure with structured metadata. */
    public static void mediaFailed(Context context, MediaInfo info) {
        if (info == null) return;
        info.ok = false;
        String svc = info.service != null ? info.service : "media";
        enqueue(context, "fail_" + svc + ":" + info.contentKey(), FAIL_COOLDOWN_MS,
                info.formatLine(), svc, false);
    }

    /** Backward-compatible failure without metadata. */
    public static void mediaFailed(Context context, String service, String detail) {
        mediaFailed(context, MediaInfo.of(service).reason(detail));
    }

    /** Rare success ping with metadata (long cooldown). */
    public static void mediaOk(Context context, MediaInfo info) {
        if (info == null) return;
        info.ok = true;
        String svc = info.service != null ? info.service : "media";
        enqueue(context, "ok_" + svc + ":" + info.contentKey(), OK_COOLDOWN_MS,
                info.formatLine(), svc, true);
    }

    public static void mediaOk(Context context, String service, String detail) {
        mediaOk(context, MediaInfo.of(service).reason(detail).ok(true));
    }

    private static boolean allowHourBudget() {
        long now = System.currentTimeMillis();
        if (now - hourBudgetWindowStartMs > 3600_000L) {
            hourBudgetWindowStartMs = now;
            hourBudget.set(0);
        }
        return hourBudget.incrementAndGet() <= MAX_PINGS_PER_HOUR;
    }

    private static void enqueue(final Context context, final String key, final long cooldownMs,
            final String oneLine, final String feature, final boolean ok) {
        if (oneLine == null || oneLine.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = lastSentMs.get(key);
        if (last != null && now - last < cooldownMs) return;
        if (!allowHourBudget()) return;
        lastSentMs.put(key, now);

        // Always breadcrumb locally for GH diag bundles (memory ring; no disk storm).
        try {
            String scrubbed = SolarLog.scrub(oneLine);
            if (ok) {
                SolarDiagFeatureLog.event(feature != null ? feature : "media", scrubbed);
            } else {
                SolarDiagFeatureLog.warn(feature != null ? feature : "media", scrubbed);
            }
        } catch (Throwable ignored) {}

        final Context app = resolveApp(context);
        final String lineFinal = oneLine;
        WORKER.execute(new Runnable() {
            @Override
            public void run() {
                if (!workerBusy.compareAndSet(false, true)) return;
                try {
                    sendNow(app, lineFinal);
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
        // 2026-07-17 — Soulseek off: keep local feature-log breadcrumbs only; no wire session.
        if (!ReachPolicy.allowsBackgroundSoulseekWork(prefs)) return;
        SoulseekAccount acct = SoulseekAccount.load(prefs, app);
        String user = acct != null && acct.username != null && !acct.username.isEmpty()
                ? acct.username : "user";
        String device = DeviceFeatures.deviceModelLabel();
        String scrubbed = SolarLog.scrub(oneLine);
        String body = SolarDeveloperAccounts.formatImpactPing(user, device, scrubbed);

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
        try {
            SolarDiagSessionManager.sendToRecipients(app, prefs, new String[] { primary }, body);
        } catch (Exception ignored) {}
    }
}
