package com.solar.launcher.globalcontext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * 2026-07-10 — Fast path for overlay keys into the companion :overlay process.
 * Layman: wheel / Back / OK reach the global menu without starting a service each tick.
 * Technical: Xposed / MainActivity sendBroadcast → this receiver (same process as shell)
 * → main-thread CompanionOverlayKeyGate handler. startService remains a fallback path.
 * Was: deliver on binder thread (ChipContextMenu UI never moved) + startService-only.
 * Reversal: remove receiver; OverlayKeyForwarder uses startService only again.
 */
public final class CompanionOverlayKeyReceiver extends BroadcastReceiver {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!CompanionOverlayActions.ACTION_OVERLAY_KEY.equals(intent.getAction())) return;
        // 2026-07-14 — Accept both names (Xposed overlay_key_*; older hold path key_code).
        // Was: only overlay_key_code → silent drop when sender used CompanionOverlayTriggers.key_code.
        int keyCode = intent.getIntExtra(CompanionOverlayActions.EXTRA_KEY_CODE, 0);
        if (keyCode == 0) {
            keyCode = intent.getIntExtra(CompanionOverlayTriggers.EXTRA_KEY_CODE, 0);
        }
        if (keyCode == 0) {
            // #region agent log
            DebugSession083511.log("H1", "CompanionOverlayKeyReceiver.onReceive",
                    "drop_keyCode0", "{\"hasOverlayExtra\":"
                            + intent.hasExtra(CompanionOverlayActions.EXTRA_KEY_CODE)
                            + ",\"hasLegacyExtra\":"
                            + intent.hasExtra(CompanionOverlayTriggers.EXTRA_KEY_CODE) + "}");
            // #endregion
            return;
        }
        final int code = keyCode;
        final int action = intent.getIntExtra(CompanionOverlayActions.EXTRA_KEY_ACTION,
                intent.getIntExtra("key_action", KeyEvent.ACTION_DOWN));
        // #region agent log
        DebugSession083511.log("H1", "CompanionOverlayKeyReceiver.onReceive",
                "queued", "{\"keyCode\":" + code + ",\"action\":" + action
                        + ",\"pid\":" + android.os.Process.myPid() + "}");
        // #endregion
        // ChipContextMenu / ChipOverlayHost must run on the UI thread.
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                boolean handled;
                if (action == KeyEvent.ACTION_UP) {
                    handled = CompanionOverlayKeyGate.deliverUp(code);
                } else {
                    handled = CompanionOverlayKeyGate.deliver(code);
                }
                // #region agent log
                DebugSession083511.log("H2", "CompanionOverlayKeyReceiver.onReceive",
                        "delivered", "{\"keyCode\":" + code + ",\"action\":" + action
                                + ",\"handled\":" + handled
                                + ",\"keysActive\":" + CompanionOverlayKeyGate.isKeysActive()
                                + "}");
                // #endregion
            }
        });
    }
}
