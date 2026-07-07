package com.solar.launcher.soulseek;

import android.content.Context;
import android.widget.Toast;

import java.util.Locale;

/**
 * 2026-07-05 — Shared Reach transient error classification + calm user feedback.
 * Layman: brief disconnect hiccups retry quietly; only show one toast, do not jump screens.
 * Technical: extracted from MainActivity.isTransientReachError for SoulseekClient callbacks.
 * Reversal: inline checks back into MainActivity only.
 */
public final class ReachErrorPolicy {

    private static volatile long lastTransientToastMs;

    private ReachErrorPolicy() {}

    /** True for EOF/socket/disconnect strings — safe to retry without UI churn. */
    public static boolean isTransient(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        String lower = msg.toLowerCase(Locale.US);
        if (lower.contains("eof") || lower.contains("socket")
                || lower.contains("disconnected") || lower.contains("econnreset")
                || lower.contains("not connected") || lower.contains("connection reset")) {
            return true;
        }
        return "EOFException".equals(msg) || "SocketException".equals(msg)
                || "SocketTimeoutException".equals(msg);
    }

    /** One toast per 8s for transient errors — optional reconnect hook. */
    public static void notifyTransientIfNeeded(Context context, String msg, Runnable reconnect) {
        if (context == null || !isTransient(msg)) return;
        long now = System.currentTimeMillis();
        if (now - lastTransientToastMs > 8000L) {
            lastTransientToastMs = now;
            Toast.makeText(context.getApplicationContext(),
                    context.getString(com.solar.launcher.R.string.reach_error_transient),
                    Toast.LENGTH_SHORT).show();
        }
        if (reconnect != null) {
            reconnect.run();
        }
    }
}
