package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.solar.home.policy.LauncherCompetitionPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 2026-07-05 — Launchable apps from PackageManager; Apps menu filtered by {@link AppsMenuPolicy}. */
public final class AppLauncher {

    public static final class Entry implements Comparable<Entry> {
        public final String label;
        public final String packageName;

        public Entry(String label, String packageName) {
            this.label = label != null ? label : "";
            this.packageName = packageName;
        }

        @Override
        public int compareTo(Entry other) {
            return label.compareToIgnoreCase(other.label);
        }
    }

    private AppLauncher() {}

    /** Sorted launchable entries — skips Solar self and packages hidden by Apps menu policy. */
    public static List<Entry> load(PackageManager pm, String excludePackage) {
        List<Entry> out = new ArrayList<Entry>();
        if (pm == null) return out;
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        if (installed == null) return out;
        for (ApplicationInfo info : installed) {
            if (info == null || info.packageName == null) continue;
            if (excludePackage != null && excludePackage.equals(info.packageName)) continue;
            if (!AppsMenuPolicy.isVisibleInAppsMenu(info.packageName)) continue;
            Intent launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null) continue;
            CharSequence name = pm.getApplicationLabel(info);
            String label = name != null ? name.toString() : info.packageName;
            out.add(new Entry(label, info.packageName));
        }
        Collections.sort(out);
        return out;
    }

    public static boolean launch(Context context, String packageName) {
        if (context == null || packageName == null) return false;
PackageManager pm = context.getPackageManager();
        Intent launch = pm != null ? pm.getLaunchIntentForPackage(packageName) : null;
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True when package resolves as Android HOME launcher. */
    public static boolean isHomeLauncherPackage(PackageManager pm, String packageName) {
        if (pm == null || packageName == null) return false;
        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> matches = pm.queryIntentActivities(probe, 0);
        if (matches == null) return false;
        for (ResolveInfo info : matches) {
            if (info.activityInfo != null
                    && packageName.equals(info.activityInfo.packageName)) {
                return true;
            }
        }
        return LauncherCompetitionPolicy.targetForPackage(packageName) != null;
    }
}
