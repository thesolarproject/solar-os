package com.solar.launcher;

import org.junit.Test;

public class XposedModuleEnsurerTest {

    @Test
    public void requiredModulesMatchDeviceFamily() {
        java.util.List<String> pkgs = XposedModuleEnsurer.requiredModulePackages();
        if (pkgs.size() != 2) {
            throw new AssertionError("expected bridge + theme font");
        }
        String bridge = pkgs.get(0);
        if (DeviceFeatures.isY2()) {
            if (!"com.solar.launcher.xposed.bridge.y2".equals(bridge)) {
                throw new AssertionError("Y2 bridge pkg");
            }
        } else if (!"com.solar.launcher.xposed.bridge.y1".equals(bridge)) {
            throw new AssertionError("Y1 bridge pkg");
        }
        if (!"com.solar.launcher.xposed.themefont".equals(pkgs.get(1))) {
            throw new AssertionError("theme font pkg");
        }
    }

    @Test
    public void ensurerExposesUserOverrideSkipHook() {
        if (XposedModuleEnsurer.shouldSkipForcedEnable(null)) {
            throw new AssertionError("null package must not skip repair");
        }
    }
}
