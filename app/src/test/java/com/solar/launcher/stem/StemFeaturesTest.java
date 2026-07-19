package com.solar.launcher.stem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Opt-in gates for Stem / Mix / solo menus.
 * 2026-07-19
 */
public class StemFeaturesTest {

    /** Demo key alone does not unlock cloud menus. 2026-07-19 */
    @Test
    public void demoNotOptedIn() {
        MemPrefs prefs = new MemPrefs();
        assertFalse(StemFeatures.isOptedIn(prefs));
        assertFalse(StemFeatures.showCloudStemMenus(prefs));
        assertFalse(StemFeatures.showSoloMenu(prefs, false));
        assertTrue(StemFeatures.showSoloMenu(prefs, true));
    }

    /** User key unlocks Stem / Mix / solo. 2026-07-19 */
    @Test
    public void userKeyOptsIn() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.saveUserKey(prefs, "user-key-abcdefgh");
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertTrue(StemFeatures.showCloudStemMenus(prefs));
        assertTrue(StemFeatures.showSoloMenu(prefs, false));
    }

    /** Minimal in-memory prefs for JVM tests. 2026-07-19 */
    private static final class MemPrefs implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return map;
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                @Override
                public Editor putString(String key, String value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    map.remove(key);
                    return this;
                }

                @Override
                public Editor clear() {
                    map.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    return true;
                }

                @Override
                public void apply() {}
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
    }
}
