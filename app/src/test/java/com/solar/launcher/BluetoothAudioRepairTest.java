package com.solar.launcher;

import android.bluetooth.BluetoothDevice;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BluetoothAudioRepairTest {

    @Test
    public void testNormalizePairingPin() {
        assertEquals("0000", BluetoothAudioRepair.normalizePairingPin(null));
        assertEquals("0000", BluetoothAudioRepair.normalizePairingPin("   "));
        assertEquals("1234", BluetoothAudioRepair.normalizePairingPin(" 1234 "));
        assertEquals("1234567890123456", BluetoothAudioRepair.normalizePairingPin("12345678901234567890"));
    }

    @Test
    public void testIsBondAuthFailure() {
        int state = BluetoothDevice.BOND_NONE;
        int prev = BluetoothDevice.BOND_BONDING;

        assertTrue(BluetoothAudioRepair.isBondAuthFailure(state, prev, BluetoothAudioRepair.BOND_REASON_AUTH_FAILED));
        assertTrue(BluetoothAudioRepair.isBondAuthFailure(state, prev, BluetoothAudioRepair.BOND_REASON_AUTH_REJECTED));
        assertTrue(BluetoothAudioRepair.isBondAuthFailure(state, prev, BluetoothAudioRepair.BOND_REASON_AUTH_TIMEOUT));
        assertFalse(BluetoothAudioRepair.isBondAuthFailure(state, prev, 0));
        assertFalse(BluetoothAudioRepair.isBondAuthFailure(BluetoothDevice.BOND_BONDED, prev, BluetoothAudioRepair.BOND_REASON_AUTH_FAILED));
    }
}
