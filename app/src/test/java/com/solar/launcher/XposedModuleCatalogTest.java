package com.solar.launcher;

import org.junit.Test;

public class XposedModuleCatalogTest {

    @Test
    public void solarManagedPrefixRecognized() {
        if (!XposedModuleCatalog.isSolarManagedPackage("com.solar.launcher.xposed.bridge.y1")) {
            throw new AssertionError("bridge y1");
        }
        if (XposedModuleCatalog.isSolarManagedPackage("com.example.hook")) {
            throw new AssertionError("third party");
        }
    }

    @Test
    public void requiredProductionExcludesLabPackage() {
        if (!XposedModuleCatalog.isRequiredProductionPackage("com.solar.launcher.xposed.themefont")) {
            throw new AssertionError("theme font required");
        }
        if (XposedModuleCatalog.isRequiredProductionPackage("com.solar.launcher.xposed.powermenu")) {
            throw new AssertionError("powermenu lab");
        }
        if (XposedModuleCatalog.isRequiredProductionPackage("com.example.hook")) {
            throw new AssertionError("user pkg");
        }
    }

    @Test
    public void allRowsNullContextEmpty() {
        if (!XposedModuleCatalog.allRows(null).isEmpty()) {
            throw new AssertionError("null ctx");
        }
    }
}
