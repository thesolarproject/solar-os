package com.solar.launcher.globalcontext;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

import com.solar.input.policy.GlobalInputPolicy;
import com.solar.input.policy.StaleOverlayGate;

/**
 * 2026-07-05 — Sole Y2 POWER hold FSM; Xposed forwards HOLD_DOWN/UP here.
 * Layman: decides when sleep tap passes through vs when quick menu or rescue HUD arms.
 * Technical: uses shared {@link GlobalInputPolicy} JAR; fg-dependent modal hold delays.
 * Reversal: delete; PWM keeps full FSM inside SystemServerHooks.
 */
public final class GlobalInputCoordinatorService extends Service {

    private static final String TAG = "GlobalInputCoordinator";

    private HandlerThread workerThread;
    private Handler worker;

    private int activeKeyCode;
    private String foregroundPkg;
    private long holdDownAt;
    private boolean modalFired;
    private boolean rescueArmed;
    private Runnable modalRunnable;
    private Runnable rescueTickRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread(TAG);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        SysPropHelper.set(GlobalInputPolicy.POLICY_REV_PROPERTY,
                String.valueOf(GlobalInputPolicy.POLICY_REV));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }
        final String action = intent.getAction();
        if (CompanionOverlayTriggers.ACTION_HOLD_DOWN.equals(action)) {
            handleHoldDown(intent);
        } else if (CompanionOverlayTriggers.ACTION_HOLD_UP.equals(action)) {
            handleHoldUp();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelRunnables();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleHoldDown(Intent intent) {
        activeKeyCode = intent.getIntExtra(CompanionOverlayTriggers.EXTRA_KEY_CODE, 0);
        foregroundPkg = intent.getStringExtra(CompanionOverlayTriggers.EXTRA_FOREGROUND_PKG);
        boolean y2 = intent.getBooleanExtra(CompanionOverlayTriggers.EXTRA_Y2_DEVICE,
                CompanionDeviceFeatures.isY2());
        long holdMs = intent.getLongExtra(CompanionOverlayTriggers.EXTRA_HOLD_MS, 0L);

        if (activeKeyCode == GlobalInputPolicy.KEYCODE_POWER && y2) {
            if (GlobalInputPolicy.shouldPassthroughPowerTap(holdMs > 0 ? holdMs :
                    (holdDownAt > 0 ? SystemClock.uptimeMillis() - holdDownAt : 0L))) {
                return;
            }
        }

        if (holdDownAt == 0L) {
            holdDownAt = SystemClock.uptimeMillis();
            modalFired = false;
            rescueArmed = false;
            StaleOverlayGate.clearIfNeeded();
            scheduleModalAtHoldMs(y2);
            scheduleRescueArm(y2);
        }
    }

    private void handleHoldUp() {
        cancelRunnables();
        if (rescueArmed && CompanionRescueHoldState.isHoldActive()) {
            CompanionRescueHoldState.disarm();
        }
        holdDownAt = 0L;
        modalFired = false;
        rescueArmed = false;
        activeKeyCode = 0;
        foregroundPkg = null;
    }

    private void scheduleModalAtHoldMs(final boolean y2) {
        cancelModalRunnable();
        final long holdDelayMs = activeKeyCode == GlobalInputPolicy.KEYCODE_POWER
                ? GlobalInputPolicy.powerModalHoldMsForPackage(foregroundPkg)
                : GlobalInputPolicy.backModalHoldMsForPackage(foregroundPkg);
        modalRunnable = new Runnable() {
            @Override
            public void run() {
                if (holdDownAt == 0L || modalFired) return;
                long held = SystemClock.uptimeMillis() - holdDownAt;
                long thresholdMs = activeKeyCode == GlobalInputPolicy.KEYCODE_POWER
                        ? GlobalInputPolicy.powerModalHoldMsForPackage(foregroundPkg)
                        : GlobalInputPolicy.backModalHoldMsForPackage(foregroundPkg);
                if (activeKeyCode == GlobalInputPolicy.KEYCODE_POWER) {
                    if (!GlobalInputPolicy.shouldOfferPowerLongModal(foregroundPkg, y2)) return;
                } else {
                    boolean emergency = EmergencyRockboxMode.isEmergencyMode();
                    boolean ime = SysPropHelper.isTrue("sys.solar.ime.active");
                    if (!GlobalInputPolicy.shouldOfferBackLongModal(
                            foregroundPkg, y2, ime, emergency)) {
                        return;
                    }
                }
                if (held >= thresholdMs) {
                    modalFired = true;
                    openPowerOverlay();
                }
            }
        };
        worker.postDelayed(modalRunnable, holdDelayMs);
    }

    private void scheduleRescueArm(final boolean y2) {
        cancelRescueTick();
        rescueTickRunnable = new Runnable() {
            @Override
            public void run() {
                if (holdDownAt == 0L) return;
                long held = SystemClock.uptimeMillis() - holdDownAt;
                if (!rescueArmed && held >= GlobalInputPolicy.HUD_COUNTDOWN_START_MS) {
                    if (GlobalInputPolicy.shouldArmRescueHoldForPackage(foregroundPkg, activeKeyCode)) {
                        rescueArmed = true;
                        String kind = activeKeyCode == GlobalInputPolicy.KEYCODE_POWER
                                ? CompanionRescueHoldState.KIND_POWER
                                : CompanionRescueHoldState.KIND_BACK;
                        CompanionRescueHoldState.armFromHoldStart(kind, holdDownAt);
                        ensureHoldService();
                    }
                }
                if (rescueArmed && held >= GlobalInputPolicy.RESCUE_EXECUTE_MS) {
                    CompanionRescueHoldState.signalRestarting();
                    RescueExecutor.execute(getApplicationContext(), foregroundPkg);
                    handleHoldUp();
                    return;
                }
                if (holdDownAt > 0L) {
                    worker.postDelayed(this, 100L);
                }
            }
        };
        worker.postDelayed(rescueTickRunnable, GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
    }

    private void openPowerOverlay() {
        if (CompanionOverlayRouter.startSolarOverlayPower(this)) {
            return;
        }
        CompanionOverlayRouter.startCompanionPowerOverlay(this);
        Intent keep = new Intent(CompanionOverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        keep.setComponent(new ComponentName(this, GlobalContextOverlayService.class));
        try {
            startService(keep);
        } catch (Exception ignored) {}
    }

    private void ensureHoldService() {
        Intent hold = new Intent(CompanionOverlayTriggers.ACTION_RESCUE_HOLD_KEEPALIVE);
        hold.setComponent(new ComponentName(this, RescueHoldService.class));
        try {
            startService(hold);
        } catch (Exception ignored) {}
    }

    private void cancelRunnables() {
        cancelModalRunnable();
        cancelRescueTick();
    }

    private void cancelModalRunnable() {
        if (worker != null && modalRunnable != null) {
            worker.removeCallbacks(modalRunnable);
        }
        modalRunnable = null;
    }

    private void cancelRescueTick() {
        if (worker != null && rescueTickRunnable != null) {
            worker.removeCallbacks(rescueTickRunnable);
        }
        rescueTickRunnable = null;
    }
}
