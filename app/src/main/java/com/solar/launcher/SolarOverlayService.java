package com.solar.launcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.os.Build;
import com.solar.launcher.theme.ThemeManager;

/**
 * System overlay host for the global context modal — drawn above any app via WindowManager.
 * Runs in {@code :overlay} process so starting the service does not resume {@link MainActivity}.
 */
public final class SolarOverlayService extends Service {

    /** Match {@link OverlayModalHost} — block BACK dismiss during open-gesture tail while theme loads. */
    private long overlayDismissGraceMs =
            com.solar.input.policy.GlobalInputPolicy.GLOBAL_MODAL_HOLD_MS;

    private static WindowManager windowManager;
    private static FrameLayout overlayRoot;
    private static ThemedContextMenu themedContextMenu;
    private static OverlayModalHost modalHost;
    /** Uptime when provisional key gate armed — BACK dismiss waits until grace elapses. */
    private long overlayArmAt;
    /** In-process guard — two startCommands can race before overlayRoot is assigned. */
    private volatile boolean overlayOpenInFlight;
    private volatile boolean isProcessingKey = false;
    /** Stale in-flight clear — blocked second POWER open after crash mid-paint (2026-07-06). */
    private static final long OPEN_IN_FLIGHT_STALE_MS = 2500L;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable clearOpenInFlightRunnable = new Runnable() {
        @Override
        public void run() {
            overlayOpenInFlight = false;
        }
    };
    /** 2026-07-05 debug 72b98f — periodic gate snapshot while WM overlay is up. */
    private static final long OVERLAY_WATCHDOG_MS = 10000L;
    private final Runnable overlayWatchdog = new Runnable() {
        @Override
        public void run() {
            if (overlayRoot == null) return;
            // #region agent log
            try {
                org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
                d.put("hasModalHost", modalHost != null);
                d.put("menuShowing", themedContextMenu != null && themedContextMenu.isShowing());
                DebugOverlayStuckLog.log("SolarOverlayService.overlayWatchdog",
                        "overlay still painted", "H-B", d);
            } catch (Exception ignored) {}
            // #endregion
            OverlayKeyGate.refreshLiveOverlayGate();
            if (overlayRoot != null) {
                overlayRoot.postDelayed(this, OVERLAY_WATCHDOG_MS);
            }
        }
    };

    /** 2026-07-05 — self-heal dim shells or stuck opening props if UI is not painted within 1800ms. */
    private static final long PAINT_WATCHDOG_MS = 1800L;
    private final Runnable paintWatchdog = new Runnable() {
        @Override
        public void run() {
            if (overlayRoot == null) return;
            boolean menuPainted = themedContextMenu != null && themedContextMenu.isShowing();
            if (!menuPainted) {
                // #region agent log
                try {
                    org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
                    d.put("menuPainted", false);
                    d.put("hasModalHost", modalHost != null);
                    Debug065122Log.log("SolarOverlayService.paintWatchdog",
                            "orphan dim shell — tearing down", "H-A", d);
                } catch (Exception ignored) {}
                // #endregion
                tearDownOverlay();
                return;
            }
            if (!OverlayKeyGate.isOverlayUiVisible() && OverlayKeyGate.isActive()) {
                // #region agent log
                try {
                    org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
                    Debug065122Log.log("SolarOverlayService.paintWatchdog",
                            "active without ui — self-healing teardown", "H-A", d);
                } catch (Exception ignored) {}
                // #endregion
                tearDownOverlay();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // 2026-07-07 — IPC lives in :overlay with modalHost sessions (companion reads live rows).
        SolarOverlayStateService.registerActionHandler(new SolarOverlayStateService.OverlayActionHandler() {
            @Override
            public boolean dispatchAction(String sessionId, int actionIndex) {
                return OverlayMenuSessionRegistry.dispatchAction(
                        SolarOverlayService.this, sessionId, actionIndex);
            }

            @Override
            public android.os.Bundle buildContextSnapshot(String sessionId) {
                return OverlayMenuSessionRegistry.buildSnapshot(sessionId);
            }

            @Override
            public android.os.Bundle buildPowerMenuSnapshot() {
                return OverlayMenuSnapshotBuilder.buildPowerFallback(SolarOverlayService.this);
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (OverlayTriggers.ACTION_DISMISS_OVERLAY.equals(action)) {
            if (modalHost != null && !modalHost.isUsbStoragePromptVisible()) {
                return START_NOT_STICKY;
            }
            tearDownOverlay();
            return START_NOT_STICKY;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("action", action);
            d.put("y2", DeviceFeatures.isY2());
            DebugMenuLog.log("SolarOverlayService.onStartCommand", "overlay start", "H-LOCK", d);
        } catch (Exception ignored) {}
        // #endregion
        if (OverlayTriggers.ACTION_OVERLAY_KEY.equals(action)) {
            deliverOverlayKeyIntent(intent);
            return START_NOT_STICKY;
        }
        if (OverlayTriggers.ACTION_OVERLAY_KEEPALIVE.equals(action)) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("action", action);
                DebugMenuLog.log("SolarOverlayService.onStartCommand", "keepalive", "H-LOCK", d);
            } catch (Exception ignored) {}
            // #endregion
            warmOverlayThemeAsync();
            return START_STICKY;
        }
        if (OverlayTriggers.ACTION_OVERLAY_THEME_RELOAD.equals(action)) {
            ThemeManager.invalidateOverlayThemeCache();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ThemeManager.ensureOverlayThemeReady(getApplicationContext());
                }
            }, "OverlayThemeReload").start();
            return START_STICKY;
        }
        // App menu refresh while overlay is up — submenu pick without tear-down.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_APP_MENU.equals(action)
                && overlayRoot != null && modalHost != null) {
            String[] titles = intent.getStringArrayExtra(OverlayTriggers.EXTRA_MENU_TITLES);
            String title = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE);
            String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
            String callerPackage = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE);
            boolean[] hasSubmenu = intent.getBooleanArrayExtra(OverlayTriggers.EXTRA_MENU_HAS_SUBMENU);
            modalHost.showAppMenuMode(title, titles, hasSubmenu, sessionId, callerPackage);
            return START_NOT_STICKY;
        }
        // Native dialog refresh while overlay is up.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_NATIVE_DIALOG.equals(action)
                && overlayRoot != null && modalHost != null) {
            String message = intent.getStringExtra(OverlayTriggers.EXTRA_DIALOG_MESSAGE);
            String[] buttons = intent.getStringArrayExtra(OverlayTriggers.EXTRA_DIALOG_BUTTONS);
            String title = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE);
            String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
            String callerPackage = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE);
            modalHost.showNativeDialogMode(title, message, buttons, sessionId, callerPackage);
            return START_NOT_STICKY;
        }
        // 2026-07-06 — USB behind native ANR/crash tier — queue, never stomp error UI.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(action)
                && overlayRoot != null && modalHost != null
                && modalHost.isNativeDialogVisible()) {
            OverlayTierScheduler.queuePendingUsbPrompt();
            return START_NOT_STICKY;
        }
        // Solar Now Playing — transport bar shows level; skip global volume HUD entirely.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)
                && SolarUiState.isNowPlayingScreen()) {
            if (overlayRoot != null) {
                tearDownOverlay();
            }
            return START_NOT_STICKY;
        }
        // Passive volume overlay already up — refresh slider level, keep Rockbox focused.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)
                && overlayRoot != null && modalHost != null) {
            modalHost.refreshVolumeSlider();
            return START_NOT_STICKY;
        }
        // Power tier already visible — refresh focus instead of tearDown/re-arm (avoids key-gate flicker).
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_POWER.equals(action)) {
            synchronized (this) {
                if (overlayRoot != null && modalHost != null) {
                    modalHost.showPowerMode();
                    return START_NOT_STICKY;
                }
                if (overlayRoot != null && modalHost == null) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("action", action);
                        d.put("overlayOpenInFlight", overlayOpenInFlight);
                        Debug2d4745Log.log("SolarOverlayService.onStartCommand",
                                "retry paint dim shell", "H4", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    final Context themedRetry = new ContextThemeWrapper(
                            getApplicationContext(), R.style.Theme_Solar);
                    overlayRoot.post(new Runnable() {
                        @Override
                        public void run() {
                            loadOverlayAndFinish(intent, action, false, themedRetry);
                        }
                    });
                    return START_NOT_STICKY;
                }
                if (overlayOpenInFlight) {
                    return START_NOT_STICKY;
                }
                overlayOpenInFlight = true;
                mainHandler.removeCallbacks(clearOpenInFlightRunnable);
                mainHandler.postDelayed(clearOpenInFlightRunnable, OPEN_IN_FLIGHT_STALE_MS);
            }
            OverlayKeyGate.setOverlayOpening(true);
        }
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(action)) {
            if (OverlayTierScheduler.shouldDeferUsbSpawn()) {
                OverlayTierScheduler.queuePendingUsbPrompt();
                disarmIfOverlayNotShown();
                return START_NOT_STICKY;
            }
            if (routeUsbStorageIntent(intent)) {
                disarmIfOverlayNotShown();
                return START_NOT_STICKY;
            }
        }
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING.equals(action)) {
            if (routeBluetoothPairingIntent(intent)) {
                disarmIfOverlayNotShown();
                return START_NOT_STICKY;
            }
        }
        // USB tier already visible — refresh rows instead of tearDown/re-arm (avoids focus flicker).
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(action)
                && overlayRoot != null && modalHost != null) {
            modalHost.showUsbStoragePromptMode();
            return START_NOT_STICKY;
        }
        // BT pairing tier already visible — refresh with latest device/session.
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING.equals(action)
                && overlayRoot != null && modalHost != null) {
            paintBluetoothPairingFromIntent(intent);
            return START_NOT_STICKY;
        }
        if (overlayRoot != null) {
            tearDownOverlay(false);
        }
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)) {
            showPassiveVolumeOverlay();
            return START_NOT_STICKY;
        }
        showOverlay(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        tearDownOverlay();
        super.onDestroy();
    }

    /** Passive volume HUD — no key gate, minimal theme work, no full-screen dim. */
    private void showPassiveVolumeOverlay() {
        final Context app = getApplicationContext();
        // Ensure stale overlay-active prop never blocks AudioService volume keys.
        OverlayKeyGate.setOverlayActive(false);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        final Context themed = new ContextThemeWrapper(app, R.style.Theme_Solar);
        overlayRoot = new KeyCapturingOverlayRoot(themed, false);
        overlayRoot.setBackgroundColor(0x00000000);
        overlayRoot.setClickable(false);
        overlayRoot.setFocusable(false);
        overlayRoot.setFocusableInTouchMode(false);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                globalOverlayWindowFlags(),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(overlayRoot, lp);
        Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (overlayRoot == null) return;
                ThemeManager.ensureOverlayPaintableMinimum(app);
                themedContextMenu = new ThemedContextMenu(themed);
                modalHost = new OverlayModalHost(themed, themedContextMenu, overlayRoot,
                        new OverlayModalHost.DismissListener() {
                            @Override
                            public void onDismissOverlay() {
                                tearDownOverlay();
                            }
                        });
                modalHost.showVolumeMode(false);
                warmOverlayThemeAsync(app);
            }
        };
        if (ThemeManager.isOverlayThemeReady() || ThemeManager.isOverlayRamCacheLoaded()) {
            finish.run();
        } else {
            overlayRoot.post(finish);
        }
    }

    /** Pre-decode overlay theme on keepalive so Y2 power-hold does not pay cold I/O at 600ms. */
    private void warmOverlayThemeAsync() {
        warmOverlayThemeAsync(getApplicationContext(), false);
    }

    /** Full theme folder + row bitmap warm — never blocks overlay paint. */
    private void warmOverlayThemeAsync(final Context app) {
        warmOverlayThemeAsync(app, true);
    }

    private void warmOverlayThemeAsync(final Context app, final boolean refreshDecorIfShowing) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ThemeManager.ensureOverlayThemeReady(app);
                if (!refreshDecorIfShowing || overlayRoot == null || themedContextMenu == null) return;
                overlayRoot.post(new Runnable() {
                    @Override
                    public void run() {
                        if (themedContextMenu != null && themedContextMenu.isShowing()) {
                            themedContextMenu.refreshThemeDecorAfterWarm();
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("themeReady", ThemeManager.isOverlayThemeReady());
                                d.put("runId", "post-fix");
                                Debug2d4745Log.log("SolarOverlayService.warmOverlayThemeAsync",
                                        "theme decor refreshed", "H1", d);
                            } catch (Exception ignored) {}
                            // #endregion
                        }
                    }
                });
            }
        }, "OverlayThemeWarm").start();
    }

    private void showOverlay(Intent intent) {
        final String action = intent.getAction();
        // Passive volume HUD — no key gate; all overlays stay NOT_FOCUSABLE (keys via OverlayKeyGate).
        final boolean passiveVolume =
                OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action);
        final boolean passiveToast =
                OverlayTriggers.ACTION_SHOW_OVERLAY_TOAST.equals(action);
        if (!passiveVolume && !passiveToast) {
            OverlayHandoffRestoreReceiver.notifyPause(getApplicationContext());
            OverlayForegroundGuard.snapshotOnArm(getApplicationContext());
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Context themed = new ContextThemeWrapper(getApplicationContext(), R.style.Theme_Solar);
        // Never capture WM focus — foreground app stays on top; keys forwarded by Xposed / Y1 daemon.
        overlayRoot = new KeyCapturingOverlayRoot(themed, false);
        // 2026-07-06 — transparent WM root; panel-only chrome (no full-screen dim tint).
        overlayRoot.setBackgroundColor(0x00000000);
        overlayRoot.setClickable(false);
        overlayRoot.setFocusable(false);
        overlayRoot.setFocusableInTouchMode(false);

        int wmFlags = globalOverlayWindowFlags();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                wmFlags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(overlayRoot, lp);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("wmRootBg", 0x00000000);
            d.put("passiveVolume", passiveVolume);
            d.put("y2", DeviceFeatures.isY2());
            DebugMenuLog.log("SolarOverlayService.showOverlay", "wm overlay added", "H3", d);
        } catch (Exception ignored) {}
        // #endregion

        // Theme + menu on next frame — arm key gate before theme I/O so keys are never swallowed into void.
        overlayRoot.post(new Runnable() {
            @Override
            public void run() {
                if (!passiveVolume && !passiveToast) {
                    armProvisionalOverlayKeyGate();
                    overlayRoot.removeCallbacks(paintWatchdog);
                    overlayRoot.postDelayed(paintWatchdog, PAINT_WATCHDOG_MS);
                }
                loadOverlayAndFinish(intent, action, passiveVolume || passiveToast, themed);
            }
        });
    }

    /** Theme decode off :overlay main thread — paint immediately; warm bitmaps on worker. */
    private void loadOverlayAndFinish(final Intent intent, final String action,
            final boolean passiveVolume, final Context themed) {
        final Context app = getApplicationContext();
        ThemeManager.ensureOverlayPaintableMinimum(app);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("action", action);
            d.put("ramCache", ThemeManager.isOverlayRamCacheLoaded());
            d.put("themeReady", ThemeManager.isOverlayThemeReady());
            d.put("hasOverlayRoot", overlayRoot != null);
            d.put("runId", "post-fix");
            Debug2d4745Log.log("SolarOverlayService.loadOverlayAndFinish", "instant paint path", "H1", d);
        } catch (Exception ignored) {}
        // #endregion
        finishShowOverlay(intent, action, passiveVolume, themed);
        warmOverlayThemeAsync(app);
    }

    /** Wire themed menu after WM dim shell is on screen. */
    private void finishShowOverlay(Intent intent, String action, boolean passiveVolume, Context themed) {
        if (overlayRoot == null) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("action", action);
                Debug2d4745Log.log("SolarOverlayService.finishShowOverlay",
                        "abort overlayRoot null", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("action", action);
            d.put("themeReady", ThemeManager.isOverlayThemeReady());
            d.put("ramCache", ThemeManager.isOverlayRamCacheLoaded());
            Debug2d4745Log.log("SolarOverlayService.finishShowOverlay", "enter paint", "H1-H4", d);
        } catch (Exception ignored) {}
        // #endregion
        themedContextMenu = new ThemedContextMenu(themed);
        modalHost = new OverlayModalHost(themed, themedContextMenu, overlayRoot,
                new OverlayModalHost.DismissListener() {
                    @Override
                    public void onDismissOverlay() {
                        tearDownOverlay();
                    }
                });

        if (OverlayTriggers.ACTION_SHOW_OVERLAY_POWER.equals(action)) {
            modalHost.showPowerMode();
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("action", action);
                DebugE93bdbLog.log("SolarOverlayService.finishShowOverlay",
                        "power overlay shown", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_APP_MENU.equals(action)) {
            String[] titles = intent.getStringArrayExtra(OverlayTriggers.EXTRA_MENU_TITLES);
            String title = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE);
            String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
            String callerPackage = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("itemCount", titles != null ? titles.length : 0);
                d.put("sessionId", sessionId != null ? sessionId.substring(0, Math.min(8, sessionId.length())) : "");
                d.put("caller", callerPackage);
                d.put("themeReady", ThemeManager.isOverlayThemeReady());
                DebugAgentLog.log(getApplicationContext(), "SolarOverlayService.finishShowOverlay",
                        "app menu paint", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
            try {
                modalHost.showAppMenuMode(title, titles,
                        intent.getBooleanArrayExtra(OverlayTriggers.EXTRA_MENU_HAS_SUBMENU),
                        sessionId, callerPackage);
            } catch (Throwable t) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("err", t.getClass().getSimpleName());
                    d.put("msg", t.getMessage() != null ? t.getMessage() : "");
                    DebugAgentLog.log(getApplicationContext(), "SolarOverlayService.finishShowOverlay",
                            "app menu crash", "H1", d);
                } catch (Exception ignored) {}
                // #endregion
                tearDownOverlay();
            }
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_NATIVE_DIALOG.equals(action)) {
            String message = intent.getStringExtra(OverlayTriggers.EXTRA_DIALOG_MESSAGE);
            String[] buttons = intent.getStringArrayExtra(OverlayTriggers.EXTRA_DIALOG_BUTTONS);
            String title = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE);
            String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
            String callerPackage = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE);
            modalHost.showNativeDialogMode(title, message, buttons, sessionId, callerPackage);
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)) {
            modalHost.showVolumeMode(false);
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_TOAST.equals(action)) {
            String text = intent.getStringExtra(OverlayTriggers.EXTRA_TOAST_TEXT);
            long duration = intent.getLongExtra(OverlayTriggers.EXTRA_TOAST_DURATION_MS,
                    OverlayModalHost.TOAST_DISMISS_SHORT_MS_PUBLIC);
            modalHost.showToastMode(text, duration);
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_PICKER.equals(action)) {
            modalHost.showLauncherPickerMode();
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY.equals(action)) {
            modalHost.showLauncherCrashRecoveryMode(
                    intent.getStringExtra(OverlayTriggers.EXTRA_RECOVERY_PROCESS));
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(action)) {
            modalHost.showUsbStoragePromptMode();
        } else if (OverlayTriggers.ACTION_SHOW_OVERLAY_BT_PAIRING.equals(action)) {
            paintBluetoothPairingFromIntent(intent);
        } else {
            tearDownOverlay();
            return;
        }
        if (!passiveVolume) {
            armOverlayKeyGate();
            OverlayKeyGate.setOverlayUiVisible(true);
            if (overlayRoot != null) {
                overlayRoot.removeCallbacks(paintWatchdog);
                overlayRoot.removeCallbacks(overlayWatchdog);
                overlayRoot.postDelayed(overlayWatchdog, OVERLAY_WATCHDOG_MS);
            }
        }
        overlayOpenInFlight = false;
        mainHandler.removeCallbacks(clearOpenInFlightRunnable);
        // #region agent log
        try {
            org.json.JSONObject d = Debug434250Log.rescueSnapshot();
            d.put("action", action);
            d.put("menuShowing", themedContextMenu != null && themedContextMenu.isShowing());
            Debug434250Log.log("SolarOverlayService.finishShowOverlay",
                    "paint complete", "H-C", d);
        } catch (Exception ignored) {}
        // #endregion
        // #region agent log
        final int rootChildCount = overlayRoot != null ? overlayRoot.getChildCount() : -1;
        final boolean menuShowing = themedContextMenu != null && themedContextMenu.isShowing();
        overlayRoot.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("action", action);
                    d.put("rootChildCount", rootChildCount);
                    d.put("rootChildCountLate", overlayRoot != null ? overlayRoot.getChildCount() : -1);
                    d.put("menuShowing", menuShowing);
                    d.put("menuShowingLate", themedContextMenu != null && themedContextMenu.isShowing());
                    d.put("hasModalHost", modalHost != null);
                    Debug2d4745Log.log("SolarOverlayService.finishShowOverlay+300ms",
                            "post paint snapshot", "H2-H3", d);
                } catch (Exception ignored) {}
            }
        }, 300L);
        // #endregion
    }

    /** Route forwarded hardware keys from Xposed system_server or root input daemon. */
    private void deliverOverlayKeyIntent(Intent intent) {
        if (!OverlayKeyGate.isOverlayKeysActive()) {
            // #region agent log
            try {
                org.json.JSONObject d = DebugOverlayStuckLog.overlayPropSnapshot();
                d.put("keyCode", intent.getIntExtra(OverlayTriggers.EXTRA_KEY_CODE, 0));
                d.put("hasOverlayRoot", overlayRoot != null);
                d.put("scenario", "skip-inactive-gate");
                DebugOverlayStuckLog.log("SolarOverlayService.deliverOverlayKeyIntent",
                        "skip inactive gate", "H-B", d);
                DebugAf054eLog.log(this, "SolarOverlayService.deliverOverlayKeyIntent",
                        "skip inactive gate", "S1", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        final int keyCode = intent.getIntExtra(OverlayTriggers.EXTRA_KEY_CODE, 0);
        if (keyCode == 0) return;
        final int keyAction = intent.getIntExtra(
                OverlayTriggers.EXTRA_KEY_ACTION, KeyEvent.ACTION_DOWN);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("keyCode", keyCode);
            d.put("action", keyAction);
            d.put("hasModalHost", modalHost != null);
            d.put("scenario", "overlay-key-deliver");
            DebugAf054eLog.log(this, "SolarOverlayService.deliverOverlayKeyIntent",
                    "deliver key", "S1,S2,S3", d);
        } catch (Exception ignored) {}
        // #endregion
        Runnable deliver = new Runnable() {
            @Override
            public void run() {
                if (isProcessingKey) return;
                isProcessingKey = true;
                try {
                    if (keyAction == KeyEvent.ACTION_UP) {
                        OverlayKeyGate.deliverUp(keyCode);
                    } else {
                        OverlayKeyGate.deliver(keyCode);
                    }
                } finally {
                    isProcessingKey = false;
                }
            }
        };
        if (overlayRoot != null) {
            overlayRoot.post(deliver);
        } else {
            deliver.run();
        }
    }

    /** Arm before menu paint — swallow open-gesture BACK tail; dismiss only after grace + modalHost. */
    private void armProvisionalOverlayKeyGate() {
        String fg = ExternalInputHandoff.getForegroundPackageName(getApplicationContext());
        overlayDismissGraceMs = com.solar.input.policy.GlobalInputPolicy
                .overlayDismissGraceMsForPackage(fg);
        overlayArmAt = android.os.SystemClock.uptimeMillis();
        OverlayKeyGate.arm(new OverlayKeyGate.Handler() {
            @Override
            public boolean onKeyDown(int keyCode) {
                if (modalHost != null) {
                    return modalHost.handleOverlayKeyDown(keyCode);
                }
                if (Y1InputKeys.isBackKey(keyCode)) {
                    if (android.os.SystemClock.uptimeMillis() - overlayArmAt < overlayDismissGraceMs) {
                        return true;
                    }
                    tearDownOverlay();
                    return true;
                }
                return true;
            }

            @Override
            public boolean onKeyUp(int keyCode) {
                if (modalHost != null) {
                    return modalHost.handleOverlayKeyUp(keyCode);
                }
                return Y1InputKeys.isBackKey(keyCode);
            }
        });
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("propAfter", "armed");
            DebugAgentLog.log(getApplicationContext(), "SolarOverlayService.armProvisionalOverlayKeyGate",
                    "provisional key gate", "H-FREEZE", d);
        } catch (Exception ignored) {}
        // #endregion
    }

    /** Interactive overlay tiers — wheel/back/center route here via Xposed key forwarder. */
    private void armOverlayKeyGate() {
        OverlayKeyGate.arm(new OverlayKeyGate.Handler() {
            @Override
            public boolean onKeyDown(int keyCode) {
                return modalHost != null && modalHost.handleOverlayKeyDown(keyCode);
            }

            @Override
            public boolean onKeyUp(int keyCode) {
                return modalHost != null && modalHost.handleOverlayKeyUp(keyCode);
            }
        });
    }

    /** Tell main Solar process to re-arm stock-app wheel inject — :overlay disarm is not enough. */
    private void notifyMainProcessHandoffRestore(Context ctx) {
        if (ctx == null) return;
        Intent restore = new Intent(OverlayTriggers.ACTION_OVERLAY_DISMISSED);
        restore.setComponent(new android.content.ComponentName(ctx,
                OverlayHandoffRestoreReceiver.class.getName()));
        try {
            ctx.sendBroadcast(restore);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("action", OverlayTriggers.ACTION_OVERLAY_DISMISSED);
                DebugAgentLog.log(ctx, "SolarOverlayService.notifyMainProcessHandoffRestore",
                        "broadcast handoff restore", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
        } catch (Exception ignored) {}
    }

    /** WM flags for global overlays — always non-focus-stealing (test hook). */
    static int globalOverlayWindowFlags() {
        return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }

    private void tearDownOverlay() {
        tearDownOverlay(true);
    }

    private void tearDownOverlay(boolean animated) {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hadModalHost", modalHost != null);
            d.put("animated", animated);
            DebugE93bdbLog.log("SolarOverlayService.tearDownOverlay", "dismiss", "H4", d);
        } catch (Exception ignored) {}
        // #endregion
        Context ctx = getApplicationContext();
        overlayOpenInFlight = false;
        mainHandler.removeCallbacks(clearOpenInFlightRunnable);
        if (overlayRoot != null) {
            overlayRoot.removeCallbacks(paintWatchdog);
            overlayRoot.removeCallbacks(overlayWatchdog);
        }
        OverlayKeyGate.setOverlayUiVisible(false);
        OverlayKeyGate.setOverlayOpening(false);
        OverlayKeyGate.disarm();
        OverlayTierScheduler.onOverlayTeardown();
        if (modalHost != null) {
            modalHost.stopRescueBannerPoll();
        }
        notifyMainProcessHandoffRestore(ctx);
        OverlayForegroundGuard.restoreIfNeeded(ctx);
        if (modalHost != null) {
            modalHost.stopRescueBannerPoll();
        }
        if (animated && themedContextMenu != null && themedContextMenu.isShowing()) {
            themedContextMenu.dismissAnimated(new Runnable() {
                @Override
                public void run() {
                    finishOverlayTeardown();
                }
            });
            return;
        }
        if (themedContextMenu != null) {
            try {
                themedContextMenu.dismiss();
            } catch (Exception ignored) {}
        }
        finishOverlayTeardown();
    }

    /** Remove WM view after menu dismiss (instant or animated). */
    private void finishOverlayTeardown() {
        themedContextMenu = null;
        modalHost = null;
        if (windowManager != null && overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {}
        }
        overlayRoot = null;
        stopSelf();
    }

    /**
     * Lock-only or pref-driven shortcuts skip the overlay UI — hand off straight to MainActivity.
     * @return true when this start command is fully handled (no overlay paint).
     */
    /** BT pairing always paints overlay — no auto-connect short-circuit like USB. */
    private boolean routeBluetoothPairingIntent(Intent intent) {
        return false;
    }

    private void paintBluetoothPairingFromIntent(Intent intent) {
        if (modalHost == null || intent == null) return;
        modalHost.showBluetoothPairingMode(
                intent.getIntExtra(OverlayTriggers.EXTRA_BT_PAIRING_MODE,
                        BluetoothPairingCoordinator.MODE_PIN),
                intent.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_ADDRESS),
                intent.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_NAME),
                intent.getIntExtra(OverlayTriggers.EXTRA_BT_PAIRING_PASSKEY, 0),
                intent.getStringExtra(OverlayTriggers.EXTRA_BT_PAIRING_PIN_PREFILL));
    }

    private boolean routeUsbStorageIntent(Intent intent) {
        Context appCtx = getApplicationContext();
        if (!UsbMassStorageExperiment.isEnabled(appCtx)) {
            return true;
        }
        boolean lockOnly = intent.getBooleanExtra(OverlayTriggers.EXTRA_USB_STORAGE_LOCK, false);
        if (lockOnly) {
            if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPrompt(appCtx)) {
                return true;
            }
            UsbStorageOverlayReceiver.launchSolarUsbHandoff(appCtx, false, true);
            return true;
        }
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(appCtx)) {
            return true;
        }
        if (UsbStorageSessionFlags.isAutoConnectEnabled(appCtx)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    UsbMassStorageController.enable(appCtx, "auto.overlay");
                }
            }, "UsbAutoConnectOverlay").start();
            UsbStorageOverlayReceiver.launchSolarUsbHandoff(appCtx, false, true);
            return true;
        }
        return false;
    }

    /** USB route bailed (auto-connect, lock handoff, prefs) — clear stale overlay-active prop. */
    private void disarmIfOverlayNotShown() {
        if (overlayRoot != null) return;
        OverlayKeyGate.disarm();
        notifyMainProcessHandoffRestore(getApplicationContext());
    }

    /** Captures wheel / back / center for interactive overlay tiers only. */
    private final class KeyCapturingOverlayRoot extends FrameLayout {

        private final boolean captureKeys;

        KeyCapturingOverlayRoot(Context context, boolean captureKeys) {
            super(context);
            this.captureKeys = captureKeys;
            setFocusable(captureKeys);
            setFocusableInTouchMode(captureKeys);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (!captureKeys) {
                return super.dispatchKeyEvent(event);
            }
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", event.getKeyCode());
                d.put("action", event.getAction());
                d.put("hasFocus", hasFocus());
                DebugOverlayKeyLog.log("SolarOverlayService.dispatchKeyEvent", "wm root key", "H3-H4", d);
            } catch (Exception ignored) {}
            // #endregion
            if (modalHost == null) return super.dispatchKeyEvent(event);
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return modalHost.handleOverlayKeyDown(event.getKeyCode()) || super.dispatchKeyEvent(event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (modalHost.handleOverlayKeyUp(event.getKeyCode())) {
                    return true;
                }
                if (Y1InputKeys.isBackKey(event.getKeyCode())) {
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            return focused != null ? focused : this;
        }
    }
}
