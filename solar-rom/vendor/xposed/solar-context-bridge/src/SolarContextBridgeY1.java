package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Y1 bridge entry — long BACK opens global quick modal (no dedicated power key on Y1). */
public final class SolarContextBridgeY1 implements IXposedHookLoadPackage {

    private static final SolarContextBridge DELEGATE =
            new SolarContextBridge(SolarContextBridge.Target.Y1);

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        DELEGATE.handleLoadPackage(lpparam);
    }
}
