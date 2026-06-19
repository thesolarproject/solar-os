package com.solar.launcher;

import android.view.KeyEvent;

/**
 * Y1 input: Solar listens to Android {@link KeyEvent} keycodes (not scancodes).
 * System {@code mtk-kpd.kl} maps physical controls; this class maps keycodes to UI roles.
 */
public final class Y1KeyMap {
    public static final String PREF_ROCKBOX_KEYMAP = "debug_rockbox_keymap";
    public static final String PREF_ROCKBOX_KEYMAP_MANUAL = "debug_rockbox_keymap_manual";

    public static final int LAYOUT_STOCK = 0;
    public static final int LAYOUT_ROCKBOX_ROM = 1;
    public static final int LAYOUT_ROCKBOX_SIDELoad = 2;
    public static final int LAYOUT_ROCKBOX_CLASSIC = 3;

    private static volatile int cachedLayout = -1;

    private Y1KeyMap() {}

    static void resetLayoutCacheForTest() {
        cachedLayout = -1;
    }

    static void setLayoutForTest(int layout) {
        cachedLayout = layout;
    }

    public static boolean isRockboxKeymap(android.content.SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_ROCKBOX_KEYMAP, false);
    }

    public static int detectMtkLayout() {
        int c = cachedLayout;
        if (c >= 0) return c;
        c = readMtkLayoutFromDevice();
        cachedLayout = c;
        return c;
    }

    private static int readMtkLayoutFromDevice() {
        String l103 = null;
        String l105 = null;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader("/system/usr/keylayout/mtk-kpd.kl"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("key 103")) l103 = line;
                else if (line.startsWith("key 105")) l105 = line;
            }
            r.close();
        } catch (Exception ignored) {
            return LAYOUT_STOCK;
        }
        return classifyMtkLines(l103, l105);
    }

    public static int classifyMtkLines(String line103, String line105) {
        if (line105 != null && line105.contains("MEDIA_PREVIOUS")) return LAYOUT_STOCK;
        if (line103 != null && line103.contains("MEDIA_PREVIOUS")) return LAYOUT_ROCKBOX_SIDELoad;
        if (line105 != null && line105.contains("DPAD_UP")
                && line103 != null && line103.contains("DPAD_LEFT")) {
            return LAYOUT_ROCKBOX_ROM;
        }
        if (line103 != null && line103.contains("DPAD_UP")) return LAYOUT_ROCKBOX_CLASSIC;
        return LAYOUT_STOCK;
    }

    public static String layoutLabel(int layout) {
        switch (layout) {
            case LAYOUT_ROCKBOX_ROM: return "Rockbox ROM";
            case LAYOUT_ROCKBOX_SIDELoad: return "Rockbox sideload";
            case LAYOUT_ROCKBOX_CLASSIC: return "Rockbox classic";
            case LAYOUT_STOCK:
            default: return "Stock Y1";
        }
    }

    public static boolean deviceHasStockMtkKpd() {
        return detectMtkLayout() == LAYOUT_STOCK;
    }

    public static boolean deviceHasRockboxMtkKpd() {
        return detectMtkLayout() != LAYOUT_STOCK;
    }

    public static boolean reconcileRockboxKeymapPref(android.content.Context ctx,
            android.content.SharedPreferences prefs) {
        if (prefs == null) return false;
        cachedLayout = -1;
        if (prefs.getBoolean(PREF_ROCKBOX_KEYMAP_MANUAL, false)) return false;
        boolean want = shouldUseRockboxKeymap(ctx);
        boolean had = prefs.getBoolean(PREF_ROCKBOX_KEYMAP, false);
        if (had == want) return false;
        prefs.edit().putBoolean(PREF_ROCKBOX_KEYMAP, want).commit();
        return true;
    }

    public static boolean shouldUseRockboxKeymap(android.content.Context ctx) {
        if (deviceHasStockMtkKpd()) return false;
        int layout = detectMtkLayout();
        if (layout != LAYOUT_STOCK) return true;
        return isRockboxPackageEnabled(ctx);
    }

    public static boolean isRockboxPackageInstalled(android.content.Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getPackageManager().getPackageInfo("org.rockbox", 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isRockboxPackageEnabled(android.content.Context ctx) {
        if (ctx == null) return false;
        try {
            android.content.pm.ApplicationInfo ai =
                    ctx.getPackageManager().getApplicationInfo("org.rockbox", 0);
            return ai != null && ai.enabled;
        } catch (Exception ignored) {}
        return false;
    }

    private static int layoutFor(boolean rockboxKeymap) {
        // ponytail: always trust live mtk-kpd; pref must not force stock semantics on Rockbox ROM
        int detected = detectMtkLayout();
        if (detected != LAYOUT_STOCK) return detected;
        return LAYOUT_STOCK;
    }

    public static boolean isWheelUp(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88;
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_ROM:
                return keyCode == KeyEvent.KEYCODE_DPAD_UP;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
        }
    }

    public static boolean isWheelDown(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87;
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_ROM:
                return keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
    }

    public static boolean isWheelKey(int keyCode, boolean rockboxKeymap) {
        return isWheelUp(keyCode, rockboxKeymap) || isWheelDown(keyCode, rockboxKeymap);
    }

    public static int wheelDelta(int keyCode, boolean rockboxKeymap) {
        if (isWheelUp(keyCode, rockboxKeymap)) return -1;
        if (isWheelDown(keyCode, rockboxKeymap)) return 1;
        return 0;
    }

    /** Horizontal nav (keyboard, context menu): LEFT/RIGHT or prev/next on Rockbox. */
    public static boolean isHorizontalLeft(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_ROM:
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
        }
    }

    public static boolean isHorizontalRight(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_ROM:
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        }
    }

    public static boolean isMediaPrevious(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_ROM:
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88
                        || keyCode == 165;
        }
    }

    public static boolean isMediaNext(int keyCode, boolean rockboxKeymap) {
        switch (layoutFor(rockboxKeymap)) {
            case LAYOUT_ROCKBOX_ROM:
            case LAYOUT_ROCKBOX_CLASSIC:
            case LAYOUT_ROCKBOX_SIDELoad:
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            case LAYOUT_STOCK:
            default:
                return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87
                        || keyCode == 163;
        }
    }

    public static boolean isMediaSkip(int keyCode, boolean rockboxKeymap) {
        return isMediaPrevious(keyCode, rockboxKeymap) || isMediaNext(keyCode, rockboxKeymap);
    }

    /** BT remotes and hardware play/pause — same on all layouts. */
    public static boolean isPlayPauseKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86;
    }
}
