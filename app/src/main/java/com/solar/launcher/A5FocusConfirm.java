package com.solar.launcher;

import android.view.View;

/**
 * 2026-07-14 — A5 list touch: first tap focuses, second tap confirms.
 * Layman: poke a menu line to highlight it, poke again to open / play it.
 * Tech: wrap OnClickListener; focusableInTouchMode; DeviceFeatures.isA5 / hasTouchscreen.
 * Reversal: pass listeners through unchanged (one-tap activate again).
 */
public final class A5FocusConfirm {

    private A5FocusConfirm() {}

    /** True when this device should use two-tap list confirm. */
    public static boolean enabled() {
        return DeviceFeatures.isA5() || DeviceFeatures.hasTouchscreen();
    }

    /**
     * Enable focus-on-first-touch chrome for a list row shell.
     * Layman: make the row able to take highlight from a finger poke.
     */
    public static void prepareRow(View row) {
        if (row == null || !enabled()) return;
        row.setClickable(true);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
    }

    /**
     * Wrap an activate listener: unfocused tap → focus only; focused tap → activate.
     * Layman: first poke selects, second poke does the action.
     * Non-A5: returns {@code activate} unchanged.
     */
    public static View.OnClickListener wrap(final View row, final View.OnClickListener activate) {
        if (activate == null) return null;
        if (!enabled()) return activate;
        prepareRow(row);
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View target = v != null ? v : row;
                if (target != null && target.isFocused()) {
                    activate.onClick(target);
                } else if (target != null) {
                    target.requestFocus();
                } else {
                    activate.onClick(v);
                }
            }
        };
    }

    /**
     * Assign a two-tap listener (or one-tap off A5).
     * Layman: plug in the same click action; A5 adds the extra select step.
     */
    public static void setOnClickListener(View row, View.OnClickListener activate) {
        if (row == null) return;
        row.setOnClickListener(wrap(row, activate));
    }
}
