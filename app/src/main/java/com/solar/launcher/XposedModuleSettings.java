package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * 2026-07-05 — Resolve and launch per-module settings activities (Xposed convention).
 * Layman: opens a hook module's own settings screen when the APK provides one.
 * Technical: queries {@code de.robv.android.xposed.category.MODULE_SETTINGS}; falls back to launch intent.
 * Reversal: delete; Debug detail screen loses external settings row only.
 */
public final class XposedModuleSettings {

    /** Standard Xposed Installer category for module preference activities. */
    public static final String MODULE_SETTINGS_CATEGORY =
            "de.robv.android.xposed.category.MODULE_SETTINGS";

    private XposedModuleSettings() {}

    /** True when the package exposes a settings or launch activity Solar can open. */
    public static boolean hasSettingsActivity(Context ctx, String pkg) {
        return resolveSettingsIntent(ctx, pkg) != null;
    }

    /** Build intent for module settings — null when none registered. */
    public static Intent resolveSettingsIntent(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return null;
        PackageManager pm = ctx.getPackageManager();

        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(MODULE_SETTINGS_CATEGORY);
        probe.setPackage(pkg);
        List<ResolveInfo> ris = pm.queryIntentActivities(probe, 0);
        if (ris != null && !ris.isEmpty()) {
            ResolveInfo ri = ris.get(0);
            Intent intent = new Intent(probe);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            return intent;
        }

        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return launch;
    }

    /** Start module settings — false when no activity or start failed. */
    public static boolean launchSettings(Context ctx, String pkg) {
        Intent intent = resolveSettingsIntent(ctx, pkg);
        if (intent == null || ctx == null) return false;
        try {
            ctx.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
