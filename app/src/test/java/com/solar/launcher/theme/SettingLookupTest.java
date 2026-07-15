package com.solar.launcher.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SettingLookupTest {

    @Test
    public void artworkPerspectiveLabelMaps() {
        assertEquals("artwork_perspective", SettingLookup.prefKeyForLabel("Artwork Perspective"));
        assertEquals("artwork_perspective", SettingLookup.prefKeyForLabel("artwork perspective"));
    }

    @Test
    public void normalizeStripsParenthetical() {
        assertEquals("full width menus", SettingLookup.normalize("Full Width Menus (experimental)"));
    }

    @Test
    public void unknownLabelReturnsNull() {
        assertNull(SettingLookup.prefKeyForLabel("Not A Real Setting"));
    }

    /** 2026-07-11 — Menu item padding label maps for enable/disable solarConfig forms. */
    @Test
    public void menuItemPaddingLabelMaps() {
        assertEquals("menu_item_padding", SettingLookup.prefKeyForLabel("Menu Item Padding"));
        assertEquals("menu_item_padding", SettingLookup.prefKeyForLabel("menu item padding"));
    }

    /** 2026-07-11 — Show Now Playing Info ↔ show_now_playing_info / settingsShow_Now_Playing_Info. */
    @Test
    public void showNowPlayingInfoLabelMaps() {
        assertEquals("show_now_playing_info", SettingLookup.prefKeyForLabel("Show Now Playing Info"));
        assertEquals("show_now_playing_info", SettingLookup.prefKeyForLabel("show now playing info"));
    }
}
