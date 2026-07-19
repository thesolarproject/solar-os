package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Solo leaf keying, artifact skip, prefer-cache resolve.
 * 2026-07-19
 */
public class LalalSoloCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Stable leaf from basename|size. 2026-07-19 */
    @Test
    public void soloCacheLeafStable() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "jam.mp3");
        writeBytes(track, new byte[200]);
        String leaf = LalalClient.soloCacheLeaf(track);
        assertTrue(leaf.startsWith("v1_"));
        assertEquals(leaf, LalalClient.soloCacheLeaf(track));
    }

    /** stem_solo trees are library artifacts. 2026-07-19 */
    @Test
    public void soloTreeSkippedFromLibrary() throws Exception {
        File cache = tmp.newFolder("cache");
        File soloRoot = new File(new File(cache, "stem_solo"), "lalal");
        File leaf = new File(soloRoot, "v1_abc");
        assertTrue(leaf.mkdirs());
        File vocals = new File(leaf, "vocals.mp3");
        writeBytes(vocals, new byte[120]);
        assertTrue(LalalClient.isStemLibraryArtifact(soloRoot));
        assertTrue(LalalClient.isStemLibraryArtifact(vocals));
    }

    /** findReadySoloFile hits solo leaf when marker matches. 2026-07-19 */
    @Test
    public void findReadyPrefersSoloLeaf() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "song.mp3");
        writeBytes(track, new byte[300]);
        File cache = tmp.newFolder("cache");
        File solo = LalalClient.soloDir(cache, track);
        assertTrue(solo.mkdirs());
        File vocals = new File(solo, "vocals.mp3");
        File instr = new File(solo, "instrumental.mp3");
        writeBytes(vocals, new byte[150]);
        writeBytes(instr, new byte[150]);
        LalalClient.writeTrackMarker(solo, track);
        File hitAcap = LalalClient.findReadySoloFile(null, track, SoloMode.ACAPELLA, cache);
        File hitInstr = LalalClient.findReadySoloFile(null, track, SoloMode.INSTRUMENTAL, cache);
        assertNotNull(hitAcap);
        assertNotNull(hitInstr);
        assertEquals(vocals.getAbsolutePath(), hitAcap.getAbsolutePath());
        assertEquals(instr.getAbsolutePath(), hitInstr.getAbsolutePath());
        assertNull(LalalClient.findReadySoloFile(null, track, SoloMode.ACAPELLA, tmp.newFolder("empty")));
    }

    private static void writeBytes(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
