package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Y2 bridge — Solar POWER in-app menu; HOLD BACK → Solar; stock GlobalActions outside Solar. */
public final class SolarContextBridgeY2 implements IXposedHookLoadPackage {

    private static final SolarContextBridge DELEGATE =
            new SolarContextBridge(SolarContextBridge.Target.Y2);

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        DELEGATE.handleLoadPackage(lpparam);
    }
}
