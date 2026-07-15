package com.solar.launcher.radio.fm;

import org.junit.Test;

/**
 * 2026-07-15 — FM package matcher + airplane snapshot pure logic.
 */
public class FmAirplaneModeHelperTest {

    @Test
    public void fmLikePackages() {
        if (!FmAirplaneModeHelper.isFmLikePackage("com.mediatek.FMRadio")) {
            throw new AssertionError("stock MTK");
        }
        if (!FmAirplaneModeHelper.isFmLikePackage("com.innioasis.fm")) {
            throw new AssertionError("innioasis fm");
        }
        if (!FmAirplaneModeHelper.isFmLikePackage("com.example.FmRadioApp")) {
            throw new AssertionError("fmradio in name");
        }
        if (!FmAirplaneModeHelper.isFmLikePackage("com.vendor.fm")) {
            throw new AssertionError("ends with .fm");
        }
        if (FmAirplaneModeHelper.isFmLikePackage("com.facebook.messenger")) {
            throw new AssertionError("messenger must not match");
        }
        if (FmAirplaneModeHelper.isFmLikePackage("com.solar.launcher")) {
            throw new AssertionError("solar must not match");
        }
        if (FmAirplaneModeHelper.isFmLikePackage(null) || FmAirplaneModeHelper.isFmLikePackage("")) {
            throw new AssertionError("empty");
        }
    }

    @Test
    public void restoreAirplaneOnlyWhenWasOn() {
        FmAirplaneModeHelper.Snapshot on =
                FmAirplaneModeHelper.captureSnapshot(true, true, false);
        FmAirplaneModeHelper.Snapshot off =
                FmAirplaneModeHelper.captureSnapshot(false, true, false);
        if (!FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(on)) {
            throw new AssertionError("must restore when was on");
        }
        if (FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(off)) {
            throw new AssertionError("must not restore when was off");
        }
        if (FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(null)) {
            throw new AssertionError("null snap");
        }
    }

    @Test
    public void forceWifiOffDuringSession() {
        if (!FmAirplaneModeHelper.shouldForceWifiOffDuringSession()) {
            throw new AssertionError("FM session must force Wi-Fi off");
        }
    }
}
