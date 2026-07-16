package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import com.solar.launcher.diag.SolarDiagFeatureLog;
import com.solar.launcher.net.SolarHttp;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-07-16 — Automatic internet clock sync for rooted Solar devices (Y1/Y2/A5).
 *
 * <ul>
 *   <li>Default <b>on</b> — user may disable in Date &amp; Time settings.</li>
 *   <li>User-owned <b>timezone</b> (never forced from geo); geo/IP only picks NTP pool.</li>
 *   <li>Triggers: process start (optional brief Wi‑Fi wake), Wi‑Fi online, hotspot with net.</li>
 *   <li>Root {@code date}/{@code hwclock} + {@code persist.sys.timezone} (A5 allowed).</li>
 * </ul>
 */
public final class SolarAutoTime {
    public static final String PREF_AUTO_INTERNET_TIME = "solar_auto_internet_time";
    public static final String PREF_TIMEZONE_ID = "solar_timezone_id";
    public static final String PREF_LAST_SYNC_MS = "solar_auto_time_last_sync_ms";
    public static final String PREF_LAST_SERVER = "solar_auto_time_last_server";

    /** Min gap between successful/attempted full syncs on reconnect (battery). */
    private static final long MIN_SYNC_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long BOOT_WIFI_WAIT_MS = 25_000L;
    private static final long ONLINE_POLL_MS = 800L;
    private static final long MAX_SKEW_APPLY_MS = 24L * 60L * 60L * 1000L * 365L * 20L;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static volatile long lastAttemptMs;

    private SolarAutoTime() {}

    public static boolean isAutoEnabled(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_AUTO_INTERNET_TIME, true);
    }

    public static void setAutoEnabled(SharedPreferences prefs, boolean on) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_AUTO_INTERNET_TIME, on).apply();
    }

    /** Stored timezone id, or system default when unset. */
    public static String timezoneId(SharedPreferences prefs) {
        if (prefs != null) {
            String id = prefs.getString(PREF_TIMEZONE_ID, "");
            if (id != null && !id.trim().isEmpty()) return id.trim();
        }
        return TimeZone.getDefault().getID();
    }

    public static void setTimezoneId(SharedPreferences prefs, String id) {
        if (prefs == null || id == null || id.trim().isEmpty()) return;
        prefs.edit().putString(PREF_TIMEZONE_ID, id.trim()).apply();
        applyTimezoneRoot(id.trim());
    }

    /** Curated list for wheel picker (id only). */
    public static String[] commonTimezoneIds() {
        return new String[] {
                "UTC",
                "Pacific/Honolulu",
                "America/Anchorage",
                "America/Los_Angeles",
                "America/Denver",
                "America/Chicago",
                "America/New_York",
                "America/Sao_Paulo",
                "Europe/London",
                "Europe/Paris",
                "Europe/Berlin",
                "Europe/Moscow",
                "Africa/Cairo",
                "Asia/Dubai",
                "Asia/Kolkata",
                "Asia/Bangkok",
                "Asia/Shanghai",
                "Asia/Tokyo",
                "Asia/Seoul",
                "Australia/Sydney",
                "Pacific/Auckland"
        };
    }

    public static int indexOfTimezone(String id) {
        String[] ids = commonTimezoneIds();
        if (id == null) return 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equalsIgnoreCase(id)) return i;
        }
        return 0;
    }

    public static String displayTimezoneLabel(String id) {
        if (id == null || id.isEmpty()) id = TimeZone.getDefault().getID();
        TimeZone tz = TimeZone.getTimeZone(id);
        int raw = tz.getRawOffset() / 60000;
        int h = Math.abs(raw) / 60;
        int m = Math.abs(raw) % 60;
        String sign = raw >= 0 ? "+" : "-";
        String off = m == 0
                ? String.format(Locale.US, "UTC%s%d", sign, h)
                : String.format(Locale.US, "UTC%s%d:%02d", sign, h, m);
        // Short city tail after last /
        String city = id;
        int slash = id.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < id.length()) city = id.substring(slash + 1).replace('_', ' ');
        return city + " (" + off + ")";
    }

    /** Process start — optional Wi‑Fi wake, then sync if online; restore radio state. */
    public static void onProcessStart(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        if (!isAutoEnabled(prefs)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncWithOptionalWifiWake(app, prefs, true);
            }
        }, "SolarAutoTimeBoot").start();
    }

    /** Wi‑Fi / hotspot has routable connectivity. */
    public static void onInternetAvailable(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        if (!isAutoEnabled(prefs)) return;
        long now = System.currentTimeMillis();
        if (now - lastAttemptMs < MIN_SYNC_INTERVAL_MS) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncNow(app, prefs, false);
            }
        }, "SolarAutoTimeNet").start();
    }

    /** Force one attempt (settings “Sync now”). */
    public static void requestSyncNow(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        final SharedPreferences prefs = app.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncNow(app, prefs, true);
            }
        }, "SolarAutoTimeForce").start();
    }

    private static void syncWithOptionalWifiWake(Context app, SharedPreferences prefs, boolean boot) {
        if (!running.compareAndSet(false, true)) return;
        lastAttemptMs = System.currentTimeMillis();
        WifiManager wm = null;
        boolean wasEnabled = true;
        boolean weEnabled = false;
        try {
            wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wasEnabled = wm.isWifiEnabled();
                if (!wasEnabled && boot) {
                    // Brief wake — only if device has known networks (isWifiEnabled false but radio can join).
                    try {
                        weEnabled = wm.setWifiEnabled(true);
                    } catch (Exception ignored) {
                        weEnabled = false;
                    }
                }
            }
            waitUntilOnline(app, BOOT_WIFI_WAIT_MS);
            if (ConnectivityHelper.isOnline(app)) {
                performSync(app, prefs);
            }
        } catch (Exception ignored) {
        } finally {
            if (weEnabled && wm != null && !wasEnabled) {
                try {
                    wm.setWifiEnabled(false);
                } catch (Exception ignored) {}
            }
            running.set(false);
        }
    }

    private static void syncNow(Context app, SharedPreferences prefs, boolean force) {
        if (!force && !isAutoEnabled(prefs)) return;
        if (!running.compareAndSet(false, true)) return;
        lastAttemptMs = System.currentTimeMillis();
        try {
            if (!ConnectivityHelper.isOnline(app)) return;
            performSync(app, prefs);
        } catch (Exception ignored) {
        } finally {
            running.set(false);
        }
    }

    private static void waitUntilOnline(Context app, long maxWaitMs) {
        long deadline = SystemClock.elapsedRealtime() + maxWaitMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ConnectivityHelper.isOnline(app)) return;
            try {
                Thread.sleep(ONLINE_POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void performSync(Context app, SharedPreferences prefs) {
        // Apply user timezone first (does not depend on NTP).
        String tzId = timezoneId(prefs);
        applyTimezoneRoot(tzId);

        String[] hosts = ntpHostsForContext(app, tzId);
        long utcMs = SolarNtpClient.queryFirstUtcEpochMs(hosts);
        if (utcMs <= 0L) {
            // Last-resort pool
            utcMs = SolarNtpClient.queryUtcEpochMs("pool.ntp.org");
        }
        if (utcMs <= 0L) return;
        // Sanity: reject absurd times (before 2015 or >20y ahead)
        long min = 1420070400000L; // 2015-01-01
        long max = System.currentTimeMillis() + MAX_SKEW_APPLY_MS;
        if (utcMs < min || utcMs > max + MAX_SKEW_APPLY_MS) {
            // still allow far-future if device clock is 1970
            if (utcMs < min) return;
        }
        if (applyUtcEpochRoot(utcMs)) {
            String server = hosts.length > 0 ? hosts[0] : "pool.ntp.org";
            prefs.edit()
                    .putLong(PREF_LAST_SYNC_MS, System.currentTimeMillis())
                    .putString(PREF_LAST_SERVER, server)
                    .apply();
            try {
                app.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
            } catch (Exception ignored) {}
            try {
                SolarDiagFeatureLog.event("time", "ntp_ok server=" + server
                        + " tz=" + tzId + " utc=" + utcMs);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * NTP pool selection: prefer geo IP timezone region; else user timezone id; else global pool.
     * Never writes timezone from geo — only chooses servers.
     */
    static String[] ntpHostsForContext(Context app, String userTzId) {
        String region = regionFromTimezone(userTzId);
        String geoTz = fetchGeoTimezoneHint(app);
        if (geoTz != null && !geoTz.isEmpty()) {
            region = regionFromTimezone(geoTz);
        }
        return poolsForRegion(region);
    }

    static String regionFromTimezone(String tzId) {
        if (tzId == null) return "global";
        String t = tzId.trim();
        if (t.startsWith("America/") || t.startsWith("US/") || t.startsWith("Canada/")
                || t.startsWith("Pacific/Honolulu") || t.startsWith("America/Anchorage")) {
            return "north-america";
        }
        if (t.startsWith("Europe/") || t.startsWith("Atlantic/")) return "europe";
        if (t.startsWith("Asia/") || t.startsWith("Indian/")) return "asia";
        if (t.startsWith("Australia/") || t.startsWith("Pacific/Auckland")
                || t.startsWith("Pacific/Fiji")) {
            return "oceania";
        }
        if (t.startsWith("Africa/")) return "africa";
        if (t.startsWith("Pacific/")) return "oceania";
        return "global";
    }

    static String[] poolsForRegion(String region) {
        if ("north-america".equals(region)) {
            return new String[] {
                    "0.north-america.pool.ntp.org",
                    "1.north-america.pool.ntp.org",
                    "pool.ntp.org"
            };
        }
        if ("europe".equals(region)) {
            return new String[] {
                    "0.europe.pool.ntp.org",
                    "1.europe.pool.ntp.org",
                    "pool.ntp.org"
            };
        }
        if ("asia".equals(region)) {
            return new String[] {
                    "0.asia.pool.ntp.org",
                    "1.asia.pool.ntp.org",
                    "pool.ntp.org"
            };
        }
        if ("oceania".equals(region)) {
            return new String[] {
                    "0.oceania.pool.ntp.org",
                    "1.oceania.pool.ntp.org",
                    "pool.ntp.org"
            };
        }
        if ("africa".equals(region)) {
            return new String[] {
                    "0.africa.pool.ntp.org",
                    "pool.ntp.org"
            };
        }
        return new String[] { "0.pool.ntp.org", "1.pool.ntp.org", "pool.ntp.org" };
    }

    /** Lightweight geo hint for NTP pool only (HTTP JSON). */
    private static String fetchGeoTimezoneHint(Context app) {
        try {
            // Cleartext fields-only endpoint — small body; TLS optional via SolarHttp if upgraded.
            String json = SolarHttp.getText(
                    "http://ip-api.com/json/?fields=status,timezone");
            if (json == null || json.isEmpty()) return null;
            JSONObject o = new JSONObject(json);
            if (!"success".equalsIgnoreCase(o.optString("status", ""))) return null;
            String tz = o.optString("timezone", "");
            return tz != null && !tz.isEmpty() ? tz : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Root: set wall clock to absolute UTC epoch (device TZ already applied for display). */
    static boolean applyUtcEpochRoot(long utcEpochMs) {
        if (utcEpochMs <= 0L) return false;
        // Try framework first (SET_TIME when granted as system).
        try {
            SystemClock.setCurrentTimeMillis(utcEpochMs);
        } catch (Exception ignored) {}
        // Always reinforce via root date formats (same self-verify script as Date & Time UI).
        Date d = new Date(utcEpochMs);
        // Use UTC for the string so date(1) stores the correct absolute instant regardless of TZ.
        SimpleDateFormat toolbox = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.US);
        toolbox.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat posix = new SimpleDateFormat("MMddHHmmyyyy.ss", Locale.US);
        posix.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat ymdFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        ymdFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String ymd = ymdFmt.format(d);
        String f1 = toolbox.format(d);
        String f2 = posix.format(d);
        String f3 = iso.format(d);
        // Disable Android auto_time so our clock sticks; write RTC.
        String cmd = "settings put global auto_time 0; settings put system auto_time 0; "
                + "date -u -s " + f1 + "; "
                + "if [ \"$(date -u +%Y%m%d)\" != \"" + ymd + "\" ]; then "
                + "  date -u " + f2 + "; "
                + "  if [ \"$(date -u +%Y%m%d)\" != \"" + ymd + "\" ]; then "
                + "    date -u -s \"" + f3 + "\"; "
                + "  fi; "
                + "fi; "
                + "hwclock -w -u 2>/dev/null; hwclock -w 2>/dev/null; sync";
        // All Solar targets are rooted — allow A5 setuid su.
        return RootShell.run(cmd, true);
    }

    static boolean applyTimezoneRoot(String tzId) {
        if (tzId == null || tzId.trim().isEmpty()) return false;
        final String id = tzId.trim();
        // Framework (may work when system-signed / SET_TIME_ZONE).
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id));
        } catch (Exception ignored) {}
        try {
            android.app.AlarmManager am = null;
            Context app = SolarApplication.getAppContext();
            if (app != null) {
                am = (android.app.AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
                if (am != null) am.setTimeZone(id);
            }
        } catch (Exception ignored) {}
        // Root persist + broadcast — reliable on Solar ROMs (all targets rooted).
        String cmd = "setprop persist.sys.timezone " + shellQuote(id)
                + "; am broadcast -a android.intent.action.TIMEZONE_CHANGED "
                + "--es time-zone " + shellQuote(id) + " 2>/dev/null; true";
        boolean ok = RootShell.run(cmd, true);
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id));
        } catch (Exception ignored) {}
        return ok;
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
