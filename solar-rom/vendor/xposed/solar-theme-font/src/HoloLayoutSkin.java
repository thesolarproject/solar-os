package com.solar.launcher.xposed.themefont;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.callbacks.XC_LayoutInflated;

/**
 * Post-inflate Holo dialog/menu layout tweaks when Solar overlay replacement fail-opens.
 * Colors come from /.solar/theme-colors.json; fonts reuse the system-font sidecar.
 */
final class HoloLayoutSkin {

    private static volatile boolean installed;

    private HoloLayoutSkin() {}

    /** Register system-wide layout hooks once at zygote init. */
    static void installAtZygote() {
        if (installed) return;
        synchronized (HoloLayoutSkin.class) {
            if (installed) return;
            try {
                Class<?> xres = Class.forName("android.content.res.XResources");
                installLayoutHook(xres, "alert_dialog_holo");
                installLayoutHook(xres, "select_dialog_item_holo");
                installLayoutHook(xres, "select_dialog_singlechoice_holo");
                installLayoutHook(xres, "popup_menu_item_layout");
                installed = true;
                TypefaceHooks.log("HoloLayoutSkin installed");
            } catch (Throwable t) {
                TypefaceHooks.log("HoloLayoutSkin skip: " + t.getClass().getSimpleName());
            }
        }
    }

    private static void installLayoutHook(Class<?> xresClass, final String layoutName) {
        try {
            java.lang.reflect.Method hookMethod = xresClass.getMethod(
                    "hookSystemWideLayout", String.class, String.class, String.class,
                    XC_LayoutInflated.class);
            hookMethod.invoke(null, "android", "layout", layoutName, new XC_LayoutInflated() {
                @Override
                protected void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable {
                    if (liparam == null || liparam.view == null) return;
                    applyThemeToTree(liparam.view);
                }
            });
        } catch (Throwable t) {
            TypefaceHooks.log("hook " + layoutName + " skip: " + t.getClass().getSimpleName());
        }
    }

    /** Walk inflated Holo dialog/menu tree and apply Solar panel/text colors + font. */
    private static void applyThemeToTree(View root) {
        if (!ThemeColorSidecar.hasColors() && FontSidecar.getCustomBase() == null) return;
        applyThemeToView(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeToTree(group.getChildAt(i));
            }
        }
    }

    private static void applyThemeToView(View view) {
        if (view == null) return;
        int panel = ThemeColorSidecar.panelColor();
        if (panel != 0 && view.getBackground() == null) {
            view.setBackgroundDrawable(new ColorDrawable(panel));
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int text = ThemeColorSidecar.textColor();
            if (text != 0) tv.setTextColor(text);
            Typeface custom = FontSidecar.getCustomBase();
            if (custom != null) tv.setTypeface(custom);
        }
    }
}
