package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-15 — Prove Wi‑Fi Transfer path containment; reject escapes used by old {@code new File(root, path)}.
 */
public class SolarWebPathsTest {

    @Test
    public void resolveUnderRejectsAbsoluteAndDotDot() {
        File root = new File("/tmp/solar-music-root");
        // Absolute + traversal must never resolve — Android Dalvik often ignored parent for abs child.
        assertNull(SolarWebPaths.resolveUnder(root, "/etc/passwd"));
        assertNull(SolarWebPaths.resolveUnder(root, "../evil"));
        assertNull(SolarWebPaths.resolveUnder(root, "a/../../evil"));
        assertNull(SolarWebPaths.resolveUnder(root, ""));
        assertNull(SolarWebPaths.resolveUnder(root, "C:\\Windows\\system.ini"));
    }

    @Test
    public void containedRejectsOutsideFile() throws Exception {
        File root = File.createTempFile("solar-music", "");
        assertTrue(root.delete());
        assertTrue(root.mkdirs());
        try {
            File outside = new File("/etc/passwd");
            assertNull("outside absolute file must not be contained",
                    SolarWebPaths.contained(root, outside));
            File inside = new File(root, "ok.mp3");
            assertTrue(inside.createNewFile());
            assertNotNull(SolarWebPaths.contained(root, inside));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void resolveUnderAcceptsRelative() throws Exception {
        File root = File.createTempFile("solar-music", "");
        assertTrue(root.delete());
        assertTrue(root.mkdirs());
        try {
            File album = new File(root, "Album");
            assertTrue(album.mkdirs());
            File song = new File(album, "track.mp3");
            assertTrue(song.createNewFile());
            File resolved = SolarWebPaths.resolveUnder(root, "Album/track.mp3");
            assertNotNull(resolved);
            assertTrue(resolved.getCanonicalPath().startsWith(root.getCanonicalPath()));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void audiobooksRejectsEscape() {
        File ab = new File("/tmp/solar-ab-root");
        assertNull(SolarWebPaths.resolveAudiobooks(ab, "AUDIOBOOKS/../../etc/passwd"));
        assertNull(SolarWebPaths.resolveAudiobooks(ab, "/etc/passwd"));
        assertNotNull(SolarWebPaths.resolveAudiobooks(ab, "AUDIOBOOKS"));
    }

    @Test
    public void safeUploadNameRejectsPathy() {
        assertNull(SolarWebPaths.safeUploadName("../x.mp3"));
        assertNull(SolarWebPaths.safeUploadName("a/b.mp3"));
        assertNull(SolarWebPaths.safeUploadName("a\\b.mp3"));
        assertNotNull(SolarWebPaths.safeUploadName("song.mp3"));
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
