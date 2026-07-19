package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * trackStemsReady — user sidecar OR any cache leaf (live ↔ premix cross-mode).
 * 2026-07-19
 */
public class LalalTrackStemsReadyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Empty track with no stems folder → not ready. 2026-07-19 */
    @Test
    public void notReadyWithoutStems() throws Exception {
        File track = tmp.newFile("song.mp3");
        writeBytes(track, 200);
        File appCache = tmp.newFolder("cache");
        assertFalse(LalalClient.trackStemsReady(null, track, false, appCache));
        assertFalse(LalalClient.trackStemsReady(null, track, true, appCache));
    }

    /** User {@code Song.stems/} with four pads counts as ready. 2026-07-19 */
    @Test
    public void readyFromUserSidecar() throws Exception {
        File track = tmp.newFile("jam.mp3");
        writeBytes(track, 200);
        File stems = new File(tmp.getRoot(), "jam.stems");
        assertTrue(stems.mkdirs());
        writeStem(stems, "vocals.mp3");
        writeStem(stems, "drums.mp3");
        writeStem(stems, "bass.mp3");
        writeStem(stems, "melody.mp3");
        File appCache = tmp.newFolder("cache");
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
        // Sidecar is mode-agnostic — either premix flag is ready. 2026-07-19
        assertTrue(LalalClient.trackStemsReady(null, track, true, appCache));
    }

    /**
     * Live app-cache leaf satisfies both live and premix probes (cross-mode fallback).
     * Was: premix=true returned false and kicked a stuck Lalal upload.
     * 2026-07-19
     */
    @Test
    public void readyFromLegacyCache() throws Exception {
        File track = tmp.newFile("cached.mp3");
        writeBytes(track, 200);
        File appCache = tmp.newFolder("cache");
        File dir = LalalClient.stemCacheDir(appCache, track, false);
        assertTrue(dir.mkdirs());
        writeFourPads(dir);
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
        // Live stems also satisfy premix request — skip re-upload. 2026-07-19
        assertTrue(LalalClient.trackStemsReady(null, track, true, appCache));
        assertNotNull(LalalClient.findReadyStemDir(null, track, true, appCache));
    }

    /**
     * Premix app-cache leaf satisfies both premix and live probes (cross-mode).
     * 2026-07-19
     */
    @Test
    public void premixAwareLegacy() throws Exception {
        File track = tmp.newFile("premix.mp3");
        writeBytes(track, 200);
        File appCache = tmp.newFolder("cache");
        File dir = LalalClient.stemCacheDir(appCache, track, true);
        assertTrue(dir.mkdirs());
        writeFourPads(dir);
        assertTrue(LalalClient.trackStemsReady(null, track, true, appCache));
        // Premix stems also satisfy live request. 2026-07-19
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
        assertNotNull(LalalClient.findReadyStemDir(null, track, false, appCache));
    }

    /**
     * App-cache leaf alone (ctx null — no durable/overflow) still counts for both modes.
     * Simulates overflow-style publish home when only the path under appCache is populated.
     * 2026-07-19
     */
    @Test
    public void readyFromAppCacheLeafBothModes() throws Exception {
        File track = tmp.newFile("overflowish.mp3");
        writeBytes(track, 200);
        File appCache = tmp.newFolder("cache_overflow");
        // Live leaf only — ctx null so stemCacheCandidates cannot see durable/media. 2026-07-19
        File live = LalalClient.stemCacheDir(appCache, track, false);
        assertTrue(live.mkdirs());
        writeFourPads(live);
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
        assertTrue(LalalClient.trackStemsReady(null, track, true, appCache));

        File track2 = tmp.newFile("overflowish2.mp3");
        writeBytes(track2, 200);
        File premix = LalalClient.stemCacheDir(appCache, track2, true);
        assertTrue(premix.mkdirs());
        writeFourPads(premix);
        assertTrue(LalalClient.trackStemsReady(null, track2, true, appCache));
        assertTrue(LalalClient.trackStemsReady(null, track2, false, appCache));
    }

    /**
     * Path remount / mtime bump: legacy path-keyed leaf still found via alias or marker.
     * Layman: moving the song path must not force a new Lalal upload.
     * 2026-07-19
     */
    @Test
    public void pathDriftStillFindsLegacyPathKeyedLeaf() throws Exception {
        File musicA = tmp.newFolder("MusicA");
        File trackA = new File(musicA, "samejam.mp3");
        writeBytes(trackA, 400);
        File appCache = tmp.newFolder("cache_drift");
        // Simulate old publish leaf keyed by path|mtime|size. 2026-07-19
        String legacyLeaf = LalalClient.CACHE_LAYOUT + "_live_" + LalalClient.cacheKeyFor(trackA);
        File legacyDir = new File(new File(appCache, "lalal_stems"), legacyLeaf);
        assertTrue(legacyDir.mkdirs());
        writeFourPads(legacyDir);
        LalalClient.writeTrackMarker(legacyDir, trackA);

        // New path, same basename + size — primary cacheLeaf differs from legacy. 2026-07-19
        File musicB = tmp.newFolder("MusicB");
        File trackB = new File(musicB, "samejam.mp3");
        writeBytes(trackB, 400);
        assertFalse(LalalClient.cacheKeyFor(trackA).equals(LalalClient.cacheKeyFor(trackB)));
        // Stable keys match; findReady via marker scan. 2026-07-19
        assertEquals(LalalClient.cacheKeyStable(trackA), LalalClient.cacheKeyStable(trackB));
        File found = LalalClient.findReadyStemDir(null, trackB, false, appCache);
        assertNotNull(found);
        assertTrue(LalalClient.trackStemsReady(null, trackB, false, appCache));
        assertTrue(LalalClient.trackStemsReady(null, trackB, true, appCache));
    }

    /**
     * v5 layout leaf still satisfies ready probe (upgrade without re-upload).
     * 2026-07-19
     */
    @Test
    public void legacyV5LayoutLeafStillReady() throws Exception {
        File track = tmp.newFile("oldlayout.mp3");
        writeBytes(track, 300);
        File appCache = tmp.newFolder("cache_v5");
        String leaf = "v5_live_" + LalalClient.cacheKeyStable(track);
        File dir = new File(new File(appCache, "lalal_stems"), leaf);
        assertTrue(dir.mkdirs());
        writeFourPads(dir);
        assertNotNull(LalalClient.findReadyStemDir(null, track, false, appCache));
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
    }

    /**
     * Cached ready dir means resolve path must not need separate — trackStemsReady gate.
     * 2026-07-19
     */
    @Test
    public void cachedNeverNeedsSeparateGate() throws Exception {
        File track = tmp.newFile("nosep.mp3");
        writeBytes(track, 250);
        File appCache = tmp.newFolder("cache_nosep");
        File dir = LalalClient.stemCacheDir(appCache, track, false);
        assertTrue(dir.mkdirs());
        writeFourPads(dir);
        LalalClient.writeTrackMarker(dir, track);
        // Gate used by startJob / openStemPlayer before separateToMp3. 2026-07-19
        assertTrue(LalalClient.trackStemsReady(null, track, false, appCache));
        assertTrue(LalalClient.trackStemsReady(null, track, true, appCache));
        assertNotNull(LalalClient.findReadyStemDir(null, track, false, appCache));
        assertFalse(LalalClient.findReadyStemDir(null, track, false, appCache) == null);
    }

    /** Four pad MP3s that satisfy cacheReadyFlexible. 2026-07-19 */
    private static void writeFourPads(File dir) throws Exception {
        writeStem(dir, "vocals.mp3");
        writeStem(dir, "drum.mp3");
        writeStem(dir, "bass.mp3");
        writeStem(dir, "melody.mp3");
    }

    private static void writeStem(File dir, String name) throws Exception {
        writeBytes(new File(dir, name), 200);
    }

    private static void writeBytes(File f, int n) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            byte[] b = new byte[n];
            out.write(b);
        } finally {
            out.close();
        }
    }
}
