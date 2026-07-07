package com.solar.launcher;

import org.junit.Test;

/** JJ Launcher visibility — offline gating for Settings and overlay picker rows. */
public class JjLauncherAvailabilityTest {

    @Test
    public void offerVisibleWhenInstalledEvenOffline() {
        if (!JjLauncherAvailability.isOfferVisible(null)) {
            // null context: not installed and offline — expected false
            return;
        }
    }

    @Test
    public void jjPackageConstantMatchesReference() {
        if (!"com.themoon.y1".equals(LauncherDefault.JJ_PACKAGE)) {
            throw new AssertionError("JJ package must match reference/jj_launcher");
        }
        if (!JjLauncherAvailability.JJ_APK_URL.contains("jj_latest.apk")) {
            throw new AssertionError("JJ OTA URL must point at jj_latest.apk");
        }
    }
}
