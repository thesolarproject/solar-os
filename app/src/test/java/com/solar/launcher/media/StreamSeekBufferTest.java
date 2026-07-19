package com.solar.launcher.media;

import org.junit.Test;

/** 2026-07-18 — StreamSeekBuffer edge / pending-ready math. */
public class StreamSeekBufferTest {

    @Test
    public void edgeFromPercent_scalesDuration() {
        if (StreamSeekBuffer.edgeFromPercent(100_000, 50) != 50_000) {
            throw new AssertionError("50%");
        }
        if (StreamSeekBuffer.edgeFromPercent(100_000, 100) != 100_000) {
            throw new AssertionError("100%");
        }
        if (StreamSeekBuffer.edgeFromPercent(100_000, StreamSeekBuffer.PERCENT_UNKNOWN)
                != StreamSeekBuffer.PERCENT_UNKNOWN) {
            throw new AssertionError("unknown");
        }
    }

    @Test
    public void restrictiveEdge_prefersShorterLane() {
        int edge = StreamSeekBuffer.restrictiveEdgeMs(100_000, 40_000, 80);
        if (edge != 40_000) throw new AssertionError("byte shorter than percent");
        edge = StreamSeekBuffer.restrictiveEdgeMs(100_000, 90_000, 40);
        if (edge != 40_000) throw new AssertionError("percent shorter than byte");
        edge = StreamSeekBuffer.restrictiveEdgeMs(100_000, -1, -1);
        if (edge != 100_000) throw new AssertionError("local full duration");
    }

    @Test
    public void pendingReady_catchesTarget() {
        if (!StreamSeekBuffer.pendingReady(60_000, 59_500, 750, 50)) {
            throw new AssertionError("slack catch");
        }
        if (StreamSeekBuffer.pendingReady(60_000, 50_000, 750, 50)) {
            throw new AssertionError("still short");
        }
        if (!StreamSeekBuffer.pendingReady(60_000, 10_000, 750, 99)) {
            throw new AssertionError("percent full");
        }
    }

    @Test
    public void targetInBuffer_slack() {
        if (!StreamSeekBuffer.targetInBuffer(10_500, 10_000, 500)) {
            throw new AssertionError("in slack");
        }
        if (StreamSeekBuffer.targetInBuffer(12_000, 10_000, 500)) {
            throw new AssertionError("past slack");
        }
    }
}
