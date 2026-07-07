package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Ultra-long rescue allowlist and Rockbox disable contract. */
public class SolarRescueTest {

    @Test
    public void systemUiAllowlisted() {
        assertTrue(SolarRescue.isRescueAllowlisted("com.android.systemui"));
    }

    @Test
    public void rockboxNotAllowlisted() {
        assertFalse(SolarRescue.isRescueAllowlisted("org.rockbox"));
    }

    @Test
    public void rescueDisablesRockboxAndJj() {
        assertTrue(SolarRescue.willDisableAlternateLaunchers("org.rockbox"));
        assertTrue(SolarRescue.willDisableRockbox("com.themoon.y1"));
    }

    @Test
    public void jjNotAllowlisted() {
        assertFalse(SolarRescue.isRescueAllowlisted(LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void rescueForceStopsForegroundApp() {
        String cmd = SolarRescue.buildRescueCommand("com.android.settings");
        assertTrue(cmd.contains("am force-stop 'com.android.settings'"));
    }

    @Test
    public void rescueSetsHomeTargetAndBroadcast() {
        String cmd = SolarRescue.buildRescueCommand("org.rockbox");
        assertTrue(cmd.contains("setprop persist.solar.home.target solar"));
        assertTrue(cmd.contains(LauncherDefault.ACTION_SET_PREFERRED_HOME));
        assertTrue(cmd.contains("--es target solar"));
        assertTrue(cmd.contains("reboot"));
    }
}
