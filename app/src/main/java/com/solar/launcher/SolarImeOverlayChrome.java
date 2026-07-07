package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-05 — Theme wallpaper + status bar for system IME full-screen overlay.
 * Layman: paints the same background and top bar you see when typing Wi‑Fi passwords inside Solar.
 * Technical: ThemeManager wallpaper/mask + status bar colors; runs in :overlay process.
 */
public final class SolarImeOverlayChrome {

    private final Context context;
    private View statusBar;
    private TextView tvTitle;
    private TextView tvBattery;
    private ImageView ivWallpaper;
    private ImageView ivMask;

    public SolarImeOverlayChrome(Context context, View shellRoot) {
        this.context = context.getApplicationContext();
        if (shellRoot != null) {
            ivWallpaper = (ImageView) shellRoot.findViewById(R.id.iv_ime_wallpaper);
            ivMask = (ImageView) shellRoot.findViewById(R.id.iv_ime_mask);
            statusBar = shellRoot.findViewById(R.id.layout_ime_status_bar);
            tvTitle = (TextView) shellRoot.findViewById(R.id.tv_ime_status_title);
            tvBattery = (TextView) shellRoot.findViewById(R.id.tv_ime_status_battery);
        }
    }

    /** Warm theme from prefs and paint wallpaper + status chrome. */
    public void apply(String title) {
        ThemeManager.restoreSavedThemeFromPrefs(context);
        ThemeManager.ensureOverlayPaintableMinimum(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ThemeManager.ensureOverlayThemeReady(context);
                if (shellHandler() == null) return;
                shellHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        paintWallpaper();
                        applyStatusBar(title);
                    }
                });
            }
        }, "ImeThemeWarm").start();
        paintWallpaper();
        applyStatusBar(title);
    }

    /** Refresh title line when editor label changes. */
    public void refreshTitle(String title) {
        applyStatusBar(title);
    }

    private void paintWallpaper() {
        Bitmap wall = ThemeManager.getWallpaper(false);
        if (wall == null) wall = ThemeManager.getWallpaper(true);
        if (ivWallpaper != null) {
            if (wall != null) {
                ivWallpaper.setImageBitmap(wall);
                ivWallpaper.setBackgroundColor(0x00000000);
            } else {
                ivWallpaper.setImageBitmap(null);
                ivWallpaper.setBackgroundColor(ThemeManager.getOverlayBackgroundColor());
            }
        }
        Bitmap mask = ThemeManager.getSettingMask();
        if (ivMask != null) {
            if (mask != null) {
                ivMask.setImageBitmap(mask);
                ivMask.setVisibility(View.VISIBLE);
            } else {
                ivMask.setVisibility(View.GONE);
            }
        }
    }

    private void applyStatusBar(String title) {
        if (statusBar != null) {
            statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
        }
        int textColor = ThemeManager.getStatusBarTextColor();
        if (tvTitle != null) {
            String line = title != null && title.length() > 0
                    ? title : context.getString(R.string.solar_ime_label);
            tvTitle.setText(line);
            ThemeManager.applyThemedTextStyle(tvTitle, textColor);
            tvTitle.setSelected(true);
        }
        if (tvBattery != null) {
            ThemeManager.applyThemedTextStyle(tvBattery, textColor);
            tvBattery.setText(readBatteryPercent() + "%");
        }
    }

    private int readBatteryPercent() {
        try {
            Intent intent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return 100;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return 100;
            return (int) (100f * level / scale);
        } catch (Exception e) {
            return 100;
        }
    }

    private android.os.Handler shellHandler() {
        if (statusBar != null) return statusBar.getHandler();
        if (ivWallpaper != null) return ivWallpaper.getHandler();
        return null;
    }
}
