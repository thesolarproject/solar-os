package com.solar.launcher.podcast;

import org.junit.Test;

/** 2026-07-15 — Subscription store self-check. */
public class PodcastSubscriptionsTest {
    @Test
    public void selfCheck() {
        PodcastSubscriptions.selfCheck();
        PodcastPlayedStore.selfCheck();
    }
}
