package com.solar.launcher.stem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-track Stem mashup session — up to 3 songs, per-song gains/loop/chop.
 * Layman: jam three songs; first pad press focuses; same pad again changes which song you steer.
 * Technical: slots[0..2] + controlSongIndex + activeSongPerZone (routing) + SongState gains.
 * Cycle never stops audio — host keeps one StemMixer per song always running.
 * Was: cross-song pad seed made focus switch the interacted track. Reversal: seed z%N + no controlSongIndex.
 * 2026-07-19
 */
public final class StemSession {
    public static final int MAX_SONGS = 3;
    public static final int ZONE_COUNT = StemMixer.STEM_COUNT;

    /** Per-song mutable jam state. */
    public static final class SongState {
        public final float[] gains = new float[ZONE_COUNT];
        public final boolean[] zoneLoopCtrl = new boolean[ZONE_COUNT];
        public boolean looping;
        public float loopBars = StemControls.DEFAULT_LOOP_BARS;
        public boolean chopOn;
        public int chopStep; // index into CHOP_FRAC
        public float screwRate = 1f;
        public float bpm = 120f;
        public File track;
        public File bassBody;
        public List<LalalClient.StemFile> stems;

        /** Blank jam slate — all mute, no loop, chop off. 2026-07-19 */
        public void resetJam() {
            for (int i = 0; i < ZONE_COUNT; i++) {
                gains[i] = 0f;
                zoneLoopCtrl[i] = false;
            }
            looping = false;
            loopBars = StemControls.DEFAULT_LOOP_BARS;
            chopOn = false;
            chopStep = 0;
            screwRate = 1f;
        }
    }

    private final SongState[] songs = new SongState[MAX_SONGS];
    private int songCount;
    /** Which song (0..songCount-1) each zone arm is controlling. */
    private final int[] activeSongPerZone = new int[ZONE_COUNT];
    /** Song the user is interacting with — only advances on repress of focused pad. 2026-07-19 */
    private int controlSongIndex;
    private int activeZone;

    public StemSession() {
        for (int i = 0; i < MAX_SONGS; i++) {
            songs[i] = new SongState();
            songs[i].resetJam();
        }
    }

    /**
     * Bind prepared tracks (1–3). Clears jam state for each slot.
     * 2026-07-19
     */
    public void bindTracks(List<File> tracks) {
        songCount = 0;
        if (tracks != null) {
            for (int i = 0; i < tracks.size() && songCount < MAX_SONGS; i++) {
                File f = tracks.get(i);
                if (f == null || !f.isFile()) continue;
                songs[songCount].resetJam();
                songs[songCount].track = f;
                songs[songCount].stems = null;
                songs[songCount].bassBody = null;
                songCount++;
            }
        }
        if (songCount < 1) songCount = 0;
        // All pads start on song 0 — focus never jumps the interacted track. 2026-07-19
        controlSongIndex = 0;
        seedPadRoutingToControlSong();
        // No arm focused yet — first stem key focuses without cycling. 2026-07-19
        activeZone = -1;
    }

    /**
     * Point every pad at {@link #controlSongIndex} (usually 0 after bind).
     * Layman: until you repress a focused pad, every arm steers the same song.
     * Was: activeSongPerZone[z] = z % songCount (focus switched track). Reversal: that modulo seed.
     * 2026-07-19
     */
    public void seedPadRoutingToControlSong() {
        int s = controlSongIndex;
        if (s < 0) s = 0;
        if (songCount > 0 && s >= songCount) s = songCount - 1;
        for (int z = 0; z < ZONE_COUNT; z++) activeSongPerZone[z] = s;
    }

    /**
     * @deprecated name kept for callers; now seeds all pads to control song.
     * 2026-07-19
     */
    public void seedCrossSongPadRouting() {
        seedPadRoutingToControlSong();
    }

    /** Song currently steered by focus/wheel (0-based). 2026-07-19 */
    public int controlSongIndex() {
        return controlSongIndex;
    }

    public int songCount() {
        return songCount;
    }

    public boolean isMulti() {
        return songCount > 1;
    }

    public SongState song(int index) {
        if (index < 0 || index >= songCount) return null;
        return songs[index];
    }

    public int activeZone() {
        return activeZone;
    }

    public int songIndexForZone(int zone) {
        if (zone < 0 || zone >= ZONE_COUNT) return 0;
        int s = activeSongPerZone[zone];
        if (s < 0) s = 0;
        if (s >= songCount) s = Math.max(0, songCount - 1);
        return s;
    }

    public SongState activeSongState() {
        return song(songIndexForZone(activeZone));
    }

    /**
     * Stem key: focus zone, or if already focused advance the interacted song.
     * Layman: one click focuses; only a second click on that same pad changes the track.
     * Technical: focus snaps pad → controlSongIndex; cycle bumps controlSongIndex then pad.
     * Host must not call setActiveZone on key-DOWN (that armed false cycles). 2026-07-19
     * Was: focus left seeded per-pad song (track jumped on first focus). Reversal: that path.
     */
    public boolean onStemKey(int zone) {
        if (zone < 0 || zone >= ZONE_COUNT) return false;
        // Capture focus state before any write — returning to a pad must not cycle. 2026-07-19
        boolean alreadyFocused = StemControls.stemKeyShouldCycleSong(activeZone, zone, songCount);
        if (!alreadyFocused) {
            // Focus only — keep interacted track; pad steers current control song. 2026-07-19
            activeZone = zone;
            activeSongPerZone[zone] = clampSong(controlSongIndex);
            return false;
        }
        if (songCount <= 1) return false;
        controlSongIndex = (controlSongIndex + 1) % songCount;
        activeSongPerZone[zone] = controlSongIndex;
        return true;
    }

    /**
     * Focus a pad without cycling (stutter arm, UI restore).
     * Layman: lights that pad; keeps the same song you were already on.
     * 2026-07-19
     */
    public void setActiveZone(int zone) {
        if (zone < 0 || zone >= ZONE_COUNT) return;
        activeZone = zone;
        activeSongPerZone[zone] = clampSong(controlSongIndex);
    }

    /** Clamp song index into 0..songCount-1 (0 when empty). 2026-07-19 */
    private int clampSong(int s) {
        if (songCount < 1) return 0;
        if (s < 0) return 0;
        if (s >= songCount) return songCount - 1;
        return s;
    }

    /** Clear pad focus so next stem key is focus-only. 2026-07-19 */
    public void clearActiveZone() {
        activeZone = -1;
    }

    /** Display song number 1..N for a zone arm (0 if empty session). */
    public int displaySongNumber(int zone) {
        if (songCount < 1) return 0;
        return songIndexForZone(zone) + 1;
    }

    /**
     * Human track title for the song currently on a pad (no folder / extension).
     * Layman: the song name that pad is mixing from.
     * 2026-07-19
     */
    public String trackDisplayNameForZone(int zone) {
        SongState st = song(songIndexForZone(zone));
        if (st == null || st.track == null) return "";
        String n = st.track.getName();
        if (n == null) return "";
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < n.length()) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n;
    }

    public List<File> trackFiles() {
        List<File> out = new ArrayList<File>();
        for (int i = 0; i < songCount; i++) {
            if (songs[i].track != null) out.add(songs[i].track);
        }
        return out;
    }
}
