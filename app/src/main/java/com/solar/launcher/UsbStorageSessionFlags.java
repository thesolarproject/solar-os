package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cross-process USB plug-in prompt prefs and overlay dismiss handoff for {@link MainActivity}.
 */
public final class UsbStorageSessionFlags {

    /** Same store as {@link MainActivity} settings — readable from Solar :overlay process. */
    static final String PREFS = "SOLAR_SETTINGS";
    static final String PREF_USB_SUPPRESS_CONNECT_PROMPT = "usb_suppress_connect_prompt";
    static final String PREF_USB_AUTO_CONNECT = "usb_auto_connect";
    static final String PREF_USB_MANUAL_DISABLE = "usb_manual_disable";
    /** Written when user dismisses the global USB overlay — consumed on MainActivity resume. */
    static final String KEY_OVERLAY_DISMISS_PENDING = "usb_overlay_dismiss_pending";

    private UsbStorageSessionFlags() {}

    /** Settings → skip plug-in prompt — sysprop + prefs; global overlay and SystemUI read sysprop (2026-07-05). */
    public static final String SYSPROP_SKIP_PROMPT = "sys.solar.usb.skip_prompt";
    /** Settings → auto-connect — Xposed concierge reads without stale SP (2026-07-06). */
    public static final String SYSPROP_AUTO_CONNECT = "sys.solar.usb.auto_connect";

    public static boolean shouldOfferUsbConnectPrompt(Context ctx) {
        if (isSkipPromptFromSysprop()) return false;
        if (ctx == null) return true;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !prefs.getBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, false);
    }

    /**
     * 2026-07-06 — Settings skip + boot-settle gate for all USB enable prompts.
     * Layman: honors Skip prompt and waits after reboot-with-cable before asking.
     * Tech: delegates to {@link UsbHostSessionPolicy#isPromptAllowedAfterBootSettle}.
     */
    public static boolean shouldOfferUsbConnectPromptAfterBootSettle(Context ctx) {
        if (!shouldOfferUsbConnectPrompt(ctx)) return false;
        return UsbHostSessionPolicy.isPromptAllowedAfterBootSettle(ctx);
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

    /** Root setprop so :overlay + SystemUI Xposed see Skip pref without stale SP cache (2026-07-05). */
    public static void syncSkipPromptSysprop(Context ctx) {
        if (ctx == null) return;
        boolean skip = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, false);
        writeSysprop(SYSPROP_SKIP_PROMPT, skip ? "1" : "0");
    }

    /** Sync skip + auto-connect sysprops for Xposed USB concierge (2026-07-06). */
    public static void syncUsbSessionSysprops(Context ctx) {
        syncSkipPromptSysprop(ctx);
        syncAutoConnectSysprop(ctx);
        UsbHostSessionPolicy.syncSysprops(ctx);
    }

    /**
     * 2026-07-06 — Auto-connect implies skip plug-in prompts (user opted into silent attach).
     * Layman: turning on auto USB disk mode also hides repeated nag dialogs.
     */
    public static void applyAutoConnectSideEffects(Context ctx, boolean autoConnect) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (autoConnect) {
            prefs.edit().putBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, true).commit();
        }
        syncUsbSessionSysprops(ctx);
    }

    /** Root setprop — bridge reads auto-connect; unset pref = off (2026-07-06). */
    public static void syncAutoConnectSysprop(Context ctx) {
        if (ctx == null) return;
        boolean autoConnect = isAutoConnectEnabled(ctx);
        writeSysprop(SYSPROP_AUTO_CONNECT, autoConnect ? "1" : "0");
    }

    /** Test hook — sysprop skip without shell (2026-07-05). */
    static boolean isSkipPromptFromSyspropForTest(String propValue) {
        return "1".equals(propValue);
    }

    private static boolean isSkipPromptFromSysprop() {
        return isSkipPromptFromSyspropForTest(readSysprop(SYSPROP_SKIP_PROMPT));
    }

    /** getprop — no su; readable from main and :overlay (2026-07-05). */
    private static String readSysprop(String key) {
        // ponytail: reflection-based get is much faster than exec("getprop")
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, "");
            if (v != null && !v.toString().isEmpty()) {
                return v.toString().trim();
            }
        } catch (Exception ignored) {}
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"getprop", key});
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line = r.readLine();
            proc.waitFor();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    /** Settings → auto-connect — opt-in only; never default on (2026-07-06). */
    public static boolean isAutoConnectEnabled(Context ctx) {
        // ponytail: check system property first to bypass stale SharedPreferences cache in :overlay process
        String sysval = readSysprop(SYSPROP_AUTO_CONNECT);
        if ("1".equals(sysval)) {
            return true;
        } else if ("0".equals(sysval)) {
            return false;
        }
        if (ctx == null) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_USB_AUTO_CONNECT, false)
                && !prefs.getBoolean(PREF_USB_MANUAL_DISABLE, false);
    }

    /** Overlay dismiss — persist until MainActivity applies {@code onUsbStorageEnableDialogDismissed}. */
    public static void markOverlayDismissPending(Context ctx) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_OVERLAY_DISMISS_PENDING, true)
                .commit();
    }

    /** Consume one-shot dismiss flag written by the global USB overlay tier. */
    public static boolean consumeOverlayDismissPending(Context ctx) {
        if (ctx == null) return false;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_OVERLAY_DISMISS_PENDING, false)) {
            return false;
        }
        prefs.edit().remove(KEY_OVERLAY_DISMISS_PENDING).apply();
        return true;
    }

    /** Test hook — pref booleans without Android context. */
    static boolean shouldOfferFromPrefs(boolean suppressPrompt) {
        return !suppressPrompt;
    }

    /** Test hook — auto-connect gated on manual-disable flag. */
    static boolean isAutoConnectFromPrefs(boolean autoConnect, boolean manualDisable) {
        return autoConnect && !manualDisable;
    }
}
