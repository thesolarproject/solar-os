package com.solar.launcher.xposed.themefont;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Match SystemUI status bar fill + clock text to Solar theme skin sidecar.
 * Layman: the top clock strip picks up the same colors as Solar's status bar.
 */
final class SystemUiSkin {

    private static volatile boolean installed;

    /** Y1 API 17 and Y2 API 19 class names — fail-open when a name is absent on the build. */
    private static final String[] STATUS_BAR_VIEW_CLASSES = {
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.statusbar.StatusBarView",
            "com.android.systemui.statusbar.phone.StatusBarWindowView"
    };

    private SystemUiSkin() {}

    /** Only runs in com.android.systemui when skin sidecar is enabled. */
    static void install(LoadPackageParam lpparam) {
        if (lpparam == null || !"com.android.systemui".equals(lpparam.packageName)) return;
        if (installed) return;
        synchronized (SystemUiSkin.class) {
            if (installed) return;
            int hooked = 0;
            for (String className : STATUS_BAR_VIEW_CLASSES) {
                hooked += hookStatusBarViewClass(lpparam.classLoader, className);
            }
            if (hooked == 0) {
                hooked += hookGenericViewAttach(lpparam.classLoader);
            }
            installed = hooked > 0;
            TypefaceHooks.log("SystemUiSkin hooks=" + hooked + " sdk=" + Build.VERSION.SDK_INT);
        }
    }

    /** onFinishInflate on known status bar roots — API 17 vs 19 share most names on MTK. */
    private static int hookStatusBarViewClass(ClassLoader cl, String className) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, cl);
            XposedHelpers.findAndHookMethod(cls, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.thisObject instanceof View) {
                            applyStatusBarSkin((View) param.thisObject);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Fallback — skin views whose class name contains StatusBar when attached. */
    private static int hookGenericViewAttach(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View view = (View) param.thisObject;
                        String name = view.getClass().getName();
                        if (name != null && name.contains("StatusBar") && !name.contains("Panel")) {
                            applyStatusBarSkin(view);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void applyStatusBarSkin(View root) {
        if (root == null || !ThemeSkinSidecar.isEnabled()) return;
        ThemeSkinSidecar.SkinData skin = ThemeSkinSidecar.get();
        if (skin == null || !skin.enabled) return;
        if (skin.statusBarColor != 0) {
            root.setBackgroundDrawable(new ColorDrawable(skin.statusBarColor));
        }
        applyStatusBarText(root, skin);
    }

    /** Clock and icons — theme statusBarTextColor + published system font. */
    private static void applyStatusBarText(View root, ThemeSkinSidecar.SkinData skin) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (skin.statusBarTextColor != 0) {
                tv.setTextColor(skin.statusBarTextColor);
            }
            Typeface custom = FontSidecar.getCustomBase();
            if (custom != null) tv.setTypeface(custom);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyStatusBarText(group.getChildAt(i), skin);
            }
        }
    }
}
