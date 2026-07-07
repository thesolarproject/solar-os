package com.solar.launcher;

import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ScrollView;

/** 2026-07-05 — Wheel index scroll on API 17; one primary scroll axis per list (no touch focus trees). */
public final class FocusScrollHelper {
    private static final int LIST_IDLE_FOCUS_DELAY_MS = 80;
    /** 2026-07-05 — Wheel repeat inside this window uses instant scrollTo (no smooth stack). */
    private static final int RAPID_WHEEL_SCROLL_MS = 120;
    private static long lastScrollToChildBottomMs;

    private FocusScrollHelper() {}

    public static void smoothScrollListToPosition(final ListView list, final int position) {
        if (list == null || position < 0) return;
        list.smoothScrollToPosition(position);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            private boolean scrolling;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (scrolling) {
                        scrolling = false;
                        list.setOnScrollListener(null);
                        focusListPosition(list, position);
                    }
                } else {
                    scrolling = true;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {}
        });
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                focusListPosition(list, position);
            }
        }, 220);
    }

    public static void focusListPosition(ListView list, int position) {
        if (list == null || position < 0) return;
        list.setSelection(position);
        View child = list.getChildAt(position - list.getFirstVisiblePosition());
        if (child != null) {
            child.requestFocus();
        } else {
            list.post(new Runnable() {
                @Override
                public void run() {
                    View c = list.getChildAt(position - list.getFirstVisiblePosition());
                    if (c != null) c.requestFocus();
                }
            });
        }
    }

    /**
     * 2026-07-05 — Scroll focused row into view; rapid wheel uses scrollTo, isolated ticks smooth.
     * Rollback: restore always-smooth + post Runnable wrapper.
     */
    public static void scrollToChildBottom(ScrollView scroll, View child) {
        if (scroll == null || child == null) return;
        long now = SystemClock.uptimeMillis();
        boolean rapid = (now - lastScrollToChildBottomMs) < RAPID_WHEEL_SCROLL_MS;
        lastScrollToChildBottomMs = now;
        int y = child.getBottom() - scroll.getHeight();
        if (y < 0) y = 0;
        final int targetY = y;
        if (rapid) {
            scroll.scrollTo(0, targetY);
            return;
        }
        scroll.smoothScrollTo(0, targetY);
    }
}
