package com.solar.input.policy;

/**
 * 2026-07-06 — Shared opt-in infinite (wrap) list navigation for Solar main lists.
 * Layman: wheel at list bottom jumps to top when user turned the feature on in Settings.
 * Technical: pure Java; read {@link GlobalInputPolicy#PROP_INFINITE_SCROLL} or caller flag.
 * 2026-07-11 — Context/overlay modal excluded — wrap stole the path to Wi‑Fi/BT chips.
 * Reversal: delete; clamp-only navigation in all list hosts.
 */
public final class ListNavigationPolicy {

    public static final String PROP_INFINITE_SCROLL = GlobalInputPolicy.PROP_INFINITE_SCROLL;

    private ListNavigationPolicy() {}

    /**
     * 2026-07-11 — Context / overlay modal never uses infinite wrap.
     * Layman: quick chips (Wi‑Fi, Bluetooth) need a hard edge so wheel can leave the list.
     * Technical: ThemedContextMenu / ChipContextMenu must clamp; wrap would skip QUICK_BAR.
     * Reversal: return true to honor persist.solar.nav.infinite_scroll inside modals again.
     */
    public static boolean appliesToContextModal() {
        return false;
    }

    /**
     * Effective infinite flag for a host: pref on AND host allows wrap.
     * Overlay/modal passes {@code forContextModal=true} and always gets clamp.
     */
    public static boolean effectiveInfinite(boolean prefEnabled, boolean forContextModal) {
        if (forContextModal && !appliesToContextModal()) return false;
        return prefEnabled;
    }

    /** Wrap linear index when infinite is on; clamp otherwise. Returns -1 when invalid delta/count. */
    public static int wrapIndex(int current, int delta, int count, boolean infinite) {
        if (count <= 0 || delta == 0) return -1;
        int next = current + delta;
        if (!infinite) {
            if (next < 0 || next >= count) return -1;
            return next;
        }
        while (next < 0) next += count;
        while (next >= count) next -= count;
        return next;
    }

    /** Read persist prop — companion/overlay without Solar SharedPreferences. */
    public static boolean isInfiniteScrollFromProperty() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class)
                    .invoke(null, PROP_INFINITE_SCROLL, "0");
            return "1".equals(String.valueOf(v));
        } catch (Exception e) {
            return false;
        }
    }
}
