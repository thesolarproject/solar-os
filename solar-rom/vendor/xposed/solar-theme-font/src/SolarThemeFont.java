package com.solar.launcher.xposed.themefont;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed entry: apply Solar theme font system-wide via /.solar/system-font.ttf sidecar.
 */
public final class SolarThemeFont implements IXposedHookLoadPackage {

    private static final String INSTALLER = "de.robv.android.xposed.installer";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (INSTALLER.equals(lpparam.packageName)) {
            return;
        }
        TypefaceHooks.install(lpparam.classLoader);
        File sidecar = FontSidecar.resolveSidecarFile();
        if (sidecar != null) {
            TypefaceHooks.log("loaded in " + lpparam.packageName + " sidecar=" + sidecar.getPath());
        }
    }
}
