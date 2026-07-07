package com.solar.launcher;

import com.solar.launcher.platform.PlatformDeprecationCleaner;
import com.solar.launcher.platform.PlatformPrepManifest;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Deprecated artifact catalog in bundled platform manifest. */
public class PlatformDeprecationCleanerTest {

    private static final String SAMPLE = "{"
            + "\"prepVersion\":2,"
            + "\"framework\":{"
            + "\"api17\":{\"appProcess\":\"a\",\"bridgeJar\":\"b\",\"xposedProp\":\"c\"},"
            + "\"api19\":{\"appProcess\":\"a\",\"bridgeJar\":\"b\",\"xposedProp\":\"c\"},"
            + "\"installerApk\":\"x\",\"initHook\":\"i\","
            + "\"systemPaths\":{"
            + "\"appProcess\":\"/system/bin/app_process\","
            + "\"appProcessOrig\":\"/system/bin/app_process.orig\","
            + "\"bridgeJarFramework\":\"/system/framework/XposedBridge.jar\","
            + "\"bridgeJarSolar\":\"/system/etc/solar/XposedBridge.jar\","
            + "\"xposedProp\":\"/system/xposed.prop\","
            + "\"installerApk\":\"/system/app/XposedInstaller.apk\","
            + "\"initHook\":\"/system/etc/init.d/99XposedInit.sh\""
            + "}},"
            + "\"modules\":[],"
            + "\"deprecated\":["
            + "{\"systemApk\":\"/system/app/SolarContextBridge.apk\",\"device\":\"both\"},"
            + "{\"systemApk\":\"/system/app/SolarContextBridgeY1.apk\",\"device\":\"y2\"}"
            + "]}";

    @Test
    public void parseDeprecatedEntries() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(SAMPLE);
        assertEquals(2, m.deprecated.size());
    }

    @Test
    public void deprecatedForY1ExcludesY2OnlyRow() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(SAMPLE);
        List<PlatformDeprecationCleaner.DeprecatedEntry> y1 = m.deprecatedForDevice(false);
        assertEquals(1, y1.size());
        assertTrue(y1.get(0).systemApk.contains("SolarContextBridge.apk"));
    }

    @Test
    public void bundledManifestHasDeprecatedSection() throws Exception {
        java.io.File manifest = new java.io.File("app/src/main/assets/platform/manifest.json");
        if (!manifest.isFile()) manifest = new java.io.File("src/main/assets/platform/manifest.json");
        assertTrue("run sync-platform-assets.sh", manifest.isFile());
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(manifest), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        PlatformPrepManifest m = PlatformPrepManifest.parse(sb.toString());
        assertTrue(m.prepVersion >= 2);
        assertTrue(m.deprecated != null && !m.deprecated.isEmpty());
    }
}
