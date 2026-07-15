package com.solar.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * Shared shutdown / restart / screen-off / launcher switch — used by in-app power tier and overlay host.
 */
public final class PowerActions {

    private PowerActions() {}

    /** Power off via root (same as inactivity auto-shutdown). */
    public static void shutdown() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootShell.run("reboot -p");
            }
        }, "SolarShutdown").start();
    }

    /** Reboot via root. */
    public static void restart() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootShell.run("reboot");
            }
        }, "SolarRestart").start();
    }

    /** Screen off — synthetic POWER keyevent (Y1 software lock; overlay + context menu). */
    public static void screenSleep(Context ctx) {
        if (RootShell.run("input keyevent 26")) {
            return;
        }
        if (ctx != null) {
            Toast.makeText(ctx, R.string.context_action_lock_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 2026-07-08 — True when SolarHomeHelper APK is registered (canonical switch path).
     * Layman: if the middle-man home helper is installed, let it do the switch alone.
     * Technical: PackageManager probe of HELPER_PKG — no launch needed.
     */
    public static boolean isHelperInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getPackageInfo(HomeTargetPolicy.HELPER_PKG, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 2026-07-08 — Switch back to Solar — helper owns pm/script when present (was dual-fire race).
     * Layman: ask helper to make Solar home; only run the switch script yourself if helper is gone.
     * Technical: APPLY_HOME_TARGET broadcast; local LauncherSwitch only as fallback.
     * Reversal: always call LauncherSwitch.switchToSolar after prefs (dual-fire again).
     */
    public static void switchToSolar(final Context ctx) {
        if (ctx == null) return;
        final boolean helperOk = isHelperInstalled(ctx);
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_SOLAR, "overlay");
        new Thread(new Runnable() {
            @Override
            public void run() {
                LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_SOLAR);
                // Helper already runs solar-launcher-exec.sh switch — skip local script to avoid race.
                if (!helperOk) {
                    LauncherSwitch.switchToSolar(ctx.getApplicationContext());
                }
            }
        }, "SolarSwitch").start();
    }

    /**
     * 2026-07-08 — Switch to Rockbox — single switch execution (helper or local fallback).
     * Reversal: always call LauncherSwitch.switchToRockbox after prefs (dual-fire again).
     */
    public static void switchToRockbox(final Context ctx) {
        if (ctx == null) return;
        // 2026-07-11 — Rockbox HOME switch is Debug experiment only.
        if (!RockboxExperiment.isEnabled(ctx) && !RockboxExperiment.isEnabledFromSysprop()) {
            Toast.makeText(ctx, R.string.dialog_rockbox_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LauncherSwitch.isRockboxAvailable(ctx)) {
            Toast.makeText(ctx, R.string.dialog_rockbox_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LauncherSwitch.isRockboxInstalled(ctx) && !LauncherSwitch.ensureRockboxRegistered(ctx)) {
            Toast.makeText(ctx, R.string.dialog_rockbox_not_registered, Toast.LENGTH_LONG).show();
            return;
        }
        final boolean helperOk = isHelperInstalled(ctx);
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_ROCKBOX, "overlay");
        new Thread(new Runnable() {
            @Override
            public void run() {
                LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_ROCKBOX);
                if (ctx.getApplicationContext() != null) {
                    RockboxCoexistence.prepareForRockboxSwitch(ctx.getApplicationContext());
                }
                if (helperOk) {
                    // Helper broadcast already queued solar-launcher-exec.sh switch rockbox.
                    return;
                }
                final boolean ok = LauncherSwitch.switchToRockbox(ctx.getApplicationContext());
                if (!ok) {
                    toastOnMain(ctx, R.string.dialog_rockbox_failed);
                }
            }
        }, "RockboxSwitch").start();
    }

    /**
     * 2026-07-08 — Switch to JJ Launcher — install when needed; helper single-fire when present.
     * Reversal: always call LauncherSwitch.switchToJj after prefs.
     */
    public static void switchToJj(final Context ctx) {
        if (ctx == null) return;
        if (!JjLauncherAvailability.isOfferVisible(ctx)) {
            Toast.makeText(ctx, R.string.dialog_jj_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean helperOk = isHelperInstalled(ctx);
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_JJ, "overlay");
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!LauncherSwitch.isJjInstalled(ctx)) {
                    if (!JjLauncherInstaller.ensureInstalledBlocking(ctx)) {
                        toastOnMain(ctx, R.string.dialog_jj_failed);
                        return;
                    }
                }
                LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_JJ);
                if (helperOk) {
                    return;
                }
                LauncherSwitch.switchToJj(ctx.getApplicationContext());
            }
        }, "JjSwitch").start();
    }

    /**
     * 2026-07-08 — Switch to Stock Innioasis HOME — MODE_JJ inject; helper single-fire when present.
     * Reversal: always call LauncherSwitch.switchToStock after prefs (or delete for applyCustomHome only).
     */
    public static void switchToStock(final Context ctx) {
        if (ctx == null) return;
        if (!LauncherSwitch.isStockOfferVisible(ctx)) {
            Toast.makeText(ctx, R.string.dialog_stock_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean helperOk = isHelperInstalled(ctx);
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_STOCK, "overlay");
        new Thread(new Runnable() {
            @Override
            public void run() {
                LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_STOCK);
                if (helperOk) {
                    return;
                }
                final boolean ok = LauncherSwitch.switchToStock(ctx.getApplicationContext());
                if (!ok) {
                    toastOnMain(ctx, R.string.dialog_stock_failed);
                }
            }
        }, "StockSwitch").start();
    }

    /** Show a failure toast on the main looper from a background switch thread. */
    private static void toastOnMain(Context ctx, final int msgRes) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(app, msgRes, Toast.LENGTH_LONG).show();
            }
        });
    }
}
