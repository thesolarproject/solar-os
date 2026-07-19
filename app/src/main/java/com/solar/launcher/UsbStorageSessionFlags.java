package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cross-process USB plug-in prompt prefs and overlay dismiss handoff for {@link MainActivity}.
 * 2026-07-19 — Skip-prompt (without auto-connect) = let Android UsbStorageActivity handle it
 * (lighter than Solar concierge wake/finish/reclaim).
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

    /** Settings → skip Solar prompt — sysprop + prefs; Xposed reads sysprop (2026-07-05). */
    public static final String SYSPROP_SKIP_PROMPT = "sys.solar.usb.skip_prompt";
    /** Settings → auto-connect — Xposed concierge reads without stale SP (2026-07-06). */
    public static final String SYSPROP_AUTO_CONNECT = "sys.solar.usb.auto_connect";
    /**
     * 1 = leave stock UsbStorageActivity alone (no finish / no Solar wake).
     * Set when skip-prompt && !auto-connect. 2026-07-19
     */
    public static final String SYSPROP_STOCK_UI = "sys.solar.usb.stock_ui";

    /**
     * Default On — Android owns the plug-in dialog unless user wants Solar’s prompt.
     * Was: false (Solar always intercepted). Reversal: default false.
     * 2026-07-19
     */
    public static final boolean DEFAULT_SKIP_SOLAR_PROMPT = true;

    /**
     * True when Solar must not steal USB UI — stock SystemUI dialog / MTP path only.
     * Layman: cable in → Android asks, Solar stays out of the way (faster).
     * 2026-07-19
     */
    public static boolean preferStockUsbUi(Context ctx) {
        if (isAutoConnectEnabled(ctx)) return false;
        if (isStockUiFromSysprop()) return true;
        if (ctx == null) return DEFAULT_SKIP_SOLAR_PROMPT;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, DEFAULT_SKIP_SOLAR_PROMPT);
    }

    /** Test hook — skip && !auto ⇒ stock. 2026-07-19 */
    static boolean preferStockUsbUiFromPrefs(boolean skipSolarPrompt, boolean autoConnect) {
        return skipSolarPrompt && !autoConnect;
    }

    public static boolean shouldOfferUsbConnectPrompt(Context ctx) {
        if (preferStockUsbUi(ctx)) return false;
        if (isSkipPromptFromSysprop()) return false;
        if (ctx == null) return !DEFAULT_SKIP_SOLAR_PROMPT;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return !prefs.getBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, DEFAULT_SKIP_SOLAR_PROMPT);
    }

    /**
     * 2026-07-06 — Settings skip + boot-settle gate for all USB enable prompts.
     * 2026-07-16 — Also wait until Solar home is ready (no setup / prep face).
     * Layman: honors Skip prompt; waits after reboot-with-cable; never nags during setup.
     * Tech: boot settle + {@link FirstSessionReadyGate#isHomeReadyForUsbPrompt}.
     */
    public static boolean shouldOfferUsbConnectPromptAfterBootSettle(Context ctx) {
        if (!shouldOfferUsbConnectPrompt(ctx)) return false;
        if (!UsbHostSessionPolicy.isPromptAllowedAfterBootSettle(ctx)) return false;
        // 2026-07-16 — Defer enable prompt until home is usable (setup overlay / prep wizard gone).
        return FirstSessionReadyGate.isHomeReadyForUsbPrompt(ctx);
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
                .getBoolean(PREF_USB_SUPPRESS_CONNECT_PROMPT, DEFAULT_SKIP_SOLAR_PROMPT);
        writeSysprop(SYSPROP_SKIP_PROMPT, skip ? "1" : "0");
        // Stock UI when skipping Solar prompt and not auto-connecting.
        boolean stock = preferStockUsbUiFromPrefs(skip, isAutoConnectEnabled(ctx));
        writeSysprop(SYSPROP_STOCK_UI, stock ? "1" : "0");
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("skip", skip);
            d.put("stock", stock);
            d.put("auto", isAutoConnectEnabled(ctx));
            Debug543e15Log.log("UsbStorageSessionFlags.syncSkipPromptSysprop",
                    "usb sysprops synced", "H1", d);
        } catch (Exception ignored) {}
        // #endregion
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

    private static boolean isStockUiFromSysprop() {
        return "1".equals(readSysprop(SYSPROP_STOCK_UI));
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
