package com.solar.launcher.xposed.themefont;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.StateSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — Paints Activity window background + list selectors from theme skin sidecars.
 * Layman: Settings and similar apps get the user's wallpaper, highlight strip, and readable text.
 */
final class ActivitySkin {

    private static volatile boolean hooksInstalled;

    private ActivitySkin() {}

    /** Install per-package hooks when skin sidecar is enabled. */
    static void install(String packageName, ClassLoader classLoader) {
        if (!SkinPackagePolicy.shouldSkin(packageName)) return;
        if (hooksInstalled) return;
        synchronized (ActivitySkin.class) {
            if (hooksInstalled) return;
            try {
                hookActivityLifecycle(classLoader);
                hookAbsListViewConstructors(classLoader);
                hooksInstalled = true;
                TypefaceHooks.log("ActivitySkin installed for " + packageName);
            } catch (Throwable t) {
                TypefaceHooks.log("ActivitySkin skip: " + t.getClass().getSimpleName());
            }
        }
    }

    /** Window background after create/resume — catches late content inflation. */
    private static void hookActivityLifecycle(ClassLoader cl) {
        XposedHelpers.findAndHookMethod(Activity.class, "performCreate", Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            applyToActivity((Activity) param.thisObject);
                        } catch (Throwable ignored) {}
                    }
                });
        XposedHelpers.findAndHookMethod(Activity.class, "performResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    applyToActivity((Activity) param.thisObject);
                } catch (Throwable ignored) {}
            }
        });
    }

    /** ListView/GridView row highlight — themed bitmap or solid fill. */
    private static void hookAbsListViewConstructors(ClassLoader cl) {
        XC_MethodHook afterCtor = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof AbsListView) {
                        skinListView((AbsListView) param.thisObject);
                    }
                } catch (Throwable ignored) {}
            }
        };
        try {
            XposedBridge.hookAllConstructors(AbsListView.class, afterCtor);
        } catch (Throwable t) {
            TypefaceHooks.log("AbsListView ctor hook skip: " + t.getClass().getSimpleName());
        }
    }

    private static void applyToActivity(Activity activity) {
        if (activity == null || !ThemeSkinSidecar.isEnabled()) return;
        ThemeSkinSidecar.SkinData skin = ThemeSkinSidecar.get();
        if (skin == null || !skin.enabled) return;
        applyWindowBackground(activity, skin);
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor != null) {
            applyTextToTree(decor, skin);
            skinExistingListViews(decor);
        }
    }

    /** Wallpaper bitmap when published; else solid theme background color. */
    private static void applyWindowBackground(Activity activity, ThemeSkinSidecar.SkinData skin) {
        if (activity.getWindow() == null) return;
        android.graphics.Bitmap wall = ThemeSkinSidecar.wallpaperBitmap();
        if (wall != null && !wall.isRecycled()) {
            activity.getWindow().setBackgroundDrawable(
                    new BitmapDrawable(activity.getResources(), wall));
            return;
        }
        if (skin.backgroundColor != 0) {
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(skin.backgroundColor));
        }
    }

    /** Themed selector on standard Holo list rows — transparent default so wallpaper shows. */
    private static void skinListView(AbsListView list) {
        if (list == null || !ThemeSkinSidecar.isEnabled()) return;
        ThemeSkinSidecar.SkinData skin = ThemeSkinSidecar.get();
        if (skin == null || !skin.enabled) return;
        Drawable selected = buildSelectionDrawable(list, skin);
        if (selected == null) return;
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_pressed}, selected);
        selector.addState(new int[]{android.R.attr.state_focused}, selected);
        selector.addState(new int[]{android.R.attr.state_selected}, selected);
        selector.addState(new int[]{android.R.attr.state_activated}, selected);
        selector.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
        list.setSelector(selector);
        list.setCacheColorHint(0);
    }

    private static Drawable buildSelectionDrawable(View view, ThemeSkinSidecar.SkinData skin) {
        android.graphics.Bitmap bmp = ThemeSkinSidecar.selectionBitmap();
        if (bmp != null && !bmp.isRecycled()) {
            return new BitmapDrawable(view.getResources(), bmp);
        }
        if (skin.rowSelectionFillColor != 0) {
            return new ColorDrawable(skin.rowSelectionFillColor);
        }
        return null;
    }

    /** Walk decor for ListViews created before our ctor hook ran. */
    private static void skinExistingListViews(View root) {
        if (root instanceof AbsListView) {
            skinListView((AbsListView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                skinExistingListViews(group.getChildAt(i));
            }
        }
    }

    /** Conservative text recolor — only stock Holo defaults, plus custom font. */
    private static void applyTextToTree(View root, ThemeSkinSidecar.SkinData skin) {
        applyTextToView(root, skin);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextToTree(group.getChildAt(i), skin);
            }
        }
    }

    private static void applyTextToView(View view, ThemeSkinSidecar.SkinData skin) {
        if (!(view instanceof TextView)) return;
        TextView tv = (TextView) view;
        Typeface custom = FontSidecar.getCustomBase();
        if (custom != null) tv.setTypeface(custom);
        int current = tv.getCurrentTextColor();
        if (skin.textPrimary != 0 && isStockDefaultTextColor(current)) {
            tv.setTextColor(skin.textPrimary);
        }
        if (skin.selectedTextColor != 0) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_selected},
                    new int[]{android.R.attr.state_pressed},
                    new int[]{android.R.attr.state_focused},
                    new int[]{}
            };
            int normal = skin.textPrimary != 0 ? skin.textPrimary : current;
            int[] colors = new int[]{
                    skin.selectedTextColor,
                    skin.selectedTextColor,
                    skin.selectedTextColor,
                    normal
            };
            tv.setTextColor(new ColorStateList(states, colors));
        }
    }

    /** Known Holo/Material default text colors — do not override app-specific tints. */
    private static boolean isStockDefaultTextColor(int color) {
        int rgb = color | 0xFF000000;
        return rgb == 0xFF000000
                || rgb == 0xFFFFFFFF
                || rgb == 0xFF33B5E5
                || rgb == 0xFFCCCCCC
                || rgb == 0xFF999999
                || rgb == 0xFF666666;
    }
}
