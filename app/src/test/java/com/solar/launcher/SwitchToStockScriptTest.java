package com.solar.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertTrue;

/** 2026-07-06 — switch-to-stock delegates to solar-launcher-exec.sh. */
public class SwitchToStockScriptTest {

    @Test
    public void stockPathDelegatesToLauncherExec() throws Exception {
        File f = new File("app/src/main/assets/y1/switch-to-stock.sh");
        if (!f.isFile()) {
            f = new File("src/main/assets/y1/switch-to-stock.sh");
        }
        assertTrue("switch-to-stock.sh missing", f.isFile());
        String script = readFile(f);
        assertTrue(!script.toLowerCase().contains("reboot"));
        assertTrue(script.contains("solar-launcher-exec.sh"));
        assertTrue(script.contains("switch"));
        assertTrue(script.contains("solar"));
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
