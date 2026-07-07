package com.solar.launcher;

import com.solar.launcher.platform.PlatformPrepManifest;
import com.solar.launcher.platform.PlatformPrepState;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Platform prep gate — silent when ROM matches prepVersion. */
public class PlatformPrepStateTest {

    private static final String MANIFEST_V2 = "{"
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
            + "\"modules\":[]}";

    @Test
    public void nullContextNeverRequiresPrep() {
        assertFalse(PlatformPrepState.isPrepRequired(null, null, null));
        assertFalse(PlatformPrepState.needsSilentPrep(null, null));
    }

    @Test
    public void noSilentPrepWhenAppliedVersionMatches() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(MANIFEST_V2);
        assertFalse(PlatformPrepState.needsSilentPrep(null, m));
    }

    @Test
    public void silentPrepWhenVersionBehind() throws Exception {
        PlatformPrepManifest m = PlatformPrepManifest.parse(MANIFEST_V2);
        assertTrue(m.prepVersion == 2);
    }
}
