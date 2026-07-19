package com.solar.launcher.mix;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mix assign / scrub / fade gate math. 2026-07-19
 */
public class MixAssignSlotsTest {

    @Test
    public void bindAndFilled() {
        File[] slots = new File[3];
        File a = new File("/tmp/a.mp3") {
            @Override public boolean isFile() { return true; }
        };
        File b = new File("/tmp/b.mp3") {
            @Override public boolean isFile() { return true; }
        };
        assertEquals(1, MixAssignSlots.bind(slots, 0, a));
        assertEquals(2, MixAssignSlots.bind(slots, 1, b));
        assertEquals(2, MixAssignSlots.filled(slots));
        assertTrue(MixAssignSlots.canStart(slots));
        MixAssignSlots.bind(slots, 0, null);
        assertEquals(1, MixAssignSlots.filled(slots));
    }

    @Test
    public void scrubWrap() {
        assertEquals(0, MixAssignSlots.scrubWrap(9500, 10000, 500));
        assertEquals(9500, MixAssignSlots.scrubWrap(0, 10000, -500));
        assertEquals(5000, MixAssignSlots.scrubWrap(0, 10000, 5000));
    }

    @Test
    public void fadeGate() {
        assertTrue(MixAssignSlots.needsFadeBeforeReplace(0.5f, 0.02f));
        assertFalse(MixAssignSlots.needsFadeBeforeReplace(0.01f, 0.02f));
    }
}
