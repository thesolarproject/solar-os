package com.solar.launcher;

import android.app.Application;
import android.content.Intent;

import com.solar.launcher.net.TlsHelper;

import com.solar.ota.net.OtaTlsHelper;
import com.solar.launcher.theme.ThemeManager;

import com.solar.launcher.soulseek.SolarDiagnosticReporter;

import com.solar.launcher.platform.PlatformPrepLauncher;

/**
 * 2026-07-05 — Application bootstrap: prep wizard gate, parallel platform repair, overlay process split.
 * APK/ROM parity: wizard blocks UX when gaps detected; background thread runs Y1RomPrep + ensurer always.
 * Bootstrap order: prep wizard (if required) → overlay hosts → background Y1RomPrep + XposedModuleEnsurer.
 * Mutex: overlay :overlay process skips HOME/bootstrap that could steal Rockbox focus.
 * When changing: SolarPlatformPrep ladder; keep heavy I/O off main thread.
 * Reversal: remove PlatformPrepLauncher call; remove bootstrap thread platform repair steps.
 */
public class SolarApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SolarLog.installUncaughtHandler(this);
        if (!isOverlayProcess()) {
            SolarRecoveryCoordinator.onProcessStart(this);
            SolarOverlayStateService.registerActionHandler(null);
        }
        com.solar.launcher.theme.ActiveThemeEngine.init(this);
        // 2026-07-05 — Sync snapshot RAM warm before any overlay/modal paints (both processes).
        ThemeManager.loadOverlayRamCacheSync(this);
        ThemeManager.preferInternalCacheForActiveTheme(this);
        NavigationPreferences.syncPropertyFromPrefs(this);
        // Overlay process only hosts WM modal — skip HOME/bootstrap that could touch Rockbox focus.
        if (isOverlayProcess()) {
            // Theme I/O off Application main thread — startService can return while cache warms.
            if (OverlayKeyGate.isOverlayKeysActive()) {
                OverlayKeyGate.disarm();
            }
            SolarImeDismiss.recoverOnBoot(this);
            LargeFontAccessibilitySuppressor.ensureNormalFontScale(this);
            GraphicsPerformancePolicy.ensureAsync(this);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ThemeManager.ensureOverlayThemeReady(SolarApplication.this);
                    HearingSafetyVolume.syncFromPrefs(SolarApplication.this);
                }
            }, "OverlayThemeBoot").start();
            return;
        }
        // 2026-07-05 — Main process: clear ghost IME WM overlay before MainActivity paints.
        SolarImeDismiss.recoverOnBoot(this);
        SolarImeBootstrap.ensureDefaultIme(this);
        SolarLog.scrubExistingLogs(this);
        SolarDiagnosticReporter.onProcessStart(this);
        HearingSafetyVolume.syncFromPrefs(this);
        LargeFontAccessibilitySuppressor.ensureNormalFontScale(this);
        GraphicsPerformancePolicy.ensureAsync(this);
        // 2026-07-05 — Silent platform prep when prepVersion ahead; no blocking wizard on ROM-ready devices.
        PlatformPrepLauncher.ensureAsync(this);
        MediaVolumeControl.ensureAlertStreamsSilent(this);
        // Warm :overlay immediately — do not wait for background bootstrap thread.
        SolarOverlayHost.ensureStarted(this);
        SolarRescueHoldHost.ensureStarted(this);
        LauncherWatchdogService.ensureStarted(this);
        // 2026-07-06 — JJ/Rockbox HOME: claim MEDIA_BUTTON on main thread before bootstrap I/O (H2).
        String earlyHomeTarget = LauncherPreference.getHomeTarget(this);
        if (LauncherDefault.TARGET_JJ.equals(earlyHomeTarget)
                || LauncherDefault.TARGET_ROCKBOX.equals(earlyHomeTarget)) {
            MediaButtonRegistrar.ensureRegistered(this);
            ExternalInputHandoff.warmInjector(this);
            RockboxForegroundMonitor.ensureStarted(this);
            if (LauncherDefault.TARGET_JJ.equals(earlyHomeTarget)) {
                ExternalInputHandoff.armJjShim(this);
            }
            try {
                startService(new Intent(this, SolarHandoffService.class));
            } catch (Exception ignored) {}
        }
        // 2026-07-06 — Rescue setprop may land before prefs; reconcile before async bootstrap re-applies rockbox.
        LauncherPreference.reconcileHomeTargetFromProperty(this);
        // 2026-07-06 — Record USB boot host before Application bootstrap thread races USB_STATE.
        UsbHostSessionPolicy.onBootCompleted(this);
        // #region agent log
        if (DebugPerfLog.ENABLED) {
            DebugPerfLog.markStart();
            final android.os.Handler perfHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable perfSampler = new Runnable() {
                @Override
                public void run() {
                    DebugPerfLog.flushSample("SolarApplication");
                    if (DebugBee1b8Log.ENABLED) {
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("tier", "main-process");
                            DebugBee1b8Log.log("SolarApplication.perfSampler",
                                    "perf sample", "bee1b8-H-D", d);
                        } catch (Exception ignored) {}
                    }
                    perfHandler.postDelayed(this, 3000L);
                }
            };
            perfHandler.postDelayed(perfSampler, 3000L);
        }
        // #endregion
        HearingSafetyVolume.probeAbsoluteMaxAsync(this);
        // 2026-07-05 — Heavy I/O off main thread so home menu paints first on cold start.
        new Thread(new Runnable() {
            @Override
            public void run() {
                TlsHelper.init(SolarApplication.this);
                // 2026-07-06 — OTA catalog (SolarUpdateClient) shares Conscrypt + bundled LE roots with TlsHelper.
                OtaTlsHelper.init(SolarApplication.this);
                Y1InputKeys.selfCheckWheelMapping();
                SolarBootPacing.pauseBetweenBootstrapSteps();
                Y1RomPrep.ensureSwitchScripts(SolarApplication.this);
                RockboxDisable.ensureOnce(SolarApplication.this);
                SolarBootPacing.pauseBetweenBootstrapSteps();
                LargeFontAccessibilitySuppressor.ensureNormalFontScale(SolarApplication.this);
                GraphicsPerformancePolicy.ensureAsync(SolarApplication.this);
                WifiMacRandomizer.ensureOnce(SolarApplication.this);
                WirelessAdbEnabler.ensureWirelessAdb(SolarApplication.this);
                SolarBootPacing.pauseBetweenBootstrapSteps();
                ThemeManager.ensureThemesRootReady(SolarApplication.this);
                ThemeManager.ensureBundledDefault(SolarApplication.this);
                ThemeManager.loadAllThemes(SolarApplication.this);
                ThemeManager.restoreSavedThemeFromPrefs(SolarApplication.this);
                ThemeManager.ensureActiveThemeOrFallback(SolarApplication.this);
                ThemeManager.cacheActiveTheme(SolarApplication.this);
                ThemeManager.preferInternalCacheForActiveTheme(SolarApplication.this);
                ThemeManager.warmOverlayThemeCache(SolarApplication.this);
                ThemeManager.syncSavedThemeToPrefs(SolarApplication.this);
                SolarBootPacing.pauseBetweenBootstrapSteps();
                LauncherPreference.reconcileHomeTargetFromProperty(SolarApplication.this);
                // 2026-07-06 — JJ/Rockbox HOME: prewarm inject daemon before first wheel tick.
                String homeTarget = LauncherPreference.getHomeTarget(SolarApplication.this);
                if (LauncherDefault.TARGET_JJ.equals(homeTarget)
                        || LauncherDefault.TARGET_ROCKBOX.equals(homeTarget)) {
                    ExternalInputHandoff.warmInjector(SolarApplication.this);
                    ExternalInputHandoff.armForForegroundPackage(SolarApplication.this);
                    MediaButtonRegistrar.ensureRegistered(SolarApplication.this);
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("homeTarget", homeTarget);
                        d.put("mainActivityAlive", MainActivity.instance != null);
                        d.put("dpadMode", ExternalInputHandoff.getDpadMode());
                        DebugE93bdbLog.log("SolarApplication.bootstrap",
                                "alternate HOME boot arm", "H2", d);
                    } catch (Exception ignored) {}
                    // #endregion
                }
                LauncherSwitch.ensurePreferredHome(SolarApplication.this);
                com.solar.launcher.youtube.NotPipePmRegistrar.ensureRegisteredAsync(SolarApplication.this);
                SolarBootPacing.pauseBetweenBootstrapSteps();
                SolarBootPacing.schedule(SolarApplication.this, 5_000L, new Runnable() {
                    @Override
                    public void run() {
                        GlobalOverlayTrigger.ensureStarted(SolarApplication.this);
                    }
                });
                UsbMassStorageExperiment.syncExperimentSysprop(SolarApplication.this);
                UsbMassStorageController.ensureStockMtpWhenExperimentOff(SolarApplication.this);
                UsbStorageSessionFlags.syncUsbSessionSysprops(SolarApplication.this);
                // #region agent log
                if (DeviceFeatures.isY1()) {
                    try {
                        org.json.JSONObject d = Debug705932Log.usbSnapshot();
                        d.put("experimentOn", UsbMassStorageExperiment.isEnabled(SolarApplication.this));
                        Debug705932Log.log("SolarApplication.bootstrap", "Y1 boot usb state", "H5", d);
                    } catch (Exception ignored) {}
                }
                // #endregion
                SolarBootPacing.pauseBetweenBootstrapSteps();
                PmInstallPolicy.enforceInternalInstallLocation();
                XposedModuleEnsurer.ensureRequiredModulesAsync(SolarApplication.this);
                // 2026-07-06 — Monitor may already run from onCreate; idempotent ensureStarted.
                RockboxForegroundMonitor.ensureStarted(SolarApplication.this);
                LauncherWatchdogService.ensureStarted(SolarApplication.this);
                SolarImeAccessibilityBootCheck.ensureEnabled(SolarApplication.this);
                // Clear stale overlay key gate (persist prop survives crash / force-stop).
                OverlayKeyGate.disarmStaleIfNeeded(SolarApplication.this);
                ThemeManager.markAppThemeBootstrapComplete();
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("bootstrapMs", System.currentTimeMillis());
                    Debug86bbe0Log.log("SolarApplication.bootstrap", "complete", "H3", d);
                } catch (Exception ignored) {}
                // #endregion
            }
        }, "SolarAppBootstrap").start();
    }

    /** True when running in {@code :overlay} — separate from main Solar / MainActivity process. */
    private boolean isOverlayProcess() {
        try {
            String name = currentProcessName();
            return name != null && name.endsWith(":overlay");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String currentProcessName() {
        try {
            int pid = android.os.Process.myPid();
            android.app.ActivityManager am = (android.app.ActivityManager)
                    getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getRunningAppProcesses() != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo info
                        : am.getRunningAppProcesses()) {
                    if (info.pid == pid) return info.processName;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
