package com.solar.launcher;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-05 — Binds the shared full-screen wheel keyboard layout to a controller.
 * Layman: paints title, typed text, and the five letter slots — same look as Wi‑Fi / search keyboards.
 * Technical: theme row chrome via ThemeManager; used by Solar IME overlay and optionally MainActivity.
 */
public final class SolarWheelKeyboardUi {

    private final Context context;
    private final TextView tvTitle;
    private final TextView tvInput;
    private final TextView tvPprev;
    private final TextView tvPrev;
    private final TextView tvCurrent;
    private final TextView tvNext;
    private final TextView tvNnext;
    private final TextView tvHint;
    private final int rowHeightPx;
    private final int screenWidthPx;
    private final String enterLabel;

    public SolarWheelKeyboardUi(Context context, View root, String enterLabel) {
        this.context = context.getApplicationContext();
        this.enterLabel = enterLabel != null ? enterLabel : "[ENT]";
        tvTitle = (TextView) root.findViewById(R.id.tv_keyboard_title);
        tvInput = (TextView) root.findViewById(R.id.tv_keyboard_input);
        tvPprev = (TextView) root.findViewById(R.id.tv_key_pprev);
        tvPrev = (TextView) root.findViewById(R.id.tv_key_prev);
        tvCurrent = (TextView) root.findViewById(R.id.tv_key_current);
        tvNext = (TextView) root.findViewById(R.id.tv_key_next);
        tvNnext = (TextView) root.findViewById(R.id.tv_key_nnext);
        tvHint = (TextView) root.findViewById(R.id.tv_keyboard_hint);
        rowHeightPx = context.getResources().getDimensionPixelSize(R.dimen.y1_menu_item_height);
        screenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        applyTheme();
    }

    /** Theme fonts, row tiles, and hint line — matches in-app keyboard chrome. */
    public void applyTheme() {
        if (tvTitle != null) {
            ThemeManager.applyThemedTextStyle(tvTitle, ThemeManager.getSectionHeaderTextColor());
        }
        float menuTextPx = context.getResources().getDimension(R.dimen.y1_menu_text_size);
        int keyPad = (int) (6 * context.getResources().getDisplayMetrics().density);
        int layoutPad = (int) (15 * context.getResources().getDisplayMetrics().density);
        int inputRowW = screenWidthPx - layoutPad * 2;
        int keyW = (int) (52 * context.getResources().getDisplayMetrics().density);
        if (tvInput != null) {
            tvInput.setMinHeight(rowHeightPx);
            tvInput.setPadding(keyPad, 0, keyPad, 0);
            tvInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, menuTextPx);
            tvInput.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
            tvInput.setGravity(android.view.Gravity.CENTER);
        }
        styleKey(tvPprev, menuTextPx, keyPad, ThemeManager.getDimmedTextColor(0x55), 0);
        styleKey(tvPrev, menuTextPx, keyPad, ThemeManager.getTextColorPrimary(), 0);
        styleKey(tvNext, menuTextPx, keyPad, ThemeManager.getTextColorPrimary(), 0);
        styleKey(tvNnext, menuTextPx, keyPad, ThemeManager.getDimmedTextColor(0x55), 0);
        if (tvCurrent != null) {
            tvCurrent.setPadding(keyPad, keyPad / 2, keyPad, keyPad / 2);
            tvCurrent.setTextSize(TypedValue.COMPLEX_UNIT_PX, menuTextPx);
            tvCurrent.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
            android.graphics.drawable.Drawable bg = ThemeManager.getItemRowBackgroundScaled(
                    context.getResources(), true, keyW, rowHeightPx);
            if (bg != null) tvCurrent.setBackground(bg);
            ThemeManager.applyThemedTextStyle(tvCurrent, ThemeManager.getListButtonFocusedTextColor());
        }
        if (tvHint != null) {
            tvHint.setTextSize(TypedValue.COMPLEX_UNIT_PX, menuTextPx * 0.85f);
            tvHint.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
            ThemeManager.applyThemedTextStyle(tvHint, ThemeManager.getHintTextColor());
        }
        if (tvInput != null) {
            android.graphics.drawable.Drawable bg = ThemeManager.getItemRowBackgroundScaled(
                    context.getResources(), true, inputRowW, rowHeightPx);
            if (bg != null) tvInput.setBackground(bg);
        }
    }

    private void styleKey(TextView tv, float textPx, int pad, int color, int bgColor) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
        tv.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        ThemeManager.applyThemedTextStyle(tv, color);
        tv.setBackgroundColor(bgColor);
    }

    /** Refresh title, buffer line, and charset strip from controller state. */
    public void refresh(SolarWheelKeyboardController controller, String title,
            String inputText, boolean inputPlaceholder) {
        if (controller == null) return;
        int idx = controller.getIndex();
        if (tvTitle != null) {
            if (title != null && title.length() > 0) {
                tvTitle.setText(title);
                tvTitle.setVisibility(View.VISIBLE);
            } else {
                tvTitle.setVisibility(View.GONE);
            }
        }
        if (tvInput != null) {
            tvInput.setText(inputText != null ? inputText : "");
            tvInput.setSelected(!inputPlaceholder);
            ThemeManager.applyThemedTextStyle(tvInput, inputPlaceholder
                    ? ThemeManager.getHintTextColor()
                    : ThemeManager.getListButtonFocusedTextColor());
        }
        setKeyLabel(controller, tvPprev, idx, -2);
        setKeyLabel(controller, tvPrev, idx, -1);
        setKeyLabel(controller, tvCurrent, idx, 0);
        setKeyLabel(controller, tvNext, idx, 1);
        setKeyLabel(controller, tvNnext, idx, 2);
    }

    private void setKeyLabel(SolarWheelKeyboardController controller, TextView tv,
            int baseIdx, int offset) {
        if (tv == null || controller == null) return;
        int i = offset == 0 ? baseIdx : controller.wrapActiveIndex(baseIdx, offset);
        tv.setText(SolarWheelKeyboardController.displayChar(controller.charAt(i), enterLabel));
    }
}
