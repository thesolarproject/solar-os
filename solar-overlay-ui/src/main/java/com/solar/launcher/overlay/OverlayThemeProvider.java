package com.solar.launcher.overlay;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/**
 * 2026-07-08 — Process-wide OverlayTheme holder (app adapter or companion/fallback).
 * One install() at Application/Service start; get() never returns null.
 * Was: callers hit ThemeManager; companion had no shared paint API.
 * Reversal: remove install sites; callers go back to ThemeManager / hard-coded colours.
 */
public final class OverlayThemeProvider {

    private static volatile OverlayTheme INSTANCE;

    /** Aura-like defaults so TCM never NPEs before install. */
    private static final OverlayTheme FALLBACK = new FallbackOverlayTheme();

    private OverlayThemeProvider() {}

    /** Wire the live theme bridge for this process. */
    public static void install(OverlayTheme t) {
        INSTANCE = t;
    }

    /** Active theme, or bundled fallback when nothing installed yet. */
    public static OverlayTheme get() {
        OverlayTheme t = INSTANCE;
        return t != null ? t : FALLBACK;
    }

    /**
     * 2026-07-08 — Safe defaults: dark panel, white text, blue accent, no bitmaps.
     * Enough for companion/smoke paint when ThemeManager or ThemeReader is absent.
     */
    static final class FallbackOverlayTheme implements OverlayTheme {

        // Match ThemeReader / Aura-ish palette.
        private static final int BG = 0xFF1A1A1A;
        private static final int FG = 0xFFFFFFFF;
        private static final int ACCENT = 0xFF4A90D9;
        private static final int PANEL = 0xEE252528;
        private static final int HINT = 0xFFAAAAAA;

        @Override
        public int getContextMenuPanelColor() {
            return PANEL;
        }

        @Override
        public Drawable buildContextMenuPanelDrawable(Context context) {
            float density = context != null
                    ? context.getResources().getDisplayMetrics().density : 1f;
            float r = getButtonRadius() * 2f * density;
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xE0202022);
            g.setCornerRadius(r);
            g.setStroke(Math.max(1, (int) density), 0x66FFFFFF);
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
            int muted = (themeHintColor & 0x00FFFFFF) | 0xBB000000;
            return ensureReadableOnBackground(muted, PANEL);
        }

        @Override
        public int contextMenuTextNormal(int themeNormal, int themeSelected, int panelBg,
                boolean menuRows) {
            return ensureReadableOnBackground(themeNormal, panelBg);
        }

        @Override
        public int contextMenuTextSelected(int themeSelected, boolean menuRows) {
            return ensureReadableOnBackground(themeSelected, getRowSelectionFillColor());
        }

        @Override
        public int getDialogTextColor() {
            return FG;
        }

        @Override
        public int getHintTextColor() {
            return HINT;
        }

        @Override
        public int getSectionHeaderTextColor() {
            return ACCENT;
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
            return FG;
        }

        @Override
        public int getButtonRadius() {
            return 10;
        }

        @Override
        public Drawable getDialogOptionRowBackgroundScaled(Resources res, boolean selected,
                int widthPx, int heightPx) {
            return selected ? new ColorDrawable(ACCENT) : null;
        }

        @Override
        public Drawable getMenuRowBackgroundScaled(Resources res, boolean selected,
                int widthPx, int heightPx) {
            return selected ? new ColorDrawable(ACCENT) : null;
        }

        @Override
        public Drawable getItemRowBackgroundScaled(Resources res, boolean selected,
                int widthPx, int heightPx) {
            return selected ? new ColorDrawable(ACCENT) : null;
        }

        @Override
        public int getRowSelectionFillColor() {
            return ACCENT;
        }

        @Override
        public int getDialogOptionTextColorNormal() {
            return FG;
        }

        @Override
        public int getDialogOptionTextColorSelected() {
            return BG;
        }

        @Override
        public int getSettingMenuTextColorNormal() {
            return FG;
        }

        @Override
        public int getSettingMenuTextColorSelected() {
            return BG;
        }

        @Override
        public int getItemTextColorNormal() {
            return FG;
        }

        @Override
        public int getItemTextColorSelected() {
            return BG;
        }

        @Override
        public boolean isOverlayThemeReady() {
            return true;
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
}
