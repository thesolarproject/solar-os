package com.solar.home.policy;

/**
 * 2026-07-07 — Which launcher packages compete for HOME and which get pm-disabled per active choice.
 * Layman: any app Android lists as a home screen competes; only your pick stays enabled.
 * Technical: pure Java; no android imports — shared by helper, Solar, bridge, shell callers.
 * Reversal: delete; restore ad-hoc pm enable/disable in LauncherDefault + switch-to-stock.sh.
 */
public final class LauncherCompetitionPolicy {

    /** Packages that must never be pm-disabled (overlay, PM middle-man, companion). */
    private static final String[] PLATFORM_NEVER_DISABLE = {
            HomeTargetPolicy.SOLAR_PKG,
            HomeTargetPolicy.HELPER_PKG,
            HomeTargetPolicy.COMPANION_PKG
    };

    /** Input-policy exceptions — longer BACK hold / nav-owned rules; not generic Android launchers. */
    private static final String[] SPECIAL_INPUT_LAUNCHERS = {
            HomeTargetPolicy.ROCKBOX_PKG,
            HomeTargetPolicy.JJ_PKG
    };

    private LauncherCompetitionPolicy() {}

    /** solar / rockbox / jj / custom — canonical HOME target ids. */
    public static String[] knownHomeTargets() {
        return new String[] {
                HomeTargetPolicy.TARGET_SOLAR,
                HomeTargetPolicy.TARGET_ROCKBOX,
                HomeTargetPolicy.TARGET_JJ,
                HomeTargetPolicy.TARGET_CUSTOM
        };
    }

    /** Package name for a normalized HOME target — custom resolves via component prop elsewhere. */
    public static String packageForTarget(String target) {
        String t = HomeTargetPolicy.normalizeTarget(target);
        if (HomeTargetPolicy.TARGET_ROCKBOX.equals(t)) {
            return HomeTargetPolicy.ROCKBOX_PKG;
        }
        if (HomeTargetPolicy.TARGET_JJ.equals(t)) {
            return HomeTargetPolicy.JJ_PKG;
        }
        if (HomeTargetPolicy.TARGET_CUSTOM.equals(t)) {
            String[] parts = HomeTargetPolicy.parseComponent(readComponentPropForPolicy());
            return parts != null ? parts[0] : HomeTargetPolicy.SOLAR_PKG;
        }
        return HomeTargetPolicy.SOLAR_PKG;
    }

    /** Map launcher package to target id — null when not a known named HOME (use custom for PM HOME). */
    public static String targetForPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return null;
        String base = basePackageName(pkg);
        if (HomeTargetPolicy.ROCKBOX_PKG.equals(base)) {
            return HomeTargetPolicy.TARGET_ROCKBOX;
        }
        if (HomeTargetPolicy.JJ_PKG.equals(base)) {
            return HomeTargetPolicy.TARGET_JJ;
        }
        if (HomeTargetPolicy.SOLAR_PKG.equals(base)) {
            return HomeTargetPolicy.TARGET_SOLAR;
        }
        if (HomeTargetPolicy.HELPER_PKG.equals(base)) {
            return null;
        }
        return null;
    }

    /**
     * 2026-07-07 — Legacy target-based disable list — prefer {@link #packagesToDisableForActiveHome}.
     * Keeps Rockbox/JJ matrix for scripts/tests; custom uses active-package scan.
     */
    public static String[] packagesToDisableForTarget(String target) {
        String t = HomeTargetPolicy.normalizeTarget(target);
        if (HomeTargetPolicy.TARGET_CUSTOM.equals(t)) {
            return packagesToDisableForActiveHome(packageForTarget(t), null);
        }
        if (HomeTargetPolicy.TARGET_ROCKBOX.equals(t)) {
            return new String[] { HomeTargetPolicy.JJ_PKG };
        }
        if (HomeTargetPolicy.TARGET_JJ.equals(t)) {
            return new String[] { HomeTargetPolicy.ROCKBOX_PKG };
        }
        return new String[] { HomeTargetPolicy.ROCKBOX_PKG, HomeTargetPolicy.JJ_PKG };
    }

    /**
     * 2026-07-07 — Disable every HOME competitor except {@code activePkg} and platform packages.
     * {@code discoveredHomePkgs} from PM CATEGORY_HOME scan (may be null → rockbox+jj fallback).
     */
    public static String[] packagesToDisableForActiveHome(String activePkg, String[] discoveredHomePkgs) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        String active = basePackageName(activePkg);
        if (discoveredHomePkgs != null) {
            for (int i = 0; i < discoveredHomePkgs.length; i++) {
                String pkg = basePackageName(discoveredHomePkgs[i]);
                if (pkg.length() == 0) continue;
                if (pkg.equals(active)) continue;
                if (isPlatformHomePackage(pkg)) continue;
                out.add(pkg);
            }
        }
        // Always consider Rockbox/JJ when installed — even if PM scan misses them.
        addIfNotActive(out, HomeTargetPolicy.ROCKBOX_PKG, active);
        addIfNotActive(out, HomeTargetPolicy.JJ_PKG, active);
        if (discoveredHomePkgs == null || discoveredHomePkgs.length == 0) {
            return out.toArray(new String[out.size()]);
        }
        return out.toArray(new String[out.size()]);
    }

    private static void addIfNotActive(java.util.LinkedHashSet<String> out, String pkg, String active) {
        if (pkg == null || pkg.equals(active) || isPlatformHomePackage(pkg)) return;
        out.add(pkg);
    }

    /** True when relaunch is allowed — disabled packages must not be started except fail-open to Solar. */
    public static boolean isLaunchAllowed(String target, boolean packageDisabled) {
        if (!packageDisabled) return true;
        return HomeTargetPolicy.TARGET_SOLAR.equals(HomeTargetPolicy.normalizeTarget(target));
    }

    /** Solar/Rockbox/JJ/helper/companion — process suffixes stripped. */
    public static boolean isKnownLauncherPackage(String procOrPkg) {
        if (procOrPkg == null || procOrPkg.length() == 0) return false;
        String base = basePackageName(procOrPkg);
        return HomeTargetPolicy.SOLAR_PKG.equals(base)
                || HomeTargetPolicy.ROCKBOX_PKG.equals(base)
                || HomeTargetPolicy.JJ_PKG.equals(base)
                || HomeTargetPolicy.HELPER_PKG.equals(base);
    }

    /** 2026-07-07 — Platform packages never pm-disabled during HOME competition. */
    public static boolean isPlatformHomePackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        String base = basePackageName(pkg);
        for (int i = 0; i < PLATFORM_NEVER_DISABLE.length; i++) {
            if (PLATFORM_NEVER_DISABLE[i].equals(base)) return true;
        }
        return false;
    }

    /** 2026-07-07 — Rockbox/JJ only — input hooks use nav-owned / 300 ms passthrough tier. */
    public static boolean isSpecialInputLauncher(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        String base = basePackageName(pkg);
        for (int i = 0; i < SPECIAL_INPUT_LAUNCHERS.length; i++) {
            if (SPECIAL_INPUT_LAUNCHERS[i].equals(base)) return true;
        }
        return false;
    }

    /** Parse comma-separated persist.solar.home.launcher_pkgs — empty array when unset. */
    public static String[] parseLauncherPkgsProperty(String raw) {
        if (raw == null || raw.trim().length() == 0) return new String[0];
        String[] parts = raw.split(",");
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.length() > 0 && !isPlatformHomePackage(p)) {
                out.add(p);
            }
        }
        return out.toArray(new String[out.size()]);
    }

    /** Join package list for persist prop — shell disable-competitors reads this. */
    public static String joinLauncherPkgsProperty(String[] pkgs) {
        if (pkgs == null || pkgs.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pkgs.length; i++) {
            if (pkgs[i] == null || pkgs[i].length() == 0) continue;
            if (isPlatformHomePackage(pkgs[i])) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(pkgs[i]);
        }
        return sb.toString();
    }

    /** Strip :overlay / :watchdog process suffix for policy checks. */
    public static String basePackageName(String procOrPkg) {
        if (procOrPkg == null) return "";
        int colon = procOrPkg.indexOf(':');
        if (colon > 0) return procOrPkg.substring(0, colon);
        return procOrPkg;
    }

    /** Policy JAR cannot read SystemProperties at runtime in unit tests — override in tests. */
    private static volatile String testComponentPropOverride;

    static void setComponentPropForTest(String flat) {
        testComponentPropOverride = flat;
    }

    static void resetComponentPropForTest() {
        testComponentPropOverride = null;
    }

    private static String readComponentPropForPolicy() {
        if (testComponentPropOverride != null) return testComponentPropOverride;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, HomeTargetPolicy.PROP_HOME_COMPONENT, "");
            return v != null ? v.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }
}
