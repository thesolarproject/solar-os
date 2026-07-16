package com.solar.launcher.soulseek;

import org.junit.Test;

public class SolarDiagProbesTest {

    @Test
    public void barePullIsNotProbe() {
        SolarDiagProbes.Parsed p = SolarDiagProbes.parse("solar_diag");
        if (!p.barePull) throw new AssertionError("bare");
        if (p.hasProbes()) throw new AssertionError("no probes");
        if (!p.isCommandOnly()) throw new AssertionError("command only");
    }

    @Test
    public void probeSuffixNotBarePull() {
        SolarDiagProbes.Parsed p = SolarDiagProbes.parse("solar_diag_wifi_ssid");
        if (p.barePull) throw new AssertionError("not bare");
        if (!p.hasProbes()) throw new AssertionError("probe");
        if (!p.probeKeys.contains("wifi_ssid")) throw new AssertionError(p.probeKeys.toString());
        if (!p.isCommandOnly()) throw new AssertionError("command only");
    }

    @Test
    public void mixedTextStripsTokens() {
        SolarDiagProbes.Parsed p = SolarDiagProbes.parse("hey solar_diag please fix solar_diag_wifi");
        if (!p.barePull) throw new AssertionError("bare in mixed");
        if (!p.hasProbes()) throw new AssertionError("probe in mixed");
        String s = p.strippedText.toLowerCase();
        if (s.contains("solar_diag")) throw new AssertionError("token left: " + s);
        if (!s.contains("hey") || !s.contains("please")) throw new AssertionError(s);
    }

    @Test
    public void accountsBareVsProbe() {
        if (!SolarDeveloperAccounts.isDiagRemotePullCommand("please solar_diag now")) {
            throw new AssertionError("bare pull");
        }
        if (SolarDeveloperAccounts.isDiagRemotePullCommand("solar_diag_wifi_ssid")) {
            throw new AssertionError("probe is not pull");
        }
        if (!SolarDeveloperAccounts.isDiagProbeCommand("solar_diag_ip")) {
            throw new AssertionError("probe");
        }
        if (!SolarDeveloperAccounts.isAutoDiagnosticText("solar_diag")) {
            throw new AssertionError("pure bare hidden");
        }
        if (!SolarDeveloperAccounts.isAutoDiagnosticText("solar_diag_bt_mac")) {
            throw new AssertionError("pure probe hidden");
        }
        if (SolarDeveloperAccounts.isAutoDiagnosticText("hello there")) {
            throw new AssertionError("normal visible");
        }
    }

    @Test
    public void stripLeavesEmptyForCommandOnly() {
        String s = SolarDeveloperAccounts.stripDiagCommands("  solar_diag_wifi_ssid  ");
        if (s != null && !s.trim().isEmpty()) throw new AssertionError(s);
    }
}
