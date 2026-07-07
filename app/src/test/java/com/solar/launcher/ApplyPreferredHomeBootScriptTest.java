package com.solar.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertTrue;

/** Boot script re-applies saved HOME from persist.solar.home.target every boot. */
public class ApplyPreferredHomeBootScriptTest {

    @Test
    public void bootScriptReadsPropAndBroadcastsPreferredHome() throws Exception {
        File f = new File("app/src/main/assets/y1/apply-preferred-home-boot.sh");
        if (!f.isFile()) {
            f = new File("src/main/assets/y1/apply-preferred-home-boot.sh");
        }
        assertTrue("apply-preferred-home-boot.sh missing", f.isFile());
        String script = readFile(f);
        assertTrue(script.contains("persist.solar.home.target"));
        assertTrue(script.contains("com.solar.launcher.homehelper"));
        assertTrue(script.contains("SET_PREFERRED_HOME"));
        assertTrue(script.contains("ensure_helper_registered"));
        assertTrue(script.contains("apply_home_target"));
        assertTrue(script.contains("OVERLAY_KEEPALIVE"));
        assertTrue(!script.contains("reboot"));
    }

    private static String readFile(File f) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }
}
