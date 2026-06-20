package com.solar.launcher;

import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * Y1 input per {@code .cursor/rules/input-rules.mdc}:
 * <ul>
 *   <li><b>Stock</b> wheel 21/22 (DPAD_LEFT/RIGHT), skip 88/87</li>
 *   <li><b>Rockbox ROM</b> wheel 126/127 (MEDIA_PLAY/PAUSE), skip 88/87 and 21/22</li>
 * </ul>
 * Scancodes 103/108 (wheel) and 105/106 (skip) disambiguate when keylayout files disagree.
 */
public final class Y1KeyMap {
    public static final String PREF_ROCKBOX_KEYMAP = "debug_rockbox_keymap";
    public static final String PREF_ROCKBOX_KEYMAP_MANUAL = "debug_rockbox_keymap_manual";

    public static final int SCAN_WHEEL_CCW = 103;
    public static final int SCAN_WHEEL_CW = 108;
    public static final int SCAN_WHEEL_UP = 114;
    public static final int SCAN_WHEEL_DOWN = 115;
    public static final int SCAN_PREV = 105;
    public static final int SCAN_NEXT = 106;

    public static final int LAYOUT_STOCK = 0;
    public static final int LAYOUT_ROCKBOX_ROM = 1;
    public static final int LAYOUT_ROCKBOX_SIDELoad = 2;
    public static final int LAYOUT_ROCKBOX_CLASSIC = 3;

    private static final String MTK_KL = "/system/usr/keylayout/mtk-kpd.kl";
    private static final String GENERIC_KL = "/system/usr/keylayout/Generic.kl";

    private static volatile int cachedLayout = -1;
    private static volatile int runtimeLayoutHint = -1;

    private Y1KeyMap() {}

    static void resetLayoutCacheForTest() {
        cachedLayout = -1;
        runtimeLayoutHint = -1;
    }

    static void setLayoutForTest(int layout) {
        cachedLayout = layout;
    }

    static void setRuntimeLayoutHintForTest(int layout) {
        runtimeLayoutHint = layout;
    }

    static int getRuntimeLayoutHintForTest() {
        return runtimeLayoutHint;
    }

    public static boolean isRockboxKeymap(android.content.SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_ROCKBOX_KEYMAP, false);
    }

    public static int detectMtkLayout() {
        int c = cachedLayout;
        if (c >= 0) return c;
        c = readLayoutFromDevice();
        cachedLayout = c;
        return c;
    }

    static final class KlLines {
        String l103;
        String l105;
        String l114;
    }

    private static KlLines readKlLines(String path) {
        KlLines out = new KlLines();
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("key 103")) out.l103 = line;
                else if (line.startsWith("key 105")) out.l105 = line;
                else if (line.startsWith("key 114")) out.l114 = line;
            }
            r.close();
        } catch (Exception ignored) {}
        return out;
    }

    private static boolean lineHasMediaPrevious(String line) {
        return line != null && line.contains("MEDIA_PREVIOUS");
    }

    /** rockbox-y1 {@code Rockbox.kl} on Generic.kl (Y1 loads Generic for mtk-kpd). */
    private static boolean lineHasRockboxKlGeneric(KlLines gen) {
        return gen.l103 != null && gen.l103.contains("MEDIA_PLAY");
    }

    private static int readLayoutFromDevice() {
        return classifyLayoutFromKlFiles(readKlLines(MTK_KL), readKlLines(GENERIC_KL));
    }

    static int classifyLayoutFromKlFiles(KlLines mtk, KlLines gen) {
        if (gen.l103 != null && gen.l103.contains("MEDIA_PLAY")) {
            return LAYOUT_ROCKBOX_ROM;
        }
        if (mtk.l103 != null && mtk.l103.contains("MEDIA_PLAY")) {
            return LAYOUT_ROCKBOX_ROM;
        }
        if (mtk.l103 != null && mtk.l103.contains("DPAD_UP")) {
            return LAYOUT_ROCKBOX_CLASSIC;
        }
        if (gen.l103 != null && gen.l103.contains("DPAD_UP")) {
            return LAYOUT_ROCKBOX_CLASSIC;
        }
        if ((mtk.l103 != null && mtk.l103.contains("MEDIA_PREVIOUS"))
                || (gen.l103 != null && gen.l103.contains("MEDIA_PREVIOUS"))) {
            return LAYOUT_ROCKBOX_SIDELoad;
        }
        return LAYOUT_STOCK;
    }

    public static int classifyMtkLines(String line103, String line105) {
        if (line105 != null && line105.contains("MEDIA_PREVIOUS")) return LAYOUT_STOCK;
        if (line103 != null && line103.contains("MEDIA_PREVIOUS")) return LAYOUT_ROCKBOX_SIDELoad;
        if (line105 != null && line105.contains("DPAD_UP")
                && line103 != null && line103.contains("DPAD_LEFT")) {
            return LAYOUT_ROCKBOX_ROM;
        }
        if (line105 != null && line105.contains("DPAD_LEFT")) return LAYOUT_ROCKBOX_CLASSIC;
        if (line103 != null && line103.contains("DPAD_UP")) return LAYOUT_ROCKBOX_CLASSIC;
        return LAYOUT_STOCK;
    }

    public static boolean isGenericMtkWheelMismatch() {
        KlLines mtk = readKlLines(MTK_KL);
        KlLines gen = readKlLines(GENERIC_KL);
        if (mtk.l103 == null || gen.l103 == null) return false;
        return mtk.l103.contains("DPAD_UP") && gen.l103.contains("DPAD_LEFT");
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

    public static boolean noteHardwareKey(KeyEvent event) {
        if (event == null) return false;
        return noteHardwareKeyInput(event.getSource(), event.getScanCode(), event.getKeyCode());
    }

    static boolean noteHardwareKeyInput(int source, int scanCode, int keyCode) {
        if ((source & InputDevice.SOURCE_KEYBOARD) == 0
                && (source & InputDevice.SOURCE_DPAD) == 0) {
            return false;
        }
        int prev = runtimeLayoutHint;
        if (scanCode == SCAN_WHEEL_CCW) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_ROM;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                runtimeLayoutHint = LAYOUT_STOCK;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_CLASSIC;
            }
        } else if (scanCode == SCAN_WHEEL_CW) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_ROM;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                runtimeLayoutHint = LAYOUT_STOCK;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_CLASSIC;
            }
        } else if (scanCode == SCAN_PREV) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                runtimeLayoutHint = isRockboxLayout(runtimeLayoutHint) ? runtimeLayoutHint : LAYOUT_STOCK;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_CLASSIC;
            }
        } else if (scanCode == SCAN_NEXT) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                runtimeLayoutHint = isRockboxLayout(runtimeLayoutHint) ? runtimeLayoutHint : LAYOUT_STOCK;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                runtimeLayoutHint = LAYOUT_ROCKBOX_CLASSIC;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126) {
            runtimeLayoutHint = LAYOUT_ROCKBOX_ROM;
        }
        return runtimeLayoutHint != prev;
    }

    private static boolean isRockboxLayout(int layout) {
        return layout == LAYOUT_ROCKBOX_ROM || layout == LAYOUT_ROCKBOX_CLASSIC
                || layout == LAYOUT_ROCKBOX_SIDELoad;
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
        int layout = detectMtkLayout();
        if (layout != LAYOUT_STOCK) return true;
        if (runtimeLayoutHint >= 0 && runtimeLayoutHint != LAYOUT_STOCK) return true;
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
        int detected = detectMtkLayout();
        if (detected != LAYOUT_STOCK) return detected;
        if (runtimeLayoutHint >= 0) return runtimeLayoutHint;
        if (rockboxKeymap) return LAYOUT_ROCKBOX_CLASSIC;
        return LAYOUT_STOCK;
    }

    private static boolean isRockboxWheelLayout(int layout) {
        return layout == LAYOUT_ROCKBOX_ROM || layout == LAYOUT_ROCKBOX_SIDELoad
                || layout == LAYOUT_ROCKBOX_CLASSIC;
    }

    private static boolean isWheelUpByKeycode(int keyCode, boolean rockboxKeymap) {
        int layout = layoutFor(rockboxKeymap);
        if (layout == LAYOUT_ROCKBOX_SIDELoad) {
            return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88;
        }
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_DPAD_UP;
    }

    private static boolean isWheelDownByKeycode(int keyCode, boolean rockboxKeymap) {
        int layout = layoutFor(rockboxKeymap);
        if (layout == LAYOUT_ROCKBOX_SIDELoad) {
            return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87;
        }
        return keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private static boolean scancodeIsWheelUp(int scanCode) {
        return scanCode == SCAN_WHEEL_CCW || scanCode == SCAN_WHEEL_UP;
    }

    private static boolean scancodeIsWheelDown(int scanCode) {
        return scanCode == SCAN_WHEEL_CW || scanCode == SCAN_WHEEL_DOWN;
    }

    public static boolean isWheelUp(int keyCode, int scanCode, boolean rockboxKeymap) {
        if (scancodeIsWheelUp(scanCode)) return true;
        if (scancodeIsWheelDown(scanCode) || scanCode == SCAN_PREV || scanCode == SCAN_NEXT) {
            return false;
        }
        return isWheelUpByKeycode(keyCode, rockboxKeymap);
    }

    public static boolean isWheelUp(int keyCode, boolean rockboxKeymap) {
        return isWheelUp(keyCode, 0, rockboxKeymap);
    }

    public static boolean isWheelDown(int keyCode, int scanCode, boolean rockboxKeymap) {
        if (scancodeIsWheelDown(scanCode)) return true;
        if (scancodeIsWheelUp(scanCode) || scanCode == SCAN_PREV || scanCode == SCAN_NEXT) {
            return false;
        }
        return isWheelDownByKeycode(keyCode, rockboxKeymap);
    }

    public static boolean isWheelDown(int keyCode, boolean rockboxKeymap) {
        return isWheelDown(keyCode, 0, rockboxKeymap);
    }

    public static boolean isWheelKey(int keyCode, int scanCode, boolean rockboxKeymap) {
        return isWheelUp(keyCode, scanCode, rockboxKeymap)
                || isWheelDown(keyCode, scanCode, rockboxKeymap);
    }

    public static boolean isWheelKey(int keyCode, boolean rockboxKeymap) {
        return isWheelKey(keyCode, 0, rockboxKeymap);
    }

    public static int wheelDelta(int keyCode, int scanCode, boolean rockboxKeymap) {
        if (isWheelUp(keyCode, scanCode, rockboxKeymap)) return -1;
        if (isWheelDown(keyCode, scanCode, rockboxKeymap)) return 1;
        return 0;
    }

    public static int wheelDelta(int keyCode, boolean rockboxKeymap) {
        return wheelDelta(keyCode, 0, rockboxKeymap);
    }

    public static boolean isHorizontalLeft(int keyCode, boolean rockboxKeymap) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    public static boolean isHorizontalRight(int keyCode, boolean rockboxKeymap) {
        return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isMediaPreviousByKeycode(int keyCode, boolean rockboxKeymap) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88 || keyCode == 165
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == 21;
    }

    private static boolean isMediaNextByKeycode(int keyCode, boolean rockboxKeymap) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87 || keyCode == 163
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == 22;
    }

    public static boolean isMediaPrevious(int keyCode, int scanCode, boolean rockboxKeymap) {
        if (scanCode == SCAN_PREV) return true;
        if (scancodeIsWheelUp(scanCode) || scancodeIsWheelDown(scanCode) || scanCode == SCAN_NEXT) {
            return false;
        }
        if (scanCode == SCAN_WHEEL_CCW || scanCode == SCAN_WHEEL_CW) return false;
        return isMediaPreviousByKeycode(keyCode, rockboxKeymap);
    }

    public static boolean isMediaPrevious(int keyCode, boolean rockboxKeymap) {
        return isMediaPrevious(keyCode, 0, rockboxKeymap);
    }

    public static boolean isMediaNext(int keyCode, int scanCode, boolean rockboxKeymap) {
        if (scanCode == SCAN_NEXT) return true;
        if (scancodeIsWheelUp(scanCode) || scancodeIsWheelDown(scanCode) || scanCode == SCAN_PREV) {
            return false;
        }
        if (scanCode == SCAN_WHEEL_CCW || scanCode == SCAN_WHEEL_CW) return false;
        return isMediaNextByKeycode(keyCode, rockboxKeymap);
    }

    public static boolean isMediaNext(int keyCode, boolean rockboxKeymap) {
        return isMediaNext(keyCode, 0, rockboxKeymap);
    }

    public static boolean isMediaSkip(int keyCode, int scanCode, boolean rockboxKeymap) {
        return isMediaPrevious(keyCode, scanCode, rockboxKeymap)
                || isMediaNext(keyCode, scanCode, rockboxKeymap);
    }

    public static boolean isMediaSkip(int keyCode, boolean rockboxKeymap) {
        return isMediaSkip(keyCode, 0, rockboxKeymap);
    }

    public static boolean isPlayPauseKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == 86;
    }

    public static boolean isBackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == 4;
    }

    public static boolean isCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == 66;
    }
}
