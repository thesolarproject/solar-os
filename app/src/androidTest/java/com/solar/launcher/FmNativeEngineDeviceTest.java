package com.solar.launcher;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.solar.launcher.radio.fm.FmEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-06 — Hardware FM smoke on rooted Y1/Y2 (native opendev + powerDown).
 * Layman: quick check that Solar can talk to the FM chip on device.
 */
@RunWith(AndroidJUnit4.class)
public class FmNativeEngineDeviceTest {

    private Context ctx;
    private FmEngine engine;

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getTargetContext();
        engine = new FmEngine(ctx);
    }

    @Test
    public void fmPackageOrNativeAvailable() {
        assertTrue("FM should be available on Solar ROM", engine.isAvailable());
    }

    @Test
    public void powerCycleDoesNotCrash() {
        if (!engine.isAvailable()) return;
        engine.powerDown();
        boolean ok = engine.playStation(87500);
        if (!ok) {
            String err = engine.lastError();
            assertNotNull("lastError when play fails", err);
        }
        engine.powerDown();
    }
}
