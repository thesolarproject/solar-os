package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;

/**
 * ROM verify scripts must grep 99XposedInit.sh for device bridge packages —
 * prevents Y1/Y2 parity regressions when someone weakens post-build audits.
 */
public class XposedParityVerifyScriptsTest {

    private static String readScript(String name) throws Exception {
        File f = new File("solar-rom/scripts/" + name);
        if (!f.isFile()) {
            f = new File("../solar-rom/scripts/" + name);
        }
        if (!f.isFile()) {
            throw new AssertionError("missing script: " + name);
        }
        InputStream in = new FileInputStream(f);
        try {
            Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        } finally {
            in.close();
        }
    }

    @Test
    public void y1VerifyScriptRequiresBridgeY1InInitHook() throws Exception {
        String body = readScript("verify-y1-rom-contents.sh");
        if (!body.contains("com.solar.launcher.xposed.bridge.y1")) {
            throw new AssertionError("verify-y1-rom-contents.sh must grep bridge.y1 in 99XposedInit.sh");
        }
    }

    @Test
    public void y2VerifyScriptRequiresBridgeY2InInitHook() throws Exception {
        String body = readScript("verify-y2-rom-contents.sh");
        if (!body.contains("com.solar.launcher.xposed.bridge.y2")) {
            throw new AssertionError("verify-y2-rom-contents.sh must grep bridge.y2 in 99XposedInit.sh");
        }
    }

    /** 2026-07-15 — A5 ROM uses Y1 bridge + family pin; must not require Rockbox. */
    @Test
    public void a5VerifyScriptRequiresBridgeY1AndFamilyPin() throws Exception {
        String body = readScript("verify-a5-rom-contents.sh");
        if (!body.contains("com.solar.launcher.xposed.bridge.y1")) {
            throw new AssertionError("verify-a5-rom-contents.sh must grep bridge.y1 in 99XposedInit.sh");
        }
        if (!body.contains("persist.solar.device_family=a5")) {
            throw new AssertionError("verify-a5-rom-contents.sh must require A5 family pin");
        }
        if (!body.contains("A5-mtk.kl")) {
            throw new AssertionError("verify-a5-rom-contents.sh must audit A5-mtk.kl");
        }
    }

    @Test
    public void xposedVerifyScriptRequiresBridgePackageByApi() throws Exception {
        String body = readScript("verify-xposed-rom-contents.sh");
        if (!body.contains("com.solar.launcher.xposed.bridge.y1")
                || !body.contains("com.solar.launcher.xposed.bridge.y2")) {
            throw new AssertionError("verify-xposed-rom-contents.sh must grep bridge package by API level");
        }
    }

    /** enabled_modules merge must use repair helper — unsafe map-tag grep crashed Installer on JB. */
    @Test
    public void xposedInitMustNotUseUnsafeEnabledModulesMerge() throws Exception {
        File f = new File("solar-rom/system/99XposedInit.sh");
        if (!f.isFile()) {
            f = new File("../solar-rom/system/99XposedInit.sh");
        }
        if (!f.isFile()) {
            throw new AssertionError("missing 99XposedInit.sh");
        }
        InputStream in = new FileInputStream(f);
        String body;
        try {
            Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
            body = s.hasNext() ? s.next() : "";
        } finally {
            in.close();
        }
        if (!body.contains("_xposed_set_module_enabled_in_prefs")) {
            throw new AssertionError("99XposedInit.sh must use safe enabled_modules merge helper");
        }
        if (body.contains("grep -v '</map>' \"$PREFS\"")) {
            throw new AssertionError("99XposedInit.sh must not grep -v '</map>' on enabled_modules.xml");
        }
    }

    /** Post-zip verify must reject unsafe enabled_modules merge in baked init hook. */
    @Test
    public void verifyXposedScriptAuditsSafeEnabledModulesMerge() throws Exception {
        String body = readScript("verify-xposed-rom-contents.sh");
        if (!body.contains("_xposed_set_module_enabled_in_prefs")) {
            throw new AssertionError("verify-xposed-rom-contents.sh must grep safe merge helper");
        }
        if (!body.contains("_xposed_repair_enabled_modules_xml")) {
            throw new AssertionError("verify-xposed-rom-contents.sh must grep repair helper");
        }
    }
}
