package com.solar.launcher.globalcontext;

/**
 * 2026-07-05 — Reflection wrapper for android.os.SystemProperties (API 17 safe).
 * Layman: reads/writes the small system flags companion and Solar share.
 * Technical: isolates Class.forName so policy JAR stays android-import-free.
 */
public final class SysPropHelper {

    private SysPropHelper() {}

    public static String get(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    public static void set(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Throwable ignored) {}
    }

    public static boolean isTrue(String key) {
        return "1".equals(get(key, "0"));
    }

    /** 2026-07-06 — Opt-in list wrap from persist.solar.nav.infinite_scroll (companion overlay lists). */
    public static boolean isInfiniteScrollEnabled() {
        return isTrue(com.solar.input.policy.GlobalInputPolicy.PROP_INFINITE_SCROLL);
    }
}
