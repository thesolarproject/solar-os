package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Stem mashup session — focus vs cycle + cold mute + track name. 2026-07-19
 */
public class StemSessionTest {

    /** First press focuses; second on same arm cycles song. 2026-07-19 */
    @Test
    public void focusThenCycleSong() {
        StemSession s = new StemSession();
        List<File> tracks = fakeTracks(3);
        s.bindTracks(tracks);
        assertEquals(3, s.songCount());
        assertFalse(s.onStemKey(0)); // focus vocals
        assertEquals(0, s.activeZone());
        assertEquals(1, s.displaySongNumber(0));
        assertTrue(s.onStemKey(0)); // cycle to song 2
        assertEquals(2, s.displaySongNumber(0));
        assertTrue(s.onStemKey(0)); // song 3
        assertEquals(3, s.displaySongNumber(0));
        assertTrue(s.onStemKey(0)); // wrap to 1
        assertEquals(1, s.displaySongNumber(0));
    }

    /** Switching arms does not cycle the previous arm’s song. 2026-07-19 */
    @Test
    public void otherArmFocusDoesNotCycle() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(2));
        // Seed: drums (zone1) already steers song 2. 2026-07-19
        assertEquals(2, s.displaySongNumber(1));
        s.onStemKey(1); // focus drums
        assertTrue(s.onStemKey(1)); // drums → song 1
        assertFalse(s.onStemKey(2)); // focus bass — no cycle
        assertEquals(2, s.activeZone());
        assertEquals(1, s.displaySongNumber(1)); // drums still song 1
        assertEquals(1, s.displaySongNumber(2)); // bass still song 1 (seed zone2→0)
    }

    /**
     * Focus A → focus B → press A again = re-focus only (no cycle).
     * Layman: coming back to a pad does not skip songs; repress while focused does.
     * 2026-07-19
     */
    @Test
    public void focusOtherThenBackDoesNotCycle() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(3));
        assertFalse(s.onStemKey(0)); // focus A
        assertEquals(1, s.displaySongNumber(0));
        assertFalse(s.onStemKey(1)); // focus B
        assertFalse(s.onStemKey(0)); // back to A — focus only
        assertEquals(0, s.activeZone());
        assertEquals(1, s.displaySongNumber(0));
        assertTrue(s.onStemKey(0)); // already focused → cycle
        assertEquals(2, s.displaySongNumber(0));
    }

    /**
     * Multi bind seeds pads across songs so raising different pads layers without cycling.
     * 2026-07-19
     */
    @Test
    public void multiBindSeedsCrossSongPadRouting() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(2));
        assertEquals(0, s.songIndexForZone(0));
        assertEquals(1, s.songIndexForZone(1));
        assertEquals(0, s.songIndexForZone(2));
        assertEquals(1, s.songIndexForZone(3));
        StemSession s3 = new StemSession();
        s3.bindTracks(fakeTracks(3));
        assertEquals(0, s3.songIndexForZone(0));
        assertEquals(1, s3.songIndexForZone(1));
        assertEquals(2, s3.songIndexForZone(2));
        assertEquals(0, s3.songIndexForZone(3));
    }

    /**
     * Pads keep independent song indices so all can steer different tracks together.
     * Cycle one pad does not rewrite other pads’ song indices or gains.
     * 2026-07-19
     */
    @Test
    public void multiPadsIndependentSongIndicesActive() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(3));
        // Seed already spreads 0/1/2 across pads. 2026-07-19
        assertEquals(0, s.songIndexForZone(0));
        assertEquals(1, s.songIndexForZone(1));
        assertEquals(2, s.songIndexForZone(2));
        s.song(0).gains[0] = 0.6f;
        s.song(1).gains[1] = 0.4f;
        assertFalse(s.onStemKey(0));
        assertTrue(s.onStemKey(0)); // vocals control → song 2
        assertEquals(1, s.songIndexForZone(0));
        assertEquals(1, s.songIndexForZone(1)); // drums unchanged
        assertEquals(2, s.songIndexForZone(2)); // bass unchanged
        // Other songs’ gains untouched by cycle (host must not zero them). 2026-07-19
        assertEquals(0.6f, s.song(0).gains[0], 0.001f);
        assertEquals(0.4f, s.song(1).gains[1], 0.001f);
        assertEquals(0f, s.song(1).gains[0], 0.001f); // song2 vocals still cold
    }

    /** Focused pad’s track display name updates when cycling. 2026-07-19 */
    @Test
    public void trackDisplayNameFollowsPadSong() {
        StemSession s = new StemSession();
        List<File> tracks = fakeTracks(2);
        s.bindTracks(tracks);
        assertFalse(s.onStemKey(0));
        String n1 = s.trackDisplayNameForZone(0);
        assertTrue(n1.length() > 0);
        assertTrue(s.onStemKey(0)); // cycle → song 2
        String n2 = s.trackDisplayNameForZone(0);
        assertTrue(n2.length() > 0);
        assertFalse(n1.equals(n2));
    }

    /** Cold bind — all gains 0, no loop. 2026-07-19 */
    @Test
    public void coldStartMuteNoLoop() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(1));
        StemSession.SongState st = s.song(0);
        assertEquals(0f, st.gains[0], 0.001f);
        assertEquals(0f, st.gains[2], 0.001f);
        assertFalse(st.looping);
        assertFalse(st.zoneLoopCtrl[0]);
    }

    /**
     * Raise Song1 Vocals, cycle Vocals → Song2: Song2 stays mute / no loop;
     * Song1 keeps its own gain and loop (no inheritance). 2026-07-19
     */
    @Test
    public void cycleDoesNotInheritGainsOrLoop() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(3));
        assertFalse(s.onStemKey(0)); // focus vocals on song 1
        StemSession.SongState song1 = s.song(0);
        song1.gains[0] = 0.75f;
        song1.zoneLoopCtrl[0] = true;
        song1.looping = true;
        assertTrue(s.onStemKey(0)); // cycle → song 2
        StemSession.SongState song2 = s.song(1);
        assertEquals(2, s.displaySongNumber(0));
        assertEquals(0f, song2.gains[0], 0.001f);
        assertFalse(song2.zoneLoopCtrl[0]);
        assertFalse(song2.looping);
        // Song1 unchanged while arm is on Song2. 2026-07-19
        assertEquals(0.75f, song1.gains[0], 0.001f);
        assertTrue(song1.zoneLoopCtrl[0]);
        assertTrue(song1.looping);
        assertTrue(s.onStemKey(0)); // cycle → song 3 still cold
        StemSession.SongState song3 = s.song(2);
        assertEquals(0f, song3.gains[0], 0.001f);
        assertFalse(song3.looping);
        assertTrue(s.onStemKey(0)); // wrap → song 1 still has raised gain
        assertEquals(1, s.displaySongNumber(0));
        assertEquals(0.75f, s.song(0).gains[0], 0.001f);
        assertTrue(s.song(0).looping);
    }

    @Test
    public void stemKeyShouldCycleHelper() {
        assertFalse(StemControls.stemKeyShouldCycleSong(0, 1, 3));
        assertTrue(StemControls.stemKeyShouldCycleSong(1, 1, 3));
        assertFalse(StemControls.stemKeyShouldCycleSong(1, 1, 1));
        assertFalse(StemControls.stemKeyShouldCycleSong(-1, 0, 3)); // no focus yet
        // Face: volume beads while wheelUsesVolume; loop bars only in loop wheel. 2026-07-19
        assertFalse(StemControls.faceShowsLoopBars(false, false));
        assertTrue(StemControls.faceShowsLoopBars(true, false));
        assertFalse(StemControls.faceShowsLoopBars(true, true)); // hold-Center volume peek
    }

    /** Single track — repress never cycles; no mashup song digit. 2026-07-19 */
    @Test
    public void singleTrackFocusOnlyNoCycle() {
        StemSession s = new StemSession();
        s.bindTracks(fakeTracks(1));
        assertFalse(s.isMulti());
        assertFalse(s.onStemKey(0)); // focus
        assertFalse(s.onStemKey(0)); // still focus — songCount<=1 no-ops cycle
        assertEquals(0, s.activeZone());
        assertEquals(1, s.displaySongNumber(0)); // slot 1 internally
        // Face uses isMulti() → digits 0; displaySongNumber alone is fine for status. 2026-07-19
    }

    private static List<File> fakeTracks(int n) {
        List<File> out = new ArrayList<File>();
        for (int i = 0; i < n; i++) {
            // Non-existent path is fine — bindTracks only checks isFile(); skip empty.
            // Use temp files so isFile() is true.
            try {
                File f = File.createTempFile("stem-song-" + i + "-", ".mp3");
                f.deleteOnExit();
                out.add(f);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return out;
    }
}
