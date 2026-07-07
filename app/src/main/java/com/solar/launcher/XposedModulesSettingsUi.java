package com.solar.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-07-05 — Debug → Xposed modules: flat enable/disable list with ✓/✗ and disable warnings.
 * Layman: shows every hook on the device; tap toggles; Apply + reboot saves; prefs live in Installer.
 * Technical: async discovery + batched enabled_modules parse; staged map until Apply.
 * Reversal: delete class; users manage modules only via Xposed Installer.
 */
public final class XposedModulesSettingsUi {

    /** MainActivity settings chrome — see {@link XposedModulesSettingsAdapter}. */
    public interface Host {
        Activity activity();
        Context context();
        SharedPreferences prefs();
        LinearLayout settingsContainer();
        Button createBackButton(String label);
        LinearLayout createRow(String rowKey, int labelResId, boolean submenu);
        LinearLayout createRow(String rowKey, CharSequence title, boolean submenu);
        void addInfoParagraph(String text);
        void styleSecondaryLabel(Button btn);
        void clickFeedback();
        void setSubScreen(String key, String extra);
        void updateStatusBarTitle();
        void updateScreenBackground(int state);
        void applyReachBrowseLayoutMode();
        void refreshRowPreview(String rowKey);
        void openDebugMenu();
        void runOnUiThread(Runnable r);
        void showDisableConfirm(CharSequence title, String message, Runnable onConfirm);
        boolean isXposedModulesScreenActive();
    }

    private static final int STATE_SETTINGS = 4;

    private final Host host;
    private Map<String, Boolean> staged;
    private int loadGeneration;

    public XposedModulesSettingsUi(Host host) {
        this.host = host;
    }

    /** Staged toggles — shared across preview refreshes on one adapter instance. */
    public Map<String, Boolean> getStaged() {
        ensureStagedLoaded();
        return staged;
    }

    /** Invalidate staged map after Apply or when reopening menu. */
    public void resetStaged() {
        staged = null;
    }

    /** Debug → Xposed modules — async load so UI thread never blocks on root shell. */
    public void buildModuleList() {
        host.setSubScreen(SettingsScreens.XPOSED_MODULES, null);
        host.applyReachBrowseLayoutMode();
        host.updateStatusBarTitle();
        host.updateScreenBackground(STATE_SETTINGS);
        LinearLayout container = host.settingsContainer();
        if (container == null) return;
        container.removeAllViews();

        Button btnBack = host.createBackButton(host.context().getString(R.string.common_back_short));
        host.styleSecondaryLabel(btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.clickFeedback();
                host.openDebugMenu();
            }
        });
        container.addView(btnBack);

        host.addInfoParagraph(host.context().getString(R.string.settings_debug_xposed_intro));

        LinearLayout loading = host.createRow(RowKeys.DEBUG_XPOSED_MODULES,
                host.context().getString(R.string.settings_debug_xposed_loading), false);
        loading.setFocusable(false);
        loading.setClickable(false);
        container.addView(loading);

        final int gen = ++loadGeneration;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<XposedModuleCatalog.ModuleRow> rows =
                        XposedModuleCatalog.allRows(host.context());
                final Map<String, Boolean> snapshot =
                        XposedModuleStore.readEnabledSnapshot(host.context());
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!host.isXposedModulesScreenActive() || gen != loadGeneration) return;
                        bindModuleList(rows, snapshot);
                    }
                });
            }
        }, "XposedModuleListLoad").start();
    }

    private void bindModuleList(List<XposedModuleCatalog.ModuleRow> rows,
            Map<String, Boolean> snapshot) {
        LinearLayout container = host.settingsContainer();
        if (container == null) return;
        container.removeAllViews();

        Button btnBack = host.createBackButton(host.context().getString(R.string.common_back_short));
        host.styleSecondaryLabel(btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.clickFeedback();
                host.openDebugMenu();
            }
        });
        container.addView(btnBack);

        host.addInfoParagraph(host.context().getString(R.string.settings_debug_xposed_intro));

        if (!XposedModuleDiscovery.findDisabledRequiredSolar(host.context()).isEmpty()) {
            host.addInfoParagraph(host.context().getString(
                    R.string.settings_debug_xposed_required_off_banner));
        }

        staged = new HashMap<String, Boolean>(snapshot);

        if (rows.isEmpty()) {
            LinearLayout empty = host.createRow(RowKeys.DEBUG_XPOSED_MODULES,
                    host.context().getString(R.string.settings_debug_xposed_empty), false);
            empty.setFocusable(false);
            empty.setClickable(false);
            container.addView(empty);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                addModuleToggleRow(container, rows.get(i));
            }
        }

        LinearLayout btnApply = host.createRow(RowKeys.DEBUG_XPOSED_APPLY,
                R.string.settings_debug_xposed_apply, false);
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.clickFeedback();
                applyStagedAndReboot();
            }
        });
        container.addView(btnApply);
        host.refreshRowPreview(RowKeys.DEBUG_XPOSED_APPLY);

        if (container.getChildCount() > 1) {
            container.getChildAt(1).requestFocus();
        }
    }

    private void addModuleToggleRow(LinearLayout container, final XposedModuleCatalog.ModuleRow row) {
        final String pkg = row.packageName;
        final String rowKey = RowKeys.xposedModuleRowKey(pkg);
        CharSequence title = moduleTitle(row);
        final LinearLayout item = host.createRow(rowKey, title, false);
        if (row.forceDisabled) {
            item.setFocusable(false);
            item.setClickable(false);
        } else {
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.clickFeedback();
                    toggleModule(row, title);
                }
            });
        }
        container.addView(item);
        host.refreshRowPreview(rowKey);
    }

    private void toggleModule(final XposedModuleCatalog.ModuleRow row, final CharSequence title) {
        ensureStagedLoaded();
        final String pkg = row.packageName;
        Boolean cur = staged.get(pkg);
        final boolean enabled = cur != null && cur.booleanValue();
        if (enabled && row.required && row.disableWarningResId != 0) {
            host.showDisableConfirm(
                    title,
                    host.context().getString(row.disableWarningResId),
                    new Runnable() {
                        @Override
                        public void run() {
                            stageToggle(pkg, false);
                        }
                    });
            return;
        }
        stageToggle(pkg, !enabled);
    }

    private void stageToggle(String pkg, boolean enable) {
        ensureStagedLoaded();
        staged.put(pkg, Boolean.valueOf(enable));
        host.refreshRowPreview(RowKeys.xposedModuleRowKey(pkg));
    }

    /** On/Off for toggle_mark — staged value wins (2026-07-05). */
    public String previewForRow(String rowKey) {
        if (rowKey == null) return "";
        if (RowKeys.DEBUG_XPOSED_APPLY.equals(rowKey)) return "";
        if (!RowKeys.isXposedModuleRow(rowKey)) return "";
        String pkg = rowKey.substring(RowKeys.XPOSED_MODULE_ROW_PREFIX.length());
        ensureStagedLoaded();
        Boolean stagedVal = staged.get(pkg);
        boolean on = stagedVal != null && stagedVal.booleanValue();
        XposedModuleCatalog.ModuleRow row = XposedModuleCatalog.findRow(host.context(), pkg);
        if (row != null && row.forceDisabled) {
            return host.context().getString(R.string.settings_debug_xposed_powermenu_locked);
        }
        return host.context().getString(on ? R.string.common_on : R.string.common_off);
    }

    private CharSequence moduleTitle(XposedModuleCatalog.ModuleRow row) {
        if (row == null) return "";
        XposedModuleRegistry.Entry reg = XposedModuleRegistry.findByPackage(row.packageName);
        if (reg != null) {
            return host.context().getString(reg.labelResId);
        }
        return row.label != null ? row.label : row.packageName;
    }

    private void ensureStagedLoaded() {
        if (staged != null) return;
        staged = new HashMap<String, Boolean>(XposedModuleStore.readEnabledSnapshot(host.context()));
    }

    private void applyStagedAndReboot() {
        ensureStagedLoaded();
        if (!RootShell.canRun()) {
            Toast.makeText(host.context(), R.string.settings_debug_xposed_root_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(host.context(), R.string.settings_debug_xposed_rebooting, Toast.LENGTH_SHORT).show();
        final Map<String, Boolean> toApply = new HashMap<String, Boolean>(staged);
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = XposedModuleStore.applyStagedSelections(host.context(), toApply);
                if (ok) {
                    RootShell.run("sync; reboot");
                }
                final boolean success = ok;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!success) {
                            Toast.makeText(host.context(),
                                    R.string.settings_debug_xposed_apply_failed, Toast.LENGTH_LONG).show();
                        } else {
                            resetStaged();
                        }
                    }
                });
            }
        }, "XposedModuleApply").start();
    }

    /** ponytail: redirect detail view to main module list (simplest working solution). */
    public void buildModuleDetail(String packageName) {
        buildModuleList();
    }
}
