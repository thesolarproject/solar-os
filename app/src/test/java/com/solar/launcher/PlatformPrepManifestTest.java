package com.solar.launcher;

import com.solar.launcher.platform.PlatformPrepManifest;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for bundled platform/manifest.json parsing. */
public class PlatformPrepManifestTest {

    private static final String SAMPLE = "{"
            + "\"prepVersion\":1,"
            + "\"framework\":{"
            + "\"api17\":{\"appProcess\":\"a17/ap\",\"bridgeJar\":\"a17/j\",\"xposedProp\":\"a17/p\"},"
            + "\"api19\":{\"appProcess\":\"a19/ap\",\"bridgeJar\":\"a19/j\",\"xposedProp\":\"a19/p\"},"
            + "\"installerApk\":\"xposed/XposedInstaller.apk\","
            + "\"initHook\":\"init/99XposedInit.sh\","
            + "\"systemPaths\":{"
            + "\"appProcess\":\"/system/bin/app_process\","
            + "\"appProcessOrig\":\"/system/bin/app_process.orig\","
            + "\"bridgeJarFramework\":\"/system/framework/XposedBridge.jar\","
            + "\"bridgeJarSolar\":\"/system/etc/solar/XposedBridge.jar\","
            + "\"xposedProp\":\"/system/xposed.prop\","
            + "\"installerApk\":\"/system/app/XposedInstaller.apk\","
            + "\"initHook\":\"/system/etc/init.d/99XposedInit.sh\""
            + "}},"
            + "\"modules\":["
            + "{\"pkg\":\"com.solar.launcher.xposed.bridge.y1\",\"asset\":\"y1.apk\","
            + "\"systemApk\":\"/system/app/SolarContextBridgeY1.apk\",\"required\":true,"
            + "\"device\":\"y1\",\"versionCode\":2},"
            + "{\"pkg\":\"com.solar.launcher.xposed.themefont\",\"asset\":\"tf.apk\","
            + "\"systemApk\":\"/system/app/SolarThemeFont.apk\",\"required\":true,"
            + "\"device\":\"both\",\"versionCode\":1}"
            + "]}";

    @Test
    public void parseSampleManifest() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(SAMPLE);
        assertEquals(1, m.prepVersion);
        assertEquals("a17/ap", m.api17.appProcess);
        assertEquals("a19/j", m.api19.bridgeJar);
        assertEquals(2, m.modules.size());
    }

    @Test
    public void requiredModulesForY1ExcludesY2Bridge() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(SAMPLE);
        List<PlatformPrepManifest.ModuleEntry> y1 = m.requiredModulesForDevice(false);
        assertEquals(2, y1.size());
        assertTrue(y1.get(0).pkg.contains(".y1"));
    }

    @Test
    public void moduleAppliesToDeviceBothAndY2() {
        PlatformPrepManifest.ModuleEntry both = new PlatformPrepManifest.ModuleEntry(
                "a", "b", "c", true, "both", 1, "");
        assertTrue(PlatformPrepManifest.moduleAppliesToDevice(both, false));
        assertTrue(PlatformPrepManifest.moduleAppliesToDevice(both, true));
        PlatformPrepManifest.ModuleEntry y2only = new PlatformPrepManifest.ModuleEntry(
                "a", "b", "c", true, "y2", 1, "");
        assertFalse(PlatformPrepManifest.moduleAppliesToDevice(y2only, false));
        assertTrue(PlatformPrepManifest.moduleAppliesToDevice(y2only, true));
    }

    @Test
    public void bundledAssetManifestExists() throws Exception {
        java.io.File manifest = new java.io.File("app/src/main/assets/platform/manifest.json");
        if (!manifest.isFile()) {
            manifest = new java.io.File("src/main/assets/platform/manifest.json");
        }
        assertTrue("run sync-platform-assets.sh", manifest.isFile());
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(manifest), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        PlatformPrepManifest m = PlatformPrepManifest.parse(sb.toString());
        assertTrue(m.prepVersion >= 2);
        assertFalse(m.requiredModulesForDevice(true).isEmpty());
        assertFalse(m.requiredModulesForDevice(false).isEmpty());
    }
}
