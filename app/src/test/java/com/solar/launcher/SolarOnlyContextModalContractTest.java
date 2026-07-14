package com.solar.launcher;

import com.solar.input.policy.GlobalInputPolicy;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-14 — Debug session c54726: Solar-only context modal + HOLD BACK → Solar.
 * Layman: proves Power menu stays inside Solar and Back-hold still comes home from apps.
 */
public final class SolarOnlyContextModalContractTest {

    @Test
    public void policyRevTwentyTwoSolarOnlyPowerAndBackHome() throws Exception {
        // H1: Y2 POWER Solar-only
        assertTrue(GlobalInputPolicy.shouldOfferPowerLongModal("com.solar.launcher", true));
        assertFalse(GlobalInputPolicy.shouldOfferPowerLongModal("com.android.settings", true));
        // H2: stock path for non-Solar POWER (no Solar modal eligibility)
        assertFalse(GlobalInputPolicy.shouldOfferPowerLongModal("org.rockbox", true));
        // H3: HOLD BACK returns to Solar from third-party; Rockbox keeps BACK
        assertTrue(GlobalInputPolicy.shouldLaunchSolarOnBackLong(
                "com.android.settings", false, false));
        assertFalse(GlobalInputPolicy.shouldLaunchSolarOnBackLong(
                "org.rockbox", false, false));
        assertEquals(22, GlobalInputPolicy.POLICY_REV);

        // #region agent log
        JSONObject d = new JSONObject();
        d.put("policyRev", GlobalInputPolicy.POLICY_REV);
        d.put("solarPower", true);
        d.put("settingsPower", false);
        d.put("settingsBackHome", true);
        d.put("rockboxBackHome", false);
        DebugC54726Log.log("SolarOnlyContextModalContractTest",
                "policy contract verified", "H1-H3", d);
        // #endregion
    }
}
