package com.solar.launcher.platform;

import android.view.KeyEvent;

import com.solar.launcher.Y1InputKeys;

/**
 * 2026-07-05 — Y1/Y2 wheel key routing for platform prep wizard (not stock Android focus).
 * Reversal: delete; wizard falls back to touch/focus only.
 */
public final class PlatformPrepWizardKeys {

    private PlatformPrepWizardKeys() {}

    /** Center / play-pause confirm on key-up — matches MainActivity list row OK. */
    public static boolean shouldActivateAction(int keyCode, int action) {
        if (action != KeyEvent.ACTION_UP) return false;
        return Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isPlayPauseKey(keyCode);
    }

    /** Back dismisses wizard into Solar on key-up — even while prep is running. */
    public static boolean shouldDismissWizard(int keyCode, int action) {
        if (action != KeyEvent.ACTION_UP) return false;
        return Y1InputKeys.isBackKey(keyCode);
    }

    /** Consume center/play on key-down so stock handlers do not steal the press. */
    public static boolean shouldConsumeKeyDown(int keyCode) {
        return Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isPlayPauseKey(keyCode)
                || Y1InputKeys.isBackKey(keyCode);
    }
}
