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
    public void y2DefaultOffBlocksUms() {
        if (UsbMassStorageExperiment.isEnabledForFamily("y2", false)) {
            throw new AssertionError("Y2 experiment off must block UMS");
        }
    }

    @Test
    public void y2ExperimentOnAllowsUms() {
        if (!UsbMassStorageExperiment.isEnabledForFamily("y2", true)) {
            throw new AssertionError("Y2 experiment on must allow UMS");
        }
    }

    @Test
    public void connectionsUsbHiddenOnY2WhenExperimentOff() {
        if (UsbMassStorageExperiment.connectionsUsbMenuVisibleForFamily("y2", false)) {
            throw new AssertionError("Connections USB submenu must hide when experiment off");
        }
    }

    @Test
    public void connectionsUsbVisibleOnY1Always() {
        if (!UsbMassStorageExperiment.connectionsUsbMenuVisibleForFamily("y1", false)) {
            throw new AssertionError("Y1 Connections USB submenu must stay visible");
        }
    }
}
