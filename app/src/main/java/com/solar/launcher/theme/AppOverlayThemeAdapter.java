package com.solar.launcher.theme;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import com.solar.launcher.overlay.OverlayTheme;

/**
 * 2026-07-08 — App OverlayTheme: every call delegates to ThemeManager (full theme engine).
 * Keeps ThemedContextMenu off ThemeManager while Solar still owns real chrome.
 * Was: TCM called ThemeManager statics; companion could not share that class.
 * Reversal: remove install in SolarApplication; restore ThemeManager. in ThemedContextMenu.
 */
public final class AppOverlayThemeAdapter implements OverlayTheme {

    @Override
    public int getContextMenuPanelColor() {
        return ThemeManager.getContextMenuPanelColor();
    }

    @Override
    public Drawable buildContextMenuPanelDrawable(Context context) {
        return ThemeManager.buildContextMenuPanelDrawable(context);
    }

    @Override
    public void applyThemedTextStyle(TextView tv, int fillColor) {
        ThemeManager.applyThemedTextStyle(tv, fillColor);
    }

    @Override
    public int ensureReadableOnBackground(int textColor, int backgroundColor) {
        return ThemeManager.ensureReadableOnBackground(textColor, backgroundColor);
    }

    @Override
    public int contextMenuMutedText(int themeHintColor) {
        return ThemeManager.contextMenuMutedText(themeHintColor);
    }

    @Override
    public int contextMenuTextNormal(int themeNormal, int themeSelected, int panelBg,
            boolean menuRows) {
        return ThemeManager.contextMenuTextNormal(themeNormal, themeSelected, panelBg, menuRows);
    }

    @Override
    public int contextMenuTextSelected(int themeSelected, boolean menuRows) {
        return ThemeManager.contextMenuTextSelected(themeSelected, menuRows);
    }

    @Override
    public int getDialogTextColor() {
        return ThemeManager.getDialogTextColor();
    }

    @Override
    public int getHintTextColor() {
        return ThemeManager.getHintTextColor();
    }

    @Override
    public int getSectionHeaderTextColor() {
        return ThemeManager.getSectionHeaderTextColor();
    }

    @Override
    public Typeface getCustomFont() {
        return ThemeManager.getCustomFont();
    }

    @Override
    public Bitmap getSettingIcon(String configKey) {
        return ThemeManager.getSettingIcon(configKey);
    }

    @Override
    public Bitmap getWifiIcon(int signalIndex) {
        return ThemeManager.getWifiIcon(signalIndex);
    }

    @Override
    public Bitmap getPlaybackModeIcon(String key) {
        return ThemeManager.getPlaybackModeIcon(key);
    }

    @Override
    public Bitmap getScaledItemRightArrow(int maxHeightPx) {
        return ThemeManager.getScaledItemRightArrow(maxHeightPx);
    }

    @Override
    public int getStatusBarTextColor() {
        return ThemeManager.getStatusBarTextColor();
    }

    @Override
    public int getButtonRadius() {
        return ThemeManager.getButtonRadius();
    }

    @Override
    public Drawable getDialogOptionRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return ThemeManager.getDialogOptionRowBackgroundScaled(res, selected, widthPx, heightPx);
    }

    @Override
    public Drawable getMenuRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return ThemeManager.getMenuRowBackgroundScaled(res, selected, widthPx, heightPx);
    }

    @Override
    public Drawable getItemRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return ThemeManager.getItemRowBackgroundScaled(res, selected, widthPx, heightPx);
    }

    @Override
    public int getRowSelectionFillColor() {
        return ThemeManager.getRowSelectionFillColor();
    }

    @Override
    public int getDialogOptionTextColorNormal() {
        return ThemeManager.getDialogOptionTextColorNormal();
    }

    @Override
    public int getDialogOptionTextColorSelected() {
        return ThemeManager.getDialogOptionTextColorSelected();
    }

    @Override
    public int getSettingMenuTextColorNormal() {
        return ThemeManager.getSettingMenuTextColorNormal();
    }

    @Override
    public int getSettingMenuTextColorSelected() {
        return ThemeManager.getSettingMenuTextColorSelected();
    }

    @Override
    public int getItemTextColorNormal() {
        return ThemeManager.getItemTextColorNormal();
    }

    @Override
    public int getItemTextColorSelected() {
        return ThemeManager.getItemTextColorSelected();
    }

    @Override
    public boolean isOverlayThemeReady() {
        return ThemeManager.isOverlayThemeReady();
    }
}
