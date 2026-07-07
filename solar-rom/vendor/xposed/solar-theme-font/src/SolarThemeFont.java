package com.solar.launcher.xposed.themefont;

import java.io.File;

import de.robv.android.xposed.IXposedHookInitZygote;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed entry: Solar theme font + Holo cosmetics + Activity/SystemUI skin from sidecars.
 */
public final class SolarThemeFont implements IXposedHookLoadPackage, IXposedHookInitZygote {

    private static final String INSTALLER = "de.robv.android.xposed.installer";

    /** Post-inflate Holo skin hooks apply to every app from zygote. */
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        HoloLayoutSkin.installAtZygote();
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (INSTALLER.equals(lpparam.packageName)) {
            return;
        }
        TypefaceHooks.install(lpparam.classLoader);
        ActivitySkin.install(lpparam.packageName, lpparam.classLoader);
        SystemUiSkin.install(lpparam);
        File sidecar = FontSidecar.resolveSidecarFile();
        if (sidecar != null) {
            TypefaceHooks.log("loaded in " + lpparam.packageName + " sidecar=" + sidecar.getPath());
        }
    }
}
