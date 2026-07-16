package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Ensure every Reach inbox has a blank Solar Development (Tom-from-MySpace) row.
 * Does not ship diagnostics or send wire PMs.
 */
public final class SolarDevelopmentBootstrap {
    private static final String PREF_PLACEHOLDER_SEEDED = "solar_dev_inbox_placeholder_seeded";
    private static volatile boolean startupDone;

    private SolarDevelopmentBootstrap() {}

    /** After Soulseek login — seed local placeholder only (no network). */
    public static void onSoulseekConnected(final Context context, final SharedPreferences prefs,
            final SoulseekClient client) {
        ensureVirtualInboxPlaceholder(context, prefs);
    }

    static void sendDeveloperOnlinePing(Context context, SharedPreferences prefs,
            SoulseekClient client) {
        // Intentionally empty — online pings are impact/diag paths, not bootstrap.
    }

    /**
     * Guarantee the virtual Solar Development peer exists in local PM storage so Messages
     * always shows the row even with zero human messages (preview blank).
     */
    public static void ensureVirtualInboxPlaceholder(Context context, SharedPreferences prefs) {
        if (context == null) return;
        try {
            SharedPreferences p = prefs != null ? prefs
                    : context.getApplicationContext()
                    .getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
            // Always ensure DB row; cheap when already present.
            SoulseekMessaging.Message last = SoulseekMessaging.lastMessageForPeer(
                    context, p, SolarDeveloperAccounts.VIRTUAL_PEER);
            if (last != null) {
                if (p != null && !p.getBoolean(PREF_PLACEHOLDER_SEEDED, false)) {
                    p.edit().putBoolean(PREF_PLACEHOLDER_SEEDED, true).apply();
                }
                return;
            }
            // Seed a zero-length outgoing placeholder that is not auto-diag so inbox lists it.
            // Empty body → UI preview stays blank (Tom-style presence without a fake welcome).
            SoulseekMessaging.append(context, p, new SoulseekMessaging.Message(
                    1,
                    1,
                    SolarDeveloperAccounts.VIRTUAL_PEER,
                    "\u200B", // zero-width space — not auto-diag, invisible preview
                    false));
            if (p != null) {
                p.edit().putBoolean(PREF_PLACEHOLDER_SEEDED, true).apply();
            }
            startupDone = true;
        } catch (Exception ignored) {}
    }

    /** Test hook — reset process guard. */
    static void resetForTest() {
        startupDone = false;
    }
}
