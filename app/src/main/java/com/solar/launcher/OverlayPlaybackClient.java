package com.solar.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * IPC from overlay process (:overlay) to main process for queue play / reorder and background transport.
 */
public final class OverlayPlaybackClient {

    public static final String ACTION_OVERLAY_QUEUE_PLAY =
            "com.solar.launcher.action.OVERLAY_QUEUE_PLAY";
    public static final String ACTION_OVERLAY_QUEUE_MOVE =
            "com.solar.launcher.action.OVERLAY_QUEUE_MOVE";
    public static final String ACTION_OVERLAY_TRANSPORT =
            "com.solar.launcher.action.OVERLAY_TRANSPORT";
    public static final String EXTRA_QUEUE_INDEX = "queue_index";
    public static final String EXTRA_QUEUE_FROM = "queue_from";
    public static final String EXTRA_QUEUE_TO = "queue_to";
    /** {@link KeyEvent#KEYCODE_MEDIA_NEXT}, PLAY_PAUSE, etc. */
    public static final String EXTRA_TRANSPORT_KEY = "transport_key_code";

    private static final String SOLAR_PKG = "com.solar.launcher";

    private OverlayPlaybackClient() {}

    public static void playQueueIndex(Context ctx, int index) {
        send(ctx, ACTION_OVERLAY_QUEUE_PLAY, index, -1, -1, 0);
    }

    public static void moveQueueItem(Context ctx, int from, int to) {
        send(ctx, ACTION_OVERLAY_QUEUE_MOVE, -1, from, to, 0);
    }

    /** Side prev/next while global overlay is up — keeps Solar playback alive under Rockbox. */
    public static void transportKey(Context ctx, int keyCode) {
        if (ctx == null || keyCode == 0) return;
        send(ctx, ACTION_OVERLAY_TRANSPORT, -1, -1, -1, keyCode);
    }

    public static void togglePlayPause(Context ctx) {
        transportKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public static void skipNext(Context ctx) {
        transportKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static void skipPrevious(Context ctx) {
        transportKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /** Main process {@link MainActivity.MediaBtnReceiver} — BT keys while overlay is armed. */
    public static boolean deliverMediaButton(Context ctx, KeyEvent event) {
        if (ctx == null || event == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return false;
        }
        int code = event.getKeyCode();
        if (!Y1InputKeys.isTrackNextKey(code) && !Y1InputKeys.isTrackPreviousKey(code)
                && !Y1InputKeys.isPlayPauseKey(code)
                && code != KeyEvent.KEYCODE_HEADSETHOOK && code != 79) {
            return false;
        }
        if (Y1InputKeys.isWheelKey(code)) return false;
        transportKey(ctx, code);
        return true;
    }

    private static void send(Context ctx, String action, int index, int from, int to, int transportKey) {
        if (ctx == null) return;
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(SOLAR_PKG, SOLAR_PKG + ".OverlayPlaybackReceiver"));
        if (index >= 0) intent.putExtra(EXTRA_QUEUE_INDEX, index);
        if (from >= 0) intent.putExtra(EXTRA_QUEUE_FROM, from);
        if (to >= 0) intent.putExtra(EXTRA_QUEUE_TO, to);
        if (transportKey != 0) intent.putExtra(EXTRA_TRANSPORT_KEY, transportKey);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        ctx.sendBroadcast(intent);
    }
}
