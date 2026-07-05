package com.solar.launcher.xposed;

/**
 * Placeholder Xposed module entry — not shipped in ROM until hook phase.
 * Future hooks (see solar-rom/patches/xposed/README.md):
 * - PhoneWindowManager.showGlobalActions → Solar power tier (Y2)
 * - UsbStorageActivity.onCreate → Solar USB lock screen
 * - interceptKeyBeforeQueueing (long BACK) → global context modal
 */
public final class SolarXposedBootstrap implements de.robv.android.xposed.IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam lpparam) {
        // Intentionally empty — enable module in Xposed Installer when implemented.
    }
}
