package com.solar.launcher.youtube;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 2026-07-15 — Native client JSON matches YouTubeResultJson shapes. */
public class YouTubeClientJsonTest {

    @Test
    public void videosJsonRoundTrip() throws Exception {
        List<YouTubeVideo> vids = new ArrayList<YouTubeVideo>();
        vids.add(new YouTubeVideo("abc", "Title", "Author", "3:45"));
        String json = YouTubeClient.videosToJson(vids);
        List<YouTubeVideo> parsed = YouTubeResultJson.parseVideos(json);
        if (parsed.size() != 1 || !"abc".equals(parsed.get(0).id)) {
            throw new AssertionError("video round-trip failed: " + json);
        }
        if (!"3:45".equals(parsed.get(0).duration)) {
            throw new AssertionError("duration lost: " + parsed.get(0).duration);
        }
    }

    @Test
    public void commentsJsonRoundTrip() throws Exception {
        List<YouTubeComment> comments = new ArrayList<YouTubeComment>();
        comments.add(new YouTubeComment("Bob", "Hello"));
        String json = YouTubeClient.commentsToJson(comments);
        List<YouTubeComment> parsed = YouTubeResultJson.parseComments(json);
        if (parsed.size() != 1 || !"Bob".equals(parsed.get(0).author)) {
            throw new AssertionError("comment round-trip failed: " + json);
        }
    }

    @Test
    public void qualityLadder() {
        if (!"360".equals(YouTubeQuality.fallbackVideoQuality("480"))) {
            throw new AssertionError("480 -> 360");
        }
        if (YouTubeQuality.fallbackVideoQuality("360") != null) {
            throw new AssertionError("360 has no fallback");
        }
    }
}
