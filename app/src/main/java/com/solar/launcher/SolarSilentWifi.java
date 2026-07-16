package com.solar.launcher;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 2026-07-16 — Background Wi‑Fi wake that stays invisible in Solar UI.
 *
 * <p>Boot/NTP/diag may briefly power the radio without the user having “turned Wi‑Fi on”.
 * While a silent session is active:
 * <ul>
 *   <li>Status-bar Wi‑Fi icon stays hidden</li>
 *   <li>Wi‑Fi settings / quick toggle report Off</li>
 *   <li>Real networking still works (ConnectivityHelper, NTP, HTTP)</li>
 * </ul>
 * When the user explicitly enables Wi‑Fi (settings toggle, context chip, etc.),
 * {@link #onUserWifiIntent()} clears the silent mask so UI shows the true radio state.
 *
 * <p>Nested {@link #begin}/{@link #end} pairs are reference-counted. Only the session that
 * first powered the radio will power it back off if the user never claimed it.
 */
public final class SolarSilentWifi {
    private static final AtomicInteger depth = new AtomicInteger(0);
    /** True when this class turned the radio on and still owns turning it off. */
    private static final AtomicBoolean weEnabledRadio = new AtomicBoolean(false);
    private static final Object lock = new Object();

    private SolarSilentWifi() {}

    /** UI should pretend Wi‑Fi is off (icon, labels, network list). */
    public static boolean isUiHidden() {
        return depth.get() > 0;
    }

    /** True when radio is powered but Solar is masking it from the user. */
    public static boolean isSilentRadioOn(Context ctx) {
        if (!isUiHidden()) return false;
        try {
            WifiManager wm = wifi(ctx);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * User intentionally interacted with Wi‑Fi power (toggle on/off, open setup to connect).
     * Clears silent mask; radio state is left as-is for the caller to manage.
     */
    public static void onUserWifiIntent() {
        synchronized (lock) {
            depth.set(0);
            weEnabledRadio.set(false);
        }
    }

    /**
     * Ensure Wi‑Fi radio is on for background work. If it was already on for the user,
     * does not enter silent mode. If it was off, enables and marks silent.
     *
     * @return true if the radio is enabled after this call (or was already)
     */
    public static boolean begin(Context ctx) {
        if (ctx == null) return false;
        final Context app = ctx.getApplicationContext();
        synchronized (lock) {
            WifiManager wm = wifi(app);
            if (wm == null) return false;
            boolean wasOn;
            try {
                wasOn = wm.isWifiEnabled();
            } catch (Exception e) {
                return false;
            }
            if (wasOn) {
                if (depth.get() > 0) {
                    // Nested silent work while already silent.
                    depth.incrementAndGet();
                }
                // User-visible on — leave UI alone, no restore obligation.
                return true;
            }
            // Radio off → silent enable.
            depth.incrementAndGet();
            boolean enabled = false;
            try {
                enabled = wm.setWifiEnabled(true);
            } catch (Exception ignored) {
                enabled = false;
            }
            if (!enabled) {
                // Root kick when framework setWifiEnabled no-ops on some builds.
                try {
                    RootShell.run("svc wifi enable", true);
                    enabled = true;
                } catch (Exception ignored) {}
            }
            if (enabled) {
                weEnabledRadio.set(true);
                return true;
            }
            // Failed to enable — drop this depth level.
            int d = depth.decrementAndGet();
            if (d < 0) depth.set(0);
            return false;
        }
    }

    /**
     * End one {@link #begin} pairing. When the last silent session ends and we still own
     * the radio (user never claimed it), power Wi‑Fi back off.
     */
    public static void end(Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        boolean shouldDisable = false;
        synchronized (lock) {
            int d = depth.decrementAndGet();
            if (d < 0) {
                depth.set(0);
                d = 0;
            }
            if (d == 0 && weEnabledRadio.compareAndSet(true, false)) {
                shouldDisable = true;
            }
        }
        if (!shouldDisable) return;
        try {
            WifiManager wm = wifi(app);
            if (wm != null) {
                try {
                    wm.setWifiEnabled(false);
                } catch (Exception ignored) {
                    RootShell.run("svc wifi disable", true);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Run work with optional silent wake if offline; always restores if we woke the radio.
     * Blocks the calling thread up to {@code waitOnlineMs} for connectivity.
     */
    public static void runWithOptionalWake(Context ctx, long waitOnlineMs, Runnable work) {
        if (ctx == null || work == null) return;
        final Context app = ctx.getApplicationContext();
        boolean online = false;
        try {
            online = ConnectivityHelper.isOnline(app);
        } catch (Exception ignored) {}
        boolean began = false;
        if (!online) {
            began = begin(app);
            if (began) {
                waitUntilOnline(app, waitOnlineMs);
            }
        }
        try {
            work.run();
        } finally {
            if (began) end(app);
        }
    }

    private static void waitUntilOnline(Context app, long maxWaitMs) {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0L, maxWaitMs);
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (ConnectivityHelper.isOnline(app)) return;
            } catch (Exception ignored) {}
            try {
                Thread.sleep(800L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static WifiManager wifi(Context ctx) {
        if (ctx == null) return null;
        try {
            return (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }
}
