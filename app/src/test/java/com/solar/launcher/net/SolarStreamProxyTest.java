package com.solar.launcher.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SolarStreamProxy}.
 */
public class SolarStreamProxyTest {

    @Before
    public void setUp() throws IOException {
        SolarStreamProxy.stop();
    }

    @After
    public void tearDown() {
        SolarStreamProxy.stop();
    }

    @Test
    public void testProxyUrlWhenNotStartedReturnsOriginal() {
        String target = "https://googlevideo.com/videoplayback?id=123";
        assertEquals(target, SolarStreamProxy.proxyUrl(target));
    }

    @Test
    public void testProxyUrlLeavesLocalAndEmptyUntouched() throws IOException {
        SolarStreamProxy.ensureStarted(null);
        assertEquals("", SolarStreamProxy.proxyUrl(""));
        assertEquals(null, SolarStreamProxy.proxyUrl(null));
        assertEquals("/sdcard/Videos/test.mp4", SolarStreamProxy.proxyUrl("/sdcard/Videos/test.mp4"));
        assertEquals("file:///sdcard/Videos/test.mp4", SolarStreamProxy.proxyUrl("file:///sdcard/Videos/test.mp4"));
        assertEquals("http://127.0.0.1:8080/local", SolarStreamProxy.proxyUrl("http://127.0.0.1:8080/local"));
    }

    @Test
    public void testProxyUrlWrapsRemoteHttps() throws IOException {
        SolarStreamProxy.ensureStarted(null);
        String target = "https://googlevideo.com/videoplayback?id=123&itag=18";
        String proxied = SolarStreamProxy.proxyUrl(target);
        assertTrue("Proxied URL must start with loopback proxy prefix: " + proxied,
                proxied.startsWith("http://127.0.0.1:"));
        assertTrue("Proxied URL must encode target URL: " + proxied,
                proxied.contains("/stream?url=https%3A%2F%2Fgooglevideo.com%2Fvideoplayback%3Fid%3D123%26itag%3D18"));
    }
}
