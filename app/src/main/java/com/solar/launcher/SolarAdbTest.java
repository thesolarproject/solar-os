package com.solar.launcher;

import android.util.Log;

/** Logcat probe for {@code scripts/test_context_queue_adb.sh} — tag {@code SolarAdbTest}. */
final class SolarAdbTest {
    static final String TAG = "SolarAdbTest";

    private SolarAdbTest() {}

    static void queueScroll(int focusIndex, int count, int topPad, int scrollY, int maxScroll, int slot) {
        queueScroll(focusIndex, count, topPad, scrollY, maxScroll, slot, -1);
    }

    static void queueScroll(int focusIndex, int count, int topPad, int scrollY, int maxScroll, int slot,
            int nowPlaying) {
        Log.i(TAG, "queueScroll focus=" + focusIndex + " count=" + count + " topPad=" + topPad
                + " scrollY=" + scrollY + " maxScroll=" + maxScroll + " slot=" + slot
                + " nowPlaying=" + nowPlaying);
    }

    static void queuePlaybackState(boolean playing) {
        Log.i(TAG, "queuePlaybackState playing=" + playing);
    }

    static void queueOpen(int count, boolean listVisible, int scrollHeight, int childCount) {
        Log.i(TAG, "queueOpen count=" + count + " listVisible=" + listVisible
                + " scrollHeight=" + scrollHeight + " childCount=" + childCount);
    }

    static void queueRestoreState(int diskItems, int memItems, int missingPaths) {
        Log.i(TAG, "queueRestoreState disk=" + diskItems + " mem=" + memItems
                + " missingPaths=" + missingPaths);
    }

    static void fail(String reason) {
        Log.i(TAG, "FAIL " + reason);
    }

    static void pass(String reason) {
        Log.i(TAG, "PASS " + reason);
    }

    /** 2026-07-16 — Real-world matrix step progress (scripts/test_realworld_matrix_adb.sh). */
    static void step(int index, String name, String detail) {
        Log.i(TAG, "STEP " + index + " " + name + (detail != null && detail.length() > 0 ? " " + detail : ""));
    }

    /** 2026-07-16 — Timing line for matrix / keyevent suite. */
    static void timing(String name, long ms) {
        Log.i(TAG, "TIMING " + name + " ms=" + ms);
    }

    static void avrcpHarness(String phase, String detail) {
        Log.i(TAG, "avrcp_harness " + phase + " " + detail);
    }

    /** Flow carousel geometry — scripts/test_flow_adb.sh checks neighbor peek. */
    static void flowCarousel(int focus, int itemCount, float centerCx, float rightCx,
            float rightRotY, float rightWidth) {
        Log.i(TAG, "flow_carousel focus=" + focus + " count=" + itemCount
                + " centerCx=" + centerCx + " rightCx=" + rightCx
                + " rightRotY=" + rightRotY + " rightWidth=" + rightWidth);
    }
}
