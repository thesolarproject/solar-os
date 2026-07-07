package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * 2026-07-06 — USB host session boot gate: no enable prompt while PC cable was in at boot
 * until settle window passes or user unplugged once.
 * Layman: reboot with USB already plugged in waits before nagging about disk mode.
 * Tech: prefs + sysprops for :overlay/Xposed; {@link #onBootCompleted} records sticky USB_STATE.
 * Reversal: delete and call {@link UsbStorageSessionFlags#shouldOfferUsbConnectPrompt} only.
 */
public final class UsbHostSessionPolicy {

    /** Same store as {@link UsbStorageSessionFlags}. */
    static final String PREFS = UsbStorageSessionFlags.PREFS;
    static final String PREF_BOOT_HOST_AT_BOOT = "usb_boot_host_at_boot";
    static final String PREF_HOST_DISC_SINCE_BOOT = "usb_host_disc_since_boot";
    static final String PREF_BOOT_ELAPSED_RT = "usb_boot_elapsed_rt";
    /** PC host session — one prompt evaluation per plug; cleared on cable unplug (2026-07-06). */
    static final String PREF_SESSION_ACTIVE = "usb_host_session_active";
    static final String PREF_SESSION_DISMISSED = "usb_host_session_dismissed";
    static final String PREF_SESSION_PROMPT_EVALUATED = "usb_host_session_prompt_evaluated";

    /** Xposed / :overlay read without SharedPreferences staleness (2026-07-06). */
    public static final String SYSPROP_BOOT_HOST_AT_BOOT = "sys.solar.usb.boot_host_at_boot";
    public static final String SYSPROP_BOOT_SETTLE_READY = "sys.solar.usb.boot_settle_ready";
    /** 1 while user dismissed enable prompt this host session — stops poll/recovery (2026-07-06). */
    public static final String SYSPROP_SESSION_DISMISSED = "sys.solar.usb.session_dismissed";

    /** Post-boot quiet window before prompting when host was connected at boot (ms). */
    private static final long BOOT_SETTLE_MS = 60_000L;

    private static final Handler SETTLE_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pendingSettleRunnable;

    private UsbHostSessionPolicy() {}

    /**
     * Call from {@link BootReceiver} and {@link SolarApplication} once per boot.
     * Records whether PC host was already connected and arms settle timer if needed.
     */
    public static void onBootCompleted(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        boolean hostAtBoot = probeHostConnectedAtBoot(app);
        long elapsed = SystemClock.elapsedRealtime();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_BOOT_HOST_AT_BOOT, hostAtBoot)
                .putBoolean(PREF_HOST_DISC_SINCE_BOOT, false)
                .putLong(PREF_BOOT_ELAPSED_RT, elapsed)
                .commit();
        syncSysprops(app, hostAtBoot, false, elapsed);
        scheduleSettleUnlock(app, hostAtBoot, elapsed);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hostAtBoot", hostAtBoot);
            d.put("bootElapsedRt", elapsed);
            d.put("settleMs", BOOT_SETTLE_MS);
            DebugAf054eLog.log(app, "UsbHostSessionPolicy.onBootCompleted",
                    "boot USB session recorded", "USB-SETTLE", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /**
     * 2026-07-06 — Fresh PC host plug after disconnect/charger-only; arms one-shot prompt evaluation.
     * Layman: new USB session starts — we may ask about disk mode once.
     * Tech: session flags reset on prior {@link #onUsbHostDisconnected}; idempotent if already active.
     * Reversal: delete and rely on MainActivity {@code usbDialog*} booleans only (process-local).
     */
    public static void onUsbHostConnected(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastDisc = prefs.getLong("usb_last_disconnect_time", 0L);
        long elapsed = SystemClock.elapsedRealtime();
        boolean isFlap = lastDisc > 0L && (elapsed - lastDisc) < 3000L;
        if (isFlap) {
            // Cable flap — keep existing session state (evaluated/dismissed)
            return;
        }
        // ponytail: reset and start new session since it's not a flap (avoids stuck active sessions)
        prefs.edit()
                .putBoolean(PREF_SESSION_ACTIVE, true)
                .putBoolean(PREF_SESSION_DISMISSED, false)
                .putBoolean(PREF_SESSION_PROMPT_EVALUATED, false)
                .commit();
        syncSessionSysprop(app, false);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("sessionActive", true);
            DebugAf054eLog.log(app, "UsbHostSessionPolicy.onUsbHostConnected",
                    "host session armed — evaluate prompt once", "USB-SESSION", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** Cable unplug — reset host session + unlock boot-settle without waiting full window (2026-07-06). */
    public static void onUsbHostDisconnected(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong("usb_last_disconnect_time", SystemClock.elapsedRealtime()).commit();
        // Do not clear flags immediately, let onUsbHostConnected do it if not a flap
        syncSessionSysprop(app, false);
        if (!prefs.getBoolean(PREF_HOST_DISC_SINCE_BOOT, false)) {
            long bootElapsed = prefs.getLong(PREF_BOOT_ELAPSED_RT, 0L);
            prefs.edit().putBoolean(PREF_HOST_DISC_SINCE_BOOT, true).commit();
            syncSysprops(app, prefs.getBoolean(PREF_BOOT_HOST_AT_BOOT, false), true, bootElapsed);
        }
        cancelPendingSettle();
        cancelPendingHostEval();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("reason", "session_reset");
            DebugAf054eLog.log(app, "UsbHostSessionPolicy.onUsbHostDisconnected",
                    "host disconnect — session cleared", "USB-SESSION", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** User chose Turn on USB storage — clear dismiss idle for this plug (2026-07-06). */
    public static void clearUserDismissed(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_SESSION_DISMISSED, false)
                .commit();
        syncSessionSysprop(app, false);
    }

    /** User dismissed USB enable modal — idle until cable unplug (2026-07-06). */
    public static void markUserDismissed(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_SESSION_DISMISSED, true)
                .putBoolean(PREF_SESSION_PROMPT_EVALUATED, true)
                .commit();
        syncSessionSysprop(app, true);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("dismissed", true);
            DebugAf054eLog.log(app, "UsbHostSessionPolicy.markUserDismissed",
                    "USB session idle until disconnect", "USB-SESSION", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** Prompt shown, auto-connect, or policy skip — no re-evaluation this plug (2026-07-06). */
    public static void markPromptEvaluated(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_SESSION_PROMPT_EVALUATED, true)
                .commit();
    }

    /** True after user taps Dismiss on USB enable tier this host session. */
    public static boolean hasUserDismissedThisSession(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SESSION_DISMISSED, false);
    }

    /** True once this plug's prompt decision ran (show, skip, auto-connect, or dismiss). */
    public static boolean hasPromptEvaluatedThisSession(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SESSION_PROMPT_EVALUATED, false);
    }

    /**
     * One evaluation per host session — connect event only, not timer/resume/focus (2026-07-06).
     * Reversal: always return true and restore poll-driven {@code routeUsbHostInterceptUi}.
     */
    public static boolean shouldEvaluatePromptThisSession(Context context) {
        if (context == null) return false;
        if (!isPcHostConnected(context)) return false;
        if (hasUserDismissedThisSession(context)) return false;
        if (hasPromptEvaluatedThisSession(context)) return false;
        return isPromptAllowedAfterBootSettle(context);
    }

    /** Poll/HOME/recovery agent must stay off after dismiss or when Xposed owns USB (2026-07-06). */
    public static boolean isAggressiveUsbWorkSuppressed(Context context) {
        if (context == null) return false;
        if (hasUserDismissedThisSession(context)) return true;
        return UsbStorageConcierge.isXposedConciergeActive();
    }

    /**
     * Run {@code work} now or after boot-settle when PC was connected at cold start (2026-07-06).
     * Layman: reboot with USB plugged in waits before fighting SystemUI.
     */
    public static void runWhenPromptAllowed(final Context context, final Runnable work) {
        if (context == null || work == null) return;
        final Context app = context.getApplicationContext();
        if (isPromptAllowedAfterBootSettle(app)) {
            work.run();
            return;
        }
        cancelPendingHostEval();
        final SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final long bootElapsed = prefs.getLong(PREF_BOOT_ELAPSED_RT, 0L);
        long delay = bootElapsed > 0L
                ? Math.max(0L, bootElapsed + BOOT_SETTLE_MS - SystemClock.elapsedRealtime())
                : BOOT_SETTLE_MS;
        pendingHostEvalRunnable = new Runnable() {
            @Override
            public void run() {
                pendingHostEvalRunnable = null;
                if (isPromptAllowedAfterBootSettle(app) && isPcHostConnected(app)) {
                    work.run();
                }
            }
        };
        SETTLE_HANDLER.postDelayed(pendingHostEvalRunnable, delay);
    }

    private static Runnable pendingHostEvalRunnable;

    private static void cancelPendingHostEval() {
        if (pendingHostEvalRunnable != null) {
            SETTLE_HANDLER.removeCallbacks(pendingHostEvalRunnable);
            pendingHostEvalRunnable = null;
        }
    }

    private static void clearHostSessionPrefs(SharedPreferences prefs) {
        prefs.edit()
                .putBoolean(PREF_SESSION_ACTIVE, false)
                .putBoolean(PREF_SESSION_DISMISSED, false)
                .putBoolean(PREF_SESSION_PROMPT_EVALUATED, false)
                .commit();
    }

    private static void writeSysprop(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, val);
        } catch (Exception e) {
            if (RootShell.canRun()) {
                RootShell.run("setprop " + key + " " + val);
            }
        }
    }

    private static void syncSessionSysprop(Context context, boolean dismissed) {
        writeSysprop(SYSPROP_SESSION_DISMISSED, dismissed ? "1" : "0");
    }

    /**
     * True when USB enable prompt / auto-connect may run (boot settle satisfied).
     * Layman: OK to ask about disk mode now.
     * Tech: not boot-host-at-boot, or disconnect seen, or settle deadline passed.
     */
    public static boolean isPromptAllowedAfterBootSettle(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_BOOT_HOST_AT_BOOT, false)) {
            return true;
        }
        if (prefs.getBoolean(PREF_HOST_DISC_SINCE_BOOT, false)) {
            return true;
        }
        long bootElapsed = prefs.getLong(PREF_BOOT_ELAPSED_RT, 0L);
        if (bootElapsed <= 0L) {
            return true;
        }
        boolean settled = SystemClock.elapsedRealtime() >= bootElapsed + BOOT_SETTLE_MS;
        if (settled) {
            return true;
        }
        logPromptSuppressed(context, "boot_settle");
        return false;
    }

    /** Sync sysprops so SystemUI Xposed sees boot-settle + session dismiss without SP (2026-07-06). */
    public static void syncSysprops(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        syncSysprops(context,
                prefs.getBoolean(PREF_BOOT_HOST_AT_BOOT, false),
                prefs.getBoolean(PREF_HOST_DISC_SINCE_BOOT, false),
                prefs.getLong(PREF_BOOT_ELAPSED_RT, 0L));
        syncSessionSysprop(context, prefs.getBoolean(PREF_SESSION_DISMISSED, false));
    }

    private static void syncSysprops(Context context, boolean hostAtBoot,
            boolean discSinceBoot, long bootElapsedRt) {
        writeSysprop(SYSPROP_BOOT_HOST_AT_BOOT, hostAtBoot ? "1" : "0");
        boolean ready = !hostAtBoot || discSinceBoot
                || (bootElapsedRt > 0L
                && SystemClock.elapsedRealtime() >= bootElapsedRt + BOOT_SETTLE_MS);
        writeSysprop(SYSPROP_BOOT_SETTLE_READY, ready ? "1" : "0");
    }

    private static void scheduleSettleUnlock(final Context app, boolean hostAtBoot, final long bootElapsed) {
        cancelPendingSettle();
        if (!hostAtBoot) return;
        pendingSettleRunnable = new Runnable() {
            @Override
            public void run() {
                pendingSettleRunnable = null;
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                syncSysprops(app,
                        prefs.getBoolean(PREF_BOOT_HOST_AT_BOOT, false),
                        prefs.getBoolean(PREF_HOST_DISC_SINCE_BOOT, false),
                        bootElapsed);
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("reason", "boot_settle");
                    d.put("settleMs", BOOT_SETTLE_MS);
                    DebugAf054eLog.log(app, "UsbHostSessionPolicy.scheduleSettleUnlock",
                            "boot settle window elapsed — prompts allowed", "USB-SETTLE", d);
                } catch (Exception ignored) {}
                // #endregion
            }
        };
        SETTLE_HANDLER.postDelayed(pendingSettleRunnable, BOOT_SETTLE_MS);
    }

    private static void cancelPendingSettle() {
        if (pendingSettleRunnable != null) {
            SETTLE_HANDLER.removeCallbacks(pendingSettleRunnable);
            pendingSettleRunnable = null;
        }
    }

    private static void logPromptSuppressed(Context context, String reason) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("reason", reason);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            d.put("hostAtBoot", prefs.getBoolean(PREF_BOOT_HOST_AT_BOOT, false));
            d.put("discSinceBoot", prefs.getBoolean(PREF_HOST_DISC_SINCE_BOOT, false));
            long bootElapsed = prefs.getLong(PREF_BOOT_ELAPSED_RT, 0L);
            d.put("msUntilSettle", bootElapsed > 0L
                    ? Math.max(0L, bootElapsed + BOOT_SETTLE_MS - SystemClock.elapsedRealtime())
                    : 0L);
            DebugAf054eLog.log(context, "UsbHostSessionPolicy.isPromptAllowedAfterBootSettle",
                    "USB prompt suppressed", "USB-SETTLE", d);
        } catch (Exception ignored) {}
    }

    /** Sticky USB_STATE — host (PC) connected when Solar boots (2026-07-06). */
    static boolean probeHostConnectedAtBoot(Context context) {
        return isPcHostConnected(context);
    }

    /**
     * 2026-07-06 — Sticky USB_STATE host probe for :overlay pending-USB flush after ANR dismiss.
     * Layman: only show deferred USB prompt if the PC cable is still plugged in.
     */
    public static boolean isPcHostConnected(Context context) {
        if (context == null) return false;
        try {
            Intent sticky = context.registerReceiver(null,
                    new IntentFilter(UsbHostWakeReceiver.ACTION_USB_STATE));
            if (sticky == null) return false;
            if (!sticky.getBooleanExtra("connected", false)) return false;
            return sticky.getBooleanExtra("host_connected", false)
                    || sticky.getBooleanExtra("mass_storage", false)
                    || sticky.getBooleanExtra("USB_IS_PC_KNOW_ME", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Test hook — boot gate without Android context clock. */
    static boolean isPromptAllowedForTest(boolean hostAtBoot, boolean discSinceBoot,
            long bootElapsedRt, long nowElapsedRt) {
        if (!hostAtBoot) return true;
        if (discSinceBoot) return true;
        if (bootElapsedRt <= 0L) return true;
        return nowElapsedRt >= bootElapsedRt + BOOT_SETTLE_MS;
    }

    /** Test hook — session prompt evaluation gate without Android context. */
    static boolean shouldEvaluatePromptForTest(boolean hostConnected, boolean dismissed,
            boolean evaluated, boolean bootSettleReady) {
        if (!hostConnected) return false;
        if (dismissed) return false;
        if (evaluated) return false;
        return bootSettleReady;
    }

    /** Test hook — aggressive USB poll/recovery suppressed after dismiss. */
    static boolean isAggressiveWorkSuppressedForTest(boolean dismissed, boolean conciergeActive) {
        return dismissed || conciergeActive;
    }

    /** Test hook — session reset clears dismiss + evaluated flags. */
    static SessionStateForTest resetSessionForTest(SessionStateForTest state) {
        return new SessionStateForTest(false, false, false);
    }

    /** Test hook — host connect arms fresh session. */
    static SessionStateForTest onHostConnectForTest(SessionStateForTest state) {
        if (state.sessionActive) return state;
        return new SessionStateForTest(true, false, false);
    }

    /** Test hook — user dismiss marks session idle. */
    static SessionStateForTest onUserDismissForTest(SessionStateForTest state) {
        return new SessionStateForTest(state.sessionActive, true, true);
    }

    static final class SessionStateForTest {
        final boolean sessionActive;
        final boolean dismissed;
        final boolean promptEvaluated;

        SessionStateForTest(boolean sessionActive, boolean dismissed, boolean promptEvaluated) {
            this.sessionActive = sessionActive;
            this.dismissed = dismissed;
            this.promptEvaluated = promptEvaluated;
        }
    }
}
