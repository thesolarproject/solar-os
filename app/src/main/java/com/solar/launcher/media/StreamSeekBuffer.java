package com.solar.launcher.media;

/**
 * 2026-07-18 — Shared math for scrub-past-buffer (YouTube-style) on any media type.
 * Layman: how far the stream has arrived, and whether a scrub target is already playable.
 * Technical: combine download-byte edge with MediaPlayer/IJK onBufferingUpdate percent.
 * Reversal: delete class; inline edge checks in MainActivity / MediaSuiteHost only.
 */
public final class StreamSeekBuffer {

    /** Unknown buffer percent — ignore percent lane. */
    public static final int PERCENT_UNKNOWN = -1;

    private StreamSeekBuffer() {}

    /**
     * Furthest playable ms from a 0–100 buffer percent and known duration.
     * Layman: “this much of the track has landed.”
     */
    public static int edgeFromPercent(int durationMs, int bufferPercent) {
        if (durationMs <= 0) return 0;
        if (bufferPercent < 0) return PERCENT_UNKNOWN;
        int pct = bufferPercent;
        if (pct > 100) pct = 100;
        if (pct >= 100) return durationMs;
        return (int) ((durationMs * (long) pct) / 100L);
    }

    /**
     * Restrictive buffered edge: min of byte-based and percent-based when both known.
     * Layman: trust the shorter “already here” estimate so scrub wait stays honest.
     * Technical: byteEdge &lt; 0 means unused; percentUnknown skips percent lane;
     * when neither known, return duration (treat as fully buffered / local).
     */
    public static int restrictiveEdgeMs(int durationMs, int byteEdgeMs, int bufferPercent) {
        int fromPct = edgeFromPercent(durationMs, bufferPercent);
        boolean haveByte = byteEdgeMs >= 0;
        boolean havePct = fromPct != PERCENT_UNKNOWN && bufferPercent >= 0;
        if (haveByte && havePct) {
            return Math.min(Math.max(0, byteEdgeMs), Math.max(0, fromPct));
        }
        if (haveByte) return Math.max(0, byteEdgeMs);
        if (havePct) return Math.max(0, fromPct);
        if (durationMs > 0) return durationMs;
        return Integer.MAX_VALUE;
    }

    /**
     * True when target is already inside the buffered region (with small slack).
     * Layman: OK to jump there now without a buffering wait.
     */
    public static boolean targetInBuffer(int targetMs, int edgeMs, int slackMs) {
        if (edgeMs >= Integer.MAX_VALUE / 4) return true;
        int slack = slackMs > 0 ? slackMs : 0;
        return targetMs <= edgeMs + slack;
    }

    /**
     * True when buffer has caught a pending scrub target.
     * Layman: download/catch-up finished — start playing the spot you picked.
     */
    public static boolean pendingReady(int pendingTargetMs, int edgeMs, int catchUpSlackMs,
            int bufferPercent) {
        if (pendingTargetMs < 0) return false;
        if (bufferPercent >= 99) return true;
        int slack = catchUpSlackMs > 0 ? catchUpSlackMs : 0;
        if (edgeMs >= Integer.MAX_VALUE / 4) return true;
        return edgeMs + slack >= pendingTargetMs;
    }
}
