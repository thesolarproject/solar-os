package com.solar.launcher.homehelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Permanent Android PM preferred HOME middle-man.
 * Layman: Home button always opens this tiny app first; it forwards to Solar, Rockbox, or JJ.
 * Technical: reads persist.solar.home.target (+ optional custom component), explicit MAIN launch, finish.
 * Reversal: remove HOME intent-filter; restore direct PM preferred on Solar/Rockbox/JJ activities.
 */
public class LauncherHomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchSavedTarget(this);
        finish();
    }

    /** Read prop and start the user's effective launcher — never CATEGORY_HOME (reopens picker). */
    public static void launchSavedTarget(android.content.Context context) {
        if (context == null) return;
        String target = readSystemProperty(HomeTargetPolicy.PROP_HOME_TARGET, HomeTargetPolicy.TARGET_SOLAR);
        String custom = readSystemProperty(HomeTargetPolicy.PROP_HOME_COMPONENT, "");
        String[] component = HomeTargetPolicy.resolveLaunchComponent(target, custom);
        if (isPackageDisabled(component[0]) && !HomeTargetPolicy.SOLAR_PKG.equals(component[0])) {
            component = HomeTargetPolicy.resolveLaunchComponent(HomeTargetPolicy.TARGET_SOLAR, "");
        }
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setComponent(new ComponentName(component[0], component[1]));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
            armJjHandoffIfNeeded(context, target);
        } catch (Exception first) {
            // Fail-open to Solar when alternate package is missing or disabled.
            try {
                Intent solar = new Intent(Intent.ACTION_MAIN);
                solar.setComponent(new ComponentName(
                        HomeTargetPolicy.SOLAR_PKG, HomeTargetPolicy.SOLAR_ACTIVITY));
                solar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(solar);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 2026-07-06 — Wake Solar handoff process when routing HOME to JJ (wheel remap).
     * 2026-07-08 — Stock Innioasis HOME uses the same wheel remap as JJ, so arm for it too.
     * Best-effort: if Solar is disabled/dead this no-ops; the Xposed shim self-arms from
     * persist.solar.home.target (see JjInputHooks) so the wheel still works without Solar.
     * Reversal: restore TARGET_JJ-only check (stock loses root-inject wheel tier).
     */
    private static void armJjHandoffIfNeeded(android.content.Context context, String target) {
        String normalized = HomeTargetPolicy.normalizeTarget(target);
        if (!HomeTargetPolicy.TARGET_JJ.equals(normalized)
                && !HomeTargetPolicy.TARGET_STOCK.equals(normalized)) return;
        try {
            Intent arm = new Intent("com.solar.launcher.action.ARM_JJ_HANDOFF");
            arm.setPackage(HomeTargetPolicy.SOLAR_PKG);
            context.sendBroadcast(arm);
            Intent svc = new Intent();
            svc.setClassName(HomeTargetPolicy.SOLAR_PKG,
                    "com.solar.launcher.SolarHandoffService");
            svc.setAction("com.solar.launcher.action.ARM_JJ_HANDOFF");
            context.startService(svc);
        } catch (Exception ignored) {}
    }

    /** Instance entry for unit tests — production path uses {@link #launchSavedTarget}. */
    void routeToSavedTarget() {
        launchSavedTarget(this);
    }

    /** Read persist/sysprop without importing android.os.SystemProperties at compile time. */
    static String readSystemProperty(String key, String defaultValue) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, defaultValue);
            return String.valueOf(v);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** pm disable check — route to Solar when alternate HOME is disabled. */
    static boolean isPackageDisabled(String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "sh", "-c", "pm list packages -d " + pkg + " 2>/dev/null"
            });
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line != null && line.contains(pkg);
        } catch (Exception e) {
            return false;
        }
    }
}
