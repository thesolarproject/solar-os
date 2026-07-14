package com.solar.launcher.globalcontext;

import java.io.DataOutputStream;

/**
 * 2026-07-05 — Reflection wrapper for android.os.SystemProperties (API 17 safe).
 * 2026-07-08 — Root setprop fallback when app-context set is blocked (Y1/Y2 MTK / data install).
 * Layman: reads/writes the small system flags companion and Solar share.
 * Technical: mirrors Solar OverlayKeyGate writeProp; silently no-ops only after both fail.
 * Reversal: drop su branch — props only when companion is system-signed.
 */
public final class SysPropHelper {

    private static Class<?> sClass;
    private static java.lang.reflect.Method sGetMethod;
    private static java.lang.reflect.Method sSetMethod;

    static {
        try {
            sClass = Class.forName("android.os.SystemProperties");
            sGetMethod = sClass.getMethod("get", String.class, String.class);
            sSetMethod = sClass.getMethod("set", String.class, String.class);
        } catch (Throwable ignored) {
        }
    }

    private SysPropHelper() {}

    public static String get(String key, String def) {
        try {
            if (sGetMethod != null) {
                Object v = sGetMethod.invoke(null, key, def);
                return v != null ? v.toString() : def;
            }
        } catch (Throwable t) {
        }
        return def;
    }

    /**
     * Write a sysprop without blocking the UI thread on root setprop.
     * 2026-07-10 — Was: su waitFor on main during arm() → frozen wheel/Back until su finished.
     * Now: reflection first; root fallback on a background thread (mem flags already set).
     * Reversal: call tryRootSetprop inline again if props never stick for Xposed.
     */
    public static void set(String key, String value) {
        if (key == null) return;
        final String v = value != null ? value : "";
        if (v.equals(get(key, "\0"))) return;
        
        if (trySystemPropertiesSet(key, v)) {
            return;
        }
        // 2026-07-08 — MTK often blocks SystemProperties.set from non-system UID.
        // 2026-07-10 — Never block overlay paint / key arm on su.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                tryRootSetprop(key, v);
            }
        }, "SolarSysPropSet");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 2026-07-11 — Arm overlay capture props so Xposed PWM can forward wheel on Y2.
     * Layman: tell the system “the quick menu is open” even when normal setprop is blocked.
     * Technical: reflection + background su for active/ui/shell_visible as a batch.
     * Reversal: delete; rely on per-key set() only (Y2 Power modal keys often dead).
     */
    public static void forceCriticalOverlayProps(String value) {
        final String v = "1".equals(value) ? "1" : "0";
        final String[] keys = new String[] {
                "sys.solar.overlay.active",
                "sys.solar.overlay.ui",
                "sys.solar.overlay.shell_visible"
        };
        boolean allOk = true;
        for (int i = 0; i < keys.length; i++) {
            if (!trySystemPropertiesSet(keys[i], v)) {
                allOk = false;
            }
        }
        if (allOk) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // One su session for all three — faster than three waitFor chains.
                try {
                    String cmd = "setprop sys.solar.overlay.active " + v
                            + " && setprop sys.solar.overlay.ui " + v
                            + " && setprop sys.solar.overlay.shell_visible " + v;
                    Process p = Runtime.getRuntime().exec(new String[] {"su", "-c", cmd});
                    p.waitFor();
                } catch (Throwable ignored) {
                    tryRootSetprop("sys.solar.overlay.active", v);
                    tryRootSetprop("sys.solar.overlay.ui", v);
                    tryRootSetprop("sys.solar.overlay.shell_visible", v);
                }
            }
        }, "SolarOverlayPropArm");
        t.setDaemon(true);
        t.start();
    }

    public static boolean isTrue(String key) {
        return "1".equals(get(key, "0"));
    }

    /**
     * 2026-07-11 — Companion overlay never wraps (chips need hard edges).
     * Was: read persist.solar.nav.infinite_scroll. Reversal: return isTrue(PROP_INFINITE_SCROLL).
     */
    public static boolean isInfiniteScrollEnabled() {
        return com.solar.input.policy.ListNavigationPolicy.effectiveInfinite(
                isTrue(com.solar.input.policy.GlobalInputPolicy.PROP_INFINITE_SCROLL), true);
    }

    private static boolean trySystemPropertiesSet(String key, String value) {
        if (value.equals(get(key, "\0"))) return true;
        try {
            if (sSetMethod != null) {
                sSetMethod.invoke(null, key, value);
                // Verify — set can no-op without throwing on some ROMs.
                String read = get(key, "\0");
                return value.equals(read);
            }
        } catch (Throwable t) {
        }
        return false;
    }

    private static void tryRootSetprop(String key, String value) {
        // 2026-07-10 — Prefer non-interactive su -c (Y1/Y2 setuid su). Interactive `su`
        // shell often never writes props → active=0 → Xposed never forwards keys.
        Process p = null;
        try {
            // Values are 0/1/short tokens only — no shell metacharacters.
            p = Runtime.getRuntime().exec(new String[] {
                    "su", "-c", "setprop " + key + " " + value
            });
            p.waitFor();
            if (value.equals(get(key, "\0"))) return;
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {}
            }
        }
        // Fallback: toolbox setprop via sh -c (some SuperSU builds need this).
        try {
            p = Runtime.getRuntime().exec(new String[] {
                    "su", "0", "setprop", key, value
            });
            p.waitFor();
            if (value.equals(get(key, "\0"))) return;
        } catch (Throwable ignored) {
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {}
            }
        }
        // Fallback: interactive su pipe (older images).
        DataOutputStream os = null;
        try {
            p = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("setprop " + key + " " + value + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Throwable ignored) {
        } finally {
            try {
                if (os != null) os.close();
            } catch (Throwable ignored) {}
            if (p != null) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {}
            }
        }
    }
}
