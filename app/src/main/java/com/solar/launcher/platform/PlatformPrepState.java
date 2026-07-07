package com.solar.launcher.platform;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 2026-07-05 — Tracks platform prep version; silent repair when ROM already matches.
 * Reversal: delete prefs; boot repair runs only via XposedModuleEnsurer again.
 */
public final class PlatformPrepState {

    static final String PREFS = "solar_platform_prep";
    static final String KEY_APPLIED_VERSION = "applied_prep_version";
    static final String KEY_OUTCOME = "outcome";
    static final String KEY_RERUN = "rerun_requested";
    static final String KEY_REBOOT_PENDING = "reboot_pending";

    /** Outcome after last prep run. */
    public enum Outcome {
        COMPLETE, PARTIAL, LIMITED, DISMISSED, NONE
    }

    private PlatformPrepState() {}

    /** True when bundled prepVersion has not been applied yet (or OTA rerun). */
    public static boolean needsSilentPrep(Context ctx, PlatformPrepManifest manifest) {
        if (ctx == null || manifest == null) return false;
        if (isRerunRequested(ctx)) return true;
        return manifest.prepVersion > getAppliedVersion(ctx);
    }

    /**
     * Blocking wizard only for manual repair from Settings — not auto-boot.
     * Auto-boot uses silent {@link SolarPlatformPrep#ensureAsync}.
     */
    public static boolean isManualWizardRequired(Context ctx, PlatformPrepManifest manifest,
            PlatformProbe.Report report) {
        return needsSilentPrep(ctx, manifest);
    }

    /** @deprecated Auto-loop used gaps after prep — use {@link #needsSilentPrep} instead. */
    public static boolean isPrepRequired(Context ctx, PlatformPrepManifest manifest,
            PlatformProbe.Report report) {
        return needsSilentPrep(ctx, manifest);
    }

    public static int getAppliedVersion(Context ctx) {
        return prefs(ctx).getInt(KEY_APPLIED_VERSION, 0);
    }

    public static Outcome getOutcome(Context ctx) {
        String name = prefs(ctx).getString(KEY_OUTCOME, Outcome.NONE.name());
        try {
            return Outcome.valueOf(name);
        } catch (Exception e) {
            return Outcome.NONE;
        }
    }

    /** Persist prep result — commit sync so next gate read sees it immediately. */
    public static void markApplied(Context ctx, int prepVersion, Outcome outcome) {
        // #region agent log
        JSONObject d = new JSONObject();
        try {
            d.put("prepVersion", prepVersion);
            d.put("outcome", outcome.name());
        } catch (Exception ignored) {}
        PlatformPrepDebugLog.log(ctx, "PlatformPrepState.markApplied", "saved", "H2", d);
        // #endregion
        prefs(ctx).edit()
                .putInt(KEY_APPLIED_VERSION, prepVersion)
                .putString(KEY_OUTCOME, outcome.name())
                .putBoolean(KEY_RERUN, false)
                .commit();
    }

    public static void requestRerun(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_RERUN, true).commit();
    }

    public static boolean isRerunRequested(Context ctx) {
        return prefs(ctx).getBoolean(KEY_RERUN, false);
    }

    public static void clearRerun(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_RERUN, false).commit();
    }

    public static void markDismissed(Context ctx, int prepVersion) {
        markApplied(ctx, prepVersion, Outcome.DISMISSED);
    }

    public static void setRebootPending(Context ctx, boolean pending) {
        prefs(ctx).edit().putBoolean(KEY_REBOOT_PENDING, pending).commit();
    }

    public static boolean isRebootPending(Context ctx) {
        return prefs(ctx).getBoolean(KEY_REBOOT_PENDING, false);
    }

    public static void clearRebootPending(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_REBOOT_PENDING, false).commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
