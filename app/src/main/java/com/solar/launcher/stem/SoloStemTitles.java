package com.solar.launcher.stem;

/**
 * 2026-07-19 — Now Playing title for solo stems.
 * Layman: keep the song’s real name and add (Instrumental) or (Acapella).
 * Technical: strip prior suffix then append. Reversal: show raw ID3 only.
 */
public final class SoloStemTitles {

    public static final String SUFFIX_INSTRUMENTAL = " (Instrumental)";
    public static final String SUFFIX_ACAPELLA = " (Acapella)";

    private SoloStemTitles() {}

    /**
     * Display title from originating track title + mode.
     * Empty base → mode-only fallback label.
     * 2026-07-19
     */
    public static String displayTitle(String originatingTitle, SoloMode mode) {
        String base = stripSoloSuffix(originatingTitle);
        if (base == null || base.trim().isEmpty()) {
            return mode == SoloMode.INSTRUMENTAL ? "Instrumental" : "Acapella";
        }
        String suffix = mode == SoloMode.INSTRUMENTAL ? SUFFIX_INSTRUMENTAL : SUFFIX_ACAPELLA;
        return base.trim() + suffix;
    }

    /** Remove a prior solo suffix so re-play does not stack labels. 2026-07-19 */
    public static String stripSoloSuffix(String title) {
        if (title == null) return "";
        String t = title.trim();
        if (t.endsWith(SUFFIX_INSTRUMENTAL)) {
            return t.substring(0, t.length() - SUFFIX_INSTRUMENTAL.length()).trim();
        }
        if (t.endsWith(SUFFIX_ACAPELLA)) {
            return t.substring(0, t.length() - SUFFIX_ACAPELLA.length()).trim();
        }
        return t;
    }
}
