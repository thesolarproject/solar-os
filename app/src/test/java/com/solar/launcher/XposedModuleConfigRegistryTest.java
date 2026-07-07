package com.solar.launcher;

import org.junit.Test;

public class XposedModuleConfigRegistryTest {

    @Test
    public void configRowKeyRoundTrip() {
        String row = XposedModuleConfigRegistry.configRowKey(
                "com.solar.launcher.xposed.bridge.y1", "bridge_verbose_logging");
        XposedModuleConfigRegistry.ConfigRowRef ref =
                XposedModuleConfigRegistry.parseConfigRowKey(row);
        if (ref == null) throw new AssertionError("null ref");
        if (!"com.solar.launcher.xposed.bridge.y1".equals(ref.packageName)) {
            throw new AssertionError("pkg");
        }
        if (!"bridge_verbose_logging".equals(ref.optionKey)) {
            throw new AssertionError("key");
        }
    }

    @Test
    public void bridgeHasInlineOptions() {
        if (!XposedModuleConfigRegistry.hasInlineOptions("com.solar.launcher.xposed.bridge.y2")) {
            throw new AssertionError("bridge y2");
        }
        if (XposedModuleConfigRegistry.hasInlineOptions("com.example.hook")) {
            throw new AssertionError("third party");
        }
    }
}
