package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Y2 module entry — power-hold or long BACK global quick modal + volume panel + app menus. */
public final class SolarContextBridgeY2 implements IXposedHookLoadPackage {

    private static final SolarContextBridge DELEGATE =
            new SolarContextBridge(SolarContextBridge.Target.Y2);

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        DELEGATE.handleLoadPackage(lpparam);
    }
}
