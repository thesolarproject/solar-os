package com.solar.launcher;

import com.solar.launcher.platform.PlatformProbe;

import org.junit.Test;

public class PlatformProbeTest {

    @Test
    public void packageInstalledAcceptsPmPath() {
        if (!PlatformProbe.packageInstalledForTest("com.solar.launcher.xposed.bridge.y1",
                "package:/data/app/com.solar.launcher.xposed.bridge.y1-1.apk", null, false)) {
            throw new AssertionError("pm path");
        }
    }

    @Test
    public void packageInstalledAcceptsBakedSystemApk() {
        if (!PlatformProbe.packageInstalledForTest("com.solar.launcher.xposed.bridge.y1",
                null, "/system/app/SolarContextBridgeY1.apk", true)) {
            throw new AssertionError("system apk");
        }
    }

    @Test
    public void packageRegisteredInPmAcceptsPmPath() {
        if (!PlatformProbe.packageRegisteredInPmForTest("io.github.gohoski.notpipe",
                "package:/data/app/io.github.gohoski.notpipe-1.apk")) {
            throw new AssertionError("pm path");
        }
    }

    @Test
    public void packageRegisteredInPmRejectsSystemApkOnly() {
        if (PlatformProbe.packageRegisteredInPmForTest("io.github.gohoski.notpipe", "")) {
            throw new AssertionError("empty pm path");
        }
    }

    @Test
    public void packageInstalledRejectsMissing() {
        if (PlatformProbe.packageInstalledForTest("com.example.missing",
                null, null, false)) {
            throw new AssertionError("missing");
        }
    }
}
