package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Power IPC snapshot must list power-tier rows, never quick-bar chip labels. */
public class OverlayMenuSnapshotBuilderTest {

    @Test
    public void powerKindConstantStable() {
        assertEquals("power", OverlayMenuSnapshotBuilder.KIND_POWER);
        assertEquals("power_actions", OverlayMenuSnapshotBuilder.KEY_POWER_ACTIONS);
    }

    @Test
    public void nullContextDoesNotThrow() {
        try {
            OverlayMenuSnapshotBuilder.buildPowerFallback(null);
            OverlayMenuSnapshotBuilder.buildPowerListRows(null);
        } catch (Exception e) {
            throw new AssertionError("null ctx must return early without throwing", e);
        }
    }
}
