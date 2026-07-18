package com.solar.launcher;

import android.content.SharedPreferences;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.soulseek.SoulseekAccount;

/**
 * Reach master + per-service enable flags (Soulseek / Deezer).
 * When {@link #isSoulseekActive} is false, no Soulseek client, share scan, NAT-PMP,
 * diag PM session, or impact ping wire traffic should run (energy + heat).
 */
public final class ReachPolicy {
    private ReachPolicy() {}

    public static boolean isMasterEnabled(SharedPreferences prefs) {
        if (prefs == null) return true;
        return prefs.getBoolean(SoulseekAccount.PREF_REACH_ENABLED, true);
    }

    public static boolean isSoulseekPrefEnabled(SharedPreferences prefs) {
        // 2026-07-17 — Soulseek opt-in (off by default). Get Music defaults to Deezer-only
        // until the user enables Soulseek in Settings → Reach → Soulseek. Reduces background
        // peer work while Soulseek is still below MVP; users can turn it on when they want it.
        if (prefs == null) return false;
        return prefs.getBoolean(SoulseekAccount.PREF_SOULSEEK_ENABLED, false);
    }

    public static boolean isDeezerPrefEnabled(SharedPreferences prefs) {
        return DeezerAccount.isEnabled(prefs);
    }

    /** Master on and Soulseek service on — client, messaging, Get Music Reach. */
    public static boolean isSoulseekActive(SharedPreferences prefs) {
        return isMasterEnabled(prefs) && isSoulseekPrefEnabled(prefs);
    }

    /**
     * Background Soulseek sockets / workers allowed (main client, diag PM session, outbox).
     * False by default so cold boot stays cool when Soulseek is opted out.
     */
    public static boolean allowsBackgroundSoulseekWork(SharedPreferences prefs) {
        return isSoulseekActive(prefs);
    }

    /** Master on and Deezer service on — session, Get Music Deezer. */
    public static boolean isDeezerActive(SharedPreferences prefs) {
        return isMasterEnabled(prefs) && isDeezerPrefEnabled(prefs);
    }
}
