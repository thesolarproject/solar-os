package com.solar.launcher.xposed.themefont;

import android.graphics.Paint;
import android.graphics.Typeface;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hooks Typeface and Paint so stock apps pick up Solar's published theme font.
 */
final class TypefaceHooks {

    private static final String TAG = "SolarThemeFont";

    private TypefaceHooks() {}

    static void install(ClassLoader classLoader) {
        try {
            hookCreateStringStyle(classLoader);
            hookCreateTypefaceStyle(classLoader);
            hookDefaultFromStyle(classLoader);
            hookPaintSetTypeface(classLoader);
            log("Typeface hooks installed");
        } catch (Throwable t) {
            log("hook install failed: " + t);
        }
    }

    private static void hookCreateStringStyle(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(Typeface.class, "create", String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(FontSidecar.LOADING.get())) return;
                        Typeface base = FontSidecar.getCustomBase();
                        if (base == null) return;
                        int style = (Integer) param.args[1];
                        param.setResult(Typeface.create(base, style));
                    }
                });
    }

    private static void hookCreateTypefaceStyle(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(Typeface.class, "create", Typeface.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(FontSidecar.LOADING.get())) return;
                        Typeface base = FontSidecar.getCustomBase();
                        if (base == null) return;
                        Typeface family = (Typeface) param.args[0];
                        if (family == null || isStockDefaultFamily(family)) {
                            int style = (Integer) param.args[1];
                            param.setResult(Typeface.create(base, style));
                        }
                    }
                });
    }

    private static void hookDefaultFromStyle(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(Typeface.class, "defaultFromStyle", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(FontSidecar.LOADING.get())) return;
                        Typeface base = FontSidecar.getCustomBase();
                        if (base == null) return;
                        int style = (Integer) param.args[0];
                        param.setResult(Typeface.create(base, style));
                    }
                });
    }

    /** Canvas / SystemUI paths that set Typeface directly on Paint. */
    private static void hookPaintSetTypeface(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(Paint.class, "setTypeface", Typeface.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(FontSidecar.LOADING.get())) return;
                        Typeface base = FontSidecar.getCustomBase();
                        if (base == null) return;
                        Typeface incoming = (Typeface) param.args[0];
                        if (incoming == null || isStockDefaultFamily(incoming)) {
                            param.args[0] = base;
                        }
                    }
                });
    }

    /** Remap sans/serif/monospace defaults — leave explicit custom faces alone. */
    private static boolean isStockDefaultFamily(Typeface face) {
        if (face == null) return true;
        if (face == Typeface.DEFAULT || face == Typeface.DEFAULT_BOLD) return true;
        if (face == Typeface.SANS_SERIF || face == Typeface.SERIF || face == Typeface.MONOSPACE) {
            return true;
        }
        return false;
    }

    static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }
}
