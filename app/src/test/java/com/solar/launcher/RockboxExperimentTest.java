package com.solar.launcher;

import org.junit.Test;

/** 2026-07-11 — Rockbox experiment pref gate. */
public class RockboxExperimentTest {

    @Test
    public void disabledByDefault() {
        if (RockboxExperiment.isEnabledForTest(false)) {
            throw new AssertionError("expected off");
        }
    }

    @Test
    public void enabledWhenPrefTrue() {
        if (!RockboxExperiment.isEnabledForTest(true)) {
            throw new AssertionError("expected on");
        }
    }
}
