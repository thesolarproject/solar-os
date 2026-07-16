package com.solar.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

/**
 * 2026-07-16 — A5 must never reinstall Y1-Rockbox wheel maps on Solar start.
 * Layman: if this test fails, face/volume buttons on A5 will go wrong after boot.
 * Tech: source + asset script contract (cannot run su on JVM host).
 */
public class Y1KeymapSyncA5Test {

    @Test
    public void ensureUnifiedSourceSeedsA5Maps() throws Exception {
        String src = readProjectFile("app/src/main/java/com/solar/launcher/Y1KeymapSync.java");
        if (!src.contains("isA5()")) {
            throw new AssertionError("Y1KeymapSync must branch on isA5");
        }
        if (!src.contains("A5-mtk.kl") || !src.contains("A5.kl")) {
            throw new AssertionError("Y1KeymapSync must seed A5-mtk.kl and A5.kl");
        }
        if (!src.contains("ensureA5")) {
            throw new AssertionError("expected ensureA5 helper");
        }
        // Must pin family so sync script cannot fall through to Y1-Rockbox.
        if (!src.contains("persist.solar.device_family a5")) {
            throw new AssertionError("must setprop family a5 before sync script");
        }
    }

    @Test
    public void syncScriptDetectsA5ByDisplayNotBareTimmkoo() throws Exception {
        String script = readProjectFile("app/src/main/assets/y1/sync-y1-keymap.sh");
        if (!script.contains("looks_a5_display") && !script.contains("virtual_size")) {
            throw new AssertionError("sync script must probe display for A5");
        }
        // Bare manufacturer Timmkoo must not force A5 on active lines (Y1 is also Timmkoo).
        // Comments may document the old Was: pattern — ignore those.
        for (String line : script.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#")) continue;
            if (t.contains("*[Tt]immkoo*") && t.contains("IS_A5=1")) {
                throw new AssertionError("bare Timmkoo must not set IS_A5: " + t);
            }
        }
        if (!script.contains("A5-mtk.kl")) {
            throw new AssertionError("A5 branch missing");
        }
        if (!script.contains("MEDIA_STOP") && !script.contains("A5 face DPAD")) {
            // A5-mtk content lives in .kl; script comments document the playbook.
            if (!script.contains("A5 face DPAD")) {
                throw new AssertionError("A5 playbook comment missing");
            }
        }
    }

    @Test
    public void a5MtkHasCorrectCriticalScancodes() throws Exception {
        String mtk = readProjectFile("app/src/main/assets/y1/A5-mtk.kl");
        requireKey(mtk, "103", "DPAD_UP");
        requireKey(mtk, "108", "DPAD_DOWN");
        requireKey(mtk, "114", "VOLUME_DOWN");
        requireKey(mtk, "115", "VOLUME_UP");
        requireKey(mtk, "116", "MEDIA_STOP");
        requireKey(mtk, "158", "BACK");
        // Must not be Y1 wheel map.
        if (mtk.contains("key 103   MEDIA_PLAY") || mtk.contains("key 114   DPAD_UP")) {
            throw new AssertionError("A5-mtk.kl looks like Y1-Rockbox wheel map");
        }
    }

    private static void requireKey(String body, String scan, String name) {
        // Accept flexible whitespace.
        if (!body.matches("(?s).*key\\s+" + scan + "\\s+" + name + ".*")) {
            throw new AssertionError("missing key " + scan + " " + name);
        }
    }

    private static String readProjectFile(String rel) throws Exception {
        File f = new File(rel);
        if (!f.isFile()) {
            // Running from module cwd.
            f = new File("../" + rel);
        }
        if (!f.isFile()) {
            f = new File("../../" + rel);
        }
        if (!f.isFile()) {
            // Absolute from workspace root heuristic.
            String home = System.getProperty("user.dir");
            File try1 = new File(home, rel);
            if (try1.isFile()) f = try1;
            else {
                File try2 = new File(home, "../" + rel);
                if (try2.isFile()) f = try2;
                else throw new AssertionError("missing " + rel + " cwd=" + home);
            }
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }
}
