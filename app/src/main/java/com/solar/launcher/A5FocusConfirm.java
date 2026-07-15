package com.solar.launcher;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

/**
 * 2026-07-15 — A5 / touchscreen list UX (BlackBerry Storm style).
 * Layman: slide your finger — the highlight follows; tap the lit row again to open it.
 * Tech: {@link #followFinger} + per-row touch wasFocusedAtDown → confirm on UP; click path skips one race.
 * Was: OnClickListener wrap only (FITM first tap often skipped onClick so focus looked broken).
 * Reversal: restore click-only {@link #wrap}; drop followFinger from dispatchTouchEvent.
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
        if (row instanceof ViewGroup) {
            ((ViewGroup) row).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }

    /**
     * Wrap an activate listener for views that override setOnClickListener → super.
     * Layman: unfocused tap highlights; focused tap opens (plus touch UP confirm when attached).
     * Tech: suppress one click after focus-from-DOWN so programmatic requestFocus cannot one-tap activate.
     */
    public static View.OnClickListener wrap(final View row, final View.OnClickListener activate) {
        if (activate == null) return null;
        if (!enabled()) return activate;
        prepareRow(row);
        attachTouchConfirm(row, activate);
        final boolean[] suppressNextClick = new boolean[] { false };
        tagSuppress(row, suppressNextClick);
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View target = v != null ? v : row;
                if (target == null) {
                    activate.onClick(v);
                    return;
                }
                if (consumeSuppress(target)) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("suppressed", true);
                        d.put("cls", target.getClass().getSimpleName());
                        com.solar.launcher.debug.Debug31d3d8Log.log(target.getContext(),
                                "A5FocusConfirm.wrap", "skip click after focus-DOWN", "F2", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    return;
                }
                if (target.isFocused()) {
                    activate.onClick(target);
                } else {
                    target.requestFocus();
                }
            }
        };
    }

    /**
     * Bind Storm two-tap via public setOnClickListener (no override on the view).
     * Layman: plug in the action; A5 adds select-then-confirm.
     */
    public static void setOnClickListener(View row, View.OnClickListener activate) {
        if (row == null) return;
        if (!enabled()) {
            row.setOnClickListener(activate);
            return;
        }
        row.setOnClickListener(wrap(row, activate));
    }

    /**
     * 2026-07-15 — Slide finger: move focus to the row under the tip (Storm).
     * Layman: as you drag across the menu, the blue bar follows your finger.
     * Tech: hit-test focusable+clickable rows; {@code requestFocus()}; never consumes.
     * Skip while {@link MoveRibbonTouch#isSessionActive()}.
     */
    public static void followFinger(View root, MotionEvent event) {
        if (!enabled() || root == null || event == null) return;
        if (MoveRibbonTouch.isSessionActive()) return;
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) return;
        View hit = findFocusableRowAt(root, event.getX(), event.getY());
        if (hit == null || hit.isFocused()) return;
        boolean ok = hit.requestFocus();
        // #region agent log
        if (action == MotionEvent.ACTION_DOWN || ok) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("action", action);
                d.put("ok", ok);
                d.put("cls", hit.getClass().getSimpleName());
                d.put("x", (int) event.getX());
                d.put("y", (int) event.getY());
                com.solar.launcher.debug.Debug31d3d8Log.log(root.getContext(),
                        "A5FocusConfirm.followFinger", "focus followed finger", "F1", d);
            } catch (Exception ignored) {}
        }
        // #endregion
    }

    /** Deepest focusable+clickable view under (x,y) in root-local coords. */
    public static View findFocusableRowAt(View root, float x, float y) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        if (!(root instanceof ViewGroup)) {
            return (root.isFocusable() && root.isClickable()) ? root : null;
        }
        ViewGroup g = (ViewGroup) root;
        for (int i = g.getChildCount() - 1; i >= 0; i--) {
            View c = g.getChildAt(i);
            if (c == null || c.getVisibility() != View.VISIBLE) continue;
            float cx = x - c.getLeft() + c.getScrollX();
            float cy = y - c.getTop() + c.getScrollY();
            if (cx < 0 || cy < 0 || cx >= c.getWidth() || cy >= c.getHeight()) continue;
            View deeper = findFocusableRowAt(c, cx, cy);
            if (deeper != null) return deeper;
            if (c.isFocusable() && c.isClickable()) return c;
        }
        if (root.isFocusable() && root.isClickable()) return root;
        return null;
    }

    /**
     * Touch UP confirm when the row was already focused at DOWN (second tap).
     * Safe to call from {@link #wrap} even if a later OnTouchListener replaces this
     * (MoveRibbonTouch) — activity {@link #followFinger} still moves focus.
     */
    static void attachTouchConfirm(final View row, final View.OnClickListener activate) {
        if (row == null || activate == null || !enabled()) return;
        final boolean[] focusedAtDown = new boolean[] { false };
        final float[] downXY = new float[2];
        final int[] slop = new int[] { 16 };
        try {
            slop[0] = ViewConfiguration.get(row.getContext()).getScaledTouchSlop();
        } catch (Throwable ignored) {}
        row.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null || MoveRibbonTouch.isSessionActive()) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        focusedAtDown[0] = v.isFocused();
                        downXY[0] = event.getX();
                        downXY[1] = event.getY();
                        if (!focusedAtDown[0]) {
                            boolean ok = v.requestFocus();
                            markSuppress(v);
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("focusedAtDown", false);
                                d.put("requestOk", ok);
                                d.put("nowFocused", v.isFocused());
                                d.put("cls", v.getClass().getSimpleName());
                                com.solar.launcher.debug.Debug31d3d8Log.log(v.getContext(),
                                        "A5FocusConfirm.touch", "row DOWN focus", "F1,F2", d);
                            } catch (Exception ignored) {}
                            // #endregion
                        } else {
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("focusedAtDown", true);
                                d.put("cls", v.getClass().getSimpleName());
                                com.solar.launcher.debug.Debug31d3d8Log.log(v.getContext(),
                                        "A5FocusConfirm.touch", "row DOWN already focused", "F2", d);
                            } catch (Exception ignored) {}
                            // #endregion
                        }
                        return false;
                    case MotionEvent.ACTION_UP: {
                        float dx = event.getX() - downXY[0];
                        float dy = event.getY() - downXY[1];
                        if (dx * dx + dy * dy > slop[0] * slop[0]) return false;
                        if (focusedAtDown[0] && v.isFocused()) {
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("confirm", true);
                                d.put("cls", v.getClass().getSimpleName());
                                com.solar.launcher.debug.Debug31d3d8Log.log(v.getContext(),
                                        "A5FocusConfirm.touch", "second-tap confirm UP", "F2", d);
                            } catch (Exception ignored) {}
                            // #endregion
                            activate.onClick(v);
                            return true;
                        }
                        return false;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    private static final int TAG_SUPPRESS = 0x31d3d8f1;

    private static void tagSuppress(View row, boolean[] box) {
        if (row != null) row.setTag(TAG_SUPPRESS, box);
    }

    private static void markSuppress(View v) {
        if (v == null) return;
        Object o = v.getTag(TAG_SUPPRESS);
        if (o instanceof boolean[]) {
            ((boolean[]) o)[0] = true;
        }
    }

    private static boolean consumeSuppress(View v) {
        if (v == null) return false;
        Object o = v.getTag(TAG_SUPPRESS);
        if (o instanceof boolean[]) {
            boolean[] b = (boolean[]) o;
            if (b[0]) {
                b[0] = false;
                return true;
            }
        }
        return false;
    }
}
