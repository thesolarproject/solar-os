package com.solar.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-07-05 — In-app config schema for Solar-managed Xposed modules (Debug detail screen).
 * Layman: defines toggles Solar shows when a hook APK has no settings app of its own.
 * Technical: maps package → boolean keys stored in module shared_prefs via {@link XposedModuleConfigStore}.
 * Reversal: delete registry rows; detail screen only offers external MODULE_SETTINGS when present.
 */
public final class XposedModuleConfigRegistry {

    /** One user-facing boolean stored under module prefs. */
    public static final class BooleanOption {
        public final String key;
        public final int labelResId;
        public final int hintResId;
        public final boolean defaultValue;

        BooleanOption(String key, int labelResId, int hintResId, boolean defaultValue) {
            this.key = key;
            this.labelResId = labelResId;
            this.hintResId = hintResId;
            this.defaultValue = defaultValue;
        }
    }

    private static final Map<String, List<BooleanOption>> OPTIONS = buildOptions();

    private XposedModuleConfigRegistry() {}

    /** Options Solar hosts for this hook package — empty when none or third-party module. */
    public static List<BooleanOption> optionsForPackage(String pkg) {
        if (pkg == null) return Collections.emptyList();
        List<BooleanOption> list = OPTIONS.get(pkg);
        return list != null ? list : Collections.<BooleanOption>emptyList();
    }

    /** True when Debug detail should show inline toggles (not only external settings). */
    public static boolean hasInlineOptions(String pkg) {
        return !optionsForPackage(pkg).isEmpty();
    }

    /** Row key for one inline config toggle on module detail screen. */
    public static String configRowKey(String packageName, String optionKey) {
        return RowKeys.XPOSED_MODULE_CONFIG_ROW_PREFIX
                + (packageName != null ? packageName : "")
                + "@"
                + (optionKey != null ? optionKey : "");
    }

    /** Parse package + option key from a config row key — null when not a config row. */
    public static ConfigRowRef parseConfigRowKey(String rowKey) {
        if (rowKey == null || !rowKey.startsWith(RowKeys.XPOSED_MODULE_CONFIG_ROW_PREFIX)) {
            return null;
        }
        String tail = rowKey.substring(RowKeys.XPOSED_MODULE_CONFIG_ROW_PREFIX.length());
        int at = tail.lastIndexOf('@');
        if (at <= 0 || at >= tail.length() - 1) return null;
        return new ConfigRowRef(tail.substring(0, at), tail.substring(at + 1));
    }

    /** Parsed package + option key from {@link #configRowKey}. */
    public static final class ConfigRowRef {
        public final String packageName;
        public final String optionKey;

        ConfigRowRef(String packageName, String optionKey) {
            this.packageName = packageName;
            this.optionKey = optionKey;
        }
    }

    private static Map<String, List<BooleanOption>> buildOptions() {
        Map<String, List<BooleanOption>> map = new HashMap<String, List<BooleanOption>>();
        // 2026-07-05 — Bridge verbose hook logging (read by future bridge XSharedPreferences consumer).
        List<BooleanOption> bridge = new ArrayList<BooleanOption>();
        bridge.add(new BooleanOption(
                "bridge_verbose_logging",
                R.string.settings_debug_xposed_cfg_bridge_verbose,
                R.string.settings_debug_xposed_cfg_bridge_verbose_hint,
                false));
        map.put("com.solar.launcher.xposed.bridge.y1", bridge);
        List<BooleanOption> bridgeY2 = new ArrayList<BooleanOption>(bridge);
        bridgeY2.add(new BooleanOption(
                "y2_usb_mass_storage_hooks",
                R.string.settings_debug_xposed_cfg_y2_ums_hooks,
                R.string.settings_debug_xposed_cfg_y2_ums_hooks_hint,
                false));
        map.put("com.solar.launcher.xposed.bridge.y2", bridgeY2);
        return map;
    }
}
