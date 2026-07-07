package com.solar.launcher.globalcontext;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.solar.input.policy.StaleOverlayGate;

/**
 * 2026-07-05 — Boot keepalive for companion overlay, coordinator, and crash watchdog.
 * Layman: wakes companion services after reboot so holds still work if Solar is stopped.
 * Technical: START_STICKY on :overlay/:hold/:watchdog; idempotent startService calls.
 * Reversal: delete receiver; 99SolarInit.sh starts Solar overlay hosts only.
 */
public final class CompanionBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        Context app = context.getApplicationContext();

        CompanionRescueHoldState.disarm();
        SysPropHelper.set(StaleOverlayGate.ACTIVE_PROPERTY, "0");
        SysPropHelper.set(StaleOverlayGate.UI_PROPERTY, "0");
        SysPropHelper.set(StaleOverlayGate.OPENING_PROPERTY, "0");

        Intent dismiss = new Intent(CompanionOverlayTriggers.ACTION_DISMISS_OVERLAY);
        dismiss.setComponent(new ComponentName(app, GlobalContextOverlayService.class));
        try {
            app.startService(dismiss);
        } catch (Exception ignored) {}

        Intent overlayKeep = new Intent(CompanionOverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        overlayKeep.setComponent(new ComponentName(app, GlobalContextOverlayService.class));
        try {
            app.startService(overlayKeep);
        } catch (Exception ignored) {}

        Intent holdKeep = new Intent(CompanionOverlayTriggers.ACTION_RESCUE_HOLD_KEEPALIVE);
        holdKeep.setComponent(new ComponentName(app, RescueHoldService.class));
        try {
            app.startService(holdKeep);
        } catch (Exception ignored) {}

        Intent coordinator = new Intent(app, GlobalInputCoordinatorService.class);
        try {
            app.startService(coordinator);
        } catch (Exception ignored) {}

        Intent imeHost = new Intent(app, CompanionImeOverlayService.class);
        try {
            app.startService(imeHost);
        } catch (Exception ignored) {}

        Intent watchdog = new Intent(app, SolarCrashWatchdog.class);
        try {
            app.startService(watchdog);
        } catch (Exception ignored) {}

        EmergencyRockboxMode.onBoot(app);
        CompanionGlobalOverlayTrigger.ensureStarted(app);
    }
}
