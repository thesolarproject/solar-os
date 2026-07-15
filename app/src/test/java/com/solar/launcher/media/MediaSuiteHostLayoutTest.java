package com.solar.launcher.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-15 — Locks media suite full-width gate used by MainActivity layout helpers.
 * Layman: YouTube/radio/videos must count as media suite so lists go full width.
 * Reversal: delete this test if isMediaSuiteState is removed.
 */
public class MediaSuiteHostLayoutTest {

    @Test
    public void youtubeAndHubsAreMediaSuiteStates() {
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_YOUTUBE_BROWSE));
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_YOUTUBE_DETAIL));
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_VIDEO_HUB));
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_VIDEOS));
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_RADIO));
        assertTrue(MediaSuiteHost.isMediaSuiteState(MediaSuiteHost.STATE_PHOTOS));
        assertTrue(MediaSuiteHost.isMediaListBrowseState(MediaSuiteHost.STATE_YOUTUBE_DETAIL));
    }

    @Test
    public void unrelatedStatesAreNotMediaSuite() {
        assertFalse(MediaSuiteHost.isMediaSuiteState(0));
        assertFalse(MediaSuiteHost.isMediaSuiteState(3));
        assertFalse(MediaSuiteHost.isMediaListBrowseState(MediaSuiteHost.STATE_VIDEO_PLAYER));
    }
}
