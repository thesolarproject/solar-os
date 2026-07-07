package com.solar.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppLauncherTest {
    @Test
    public void appsMenuPolicyShowsLaunchableThirdParty() {
        if (!AppsMenuPolicy.isVisibleInAppsMenu("com.android.settings")) {
            throw new AssertionError("Settings allowed");
        }
        if (!AppsMenuPolicy.isVisibleInAppsMenu("com.example.demo")) {
            throw new AssertionError("third-party apps allowed");
        }
        if (!AppsMenuPolicy.isVisibleInAppsMenu("org.rockbox")) {
            throw new AssertionError("Rockbox visible in Apps menu");
        }
    }

    @Test
    public void entrySortsAlphabetically() {
        List<AppLauncher.Entry> list = new ArrayList<AppLauncher.Entry>();
        list.add(new AppLauncher.Entry("Zebra", "z"));
        list.add(new AppLauncher.Entry("Alpha", "a"));
        Collections.sort(list);
        if (!"Alpha".equals(list.get(0).label)) throw new AssertionError("sort order");
    }
}
