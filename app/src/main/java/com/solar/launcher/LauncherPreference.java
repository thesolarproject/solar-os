package com.solar.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * Preferred HOME launcher — overlay picker, switch scripts, and Settings → Device.
 */
public final class LauncherPreference {

    private static final String PREFS = "solar_launcher_pref";
    private static final String KEY_HOME_TARGET = "home_target";
    /** Xposed resolver hook skips re-picker while apply is in flight (breaks HOME loops). */
    public static final String PROP_HOME_APPLYING = "persist.solar.home.applying";
    /** Saved HOME app — boot shell and Xposed read this (cannot read Solar private prefs). */
    public static final String PROP_HOME_TARGET = "persist.solar.home.target";

    private LauncherPreference() {}

    /** Read persisted HOME target from SharedPreferences — defaults to Solar. */
    public static String getHomeTarget(Context context) {
        if (context == null) return LauncherDefault.TARGET_SOLAR;
        String target = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_HOME_TARGET, LauncherDefault.TARGET_SOLAR);
        return normalizeHomeTarget(target);
    }

    /** Copy prefs choice to persist prop — call on boot so scripts/Xposed see the same target. */
    public static void syncHomeTargetPropertyFromPrefs(Context context) {
        if (context == null) return;
        syncHomeTargetProperty(getHomeTarget(context));
    }

    /**
     * 2026-07-06 — Rescue/shell write persist prop first; prefs may lag until broadcast lands.
     * Layman: if emergency hold or a boot script said Solar, trust that over stale Settings memory.
     * Technical: prop wins on mismatch → {@link #applyHomeTarget}; else prefs → prop sync.
     * Reversal: revert to {@link #syncHomeTargetPropertyFromPrefs} only on boot.
     */
    public static void reconcileHomeTargetFromProperty(Context context) {
        if (context == null) return;
        String propTarget = readHomeTargetProperty();
        String prefTarget = getHomeTarget(context);
        if (!normalizeHomeTarget(propTarget).equals(normalizeHomeTarget(prefTarget))) {
            applyHomeTarget(context, propTarget);
        } else {
            syncHomeTargetPropertyFromPrefs(context);
        }
    }

    /** Read persist.solar.home.target — shell/rescue authoritative when prefs drift. */
    static String readHomeTargetProperty() {
        if (testHomeTargetPropOverride != null) {
            return normalizeHomeTarget(testHomeTargetPropOverride);
        }
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, PROP_HOME_TARGET, LauncherDefault.TARGET_SOLAR);
            return normalizeHomeTarget(String.valueOf(v));
        } catch (Exception e) {
            return LauncherDefault.TARGET_SOLAR;
        }
    }

    /** Unit tests — simulate rescue setprop without device. */
    static void setHomeTargetPropertyForTest(String target) {
        testHomeTargetPropOverride = target;
    }

    static void resetHomeTargetPropertyForTest() {
        testHomeTargetPropOverride = null;
    }

    private static volatile String testHomeTargetPropOverride;

    /** True when persisted HOME is Solar — boot must not auto-start MainActivity for Rockbox users. */
    public static boolean isSolarHome(Context context) {
        return isSolarHomeTarget(getHomeTarget(context));
    }

    /** Target string check — unit-tested without Robolectric. */
    static boolean isSolarHomeTarget(String target) {
        return LauncherDefault.TARGET_SOLAR.equals(target);
    }

    /** Set preferred HOME via PackageManager helper pin, persist target, and mirror to boot/Xposed prop. */
    public static void applyHomeTarget(Context context, String target) {
        applyHomeTargetWithComponent(context, target, null);
    }

    /** 2026-07-07 — Custom HOME includes pkg/activity flat component prop. */
    public static void applyHomeTargetWithComponent(Context context, String target, String componentFlat) {
        if (context == null || target == null) return;
        target = normalizeHomeTarget(target);
        markHomeApplyInProgress();
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_HOME_TARGET, target).apply();
        syncHomeTargetProperty(target);
        if (componentFlat != null && componentFlat.length() > 0) {
            RootShell.run("setprop " + HomeTargetPolicy.PROP_HOME_COMPONENT + " " + componentFlat);
        }
        if (LauncherDefault.TARGET_ROCKBOX.equals(target)) {
            LauncherDefault.setPreferredRockboxHome(app);
        } else if (LauncherDefault.TARGET_JJ.equals(target)) {
            LauncherDefault.setPreferredJjHome(app);
        } else if (HomeTargetPolicy.TARGET_CUSTOM.equals(target)) {
            LauncherDefault.setPreferredCustomHome(app);
        } else {
            LauncherDefault.setPreferredSolarHome(app);
        }
        SolarOverlayHost.ensureStarted(app);
        LauncherWatchdogService.ensureStarted(app);
    }

    /** Start persisted HOME — explicit component only (never ambiguous CATEGORY_HOME). */
    public static void launchHome(Context context) {
        launchHomeForTarget(context, getHomeTarget(context));
    }

    /** Launch effective HOME — explicit component (helper routes on HOME press). */
    public static void launchHomeForTarget(Context context, String target) {
        if (context == null || target == null) return;
        String normalized = normalizeHomeTarget(target);
        if (LauncherDefault.TARGET_ROCKBOX.equals(normalized)
                && LauncherSwitch.isRockboxDisabled(context)) {
            normalized = LauncherDefault.TARGET_SOLAR;
        }
        if (LauncherDefault.TARGET_JJ.equals(normalized)
                && LauncherSwitch.isJjDisabled(context)) {
            normalized = LauncherDefault.TARGET_SOLAR;
        }
        Intent launch = buildExplicitHomeIntent(context, normalized);
        if (launch == null) return;
        context.getApplicationContext().startActivity(launch);
    }

    /** Explicit MAIN launch — unit-tested without Robolectric. */
    static Intent buildExplicitHomeIntent(Context context, String target) {
        String[] parts = HomeTargetPolicy.resolveLaunchComponent(
                normalizeHomeTarget(target), readHomeComponentProperty());
        if (LauncherDefault.TARGET_ROCKBOX.equals(normalizeHomeTarget(target))
                && !LauncherSwitch.isRockboxInstalled(context)) {
            return null;
        }
        if (LauncherDefault.TARGET_JJ.equals(normalizeHomeTarget(target))
                && !LauncherSwitch.isJjInstalled(context)) {
            return null;
        }
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launch.setComponent(new ComponentName(parts[0], parts[1]));
        return launch;
    }

    static String readHomeComponentProperty() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, HomeTargetPolicy.PROP_HOME_COMPONENT, "");
            return String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    /** Tell Xposed resolver hook to stand down while PM preferred activity is settling. */
    static void markHomeApplyInProgress() {
        RootShell.run("setprop " + PROP_HOME_APPLYING + " 1");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3500L);
                } catch (InterruptedException ignored) {}
                RootShell.run("setprop " + PROP_HOME_APPLYING + " 0");
            }
        }, "SolarHomeApplyGate").start();
    }

    /** Coerce unknown values — solar, rockbox, jj, or custom. */
    static String normalizeHomeTarget(String target) {
        if (LauncherDefault.TARGET_ROCKBOX.equals(target)) {
            return LauncherDefault.TARGET_ROCKBOX;
        }
        if (LauncherDefault.TARGET_JJ.equals(target)) {
            return LauncherDefault.TARGET_JJ;
        }
        if (HomeTargetPolicy.TARGET_CUSTOM.equals(target)) {
            return HomeTargetPolicy.TARGET_CUSTOM;
        }
        return LauncherDefault.TARGET_SOLAR;
    }

    /** Write persist.solar.home.target so boot scripts and Xposed match Settings. */
    static void syncHomeTargetProperty(String target) {
        RootShell.run("setprop " + PROP_HOME_TARGET + " " + normalizeHomeTarget(target));
    }
}
