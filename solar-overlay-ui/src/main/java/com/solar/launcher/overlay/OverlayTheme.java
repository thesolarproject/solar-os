package com.solar.launcher.overlay;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/**
 * 2026-07-08 — Thin theme face for overlay chrome (ThemedContextMenu) without ThemeManager.
 * Menu paint asks here for colours, fonts, and row art so companion APK can supply a slim adapter.
 * Was: ThemedContextMenu called ThemeManager statics directly (app-only).
 * Now: OverlayThemeProvider.get() → app adapter or companion/fallback.
 * Reversal: delete this + provider; restore ThemeManager. calls in ThemedContextMenu.
 */
public interface OverlayTheme {

    /** Neutral hold-Back panel fill — shared context-menu chrome. */
    int getContextMenuPanelColor();

    /** Rounded panel + stroke drawable for the context menu shell. */
    Drawable buildContextMenuPanelDrawable(Context context);

    /** Apply fill (+ optional high-contrast stroke shadow) to a label. */
    void applyThemedTextStyle(TextView tv, int fillColor);

    /** Bump text colour when contrast on background is too weak. */
    int ensureReadableOnBackground(int textColor, int backgroundColor);

    /** Dimmed hint colour readable on the context panel. */
    int contextMenuMutedText(int themeHintColor);

    /** Unselected label colour on the context panel (may dim for achromatic themes). */
    int contextMenuTextNormal(int themeNormal, int themeSelected, int panelBg, boolean menuRows);

    /** Selected-row label colour on the context panel. */
    int contextMenuTextSelected(int themeSelected, boolean menuRows);

    int getDialogTextColor();

    int getHintTextColor();

    int getSectionHeaderTextColor();

    /** Theme typeface, or null for platform default. */
    Typeface getCustomFont();

    /** Settings-glyph bitmap for config key, or null. */
    Bitmap getSettingIcon(String configKey);

    /** Wi‑Fi bars bitmap for signal index, or null. */
    Bitmap getWifiIcon(int signalIndex);

    /** Shuffle/repeat (etc.) glyph, or null. */
    Bitmap getPlaybackModeIcon(String key);

    /** Drill chevron scaled to row height, or null. */
    Bitmap getScaledItemRightArrow(int maxHeightPx);

    int getStatusBarTextColor();

    /** Corner radius in dp (theme solarConfig / default 10). */
    int getButtonRadius();

    Drawable getDialogOptionRowBackgroundScaled(Resources res, boolean selected, int widthPx, int heightPx);

    Drawable getMenuRowBackgroundScaled(Resources res, boolean selected, int widthPx, int heightPx);

    Drawable getItemRowBackgroundScaled(Resources res, boolean selected, int widthPx, int heightPx);

    int getRowSelectionFillColor();

    int getDialogOptionTextColorNormal();

    int getDialogOptionTextColorSelected();

    int getSettingMenuTextColorNormal();

    int getSettingMenuTextColorSelected();

    int getItemTextColorNormal();

    int getItemTextColorSelected();

    /** True when overlay chrome cache/theme is warm enough to paint without I/O stall. */
    boolean isOverlayThemeReady();
}
