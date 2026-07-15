package com.solar.launcher.youtube;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.DeviceFeatures;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-07-06 — Async broadcast client for notPipe commands via Xposed bridge.
 * Layman: asks notPipe to search, resolve streams, or save URLs; waits for answer broadcast.
 * Technical: requestId correlates NOTPIPE_CMD → NOTPIPE_RESULT; retries if process was asleep.
 * Reversal: delete; YouTube browse cannot reach notPipe backend.
 */
public final class NotPipeClient {

    public interface Callback {
        void onSuccess(String payloadJson);
        void onError(String message);
    }

    private static final long DEFAULT_TIMEOUT_MS = 12000L;
    /** 2026-07-14 — Cold Piped/Invidious pick can exceed 12s; keep probe short. */
    private static final long RESOLVE_TIMEOUT_MS = 28000L;
    private static final long PROBE_TIMEOUT_MS = 3000L;
    /** Staggered sends — bridge may register after wake MainActivity. */
    private static final long[] SEND_DELAYS_MS = {300L, 1200L, 2500L};

    private static volatile NotPipeClient sInstance;
    private static volatile boolean receiverRegistered;

    private final Context appCtx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Callback> pending =
            new ConcurrentHashMap<String, Callback>();

    private NotPipeClient(Context ctx) {
        appCtx = ctx.getApplicationContext();
        ensureResultReceiver();
    }

    public static NotPipeClient getInstance(Context ctx) {
        if (sInstance == null) {
            synchronized (NotPipeClient.class) {
                if (sInstance == null) {
                    sInstance = new NotPipeClient(ctx);
                }
            }
        }
        return sInstance;
    }

    /** PM knows notPipe package — does not prove bridge is alive. */
    public static boolean isNotPipeInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getPackageManager().getApplicationInfo(NotPipeIpc.NOTPIPE_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 2026-07-14 — Preferred progressive quality for this device.
     * Layman: Y2 tries 480p first; Y1 stays at 360p for the weak SoC.
     */
    public static String preferredVideoQuality() {
        return DeviceFeatures.isY2() ? "480" : "360";
    }

    /**
     * 2026-07-14 — Next lower quality after a failed resolve / IJK error, or null if none.
     * Ladder: 480 → 360; 360 has no further step.
     */
    public static String fallbackVideoQuality(String failedQuality) {
        if ("480".equals(failedQuality)) return "360";
        return null;
    }

    public void probe(Callback cb) {
        dispatch(NotPipeIpc.CMD_PROBE, null, null, null, null, PROBE_TIMEOUT_MS, cb);
    }

    public void fetchPopular(Callback cb) {
        dispatch(NotPipeIpc.CMD_POPULAR, null, null, null, null, DEFAULT_TIMEOUT_MS, cb);
    }

    public void search(String query, Callback cb) {
        dispatch(NotPipeIpc.CMD_SEARCH, query, null, null, null, DEFAULT_TIMEOUT_MS, cb);
    }

    /** Resolve video stream at device-preferred quality. */
    public void resolveStream(String videoId, Callback cb) {
        resolveStream(videoId, preferredVideoQuality(), cb);
    }

    /**
     * 2026-07-14 — Resolve at an explicit quality (quality ladder retries).
     * Layman: ask notPipe for this resolution; Solar may try 360 after 480 fails.
     */
    public void resolveStream(String videoId, String quality, Callback cb) {
        String q = (quality != null && quality.length() > 0) ? quality : preferredVideoQuality();
        dispatch(NotPipeIpc.CMD_RESOLVE_STREAM, null, videoId, q,
                NotPipeIpc.STREAM_KIND_VIDEO, RESOLVE_TIMEOUT_MS, cb);
    }

    /** Audio-only stream for save-to-Music — not a video container. */
    public void resolveAudioStream(String videoId, Callback cb) {
        dispatch(NotPipeIpc.CMD_RESOLVE_STREAM, null, videoId, "360",
                NotPipeIpc.STREAM_KIND_AUDIO, RESOLVE_TIMEOUT_MS, cb);
    }

    /**
     * Top-level comments for a video — Solar messaging-style detail UI.
     * Payload JSON array: [{author, content}, …] via notPipe multi-instance Metadata.
     */
    public void fetchComments(String videoId, Callback cb) {
        dispatch(NotPipeIpc.CMD_GET_COMMENTS, null, videoId, null, null,
                DEFAULT_TIMEOUT_MS, cb);
    }

    private void dispatch(String cmd, String query, String videoId, String quality,
            String streamKind, long timeoutMs, final Callback cb) {
        ensureResultReceiver();
        final String requestId = UUID.randomUUID().toString();
        pending.put(requestId, cb);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                Callback removed = pending.remove(requestId);
                if (removed != null) removed.onError("timeout");
            }
        }, timeoutMs);

        final Intent i = new Intent(NotPipeIpc.ACTION_CMD);
        // 2026-07-14 — Target SolarCmdReceiver (API 17 has no am -p; component is reliable).
        // Layman: drop the YouTube ask on notPipe's named mailbox, not the whole device.
        // Reversal: setPackage(NOTPIPE_PKG) only — needs dynamic Application receiver.
        i.setClassName(NotPipeIpc.NOTPIPE_PKG, NotPipeIpc.NOTPIPE_PKG + ".SolarCmdReceiver");
        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        i.putExtra(NotPipeIpc.EXTRA_CMD, cmd);
        i.putExtra(NotPipeIpc.EXTRA_REQUEST_ID, requestId);
        if (query != null) i.putExtra(NotPipeIpc.EXTRA_QUERY, query);
        if (videoId != null) i.putExtra(NotPipeIpc.EXTRA_VIDEO_ID, videoId);
        if (quality != null) i.putExtra(NotPipeIpc.EXTRA_QUALITY, quality);
        if (streamKind != null) i.putExtra(NotPipeIpc.EXTRA_STREAM_KIND, streamKind);

        NotPipeProcessWake.ensureAwake(appCtx);
        for (int n = 0; n < SEND_DELAYS_MS.length; n++) {
            final long delay = SEND_DELAYS_MS[n];
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (pending.containsKey(requestId)) {
                        appCtx.sendBroadcast(new Intent(i));
                    }
                }
            }, delay);
        }
    }

    private void ensureResultReceiver() {
        if (receiverRegistered) return;
        synchronized (NotPipeClient.class) {
            if (receiverRegistered) return;
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;
                    if (!NotPipeIpc.ACTION_RESULT.equals(intent.getAction())) return;
                    String requestId = intent.getStringExtra(NotPipeIpc.EXTRA_REQUEST_ID);
                    if (requestId == null) return;
                    Callback call = pending.remove(requestId);
                    if (call == null) return;
                    boolean ok = intent.getBooleanExtra(NotPipeIpc.EXTRA_OK, false);
                    if (ok) {
                        call.onSuccess(intent.getStringExtra(NotPipeIpc.EXTRA_PAYLOAD));
                    } else {
                        String err = intent.getStringExtra(NotPipeIpc.EXTRA_ERROR);
                        call.onError(err != null ? err : "notPipe error");
                    }
                }
            };
            appCtx.registerReceiver(receiver, new IntentFilter(NotPipeIpc.ACTION_RESULT));
            receiverRegistered = true;
        }
    }
}
