package com.solar.launcher;

import org.junit.Test;

public class XposedModuleDiscoveryTest {

    @Test
    public void findUnpromptedDisabledNullContextEmpty() {
        if (!XposedModuleDiscovery.findUnpromptedDisabled(null).isEmpty()) {
            throw new AssertionError("null ctx");
        }
    }

    @Test
    public void listInstalledHookPackagesNullContextEmpty() {
        if (!XposedModuleDiscovery.listInstalledHookPackages(null).isEmpty()) {
            throw new AssertionError("null ctx");
        }
    }
}
