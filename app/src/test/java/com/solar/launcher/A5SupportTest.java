package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-11 — A5 family detection + button remap matrix + NP gesture classifier + portrait chrome.
 */
public class A5SupportTest {

    @After
    public void tearDown() {
        A5InputKeys.endRemapPassthrough();
        A5InputKeys.resetInvertVerticalForTest();
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void timmkooModelMapsToA5() {
        String family = DeviceFeatures.detectFamilyForTest("", "", 17, "Timmkoo A5", "Timmkoo");
        if (!"a5".equals(family)) throw new AssertionError("expected a5 got " + family);
    }

    @Test
    public void a5BeforeSdkFallback() {
        // SDK 19 would be Y2 without A5 tokens — A5 must win.
        String family = DeviceFeatures.detectFamilyForTest("", "", 19, "A5", "timmkoo");
        if (!"a5".equals(family)) throw new AssertionError("expected a5 got " + family);
    }

    @Test
    public void isY1ExclusiveNotA5() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (DeviceFeatures.isY1()) throw new AssertionError("A5 must not be isY1");
        if (DeviceFeatures.isY2()) throw new AssertionError("A5 must not be isY2");
        if (!DeviceFeatures.isA5()) throw new AssertionError("expected isA5");
        if (!DeviceFeatures.hasTouchscreen()) throw new AssertionError("A5 touchscreen");
        if (!DeviceFeatures.showsOverlayVolumeLockChips()) {
            throw new AssertionError("A5 needs overlay vol/lock chips");
        }
    }

    @Test
    public void a5DualStorageFailOpenSingleVolume() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        // JVM host usually has no /storage/sdcard1 — primary falls back to sdcard0.
        String primary = DeviceFeatures.primaryStoragePath();
        if (!"/storage/sdcard0".equals(primary) && !"/storage/sdcard1".equals(primary)) {
            throw new AssertionError("a5 primary unexpected: " + primary);
        }
        // Secondary may be null on single-volume hosts (fail-open).
        if (!"A5".equals(DeviceFeatures.deviceModelLabel())) {
            throw new AssertionError("label");
        }
    }

    @Test
    public void y1StillExclusive() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!DeviceFeatures.isY1() || DeviceFeatures.isY2() || DeviceFeatures.isA5()) {
            throw new AssertionError("y1 exclusive flags");
        }
    }

    @Test
    public void remapMatrixSelfCheck() {
        A5InputKeys.selfCheckRemapMatrix();
    }

    @Test
    public void emulatorInputMapSelfCheck() {
        EmulatorInputMap.selfCheck();
    }

    @Test
    public void delAndEscAreBackOnA5() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!Y1InputKeys.isBackKey(KeyEvent.KEYCODE_DEL)) {
            throw new AssertionError("DEL→back on A5");
        }
        if (!Y1InputKeys.isBackKey(KeyEvent.KEYCODE_ESCAPE)) {
            throw new AssertionError("ESC→back on A5");
        }
    }

    @Test
    public void keyCodeDispRemapUsesA5Family() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        // 2026-07-14 — Middle BACK(4) → OK on menus and NP; power MEDIA_STOP(86) → Back.
        int mapped = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.FACE_MIDDLE, false);
        if (mapped != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("face mid → center got " + mapped);
        }
        int npOk = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.FACE_MIDDLE, true);
        if (npOk != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("NP face mid → center got " + npOk);
        }
        int powerBack = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.SIDE_POWER, false);
        if (powerBack != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("power → back got " + powerBack);
        }
        int powerNp = A5InputKeys.remapToSolarKeyCode(null, 86, true);
        if (powerNp != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("86 → back on NP got " + powerNp);
        }
        // 2026-07-14 — Power ints: never confuse with face mid; Back scan0 stays Back.
        if (!A5InputKeys.isSidePower(86, 116) || !A5InputKeys.isSidePower(26, 116)) {
            throw new AssertionError("86/POWER+116 must be side power");
        }
        if (A5InputKeys.isSidePower(4, 158)) {
            throw new AssertionError("face mid must not be side power");
        }
        if (A5InputKeys.remapToSolarKeyCode(null, 4, 0, false) >= 0) {
            throw new AssertionError("power-as-Back scan0 must not become OK");
        }
        if (A5InputKeys.remapToSolarKeyCode(null, 4, 158, false) != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("face mid scan158 must stay OK");
        }
        // 2026-07-14 — Menus face L/R stay wheel; NP face L/R → prev/next.
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, false) >= 0) {
            throw new AssertionError("menus face left must passthrough");
        }
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_DOWN, false) >= 0) {
            throw new AssertionError("menus face right must passthrough");
        }
        int npPrev = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, true);
        if (npPrev != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("NP face left → prev got " + npPrev);
        }
        int npNext = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_DOWN, true);
        if (npNext != KeyEvent.KEYCODE_MEDIA_NEXT) {
            throw new AssertionError("NP face right → next got " + npNext);
        }
        // Side volume never remapped — MediaVolumeControl / Solar HUD path.
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.SIDE_VOL_UP, true) >= 0
                || A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.SIDE_VOL_DOWN, false) >= 0) {
            throw new AssertionError("side volume must not remap");
        }
    }

    /** 2026-07-14 — Landscape flips vertical face/wheel; portrait menus stay passthrough. */
    @Test
    public void landscapeInvertsVerticalNav() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (A5InputKeys.swapVerticalNavKey(A5InputKeys.NAV_UP) != A5InputKeys.NAV_DOWN) {
            throw new AssertionError("swap UP→DOWN");
        }
        if (A5InputKeys.swapVerticalNavKey(A5InputKeys.NAV_DOWN) != A5InputKeys.NAV_UP) {
            throw new AssertionError("swap DOWN→UP");
        }
        if (A5InputKeys.swapVerticalNavKey(126) != KeyEvent.KEYCODE_MEDIA_PAUSE) {
            throw new AssertionError("swap MEDIA_PLAY→PAUSE");
        }
        // Portrait (no override): menus still passthrough.
        A5InputKeys.setInvertVerticalForTest(Boolean.FALSE);
        if (A5InputKeys.shouldInvertVerticalNav(null)) {
            throw new AssertionError("portrait must not invert");
        }
        if (A5InputKeys.needsRemap(null, A5InputKeys.NAV_UP, false)) {
            throw new AssertionError("portrait menus must not need face remap");
        }
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, false) >= 0) {
            throw new AssertionError("portrait menus UP passthrough");
        }
        // Landscape override: menus UP→DOWN; NP raw UP → next after invert.
        A5InputKeys.setInvertVerticalForTest(Boolean.TRUE);
        if (!A5InputKeys.shouldInvertVerticalNav(null)) {
            throw new AssertionError("landscape must invert");
        }
        if (!A5InputKeys.needsRemap(null, A5InputKeys.NAV_UP, false)) {
            throw new AssertionError("landscape menus need face remap");
        }
        int menusUp = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, false);
        if (menusUp != A5InputKeys.NAV_DOWN) {
            throw new AssertionError("landscape menus UP→DOWN got " + menusUp);
        }
        int menusDown = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_DOWN, false);
        if (menusDown != A5InputKeys.NAV_UP) {
            throw new AssertionError("landscape menus DOWN→UP got " + menusDown);
        }
        // 2026-07-14 — After one hop, passthrough blocks re-invert (no UP↔DOWN ping-pong swallow).
        A5InputKeys.beginRemapPassthrough();
        try {
            if (A5InputKeys.shouldInvertVerticalNav(null)) {
                throw new AssertionError("passthrough must suppress landscape invert");
            }
            if (A5InputKeys.needsRemap(null, A5InputKeys.NAV_DOWN, false)) {
                throw new AssertionError("passthrough must not remap flipped DOWN again");
            }
            if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_DOWN, false) >= 0) {
                throw new AssertionError("passthrough DOWN must passthrough as wheel");
            }
        } finally {
            A5InputKeys.endRemapPassthrough();
        }
        if (A5InputKeys.isRemapPassthrough()) {
            throw new AssertionError("passthrough must clear");
        }
        // After clear, landscape invert still wanted for a fresh physical press.
        if (!A5InputKeys.needsRemap(null, A5InputKeys.NAV_UP, false)) {
            throw new AssertionError("fresh landscape press still needs invert");
        }
        int npUp = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, true);
        if (npUp != KeyEvent.KEYCODE_MEDIA_NEXT) {
            throw new AssertionError("landscape NP UP→NEXT got " + npUp);
        }
        int npDown = A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_DOWN, true);
        if (npDown != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("landscape NP DOWN→PREV got " + npDown);
        }
        // Y1 unchanged — family pin blocks invert even with override true.
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (A5InputKeys.shouldInvertVerticalNav(null)
                || A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, false) >= 0) {
            throw new AssertionError("Y1 must ignore A5 landscape invert");
        }
    }

    @Test
    public void mediaStopIsBackNotPlayPauseOnA5() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!Y1InputKeys.isBackKey(86)) {
            throw new AssertionError("86 must be Back on A5");
        }
        if (Y1InputKeys.isPlayPauseKey(86)) {
            throw new AssertionError("86 must not be play/pause on A5");
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!Y1InputKeys.isPlayPauseKey(86)) {
            throw new AssertionError("86 remains play/pause on Y1");
        }
    }

    @Test
    public void npGestureTapAndSwipe() {
        if (NowPlayingTouchGestures.classify(0, 0, 50)
                != NowPlayingTouchGestures.Gesture.TAP_PLAY_PAUSE) {
            throw new AssertionError("tap");
        }
        if (NowPlayingTouchGestures.classify(-80, 5, 100)
                != NowPlayingTouchGestures.Gesture.NEXT) {
            throw new AssertionError("swipe next");
        }
        if (NowPlayingTouchGestures.classify(80, 5, 100)
                != NowPlayingTouchGestures.Gesture.PREVIOUS) {
            throw new AssertionError("swipe prev");
        }
        if (NowPlayingTouchGestures.classify(5, 90, 100)
                != NowPlayingTouchGestures.Gesture.DISMISS) {
            throw new AssertionError("dismiss");
        }
    }

    @Test
    public void navModeDefaultsPortraitOrientation() {
        if (!A5NavigationMode.NAV_FACE.equals(A5NavigationMode.menuNav(null))) {
            throw new AssertionError("default face nav");
        }
        // 2026-07-14 — Default was auto; now portrait for tall A5.
        if (!A5NavigationMode.ORIENT_PORTRAIT.equals(A5NavigationMode.orientation(null))) {
            throw new AssertionError("default portrait orientation");
        }
        if (A5NavigationMode.landscapeThemeScale(null) != 1f) {
            throw new AssertionError("non-A5 scale 1");
        }
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (A5NavigationMode.portraitWidthPx() != 240 || A5NavigationMode.portraitHeightPx() != 320) {
            throw new AssertionError("portrait dims");
        }
        if (A5PortraitChrome.STRIP_HEIGHT_DP < 72) {
            throw new AssertionError("strip height");
        }
    }

    /** 2026-07-14 — Landscape experiment pref + 240p scale helpers. */
    @Test
    public void a5LandscapeExperimentAndScale() {
        if (A5LandscapeExperiment.isEnabledForTest(false)) {
            throw new AssertionError("pref false");
        }
        if (!A5LandscapeExperiment.isEnabledForTest(true)) {
            throw new AssertionError("pref true");
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (A5LandscapeExperiment.isAvailable()) {
            throw new AssertionError("not available on Y1");
        }
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (!A5LandscapeExperiment.isAvailable()) {
            throw new AssertionError("available on A5");
        }
        if (A5NavigationMode.landscapeWidthPx() != 320
                || A5NavigationMode.landscapeHeightPx() != 240) {
            throw new AssertionError("landscape dims 320x240");
        }
        // 240/360 factor: 480→320, 360→240, 45→30.
        float land = 240f / 360f;
        if (A5NavigationMode.scaleLayoutPx(480, land) != 320) {
            throw new AssertionError("480→320");
        }
        if (A5NavigationMode.scaleLayoutPx(360, land) != 240) {
            throw new AssertionError("360→240");
        }
        if (A5NavigationMode.scaleLayoutPx(45, land) != 30) {
            throw new AssertionError("45→30");
        }
        if (A5NavigationMode.scaleLayoutPx(100, 1f) != 100) {
            throw new AssertionError("identity scale");
        }
        // 2026-07-14 — Scaled NP must remain a miniature Y1 (5+235+5+235)×(45+267+48).
        if (!A5NavigationMode.npLandscapeCompositionFits(land, 320, 240)) {
            throw new AssertionError("NP landscape composition 320×240");
        }
        if (!A5NavigationMode.npLandscapeCompositionFits(1f, 480, 360)) {
            throw new AssertionError("NP identity composition 480×360");
        }
        // Non-A5 → factor 1 even with null context.
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (A5NavigationMode.landscapeThemeScale(null) != 1f) {
            throw new AssertionError("null ctx scale 1");
        }
    }

    /**
     * 2026-07-14 — Context/queue modal must not get A5 face→MEDIA_PREV/NEXT remap,
     * and must consume transport so NP behind cannot skip.
     */
    @Test
    public void contextModalBlocksNpFaceRemapAndMediaTransport() {
        // NP free → face remap allowed.
        if (!MainActivity.shouldA5NpFaceRemap(true, false, false)) {
            throw new AssertionError("NP free must face-remap");
        }
        // Context open → no face→skip remap (keys stay wheel DPAD for modal).
        if (MainActivity.shouldA5NpFaceRemap(true, true, false)) {
            throw new AssertionError("context open must not face-remap");
        }
        // Scrub mode → no face→skip either.
        if (MainActivity.shouldA5NpFaceRemap(true, false, true)) {
            throw new AssertionError("scrub must not face-remap");
        }
        // Non-player menus never face-remap.
        if (MainActivity.shouldA5NpFaceRemap(false, false, false)) {
            throw new AssertionError("menus must not face-remap");
        }
        if (!MainActivity.contextMenuConsumesMediaTransport(true)) {
            throw new AssertionError("open modal must consume transport");
        }
        if (MainActivity.contextMenuConsumesMediaTransport(false)) {
            throw new AssertionError("closed modal must not claim transport");
        }
        // Remap table with context-style nowPlaying=false: face stays DPAD on portrait A5.
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, false) >= 0) {
            throw new AssertionError("context-style remap must leave face as wheel");
        }
        if (A5InputKeys.remapToSolarKeyCode(null, A5InputKeys.NAV_UP, true)
                != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("NP-free still remaps face→prev");
        }
    }

    @Test
    public void queueViewportStaysThreeRows() {
        if (QueueMoveWindow.VISIBLE_ROWS != 3) {
            throw new AssertionError("queue must keep 3 visible tracks");
        }
    }
}
