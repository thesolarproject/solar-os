package com.solar.launcher.globalcontext;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import com.solar.launcher.overlay.OverlayTheme;

/**
 * 2026-07-10 — Companion OverlayTheme from ThemeReader (Solar sidecars).
 * Layman: paints the global quick menu with the user’s Solar theme, not a gray stub.
 * Technical: ThemeReader.refresh() before install; panel/selection from skin/snapshot JSON.
 * Was: hard-coded PANEL 0xEE252528 + ThemeReader fallback-only colors (wrong prefs path).
 * Reversal: restore constant PANEL + accent ColorDrawable only if sidecars go away.
 */
public final class CompanionOverlayTheme implements OverlayTheme {

    @Override
    public int getContextMenuPanelColor() {
        return ThemeReader.panelColor();
    }

    @Override
    public Drawable buildContextMenuPanelDrawable(Context context) {
        float density = context != null
                ? context.getResources().getDisplayMetrics().density : 1f;
        float r = getButtonRadius() * 2f * density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(ThemeReader.panelColor());
        g.setCornerRadius(r);
        // Soft edge stroke — lighten panel for border so themed skins keep a rim.
        int stroke = ThemeReader.hasSidecarTheme()
                ? (ThemeReader.foregroundColor() & 0x00FFFFFF) | 0x44000000
                : 0x66FFFFFF;
        g.setStroke(Math.max(1, (int) density), stroke);
        return g;
    }

    @Override
    public void applyThemedTextStyle(TextView tv, int fillColor) {
        if (tv == null) return;
        tv.setTextColor(fillColor);
        tv.setShadowLayer(0, 0, 0, 0);
    }

    @Override
    public int ensureReadableOnBackground(int textColor, int backgroundColor) {
        int fg = textColor | 0xFF000000;
        int bg = backgroundColor | 0xFF000000;
        if (contrastRatio(fg, bg) >= 3.0) return textColor;
        return relativeLuminance(bg) > 0.45 ? 0xFF1A1A1A : 0xFFE8E8E8;
    }

    @Override
    public int contextMenuMutedText(int themeHintColor) {
        int muted = ThemeReader.mutedTextColor();
        if (muted == 0) {
            muted = (themeHintColor & 0x00FFFFFF) | 0xBB000000;
        }
        return ensureReadableOnBackground(muted, getContextMenuPanelColor());
    }

    @Override
    public int contextMenuTextNormal(int themeNormal, int themeSelected, int panelBg,
            boolean menuRows) {
        return ensureReadableOnBackground(themeNormal, panelBg);
    }

    @Override
    public int contextMenuTextSelected(int themeSelected, boolean menuRows) {
        // 2026-07-10 — Default skins publish selectedText=-1 (white) with white selection fill →
        // invisible focus. Always force contrast against the live selection fill/bitmap avg.
        int fill = getRowSelectionFillColor();
        int wanted = themeSelected != 0 ? themeSelected : ThemeReader.selectedTextColor();
        return ensureReadableOnBackground(wanted, fill);
    }

    @Override
    public int getDialogTextColor() {
        return ThemeReader.foregroundColor();
    }

    @Override
    public int getHintTextColor() {
        int muted = ThemeReader.mutedTextColor();
        if (muted != 0 && muted != 0xFFAAAAAA) return muted;
        int fg = ThemeReader.foregroundColor();
        return (fg & 0x00FFFFFF) | 0xAA000000;
    }

    @Override
    public int getSectionHeaderTextColor() {
        return ThemeReader.accentColor();
    }

    @Override
    public Typeface getCustomFont() {
        return null;
    }

    @Override
    public Bitmap getSettingIcon(String configKey) {
        return null;
    }

    @Override
    public Bitmap getWifiIcon(int signalIndex) {
        return null;
    }

    @Override
    public Bitmap getPlaybackModeIcon(String key) {
        return null;
    }

    @Override
    public Bitmap getScaledItemRightArrow(int maxHeightPx) {
        return null;
    }

    @Override
    public int getStatusBarTextColor() {
        return ThemeReader.foregroundColor();
    }

    @Override
    public int getButtonRadius() {
        return 10;
    }

    @Override
    public Drawable getDialogOptionRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return rowBackground(res, selected, widthPx, heightPx);
    }

    @Override
    public Drawable getMenuRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return rowBackground(res, selected, widthPx, heightPx);
    }

    @Override
    public Drawable getItemRowBackgroundScaled(Resources res, boolean selected,
            int widthPx, int heightPx) {
        return rowBackground(res, selected, widthPx, heightPx);
    }

    @Override
    public int getRowSelectionFillColor() {
        return ThemeReader.accentColor();
    }

    @Override
    public int getDialogOptionTextColorNormal() {
        return ThemeReader.foregroundColor();
    }

    @Override
    public int getDialogOptionTextColorSelected() {
        return ThemeReader.selectedTextColor();
    }

    @Override
    public int getSettingMenuTextColorNormal() {
        return ThemeReader.foregroundColor();
    }

    @Override
    public int getSettingMenuTextColorSelected() {
        return ThemeReader.selectedTextColor();
    }

    @Override
    public int getItemTextColorNormal() {
        return ThemeReader.foregroundColor();
    }

    @Override
    public int getItemTextColorSelected() {
        return ThemeReader.selectedTextColor();
    }

    @Override
    public boolean isOverlayThemeReady() {
        return true;
    }

    /** Selection strip from skin PNG when present; else solid accent. */
    private Drawable rowBackground(Resources res, boolean selected, int widthPx, int heightPx) {
        if (!selected) return null;
        Bitmap strip = ThemeReader.selectionBitmap();
        if (strip != null && !strip.isRecycled() && res != null
                && widthPx > 0 && heightPx > 0) {
            try {
                Bitmap scaled = Bitmap.createScaledBitmap(strip, widthPx, heightPx, true);
                BitmapDrawable d = new BitmapDrawable(res, scaled);
                d.setAntiAlias(true);
                return d;
            } catch (Throwable ignored) {}
        }
        return new ColorDrawable(ThemeReader.accentColor());
    }

    private static double relativeLuminance(int argb) {
        return 0.2126 * channelLinear((argb >> 16) & 0xFF)
                + 0.7152 * channelLinear((argb >> 8) & 0xFF)
                + 0.0722 * channelLinear(argb & 0xFF);
    }

    private static double channelLinear(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static double contrastRatio(int fg, int bg) {
        double l1 = relativeLuminance(fg);
        double l2 = relativeLuminance(bg);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }
}
