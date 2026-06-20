package com.solar.launcher;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeMenuConfigTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new MemPrefs();
    }

    @Test
    public void defaultOrder_matchesY1StockLayout() {
        List<HomeMenuConfig.Entry> visible = HomeMenuConfig.loadVisible(prefs);
        if (visible.size() != 7) throw new AssertionError("default size " + visible.size());
        if (!HomeMenuConfig.ID_NOW_PLAYING.equals(visible.get(0).id)) {
            throw new AssertionError("now playing position");
        }
        if (!HomeMenuConfig.ID_MUSIC.equals(visible.get(1).id)) {
            throw new AssertionError("music position");
        }
        if (!HomeMenuConfig.ID_BLUETOOTH.equals(visible.get(2).id)) {
            throw new AssertionError("bluetooth position");
        }
        if (!HomeMenuConfig.ID_SETTINGS.equals(visible.get(3).id)) {
            throw new AssertionError("settings position");
        }
    }

    @Test
    public void showUnimplementedRestoresRoadmapShortcuts() {
        prefs.edit().putBoolean(DebugPrefs.PREF_SHOW_UNIMPLEMENTED, true).commit();
        List<HomeMenuConfig.Entry> visible = HomeMenuConfig.loadVisible(prefs);
        if (visible.size() != 10) throw new AssertionError("unimplemented on size " + visible.size());
        if (!HomeMenuConfig.ID_VIDEOS.equals(visible.get(2).id)) {
            throw new AssertionError("videos position");
        }
    }

    @Test
    public void settingsForcedWhenMissing() {
        HomeMenuConfig.saveOrder(prefs, Arrays.asList(
                HomeMenuConfig.ID_MUSIC, HomeMenuConfig.ID_SOULSEEK));
        List<String> ids = HomeMenuConfig.loadVisibleIds(prefs);
        if (!ids.contains(HomeMenuConfig.ID_SETTINGS)) {
            throw new AssertionError("settings missing");
        }
    }

    @Test
    public void unknownIdsStripped() {
        HomeMenuConfig.saveOrder(prefs, Arrays.asList(
                HomeMenuConfig.ID_MUSIC, "bogus", HomeMenuConfig.ID_SETTINGS));
        List<String> ids = HomeMenuConfig.loadVisibleIds(prefs);
        if (ids.contains("bogus")) throw new AssertionError("bogus kept");
        if (ids.size() != 2) throw new AssertionError("size " + ids.size());
    }

    @Test
    public void migrateGetThemesToThemes() {
        HomeMenuConfig.saveOrder(prefs, Arrays.asList(
                HomeMenuConfig.ID_MUSIC, HomeMenuConfig.ID_GET_THEMES));
        List<String> ids = HomeMenuConfig.loadHomeOrderIds(prefs);
        if (!ids.contains(HomeMenuConfig.ID_THEMES)) {
            throw new AssertionError("themes migration");
        }
    }

    @Test
    public void hideFromHomeMovesToMore() {
        HomeMenuConfig.hideFromHome(prefs, HomeMenuConfig.ID_FM);
        if (HomeMenuConfig.isVisible(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm still on home");
        }
        if (!HomeMenuConfig.isInMore(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm not in more");
        }
        if (!HomeMenuConfig.isShortcutEnabled(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm should stay enabled");
        }
    }

    @Test
    public void moreTileHiddenWhenMoreOrderEmpty() {
        HomeMenuConfig.setMoreEnabled(prefs, true);
        HomeMenuConfig.saveMoreOrder(prefs, new ArrayList<String>());
        if (HomeMenuConfig.shouldShowMoreTile(prefs, false, false)) {
            throw new AssertionError("more tile should hide when order empty");
        }
    }

    @Test
    public void moreAllEnabledIncludesGatedItems() {
        HomeMenuConfig.saveMoreOrder(prefs, Arrays.asList(
                HomeMenuConfig.ID_PODCASTS, HomeMenuConfig.ID_PC_UPLOAD));
        List<HomeMenuConfig.Entry> more = HomeMenuConfig.loadMoreAllEnabled(prefs);
        if (more.size() != 2) throw new AssertionError("expected 2, got " + more.size());
    }

    @Test
    public void moveLastItemHomeDisablesMoreTile() {
        HomeMenuConfig.hideFromHome(prefs, HomeMenuConfig.ID_FM);
        HomeMenuConfig.setVisible(prefs, HomeMenuConfig.ID_FM, true);
        if (HomeMenuConfig.isMoreEnabled(prefs)) {
            throw new AssertionError("more should auto-disable when order empty");
        }
    }

    @Test
    public void disableShortcutRemovesFromHomeAndMore() {
        HomeMenuConfig.setShortcutEnabled(prefs, HomeMenuConfig.ID_FM, false);
        if (HomeMenuConfig.isVisible(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm still on home");
        }
        if (HomeMenuConfig.isInMore(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm still in more");
        }
        if (HomeMenuConfig.isShortcutEnabled(prefs, HomeMenuConfig.ID_FM)) {
            throw new AssertionError("fm should be off");
        }
    }

    @Test
    public void optInDefaultsOff() {
        List<String> ids = HomeMenuConfig.loadHomeOrderIds(prefs);
        if (ids.contains(HomeMenuConfig.ID_APPS)) throw new AssertionError("apps default");
        if (ids.contains(HomeMenuConfig.ID_THEMES)) throw new AssertionError("themes default");
        if (!ids.contains(HomeMenuConfig.ID_VIDEOS)) throw new AssertionError("videos default on");
        if (!ids.contains(HomeMenuConfig.ID_PHOTOS)) throw new AssertionError("photos default on");
        HomeMenuConfig.setVisible(prefs, HomeMenuConfig.ID_THEMES, true);
        if (!HomeMenuConfig.isVisible(prefs, HomeMenuConfig.ID_THEMES)) {
            throw new AssertionError("themes toggle");
        }
    }

    @Test
    public void offlineFiltersLocalNetworkItemsOnly() {
        List<HomeMenuConfig.Entry> offline = HomeMenuConfig.loadVisibleForDisplay(prefs, false, false);
        for (HomeMenuConfig.Entry e : offline) {
            if (ConnectivityHelper.itemNeedsLocalNetwork(e.id)) {
                throw new AssertionError("local-network item shown offline: " + e.id);
            }
        }
    }

    @Test
    public void offlinePodcastsVisibleWhenSaved() {
        List<HomeMenuConfig.Entry> offline = HomeMenuConfig.loadVisibleForDisplay(
                prefs, false, false, true);
        boolean podcasts = false;
        for (HomeMenuConfig.Entry e : offline) {
            if (HomeMenuConfig.ID_PODCASTS.equals(e.id)) podcasts = true;
        }
        if (!podcasts) throw new AssertionError("podcasts missing with saved content");
    }

    @Test
    public void loadMoreVisible_includesReachWhenOffline() {
        HomeMenuConfig.saveMoreOrder(prefs, Arrays.asList(
                HomeMenuConfig.ID_SOULSEEK, HomeMenuConfig.ID_MUSIC));
        List<HomeMenuConfig.Entry> more = HomeMenuConfig.loadMoreVisible(prefs, false, true);
        boolean reach = false;
        boolean music = false;
        for (HomeMenuConfig.Entry e : more) {
            if (HomeMenuConfig.ID_SOULSEEK.equals(e.id)) reach = true;
            if (HomeMenuConfig.ID_MUSIC.equals(e.id)) music = true;
        }
        if (!reach) throw new AssertionError("expected reach to show offline");
        if (!music) throw new AssertionError("expected music to show");
    }

    @Test
    public void moreTileHiddenWhenAllMoreItemsFiltered() {
        HomeMenuConfig.setMoreEnabled(prefs, true);
        HomeMenuConfig.saveMoreOrder(prefs, Arrays.asList(HomeMenuConfig.ID_PC_UPLOAD));
        if (HomeMenuConfig.shouldShowMoreTile(prefs, false, false)) {
            throw new AssertionError("more tile should hide when filtered list empty");
        }
    }

    @Test
    public void lanWithoutInternetShowsPcUploadAndInternetItems() {
        List<HomeMenuConfig.Entry> onLan = HomeMenuConfig.loadVisibleForDisplay(prefs, false, true);
        boolean pc = false;
        boolean reach = false;
        for (HomeMenuConfig.Entry e : onLan) {
            if (HomeMenuConfig.ID_PC_UPLOAD.equals(e.id)) pc = true;
            if (HomeMenuConfig.ID_SOULSEEK.equals(e.id)) reach = true;
        }
        if (!pc) throw new AssertionError("pc upload missing on lan");
        if (!reach) throw new AssertionError("reach missing on lan");
    }

    @Test
    public void editorHomeEntries_ignoresConnectivity() {
        prefs.edit().putBoolean(DebugPrefs.PREF_SHOW_UNIMPLEMENTED, true).commit();
        HomeMenuConfig.hideFromHome(prefs, HomeMenuConfig.ID_FM);
        List<HomeMenuConfig.Entry> editor = HomeMenuConfig.loadEditorHomeEntries(prefs);
        List<HomeMenuConfig.Entry> offlineLive = HomeMenuConfig.loadVisibleForDisplay(prefs, false, false);
        if (editor.size() <= offlineLive.size()) {
            throw new AssertionError("editor should include home-order items regardless of connectivity");
        }
        boolean fmInMoreEditor = false;
        for (HomeMenuConfig.Entry e : HomeMenuConfig.loadEditorMoreEntries(prefs)) {
            if (HomeMenuConfig.ID_FM.equals(e.id)) fmInMoreEditor = true;
        }
        if (!fmInMoreEditor) throw new AssertionError("fm should appear in more editor list");
    }

    @Test
    public void toggleAndReorderRoundTrip() {
        HomeMenuConfig.setVisible(prefs, HomeMenuConfig.ID_THEMES, true);
        if (!HomeMenuConfig.isVisible(prefs, HomeMenuConfig.ID_THEMES)) {
            throw new AssertionError("themes not visible");
        }
        List<String> ids = new ArrayList<String>(HomeMenuConfig.loadHomeOrderIds(prefs));
        int soulseek = ids.indexOf(HomeMenuConfig.ID_SOULSEEK);
        int themes = ids.indexOf(HomeMenuConfig.ID_THEMES);
        if (soulseek < 0 || themes < 0) throw new AssertionError("missing ids");
        HomeMenuConfig.move(prefs, themes, soulseek);
        ids = HomeMenuConfig.loadHomeOrderIds(prefs);
        if (ids.indexOf(HomeMenuConfig.ID_THEMES) != soulseek) {
            throw new AssertionError("reorder failed");
        }
        HomeMenuConfig.setVisible(prefs, HomeMenuConfig.ID_SETTINGS, false);
        if (!HomeMenuConfig.isVisible(prefs, HomeMenuConfig.ID_SETTINGS)) {
            throw new AssertionError("settings must stay visible");
        }
    }

    private static final class MemPrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<String, Object>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String def) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : def;
        }
        @Override public int getInt(String key, int def) { return def; }
        @Override public long getLong(String key, long def) { return def; }
        @Override public float getFloat(String key, float def) { return def; }
        @Override public boolean getBoolean(String key, boolean def) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : def;
        }
        @Override public Set<String> getStringSet(String key, Set<String> def) { return def; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() {
            return new Editor() {
                @Override public Editor putString(String key, String value) { map.put(key, value); return this; }
                @Override public Editor putInt(String key, int value) { map.put(key, value); return this; }
                @Override public Editor putLong(String key, long value) { map.put(key, value); return this; }
                @Override public Editor putFloat(String key, float value) { map.put(key, value); return this; }
                @Override public Editor putBoolean(String key, boolean value) { map.put(key, value); return this; }
                @Override public Editor putStringSet(String key, Set<String> values) { return this; }
                @Override public Editor remove(String key) { map.remove(key); return this; }
                @Override public Editor clear() { map.clear(); return this; }
                @Override public boolean commit() { return true; }
                @Override public void apply() { commit(); }
            };
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    }
}
