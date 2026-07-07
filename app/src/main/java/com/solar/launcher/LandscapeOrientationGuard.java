package com.solar.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.provider.Settings;
import android.util.DisplayMetrics;

/**
 * 2026-07-06 — Keeps Y1/Y2 locked landscape 480×360 after external apps rotate the panel.
 * Layman: puts the screen back sideways when YouTube or others twist it.
 * Technical: activity lock + user_rotation restore on resume.
 * Reversal: delete; stock rotation after leaving third-party players.
 */
public final class LandscapeOrientationGuard {

    private static final int MIN_LANDSCAPE_WIDTH_PX = 400;

    private LandscapeOrientationGuard() {}

    /** Force this activity to landscape — call onCreate/onResume. */
    public static void enforceLandscape(Activity activity) {
        if (activity == null) return;
        try {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception ignored) {}
    }

    /** Reset system rotation to landscape home (0) when an app left it twisted. */
    public static void restoreSystemRotation(Context ctx) {
        if (ctx == null) return;
        try {
            int rot = Settings.System.getInt(ctx.getContentResolver(), Settings.System.USER_ROTATION, 0);
            if (rot != 0) {
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.USER_ROTATION, 0);
            }
            int accel = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0);
            if (accel != 0) {
                Settings.System.putInt(ctx.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 0);
            }
        } catch (Exception e) {
            RootShell.run("settings put system user_rotation 0; settings put system accelerometer_rotation 0");
        }
    }

    /** Width should exceed height on our 4:3 landscape panel. */
    public static boolean isLandscapeCorrect(Activity activity) {
        if (activity == null) return true;
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels >= dm.heightPixels && dm.widthPixels >= MIN_LANDSCAPE_WIDTH_PX;
    }

    /** Re-apply landscape when configuration landed portrait. */
    public static void recoverIfPortrait(Activity activity) {
        if (activity == null) return;
        enforceLandscape(activity);
        restoreSystemRotation(activity);
        Configuration c = activity.getResources().getConfiguration();
        if (c != null && c.orientation == Configuration.ORIENTATION_PORTRAIT) {
            try {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } catch (Exception ignored) {}
        }
    }
}
