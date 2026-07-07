package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** 2026-07-06 — Release perf guard: hot-path debug loggers stay off by default. */
public class PerfBudgetGuardTest {

    @Test
    public void wheelHotPathDebugLoggerOffByDefault() {
        assertFalse(Debug531722Log.ENABLED);
    }
}
