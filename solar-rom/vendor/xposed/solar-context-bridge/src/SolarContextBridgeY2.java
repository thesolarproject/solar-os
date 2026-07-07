package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Y2 bridge entry — power-hold or long BACK global modal; suppresses stock GlobalActions on power. */
public final class SolarContextBridgeY2 implements IXposedHookLoadPackage {

    private static final SolarContextBridge DELEGATE =
            new SolarContextBridge(SolarContextBridge.Target.Y2);

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        DELEGATE.handleLoadPackage(lpparam);
    }
}
