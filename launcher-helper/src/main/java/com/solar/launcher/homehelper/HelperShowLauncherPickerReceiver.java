package com.solar.launcher.homehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Root/Xposed fallback entry for BACK/POWER hold HOME picker.
 * Layman: opens the helper's wheel-friendly home-app list when Solar overlay cannot start.
 * Technical: explicit broadcast from SolarOverlayClient or root evdev shell.
 */
public final class HelperShowLauncherPickerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!HomeTargetPolicy.ACTION_SHOW_LAUNCHER_PICKER.equals(intent.getAction())) return;
        Intent picker = new Intent(context, LauncherPickerActivity.class);
        picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(picker);
        } catch (Exception ignored) {}
    }
}
