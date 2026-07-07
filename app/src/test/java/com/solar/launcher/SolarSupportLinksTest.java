package com.solar.launcher;

import org.junit.Test;

/** Guards support URL constants used on wheel-navigated Settings screens. */
public class SolarSupportLinksTest {

    @Test
    public void githubIssuesUrlIsStableAndNonEmpty() {
        String url = SolarSupportLinks.githubIssuesUrlForDisplay();
        if (url == null || url.trim().isEmpty()) {
            throw new AssertionError("github issues url empty");
        }
        if (!url.contains("github.com") || !url.contains("issues")) {
            throw new AssertionError("unexpected github issues url: " + url);
        }
    }

    @Test
    public void kofiUrlIsStableHttps() {
        String url = SolarSupportLinks.kofiUrlForDisplay();
        if (url == null || !url.startsWith("https://ko-fi.com/")) {
            throw new AssertionError("unexpected kofi url: " + url);
        }
    }
}
