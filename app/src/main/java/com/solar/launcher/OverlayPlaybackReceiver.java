package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * Main-process bridge — queue play/reorder and background transport from {@link SolarOverlayService}.
 */
public final class OverlayPlaybackReceiver extends BroadcastReceiver {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String action = intent.getAction();
        final int index = intent.getIntExtra(OverlayPlaybackClient.EXTRA_QUEUE_INDEX, -1);
        final int from = intent.getIntExtra(OverlayPlaybackClient.EXTRA_QUEUE_FROM, -1);
        final int to = intent.getIntExtra(OverlayPlaybackClient.EXTRA_QUEUE_TO, -1);
        final int transportKey = intent.getIntExtra(OverlayPlaybackClient.EXTRA_TRANSPORT_KEY, 0);
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                final boolean overlayUp = OverlayKeyGate.isOverlayKeysActive();
                MainActivity activity = MainActivity.peekForOverlay();
                if (activity == null && overlayUp) {
                    SolarLog.w("OverlayPlayback", "no MainActivity peek while overlay up: " + action);
                    return;
                }
                if (activity == null) {
                    activity = MainActivity.instance;
                }
                if (activity == null) {
                    SolarLog.w("OverlayPlayback", "no MainActivity for " + action);
                    return;
                }
                if (OverlayPlaybackClient.ACTION_OVERLAY_QUEUE_PLAY.equals(action)) {
                    activity.playQueueIndexFromOverlay(index);
                } else if (OverlayPlaybackClient.ACTION_OVERLAY_QUEUE_MOVE.equals(action)) {
                    activity.moveQueueIndexFromOverlay(from, to);
                } else if (OverlayPlaybackClient.ACTION_OVERLAY_TRANSPORT.equals(action)) {
                    activity.handleOverlayTransportKey(transportKey);
                }
                // IPC wakes Solar (HOME) — stay behind the app the global modal covers.
                if (overlayUp) {
                    activity.stayBackgroundForGlobalOverlay();
                }
            }
        });
    }
}
