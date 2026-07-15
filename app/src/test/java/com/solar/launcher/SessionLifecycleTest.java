package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Session leave rules for Reach: cancel search UI, keep client for PMs.
 * ponytail: one assert suite on pure helper — no device needed.
 */
public class SessionLifecycleTest {

    @Test
    public void keepClientWhenServiceActiveEvenIfNotKeepForScreen() {
        if (!SessionLifecycle.shouldKeepSoulseekClient(false, true)) {
            throw new AssertionError("Reach on → keep client for PM popups");
        }
    }

    @Test
    public void keepClientWhenKeyboardHandoffEvenIfServiceFlagOff() {
        // Keep-for-screen is keyboard/stream; service flag is Reach master.
        if (!SessionLifecycle.shouldKeepSoulseekClient(true, false)) {
            throw new AssertionError("keyboard handoff keeps session");
        }
    }

    @Test
    public void teardownWhenNeitherKeepNorService() {
        if (SessionLifecycle.shouldKeepSoulseekClient(false, false)) {
            throw new AssertionError("both off → full teardown ok");
        }
    }
}
