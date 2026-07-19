package com.solar.launcher.platform;

import android.content.Context;

import com.solar.launcher.platform.SolarPlatformPrep.PrepResult;

/**
 * 2026-07-19 — Rockbox APK install from platform assets retired (Solar-only).
 * Layman: Solar no longer copies org.rockbox onto /system from the APK.
 * Was: staged Rockbox APK + libs from assets/platform/rockbox/. Reversal: restore install body.
 * Technical: always returns true so prep ladder never fails on missing rockbox block.
 */
public final class RockboxPlatformInstall {

    private RockboxPlatformInstall() {}

    /**
     * No-op success — Rockbox is launch-if-present only.
     * 2026-07-19
     */
    public static boolean ensure(Context ctx, PlatformPrepManifest manifest,
            boolean copyToSystem, PrepResult result) {
        return true;
    }
}
