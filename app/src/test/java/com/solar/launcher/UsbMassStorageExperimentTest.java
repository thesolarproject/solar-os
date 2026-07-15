package com.solar.launcher;

import org.junit.Test;

/** Y2 USB mass storage experiment gating — unit checks without device (2026-07-05). */
public class UsbMassStorageExperimentTest {

    @Test
    public void y1AlwaysAllowsUms() {
        if (!UsbMassStorageExperiment.isEnabledForFamily("y1", false)) {
            throw new AssertionError("Y1 must allow UMS regardless of experiment pref");
        }
    }

    @Test
    public void y2AlwaysBlocksUms() {
        if (UsbMassStorageExperiment.isEnabledForFamily("y2", false)) {
            throw new AssertionError("Y2 must block UMS (MTP-only product)");
        }
        if (UsbMassStorageExperiment.isEnabledForFamily("y2", true)) {
            throw new AssertionError("Y2 must block UMS even if old experiment pref is true");
        }
    }

    @Test
    public void connectionsUsbHiddenOnY2() {
        if (UsbMassStorageExperiment.connectionsUsbMenuVisibleForFamily("y2", true)) {
            throw new AssertionError("Connections USB submenu must hide on Y2");
        }
    }

    @Test
    public void connectionsUsbVisibleOnY1Always() {
        if (!UsbMassStorageExperiment.connectionsUsbMenuVisibleForFamily("y1", false)) {
            throw new AssertionError("Y1 Connections USB submenu must stay visible");
        }
    }
}
