package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Crash streak math for {@link SolarRecoveryCoordinator}. */
public class SolarRecoveryCoordinatorTest {

    @Test
    public void emergencyThresholdAtThree() {
        assertFalse(SolarRecoveryCoordinator.shouldEnterEmergencyForTest(0));
        assertFalse(SolarRecoveryCoordinator.shouldEnterEmergencyForTest(2));
        assertTrue(SolarRecoveryCoordinator.shouldEnterEmergencyForTest(3));
        assertTrue(SolarRecoveryCoordinator.shouldEnterEmergencyForTest(5));
    }
}
