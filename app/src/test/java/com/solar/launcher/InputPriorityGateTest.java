package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Input-over-background idle window pure logic.
 */
public class InputPriorityGateTest {

    @Test
    public void defersWithinThreeSeconds() {
        long t0 = 1_000_000L;
        if (!InputPriorityGate.shouldDefer(t0 + 500L, t0)) {
            throw new AssertionError("must defer at 0.5s idle");
        }
        if (!InputPriorityGate.shouldDefer(t0 + 2999L, t0)) {
            throw new AssertionError("must defer at 2.999s idle");
        }
        if (InputPriorityGate.shouldDefer(t0 + 3000L, t0)) {
            throw new AssertionError("must allow at 3.0s idle");
        }
        if (InputPriorityGate.shouldDefer(t0 + 10_000L, t0)) {
            throw new AssertionError("must allow after long idle");
        }
    }

    @Test
    public void msUntilAllowed() {
        long t0 = 5_000L;
        long wait = InputPriorityGate.msUntilAllowed(t0 + 1000L, t0);
        if (wait != 2000L) {
            throw new AssertionError("wait=" + wait);
        }
        if (InputPriorityGate.msUntilAllowed(t0 + 3000L, t0) != 0L) {
            throw new AssertionError("zero at boundary");
        }
        if (InputPriorityGate.msUntilAllowed(t0, 0L) != 0L) {
            throw new AssertionError("unset interaction allows now");
        }
    }

    @Test
    public void nextPeriodMsQuietVsBusy() {
        long t0 = 10_000L;
        long quiet = InputPriorityGate.nextPeriodMs(t0 + 5000L, t0, 500L, 250L);
        if (quiet != 500L) {
            throw new AssertionError("quiet period=" + quiet);
        }
        long busy = InputPriorityGate.nextPeriodMs(t0 + 100L, t0, 500L, 250L);
        if (busy < 2500L) {
            throw new AssertionError("busy should wait ~2.9s, got " + busy);
        }
    }
}
