package com.solar.launcher;

import android.content.Context;
import android.widget.Toast;

/**
 * Shared shutdown / restart / screen-off / Rockbox switch — used by in-app power tier and overlay host.
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

    /** Switch back to Solar from Rockbox/JJ overlay power tier. */
    public static void switchToSolar(final Context ctx) {
        if (ctx == null) return;
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_SOLAR, "overlay");
        LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_SOLAR);
    }

    /** Switch to Rockbox when script + package are available. */
    public static void switchToRockbox(final Context ctx) {
        if (ctx == null) return;
        if (!LauncherSwitch.isRockboxAvailable(ctx)) {
            Toast.makeText(ctx, R.string.dialog_rockbox_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LauncherSwitch.isRockboxInstalled(ctx) && !LauncherSwitch.ensureRockboxRegistered(ctx)) {
            Toast.makeText(ctx, R.string.dialog_rockbox_not_registered, Toast.LENGTH_LONG).show();
            return;
        }
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_ROCKBOX, "overlay");
        LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_ROCKBOX);
        if (ctx.getApplicationContext() != null) {
            RockboxCoexistence.prepareForRockboxSwitch(ctx.getApplicationContext());
        }
        LauncherSwitch.switchToRockbox(ctx);
    }

    /** Switch to JJ Launcher — install from OTA when needed, then apply HOME. */
    public static void switchToJj(final Context ctx) {
        if (ctx == null) return;
        if (!JjLauncherAvailability.isOfferVisible(ctx)) {
            Toast.makeText(ctx, R.string.dialog_jj_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        LauncherHelperClient.applyHomeTarget(ctx, LauncherDefault.TARGET_JJ, "overlay");
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!LauncherSwitch.isJjInstalled(ctx)) {
                    if (!JjLauncherInstaller.ensureInstalledBlocking(ctx)) {
                        final Context app = ctx.getApplicationContext();
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(app, R.string.dialog_jj_failed, Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                }
                LauncherPreference.applyHomeTarget(ctx, LauncherDefault.TARGET_JJ);
                LauncherSwitch.switchToJj(ctx.getApplicationContext());
            }
        }, "JjSwitch").start();
    }
}
