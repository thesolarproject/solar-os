package com.solar.launcher;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 2026-07-05 — Installed hook APKs for Debug UI + ensurer; registry enriches known Solar packages.
 * Layman: lists every Xposed module on the device, not a fixed shortlist.
 * Technical: discovery-first merge; registry supplies labels/warnings for Solar production hooks.
 * Reversal: revert to registry-first allModules(); uninstalled Solar rows reappear in UI.
 */
public final class XposedModuleCatalog {

    /** One toggle row in Debug → Xposed modules. */
    public static final class ModuleRow {
        public final String packageName;
        public final CharSequence label;
        public final CharSequence description;
        public final boolean required;
        public final boolean forceDisabled;
        public final int disableWarningResId;
        /** Solar-shipped hook (com.solar.launcher.xposed.*). */
        public final boolean solarManaged;
        /** Module APK exposes MODULE_SETTINGS or launch activity. */
        public final boolean hasExternalSettings;
        /** Solar Debug hosts inline boolean toggles for this package. */
        public final boolean hasInlineConfig;

        ModuleRow(String packageName, CharSequence label, CharSequence description,
                boolean required, boolean forceDisabled, int disableWarningResId,
                boolean solarManaged, boolean hasExternalSettings, boolean hasInlineConfig) {
            this.packageName = packageName;
            this.label = label;
            this.description = description;
            this.required = required;
            this.forceDisabled = forceDisabled;
            this.disableWarningResId = disableWarningResId;
            this.solarManaged = solarManaged;
            this.hasExternalSettings = hasExternalSettings;
            this.hasInlineConfig = hasInlineConfig;
        }

        /** True when detail screen can show config (inline toggles or external settings app). */
        public boolean hasConfigurableOptions() {
            return hasInlineConfig || hasExternalSettings;
        }
    }

    private static final String SOLAR_XPOSED_PREFIX = "com.solar.launcher.xposed.";

    private XposedModuleCatalog() {}

    /** True for Solar ROM hook packages (bridge, theme font, future Solar modules). */
    public static boolean isSolarManagedPackage(String pkg) {
        return pkg != null && pkg.startsWith(SOLAR_XPOSED_PREFIX);
    }

    /** Production Solar hooks — required when installed; lab PowerMenuTest excluded. */
    public static boolean isRequiredProductionPackage(String pkg) {
        if (!isSolarManagedPackage(pkg)) return false;
        return !XposedModuleRegistry.isForceDisabledLabPackage(pkg);
    }

    /** Installed hooks only — sorted by label; registry enriches known Solar packages. */
    public static List<ModuleRow> allRows(Context ctx) {
        if (ctx == null) return Collections.emptyList();
        // 2026-07-06 — Always surface production hooks when baked APK exists (PM may hide them).
        Set<String> merged = new LinkedHashSet<String>();
        merged.addAll(XposedModuleDiscovery.listInstalledHookPackages(ctx));
        for (String pkg : XposedModuleRegistry.requiredModulePackages()) {
            if (XposedModuleDiscovery.isPackageOrSystemApkPresent(pkg)) {
                merged.add(pkg);
            }
        }
        List<ModuleRow> out = new ArrayList<ModuleRow>(merged.size());
        for (String pkg : merged) {
            out.add(rowForPackage(ctx, pkg));
        }
        Collections.sort(out, new Comparator<ModuleRow>() {
            @Override
            public int compare(ModuleRow a, ModuleRow b) {
                return labelText(a).compareToIgnoreCase(labelText(b));
            }
        });
        return Collections.unmodifiableList(out);
    }

    /** Packages ensurer must keep enabled unless user override file blocks repair. */
    public static List<String> requiredPackages(Context ctx) {
        List<String> out = new ArrayList<String>();
        for (ModuleRow row : allRows(ctx)) {
            if (row.required && !row.forceDisabled) {
                out.add(row.packageName);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Lookup one row for disable-warning copy and toggle rules. */
    public static ModuleRow findRow(Context ctx, String pkg) {
        if (pkg == null || ctx == null) return null;
        List<String> installed = XposedModuleDiscovery.listInstalledHookPackages(ctx);
        for (int i = 0; i < installed.size(); i++) {
            if (pkg.equals(installed.get(i))) {
                return rowForPackage(ctx, pkg);
            }
        }
        return null;
    }

    private static ModuleRow rowForPackage(Context ctx, String pkg) {
        XposedModuleRegistry.Entry reg = XposedModuleRegistry.findByPackage(pkg);
        CharSequence label = XposedModuleDiscovery.labelForPackage(ctx, pkg);
        CharSequence description = XposedModuleDiscovery.descriptionForPackage(ctx, pkg);
        boolean solar = isSolarManagedPackage(pkg);
        boolean lab = XposedModuleRegistry.isForceDisabledLabPackage(pkg);
        boolean required = reg != null ? reg.required : isRequiredProductionPackage(pkg);
        boolean forceDisabled = reg != null ? reg.forceDisabled : lab;
        int warn = reg != null ? reg.disableWarningResId : 0;
        if (warn == 0 && solar && !lab) {
            warn = R.string.settings_debug_xposed_solar_generic_disable_warning;
        }
        boolean external = XposedModuleSettings.hasSettingsActivity(ctx, pkg);
        boolean inline = XposedModuleConfigRegistry.hasInlineOptions(pkg);
        return new ModuleRow(pkg, label, description, required, forceDisabled, warn,
                solar, external, inline);
    }

    private static String labelText(ModuleRow row) {
        if (row == null || row.label == null) return "";
        return row.label.toString();
    }
}
