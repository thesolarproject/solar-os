package com.solar.launcher.jellyfin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 2026-07-14: Jellyfin URL normalize + universal stream builder smoke. */
public class JellyfinClientTest {

    @Test
    public void normalizeLanAddsDefaultPort() {
        String u = JellyfinClient.normalizeServerUrl("192.168.1.20");
        assertTrue(u.startsWith("http://"));
        assertTrue(u.contains(":8096"));
    }

    @Test
    public void normalizeKeepsExplicitPort() {
        assertEquals("http://192.168.1.20:8097",
                JellyfinClient.normalizeServerUrl("http://192.168.1.20:8097"));
    }

    @Test
    public void universalStreamIsMp3() {
        String url = JellyfinClient.buildUniversalStreamUrl(
                "http://192.168.1.20:8096", "user-1", "tok-abc", "item-9");
        assertTrue(url.contains("/Audio/item-9/universal"));
        assertTrue(url.contains("Container=mp3"));
        assertTrue(url.contains("AudioCodec=mp3"));
        assertTrue(url.contains("api_key=tok-abc"));
        assertTrue(url.contains("UserId=user-1"));
    }
}
