package com.solar.launcher.xposed.bridge;

import com.solar.launcher.xposed.bridge.extract.MenuExtract;

import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/** Xposed MenuBuilder adapter for {@link MenuExtract}. */
final class MenuBuilderReader implements MenuExtract.MenuItemReader {

    private final Object menu;

    MenuBuilderReader(Object menu) {
        this.menu = menu;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<?> visibleItems() {
        if (menu == null) return null;
        try {
            return (List<?>) XposedHelpers.callMethod(menu, "getVisibleItems");
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String title(Object item) {
        if (item == null) return "";
        try {
            CharSequence t = (CharSequence) XposedHelpers.callMethod(item, "getTitle");
            return t != null ? t.toString() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public boolean hasSubmenu(Object item) {
        if (item == null) return false;
        try {
            Object sub = XposedHelpers.callMethod(item, "getSubMenu");
            return sub != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
