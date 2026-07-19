package com.solar.launcher.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2026-07-18 — Tier-0 RAM indexes for library browse (artists/albums/counts) keyed by scan gen.
 * Layman: after a scan we keep sorted name lists in memory so opening Artists doesn’t re-walk storage.
 * Technical: rebuild once per libraryScanGen from compact path/artist/album triples; no full SongItem.
 * Reversal: delete; rebuild categories by scanning customLibrary each open.
 */
public final class LibraryRamCache {

    /** Compact row for index build — avoids holding full SongItem in the index path. */
    public static final class NavRow {
        public final String path;
        public final String artist;
        public final String album;

        public NavRow(String path, String artist, String album) {
            this.path = path != null ? path : "";
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
        }
    }

    private int libraryGen = -1;
    private LibraryMemoryBudget.Mode mode = LibraryMemoryBudget.Mode.FULL_RESIDENT;
    private List<String> artists = Collections.emptyList();
    private List<String> albums = Collections.emptyList();
    private int trackCount;
    private final LibrarySegmentCache<NavRow> segments = new LibrarySegmentCache<NavRow>();

    public LibraryRamCache() {}

    public synchronized void invalidate() {
        libraryGen = -1;
        artists = Collections.emptyList();
        albums = Collections.emptyList();
        trackCount = 0;
        segments.clear();
        mode = LibraryMemoryBudget.Mode.FULL_RESIDENT;
    }

    /**
     * Rebuild Tier-0 indexes when scan generation changes.
     * Layman: remember artist/album name lists for this library generation.
     */
    public synchronized void rebuild(int gen, List<NavRow> rows) {
        if (rows == null) rows = Collections.emptyList();
        if (gen == libraryGen && trackCount == rows.size() && !artists.isEmpty()) {
            return;
        }
        libraryGen = gen;
        trackCount = rows.size();
        mode = LibraryMemoryBudget.chooseMode(trackCount);
        Set<String> artistSet = new HashSet<String>();
        Set<String> albumSet = new HashSet<String>();
        for (int i = 0; i < rows.size(); i++) {
            NavRow r = rows.get(i);
            if (r == null) continue;
            String ar = r.artist.trim();
            if (!ar.isEmpty() && !"Unknown Artist".equalsIgnoreCase(ar)) {
                artistSet.add(ar);
            }
            String al = r.album.trim();
            if (!al.isEmpty() && !"Unknown Album".equalsIgnoreCase(al)) {
                albumSet.add(al);
            }
        }
        List<String> aOut = new ArrayList<String>(artistSet);
        Collections.sort(aOut, String.CASE_INSENSITIVE_ORDER);
        artists = Collections.unmodifiableList(aOut);
        List<String> alOut = new ArrayList<String>(albumSet);
        Collections.sort(alOut, String.CASE_INSENSITIVE_ORDER);
        albums = Collections.unmodifiableList(alOut);
        segments.clear();
        // Warm first segment for SEGMENTED opens (visible window near top).
        if (mode == LibraryMemoryBudget.Mode.SEGMENTED && !rows.isEmpty()) {
            int bs = segments.blockSize();
            int end = Math.min(bs, rows.size());
            segments.putBlock(0, rows.subList(0, end));
        }
    }

    public synchronized int generation() {
        return libraryGen;
    }

    public synchronized LibraryMemoryBudget.Mode mode() {
        return mode;
    }

    public synchronized int trackCount() {
        return trackCount;
    }

    public synchronized List<String> artists(int gen) {
        return libraryGen == gen ? artists : Collections.<String>emptyList();
    }

    public synchronized List<String> albums(int gen) {
        return libraryGen == gen ? albums : Collections.<String>emptyList();
    }

    public synchronized LibrarySegmentCache<NavRow> segments() {
        return segments;
    }

    /** True when Tier-0 artists list is warm for this gen. */
    public synchronized boolean hasArtists(int gen) {
        return libraryGen == gen && !artists.isEmpty();
    }

    static void selfCheck() {
        LibraryRamCache c = new LibraryRamCache();
        List<NavRow> rows = new ArrayList<NavRow>();
        rows.add(new NavRow("/a", "Zebra", "Z-Album"));
        rows.add(new NavRow("/b", "Alpha", "A-Album"));
        c.rebuild(1, rows);
        if (!"Alpha".equals(c.artists(1).get(0))) throw new AssertionError("sort");
        if (c.albums(1).size() != 2) throw new AssertionError("albums");
        if (c.trackCount() != 2) throw new AssertionError("count");
    }
}
