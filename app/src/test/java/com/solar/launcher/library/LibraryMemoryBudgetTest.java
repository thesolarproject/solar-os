package com.solar.launcher.library;

import org.junit.Test;

/**
 * 2026-07-18 — Library memory / segment / RAM index self-checks.
 */
public class LibraryMemoryBudgetTest {

    @Test
    public void budgetSelfCheck() {
        LibraryMemoryBudget.selfCheck();
    }

    @Test
    public void segmentSelfCheck() {
        LibrarySegmentCache.selfCheck();
    }

    @Test
    public void ramCacheSelfCheck() {
        LibraryRamCache.selfCheck();
    }
}
