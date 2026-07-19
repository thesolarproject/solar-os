package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Now Playing solo title suffixes.
 * 2026-07-19
 */
public class SoloStemTitlesTest {

    /** Append Instrumental / Acapella to a clean base title. 2026-07-19 */
    @Test
    public void displayTitleAppendsSuffix() {
        assertEquals("Song (Instrumental)",
                SoloStemTitles.displayTitle("Song", SoloMode.INSTRUMENTAL));
        assertEquals("Song (Acapella)",
                SoloStemTitles.displayTitle("Song", SoloMode.ACAPELLA));
    }

    /** Empty base → short fallback label. 2026-07-19 */
    @Test
    public void displayTitleEmptyFallback() {
        assertEquals("Instrumental", SoloStemTitles.displayTitle("", SoloMode.INSTRUMENTAL));
        assertEquals("Acapella", SoloStemTitles.displayTitle(null, SoloMode.ACAPELLA));
    }

    /** Re-play does not stack suffixes. 2026-07-19 */
    @Test
    public void stripPreventsStacking() {
        assertEquals("Song (Instrumental)",
                SoloStemTitles.displayTitle("Song (Instrumental)", SoloMode.INSTRUMENTAL));
        assertEquals("Song (Acapella)",
                SoloStemTitles.displayTitle("Song (Acapella)", SoloMode.ACAPELLA));
        assertEquals("Song (Instrumental)",
                SoloStemTitles.displayTitle("Song (Acapella)", SoloMode.INSTRUMENTAL));
    }
}
