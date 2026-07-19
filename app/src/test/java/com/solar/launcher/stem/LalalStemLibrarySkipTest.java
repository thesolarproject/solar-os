package com.solar.launcher.stem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Stem pad trees must not be library-ingested.
 * 2026-07-19
 */
public class LalalStemLibrarySkipTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Normal track under Music/ is not a stem artifact. 2026-07-19 */
    @Test
    public void normalTrackNotSkipped() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "song.mp3");
        assertTrue(track.createNewFile());
        assertFalse(LalalClient.isStemLibraryArtifact(track));
        assertFalse(LalalClient.isStemLibraryArtifact(music));
    }

    /** User sidecar Song.stems/ and files under it are skipped. 2026-07-19 */
    @Test
    public void userSidecarSkipped() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "jam.mp3");
        assertTrue(track.createNewFile());
        File stems = LalalClient.userStemsDir(track);
        assertTrue(stems.mkdirs());
        File vocals = new File(stems, "vocals.mp3");
        assertTrue(vocals.createNewFile());
        assertTrue(LalalClient.isStemLibraryArtifact(stems));
        assertTrue(LalalClient.isStemLibraryArtifact(vocals));
        assertFalse(LalalClient.isStemLibraryArtifact(track));
    }

    /** App/overflow cache folders lalal_stems and lalal_work are skipped. 2026-07-19 */
    @Test
    public void cacheTreesSkipped() throws Exception {
        File cache = tmp.newFolder("cache");
        File lalal = new File(cache, "lalal_stems");
        assertTrue(lalal.mkdirs());
        File leaf = new File(lalal, "abc123");
        assertTrue(leaf.mkdirs());
        File drum = new File(leaf, "drum.mp3");
        assertTrue(drum.createNewFile());
        assertTrue(LalalClient.isStemLibraryArtifact(lalal));
        assertTrue(LalalClient.isStemLibraryArtifact(drum));

        File work = new File(cache, "lalal_work");
        assertTrue(work.mkdirs());
        File scratch = new File(work, "bass.mp3");
        assertTrue(scratch.createNewFile());
        assertTrue(LalalClient.isStemLibraryArtifact(work));
        assertTrue(LalalClient.isStemLibraryArtifact(scratch));
    }
}
