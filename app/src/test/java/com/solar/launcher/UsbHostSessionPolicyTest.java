package com.solar.launcher;

import org.junit.Test;

/**
 * Boot-settle + host-session USB gate unit checks — no Android runtime (2026-07-06).
 */
public class UsbHostSessionPolicyTest {

    @Test
    public void promptAllowedWhenNoBootHost() {
        if (!UsbHostSessionPolicy.isPromptAllowedForTest(false, false, 1000L, 5000L)) {
            throw new AssertionError("expected prompt when PC was not connected at boot");
        }
    }

    @Test
    public void promptBlockedDuringSettle() {
        if (UsbHostSessionPolicy.isPromptAllowedForTest(true, false, 10_000L, 40_000L)) {
            throw new AssertionError("expected block before 60s settle");
        }
    }

    @Test
    public void promptAllowedAfterSettle() {
        if (!UsbHostSessionPolicy.isPromptAllowedForTest(true, false, 10_000L, 80_000L)) {
            throw new AssertionError("expected prompt after 60s settle");
        }
    }

    /**
     * 2026-07-08 — Fresh plug after Solar is already running clears boot-host settle.
     * Mirror: hostAtBoot false once markFreshHostUnlockBootSettle would fire.
     */
    @Test
    public void promptAllowedWhenBootHostCleared() {
        if (!UsbHostSessionPolicy.isPromptAllowedForTest(false, true, 10_000L, 20_000L)) {
            throw new AssertionError("expected prompt when boot-host unlocked after fresh plug");
        }
    }

    @Test
    public void promptAllowedAfterDisconnect() {
        if (!UsbHostSessionPolicy.isPromptAllowedForTest(true, true, 10_000L, 15_000L)) {
            throw new AssertionError("expected prompt after host disconnect cycle");
        }
    }

    @Test
    public void evaluateOncePerSessionWhenHostConnected() {
        if (!UsbHostSessionPolicy.shouldEvaluatePromptForTest(true, false, false, true)) {
            throw new AssertionError("fresh host session should evaluate once");
        }
        if (UsbHostSessionPolicy.shouldEvaluatePromptForTest(true, false, true, true)) {
            throw new AssertionError("already evaluated — no re-prompt");
        }
        if (UsbHostSessionPolicy.shouldEvaluatePromptForTest(true, true, false, true)) {
            throw new AssertionError("dismissed session — stay idle");
        }
        if (UsbHostSessionPolicy.shouldEvaluatePromptForTest(false, false, false, true)) {
            throw new AssertionError("no host — no evaluation");
        }
    }

    @Test
    public void aggressiveWorkSuppressedAfterDismissOrConcierge() {
        if (!UsbHostSessionPolicy.isAggressiveWorkSuppressedForTest(true, false)) {
            throw new AssertionError("dismiss should suppress poll/recovery");
        }
        if (!UsbHostSessionPolicy.isAggressiveWorkSuppressedForTest(false, true)) {
            throw new AssertionError("concierge should suppress Java fallback poll");
        }
        if (UsbHostSessionPolicy.isAggressiveWorkSuppressedForTest(false, false)) {
            throw new AssertionError("active session without concierge may run fallbacks");
        }
    }

    @Test
    public void sessionResetOnDisconnect() {
        UsbHostSessionPolicy.SessionStateForTest connected =
                UsbHostSessionPolicy.onHostConnectForTest(
                        new UsbHostSessionPolicy.SessionStateForTest(false, false, false));
        if (!connected.sessionActive || connected.dismissed || connected.promptEvaluated) {
            throw new AssertionError("host connect arms session");
        }
        UsbHostSessionPolicy.SessionStateForTest dismissed =
                UsbHostSessionPolicy.onUserDismissForTest(connected);
        if (!dismissed.dismissed || !dismissed.promptEvaluated) {
            throw new AssertionError("dismiss marks session idle");
        }
        UsbHostSessionPolicy.SessionStateForTest reset =
                UsbHostSessionPolicy.resetSessionForTest(dismissed);
        if (reset.sessionActive || reset.dismissed || reset.promptEvaluated) {
            throw new AssertionError("disconnect clears session flags");
        }
        UsbHostSessionPolicy.SessionStateForTest reconnect =
                UsbHostSessionPolicy.onHostConnectForTest(reset);
        if (!reconnect.sessionActive || reconnect.dismissed || reconnect.promptEvaluated) {
            throw new AssertionError("reconnect starts fresh session");
        }
    }
}
