package com.solar.launcher;

import org.json.JSONObject;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** APK assets/platform must match vendor tree — parity with sync-platform-assets.sh. */
public class PlatformAssetsSyncTest {

    @Test
    public void manifestChecksumsMatchAssets() throws Exception {
        File assets = new File("app/src/main/assets/platform");
        if (!assets.isDirectory()) {
            assets = new File("src/main/assets/platform");
        }
        assertTrue("run sync-platform-assets.sh", assets.isDirectory());
        File manifestFile = new File(assets, "manifest.json");
        assertTrue(manifestFile.isFile());

        String json = readFile(manifestFile);
        JSONObject root = new JSONObject(json);
        org.json.JSONArray modules = root.getJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject m = modules.getJSONObject(i);
            String asset = m.getString("asset");
            String expectedSha = m.getString("sha256");
            File apk = new File(assets, asset);
            assertTrue("missing asset " + asset, apk.isFile());
            assertEquals("sha256 drift for " + asset, expectedSha, sha256(apk));
        }
    }

    @Test
    public void vendorFrameworkTreesPresent() {
        File assets = new File("app/src/main/assets/platform");
        if (!assets.isDirectory()) {
            assets = new File("src/main/assets/platform");
        }
        for (String api : new String[] {"api17-arm", "api19-arm"}) {
            for (String f : new String[] {"app_process", "XposedBridge.jar", "xposed.prop"}) {
                File file = new File(assets, "xposed/" + api + "/" + f);
                assertTrue("missing " + file, file.isFile());
            }
        }
    }

    private static String readFile(File f) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        in.close();
        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
