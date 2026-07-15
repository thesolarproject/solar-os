package com.solar.launcher.diag;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.SystemClock;

import com.solar.launcher.ConnectivityHelper;
import com.solar.launcher.WifiSleepPolicy;

/**
 * 2026-07-16 — Optional Wi‑Fi re-enable for diagnostic uploads.
 * Default path never wakes the radio (avoids radio thrash). Remote pull may wake, throttled.
 */
public final class SolarDiagWifiWake {
    private static final String PREF_LAST_WAKE_MS = "solar_diag_last_wifi_wake_ms";
    /** Non-remote: never auto-wake Wi‑Fi (was 45 min — still too chatty on sleepers). */
    private static final long MIN_WAKE_INTERVAL_MS = Long.MAX_VALUE / 4;
    private static final long MIN_WAKE_INTERVAL_REMOTE_MS = 15L * 60L * 1000L;
    private static final long ONLINE_WAIT_MS = 12_000L;
    private static final long ONLINE_POLL_MS = 2000L;

    public static final class Session {
        public final boolean weEnabledWifi;
        public final boolean online;

        Session(boolean weEnabledWifi, boolean online) {
            this.weEnabledWifi = weEnabledWifi;
            this.online = online;
        }
    }

    private SolarDiagWifiWake() {}

    /**
     * Ensure online if already connected. Only remote_pull may re-enable Wi‑Fi.
     * Routine/crash shipping waits for natural connectivity.
     */
    public static Session ensureOnlineForShip(Context context, SharedPreferences prefs,
            boolean remotePull) {
        if (context == null) return new Session(false, false);
        Context app = context.getApplicationContext();
        if (ConnectivityHelper.isOnline(app)) {
            return new Session(false, true);
        }
        // 2026-07-16 — Never toggle Wi‑Fi for background diagnostics (performance / battery).
        if (!remotePull) {
            return new Session(false, false);
        }
        long now = System.currentTimeMillis();
        long minInterval = MIN_WAKE_INTERVAL_REMOTE_MS;
        if (prefs != null) {
            long last = prefs.getLong(PREF_LAST_WAKE_MS, 0L);
            if (last > 0 && now - last < minInterval) {
                return new Session(false, ConnectivityHelper.isOnline(app));
            }
        }

        WifiManager wm = wifi(app);
        if (wm == null) {
            return new Session(false, false);
        }
        boolean wasEnabled;
        try {
            wasEnabled = wm.isWifiEnabled();
        } catch (Exception e) {
            return new Session(false, false);
        }
        boolean weEnabled = false;
        if (!wasEnabled) {
            try {
                SolarDiagFeatureLog.event("diag", "wifi_wake_enable for diagnostic ship");
                weEnabled = wm.setWifiEnabled(true);
                if (prefs != null) {
                    prefs.edit().putLong(PREF_LAST_WAKE_MS, now).apply();
                    // Mark so sleep policy knows radio was policy-off if it already was.
                    if (prefs.getBoolean(WifiSleepPolicy.PREF_DISABLED_BY_POLICY, false)) {
                        // keep flag — we will re-disable after ship
                    }
                }
            } catch (Exception e) {
                SolarDiagFeatureLog.warn("diag", "wifi_wake_failed: " + e.getMessage());
                return new Session(false, false);
            }
        } else if (prefs != null) {
            prefs.edit().putLong(PREF_LAST_WAKE_MS, now).apply();
        }

        boolean online = waitUntilOnline(app, ONLINE_WAIT_MS);
        SolarDiagFeatureLog.event("diag", "wifi_wake_result online=" + online
                + " weEnabled=" + weEnabled);
        return new Session(weEnabled, online);
    }

    /** Turn Wi‑Fi back off after ship if this session enabled it under sleep policy. */
    public static void restoreAfterShip(Context context, SharedPreferences prefs, Session session) {
        if (session == null || !session.weEnabledWifi || context == null) return;
        boolean policyOff = prefs != null
                && prefs.getBoolean(WifiSleepPolicy.PREF_DISABLED_BY_POLICY, false);
        // Only re-disable when sleep policy still wants radio off (user didn't turn Wi‑Fi on themselves).
        if (!policyOff) return;
        try {
            WifiManager wm = wifi(context.getApplicationContext());
            if (wm != null && wm.isWifiEnabled()) {
                SolarDiagFeatureLog.event("diag", "wifi_restore_off after diagnostic ship");
                wm.setWifiEnabled(false);
            }
        } catch (Exception e) {
            SolarDiagFeatureLog.warn("diag", "wifi_restore_failed: " + e.getMessage());
        }
    }

    private static boolean waitUntilOnline(Context app, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (ConnectivityHelper.isOnline(app)) return true;
            try {
                Thread.sleep(ONLINE_POLL_MS);
            } catch (InterruptedException e) {
                return ConnectivityHelper.isOnline(app);
            }
        }
        return ConnectivityHelper.isOnline(app);
    }

    private static WifiManager wifi(Context ctx) {
        try {
            return (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }
}
