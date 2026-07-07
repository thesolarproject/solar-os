package com.solar.launcher;

import org.junit.Test;

public class AppsMenuPolicyTest {

    @Test
    public void settingsVisibleInAppsMenu() {
        if (!AppsMenuPolicy.isVisibleInAppsMenu("com.android.settings")) {
            throw new AssertionError("Settings must appear in Apps menu");
        }
    }

    @Test
    public void thirdPartyLaunchableVisible() {
        if (!AppsMenuPolicy.isVisibleInAppsMenu("io.github.gohoski.notpipe")) {
            throw new AssertionError("notPipe should appear when launchable");
        }
    }

    @Test
    public void rockboxVisibleInAppsMenu() {
        if (!AppsMenuPolicy.isVisibleInAppsMenu("org.rockbox")) {
            throw new AssertionError("Rockbox launcher should be visible in Apps menu");
        }
    }

    @Test
    public void xposedInstallerHiddenFromAppsMenu() {
        if (AppsMenuPolicy.isVisibleInAppsMenu("de.robv.android.xposed.installer")) {
            throw new AssertionError("XposedInstaller hidden — use Debug submenu");
        }
    }
}
