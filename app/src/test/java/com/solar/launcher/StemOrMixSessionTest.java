package com.solar.launcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exclusive Stem/Mix session gate. 2026-07-19
 */
public class StemOrMixSessionTest {

    @Before
    public void setUp() {
        StemOrMixSession.setActive(false);
    }

    @After
    public void tearDown() {
        StemOrMixSession.setActive(false);
    }

    @Test
    public void activeFlag() {
        assertFalse(StemOrMixSession.isActive());
        StemOrMixSession.setActive(true);
        assertTrue(StemOrMixSession.isActive());
        StemOrMixSession.setActive(false);
        assertFalse(StemOrMixSession.isActive());
    }
}
