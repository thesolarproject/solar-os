package com.solar.launcher.radio;

import android.content.SharedPreferences;

import com.solar.launcher.media.MediaSuiteHost;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RadioExperimentTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = new MemPrefs();
    }

    @Test
    public void internetRadioDisabledByDefault() {
        if (RadioExperiment.isInternetRadioEnabled(prefs)) {
            throw new AssertionError("internet radio should default off");
        }
    }

    @Test
    public void fmNotProductionByDefault() {
        if (RadioExperiment.isFmProduction()) {
            throw new AssertionError("FM should be experiment-gated (not production)");
        }
        if (RadioExperiment.isFmEnabled(prefs)) {
            throw new AssertionError("FM should default off");
        }
    }

    @Test
    public void allRadioScreensBlockedWhenExperimentOff() {
        if (!RadioExperiment.isBlockedScreenState(MediaSuiteHost.STATE_RADIO_NET_BROWSE, prefs)) {
            throw new AssertionError("net browse should be blocked");
        }
        if (!RadioExperiment.isBlockedScreenState(MediaSuiteHost.STATE_RADIO_FM_BROWSE, prefs)) {
            throw new AssertionError("FM browse should be blocked when experiment off");
        }
        if (!RadioExperiment.isBlockedScreenState(MediaSuiteHost.STATE_RADIO, prefs)) {
            throw new AssertionError("radio hub should be blocked when experiment off");
        }
        if (RadioExperiment.isInAppRadioUiEnabled(prefs)) {
            throw new AssertionError("in-app radio UI should be off by default");
        }
    }

    @Test
    public void hubStaysRadioWhenExperimentOff() {
        // No redirect into FM when feature is hidden.
        int target = RadioExperiment.resolveRadioHomeTarget(MediaSuiteHost.STATE_RADIO, prefs);
        if (target != MediaSuiteHost.STATE_RADIO) {
            throw new AssertionError("hub should not open FM when experiment off");
        }
    }

    @Test
    public void inAppRadioUiEnabledWhenExperimentOn() {
        prefs.edit().putBoolean(RadioExperiment.PREF_RADIO_EXPERIMENT, true).commit();
        if (RadioExperiment.isBlockedScreenState(MediaSuiteHost.STATE_RADIO_FM_BROWSE, prefs)) {
            throw new AssertionError("FM browse should be allowed when experiment on");
        }
        if (!RadioExperiment.isInAppRadioUiEnabled(prefs)) {
            throw new AssertionError("in-app radio UI should be on");
        }
    }

    @Test
    public void enabledWhenPrefOn() {
        prefs.edit().putBoolean(RadioExperiment.PREF_RADIO_EXPERIMENT, true).commit();
        if (!RadioExperiment.isInternetRadioEnabled(prefs)) {
            throw new AssertionError("internet radio should be on");
        }
        if (RadioExperiment.isBlockedScreenState(MediaSuiteHost.STATE_RADIO_NET_BROWSE, prefs)) {
            throw new AssertionError("net browse should be allowed");
        }
    }

    private static final class MemPrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<String, Object>();

        @Override public Map<String, ?> getAll() { return map; }
        @Override public String getString(String key, String def) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : def;
        }
        @Override public int getInt(String key, int def) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : def;
        }
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
