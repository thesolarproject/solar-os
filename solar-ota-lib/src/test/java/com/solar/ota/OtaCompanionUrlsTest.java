package com.solar.ota;

import org.junit.Test;

/** 2026-07-06 — OTA companion URL constants and fallback order. */
public class OtaCompanionUrlsTest {

    @Test
    public void selfCheckPasses() {
        OtaCompanionUrls.selfCheck();
    }

    @Test
    public void installLadderStartsWithRbY1Latest() {
        if (!OtaCompanionUrls.ROCKBOX_INSTALL_URLS[0].contains("rb_y1_latest")) {
            throw new AssertionError("primary Rockbox install URL must be rb_y1_latest");
        }
    }

    @Test
    public void libsLadderEndsWithUpdateZipConstant() {
        if (!OtaCompanionUrls.ROCKBOX_UPDATE_ZIP_URL.endsWith("update.zip")) {
            throw new AssertionError("update.zip fallback URL missing");
        }
    }
}
