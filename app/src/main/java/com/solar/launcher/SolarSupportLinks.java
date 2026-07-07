package com.solar.launcher;

/**
 * Public support URLs shown on Settings screens — readable on Y1/Y2 without a keyboard.
 * Users copy or type these on a PC browser; we do not open an in-app browser.
 */
public final class SolarSupportLinks {

    /** GitHub Issues page for bug reports and feature requests (org matches the Solar repo). */
    public static final String GITHUB_ISSUES_DISPLAY = "github.com/thesolarproject/issues";

    /** Ko-fi donation page for supporting ongoing Solar development. */
    public static final String KOFI_DISPLAY = "https://ko-fi.com/thesolarproject";

    private SolarSupportLinks() {}

    /** Full URL text for the Report Issue settings screen body. */
    public static String githubIssuesUrlForDisplay() {
        return GITHUB_ISSUES_DISPLAY;
    }

    /** Full URL text for the Donations settings screen body. */
    public static String kofiUrlForDisplay() {
        return KOFI_DISPLAY;
    }
}
