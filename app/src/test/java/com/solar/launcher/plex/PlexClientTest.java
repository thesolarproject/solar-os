package com.solar.launcher.plex;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-14: Plex URL normalize + stream builder smoke (no Android Looper). */
public class PlexClientTest {

    @Test
    public void normalizeLanAddsDefaultPort() {
        String u = PlexClient.normalizeServerUrl("192.168.1.10");
        assertTrue(u.startsWith("http://"));
        assertTrue(u.contains(":32400"));
    }

    @Test
    public void normalizeKeepsExplicitPort() {
        assertEquals("http://192.168.1.10:32401",
                PlexClient.normalizeServerUrl("http://192.168.1.10:32401"));
    }

    @Test
    public void normalizeStripsTrailingSlash() {
        assertEquals("http://192.168.1.10:32400",
                PlexClient.normalizeServerUrl("http://192.168.1.10:32400/"));
    }

    @Test
    public void streamUsesPartKeyWhenMp3() {
        String url = PlexClient.buildStreamUrl("http://192.168.1.10:32400", "tok",
                "99", "/library/parts/1/file.mp3", "mp3");
        assertTrue(url.contains("/library/parts/1/file.mp3"));
        assertTrue(url.contains("X-Plex-Token=tok"));
        assertFalse(url.contains("transcode"));
    }

    @Test
    public void streamFallsBackToMp3Transcoder() {
        String url = PlexClient.buildStreamUrl("http://192.168.1.10:32400", "tok",
                "99", "/library/parts/1/file.flac", "flac");
        assertTrue(url.contains("/music/:/transcode/universal"));
        assertTrue(url.contains("audioCodec=mp3"));
        assertTrue(url.contains("library%2Fmetadata%2F99") || url.contains("/library/metadata/99"));
    }
}
