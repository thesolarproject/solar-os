package com.solar.launcher.theme;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 2026-07-18 — Menu item padding default ON; theme solarConfig forms; no sticky global.
 */
public class MenuItemPaddingThemeTest {

    @Before
    public void setUp() throws Exception {
        ThemeManager.availableThemes.clear();
        JSONObject root = new JSONObject();
        root.put("menuConfig", new JSONObject());
        root.put("itemConfig", new JSONObject());
        root.put("homePageConfig", new JSONObject());
        ThemeManager.availableThemes.add(
                new ThemeManager.ThemeEntry("/tmp", "TestTheme", "TestTheme", root));
        ThemeManager.setThemeIndex(0);
    }

    @After
    public void tearDown() {
        ThemeManager.availableThemes.clear();
    }

    @Test
    public void defaultOnWhenUnset() {
        if (ThemeManager.themeDefaultMenuItemPadding() != null) {
            throw new AssertionError("unset theme default");
        }
        if (!ThemeManager.isMenuItemPaddingEnabled(null, false)) {
            throw new AssertionError("effective default on");
        }
    }

    @Test
    public void settingsMenuItemPaddingFalseFlushes() throws Exception {
        ThemeManager.ThemeEntry t = ThemeManager.availableThemes.get(0);
        t.root.put("solarConfig",
                new JSONObject().put(ThemeManager.SOLAR_MENU_ITEM_PADDING, false));
        if (ThemeManager.isMenuItemPaddingEnabled(null, false)) {
            throw new AssertionError("settingsMenu_Item_Padding:false");
        }
    }

    @Test
    public void enableMenuItemPaddingOn() throws Exception {
        ThemeManager.ThemeEntry t = ThemeManager.availableThemes.get(0);
        t.root.put("solarConfig", new JSONObject().put("enableMenu_Item_Padding", "on"));
        if (!Boolean.TRUE.equals(ThemeManager.themeDefaultMenuItemPadding())) {
            throw new AssertionError("enable on");
        }
        if (!ThemeManager.isMenuItemPaddingEnabled(null, false)) {
            throw new AssertionError("effective on");
        }
    }

    @Test
    public void disableMenuItemPadding() throws Exception {
        ThemeManager.ThemeEntry t = ThemeManager.availableThemes.get(0);
        t.root.put("solarConfig", new JSONObject().put("disableMenu_Item_Padding", true));
        if (ThemeManager.isMenuItemPaddingEnabled(null, false)) {
            throw new AssertionError("disable");
        }
    }

    @Test
    public void settingMenuItemPaddingAlias() throws Exception {
        ThemeManager.ThemeEntry t = ThemeManager.availableThemes.get(0);
        t.root.put("solarConfig",
                new JSONObject().put(ThemeManager.SOLAR_MENU_ITEM_PADDING_ALIAS, "true"));
        if (!Boolean.TRUE.equals(ThemeManager.themeDefaultMenuItemPadding())) {
            throw new AssertionError("alias true");
        }
    }

    @Test
    public void prefKeyIsPerThemeFolder() {
        if (!"menu_item_padding.Cupertino".equals(ThemeManager.menuItemPaddingPrefKey("Cupertino"))) {
            throw new AssertionError("folder key");
        }
    }
}
