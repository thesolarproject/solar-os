package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2026-07-06: Cached {@link FlowCatalog.SongRow} list — avoids realloc on every Flow/catalog call.
 * Layman: we copy the music list once per scan instead of on every carousel scroll.
 */
public final class FlowLibraryRows {

    private List<FlowCatalog.SongRow> cached = Collections.emptyList();
    private List<LibrarySearch.SearchRow> searchCached = Collections.emptyList();
    private int libraryGen = -1;

    /** Build rows from in-memory library; reuse when generation matches. */
    public synchronized List<FlowCatalog.SongRow> rows(List<MainActivity.SongItem> library, int gen) {
        if (cached != null && !cached.isEmpty() && libraryGen == gen) return cached;
        List<FlowCatalog.SongRow> out = new ArrayList<FlowCatalog.SongRow>();
        if (library != null) {
            synchronized (library) {
                for (MainActivity.SongItem song : library) {
                    if (song == null) continue;
                    out.add(toRow(song));
                }
            }
        }
        cached = Collections.unmodifiableList(out);
        searchCached = buildSearchRows(out, library);
        libraryGen = gen;
        return cached;
    }

    /** Rows with genre for {@link LibrarySearch#searchWithGenre}. */
    public synchronized List<LibrarySearch.SearchRow> searchRows(List<MainActivity.SongItem> library, int gen) {
        rows(library, gen);
        return searchCached;
    }

    public synchronized void invalidate() {
        cached = Collections.emptyList();
        searchCached = Collections.emptyList();
        libraryGen = -1;
    }

    static FlowCatalog.SongRow toRow(MainActivity.SongItem song) {
        String genre = song.genre != null ? song.genre : "";
        String yearLabel = song.year > 0 ? String.valueOf(song.year) : "";
        return new FlowCatalog.SongRow(song.file, song.title, song.artist, song.album,
                song.albumArtist, song.file != null ? song.file.lastModified() : 0L,
                song.trackNumber, genre, song.year, yearLabel);
    }

    private static List<LibrarySearch.SearchRow> buildSearchRows(List<FlowCatalog.SongRow> rows,
            List<MainActivity.SongItem> library) {
        List<LibrarySearch.SearchRow> out = new ArrayList<LibrarySearch.SearchRow>(rows.size());
        if (library == null) {
            for (FlowCatalog.SongRow r : rows) out.add(new LibrarySearch.SearchRow(r, r.genre));
            return Collections.unmodifiableList(out);
        }
        int i = 0;
        synchronized (library) {
            for (MainActivity.SongItem song : library) {
                if (song == null) continue;
                FlowCatalog.SongRow row = i < rows.size() ? rows.get(i) : toRow(song);
                out.add(new LibrarySearch.SearchRow(row, song.genre));
                i++;
            }
        }
        return Collections.unmodifiableList(out);
    }
}
