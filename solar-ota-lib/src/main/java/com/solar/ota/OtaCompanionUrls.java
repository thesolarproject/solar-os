package com.solar.ota;

/**
 * 2026-07-06 — OTA companion download URLs (JJ Launcher + Rockbox Y1) with ordered fallbacks.
 * Layman: where Solar fetches sidecar launcher/music apps before a Solar update installs.
 * Technical: solar-update Pages primary; github rockbox-y1 + update.zip mirrors on miss.
 */
public final class OtaCompanionUrls {

    public static final String SOLAR_UPDATE_BASE =
            "https://thesolarproject.github.io/solar-update/";

    public static final String JJ_APK_URL = SOLAR_UPDATE_BASE + "jj_latest.apk";

    public static final String RB_Y1_LATEST_URL = SOLAR_UPDATE_BASE + "rb_y1_latest.apk";

    public static final String ROCKBOX_GITHUB_URL =
            "https://github.com/rockbox-y1/rockbox/releases/latest/download/rockbox.apk";

    public static final String ROCKBOX_MIRROR_URL = SOLAR_UPDATE_BASE + "rockbox.apk";

    public static final String ROCKBOX_UPDATE_ZIP_URL = SOLAR_UPDATE_BASE + "update.zip";

    /** Rockbox APK install ladder — rb_y1_latest then github then solar-update mirror. */
    public static final String[] ROCKBOX_INSTALL_URLS = {
            RB_Y1_LATEST_URL,
            ROCKBOX_GITHUB_URL,
            ROCKBOX_MIRROR_URL
    };

    /** Native libs: prefer APK unzip (github → mirror) then update.zip. */
    public static final String[] ROCKBOX_LIBS_APK_URLS = {
            ROCKBOX_GITHUB_URL,
            ROCKBOX_MIRROR_URL
    };

    public static final String JJ_PACKAGE = "com.themoon.y1";
    public static final String ROCKBOX_PACKAGE = "org.rockbox";

    public static final String JJ_SYSTEM_APK = "/system/app/com.themoon.y1.apk";
    public static final String ROCKBOX_SYSTEM_APK = "/system/app/org.rockbox.apk";

    public static final String FILE_JJ = "jj_latest.apk";
    public static final String FILE_ROCKBOX_INSTALL = "rockbox_install.apk";
    public static final String FILE_ROCKBOX_LIBS = "rockbox_libs.apk";
    public static final String FILE_UPDATE_ZIP = "update.zip";

    private OtaCompanionUrls() {}

    /** Self-check for unit tests — URL order and non-empty constants. */
    public static void selfCheck() {
        if (!JJ_APK_URL.contains("jj_latest.apk")) {
            throw new AssertionError("JJ URL must end with jj_latest.apk");
        }
        if (ROCKBOX_INSTALL_URLS.length < 2) {
            throw new AssertionError("Rockbox install URL ladder too short");
        }
        if (!ROCKBOX_INSTALL_URLS[0].contains("rb_y1_latest")) {
            throw new AssertionError("rb_y1_latest must be primary Rockbox install URL");
        }
        if (!ROCKBOX_LIBS_APK_URLS[0].contains("github.com")) {
            throw new AssertionError("github rockbox.apk must be primary lib source");
        }
    }
}
