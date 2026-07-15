package com.solar.launcher.diag;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.ConnectivityHelper;

/**
 * 2026-07-16 — Connectivity check before diagnostic HTTPS (never toggles Wi‑Fi).
 *
 * Why no radio wake: a remote pull is triggered by a Soulseek private message.
 * That message only arrives when Reach already has a network path (typically Wi‑Fi).
 * If Wi‑Fi were asleep, the PM would not have been received — so "wake for remote pull"
 * is both unnecessary and logically inverted. Crash/routine ships wait for natural online.
 */
public final class SolarDiagWifiWake {

    public static final class Session {
        /** Always false — we never enable the radio for diag. */
        public final boolean weEnabledWifi;
        public final boolean online;

        Session(boolean weEnabledWifi, boolean online) {
            this.weEnabledWifi = weEnabledWifi;
            this.online = online;
        }
    }

    private SolarDiagWifiWake() {}

    /**
     * Snapshot current connectivity. Does not call setWifiEnabled or wait on the radio.
     * @param remotePull ignored — kept for call-site compatibility; pull already implies online
     */
    public static Session ensureOnlineForShip(Context context, SharedPreferences prefs,
            boolean remotePull) {
        if (context == null) return new Session(false, false);
        boolean online = ConnectivityHelper.isOnline(context.getApplicationContext());
        return new Session(false, online);
    }

    /** No-op restore — we never flipped Wi‑Fi on for diagnostics. */
    public static void restoreAfterShip(Context context, SharedPreferences prefs, Session session) {
        // Intentionally empty.
    }
}
