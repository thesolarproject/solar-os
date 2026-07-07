package com.solar.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/** Bridges {@link XposedModulesSettingsUi} to {@link MainActivity} (2026-07-05). */
public final class XposedModulesSettingsAdapter implements XposedModulesSettingsUi.Host {

    private final MainActivity act;
    private XposedModulesSettingsUi ui;

    public XposedModulesSettingsAdapter(MainActivity activity) {
        act = activity;
    }

    /** One UI instance — staged map survives preview refreshes (2026-07-05). */
    public XposedModulesSettingsUi ui() {
        if (ui == null) {
            ui = new XposedModulesSettingsUi(this);
        }
        return ui;
    }

    @Override public Activity activity() { return act; }
    @Override public Context context() { return act; }
    @Override public SharedPreferences prefs() { return act.getPrefs(); }

    @Override public LinearLayout settingsContainer() {
        return (LinearLayout) act.findViewById(R.id.container_settings_items);
    }

    @Override public Button createBackButton(String label) { return act.mediaCreateListButton(label); }
    @Override public LinearLayout createRow(String rowKey, int labelResId, boolean submenu) {
        return act.xposedCreateSettingsRow(rowKey, labelResId, submenu);
    }
    @Override public LinearLayout createRow(String rowKey, CharSequence title, boolean submenu) {
        return act.xposedCreateSettingsRow(rowKey, title, submenu);
    }

    @Override public void addInfoParagraph(String text) { act.xposedAddSettingsInfoParagraph(text); }
    @Override public void styleSecondaryLabel(Button btn) { act.xposedStyleSecondaryLabel(btn); }
    @Override public void clickFeedback() { act.mediaClickFeedback(); }
    @Override public void setSubScreen(String key, String extra) { act.xposedSetSettingsSubScreen(key, extra); }
    @Override public void updateStatusBarTitle() { act.xposedUpdateStatusBarTitle(); }
    @Override public void updateScreenBackground(int state) { act.xposedUpdateScreenBackground(state); }
    @Override public void applyReachBrowseLayoutMode() { act.mediaApplyReachBrowseLayoutMode(); }
    @Override public void refreshRowPreview(String rowKey) { act.xposedRefreshSettingsPreview(rowKey); }
    @Override public void openDebugMenu() { act.xposedBuildDebugUI(); }
    @Override public void runOnUiThread(Runnable r) { act.runOnUiThread(r); }

    @Override public void showDisableConfirm(CharSequence title, String message, Runnable onConfirm) {
        act.xposedShowThemedConfirm(
                title != null ? title.toString() : "",
                message,
                act.getString(R.string.common_off),
                act.getString(R.string.common_cancel),
                onConfirm,
                null);
    }

    @Override public boolean isXposedModulesScreenActive() {
        return SettingsScreens.XPOSED_MODULES.equals(act.getSettingsSubScreenKey());
    }
}
