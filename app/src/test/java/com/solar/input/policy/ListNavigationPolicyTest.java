package com.solar.input.policy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-06 — ListNavigationPolicy JAR unit tests. */
public class ListNavigationPolicyTest {

    @Test
    public void clampWhenInfiniteOff() {
        assertEquals(-1, ListNavigationPolicy.wrapIndex(0, -1, 5, false));
        assertEquals(1, ListNavigationPolicy.wrapIndex(0, 1, 5, false));
    }

    @Test
    public void wrapWhenInfiniteOn() {
        assertEquals(4, ListNavigationPolicy.wrapIndex(0, -1, 5, true));
        assertEquals(0, ListNavigationPolicy.wrapIndex(4, 1, 5, true));
    }

    /** 2026-07-11 — Overlay/context modal must not honor infinite scroll (chips need edge exit). */
    @Test
    public void contextModal_neverAppliesInfinite() {
        assertFalse(ListNavigationPolicy.appliesToContextModal());
        assertFalse(ListNavigationPolicy.effectiveInfinite(true, true));
        assertFalse(ListNavigationPolicy.effectiveInfinite(false, true));
        assertTrue(ListNavigationPolicy.effectiveInfinite(true, false));
        assertFalse(ListNavigationPolicy.effectiveInfinite(false, false));
    }
}
