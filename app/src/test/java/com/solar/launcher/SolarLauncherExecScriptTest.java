package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/** 2026-07-06 — solar-launcher-exec.sh contract in assets + ROM. */
public class SolarLauncherExecScriptTest {

    @Test
    public void assetScriptHasSubcommands() throws Exception {
        String text = readRepoFile("app/src/main/assets/y1/solar-launcher-exec.sh");
        assertTrue(text.contains("restart-active"));
        assertTrue(text.contains("disable-competitors"));
        assertTrue(text.contains("PROP_TRANSITION_UNTIL")
                || text.contains("sys.solar.launcher.trans_until"));
        assertTrue(text.contains("force-stop"));
    }

    @Test
    public void enforceForegroundSkipsWhileOverlayActive() throws Exception {
        String text = readRepoFile("app/src/main/assets/y1/solar-launcher-exec.sh");
        assertTrue(text.contains("sys.solar.overlay.active"));
        assertTrue(text.contains("sys.solar.overlay.opening"));
    }

    @Test
    public void switchToStockDelegatesToExec() throws Exception {
        String text = readRepoFile("app/src/main/assets/y1/switch-to-stock.sh");
        assertTrue(text.contains("solar-launcher-exec.sh"));
        assertTrue(text.contains("switch"));
    }

    private static String readRepoFile(String rel) throws Exception {
        File root = new File(System.getProperty("user.dir"));
        File f = new File(root, rel);
        if (!f.isFile()) {
            f = new File(root.getParentFile(), rel);
        }
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
