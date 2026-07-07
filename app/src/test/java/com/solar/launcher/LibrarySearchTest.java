package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LibrarySearchTest {

    private LibraryBrowsePrefs prefs;
    private List<LibrarySearch.SearchRow> library;

    @Before
    public void setUp() {
        prefs = new LibraryBrowsePrefs(new LibraryBrowsePrefsTest.MemSharedPreferences());
        library = new ArrayList<LibrarySearch.SearchRow>();
        library.add(row("Abbey Road", "The Beatles", "Abbey Road", "Rock"));
        library.add(row("Help!", "The Beatles", "Help!", "Rock"));
        library.add(row("Kind of Blue", "Miles Davis", "Kind of Blue", "Jazz"));
        library.add(row("So What", "Miles Davis", "Kind of Blue", "Jazz"));
    }

    @Test
    public void selfCheckRuns() {
        LibrarySearch.selfCheck();
    }

    @Test
    public void emptyQueryReturnsEmpty() {
        LibrarySearch.Results r = LibrarySearch.searchWithGenre(library, "   ", prefs);
        assertTrue(r.isEmpty());
    }

    @Test
    public void matchesArtistAlbumTitleAndGenre() {
        LibrarySearch.Results beatles = LibrarySearch.searchWithGenre(library, "beatles", prefs);
        assertFalse(beatles.isEmpty());
        assertTrue(beatles.artists.contains("The Beatles"));
        assertEquals(2, beatles.songs.size());

        LibrarySearch.Results jazz = LibrarySearch.searchWithGenre(library, "jazz", prefs);
        assertFalse(jazz.isEmpty());
        assertTrue(jazz.genres.contains("Jazz"));
        assertEquals(2, jazz.songs.size());

        LibrarySearch.Results title = LibrarySearch.searchWithGenre(library, "so what", prefs);
        assertEquals(1, title.songs.size());
        assertEquals("So What", title.songs.get(0).title);
    }

    @Test
    public void allTermsRequired() {
        LibrarySearch.Results r = LibrarySearch.searchWithGenre(library, "beatles jazz", prefs);
        assertTrue(r.isEmpty());
    }

    @Test
    public void dedupesArtistsAndAlbums() {
        LibrarySearch.Results r = LibrarySearch.searchWithGenre(library, "miles", prefs);
        assertEquals(1, r.artists.size());
        assertEquals("Miles Davis", r.artists.get(0));
        assertEquals(1, r.albums.size());
        assertEquals("Kind of Blue", r.albums.get(0).album);
    }

    @Test
    public void paginationHelpers() {
        List<String> many = new ArrayList<String>();
        for (int i = 0; i < 30; i++) many.add("row" + i);
        assertTrue(LibrarySearch.hasMore(many, LibrarySearch.PAGE_SIZE));
        assertEquals(LibrarySearch.PAGE_SIZE, LibrarySearch.page(many, LibrarySearch.PAGE_SIZE).size());
    }

    private static LibrarySearch.SearchRow row(String title, String artist, String album, String genre) {
        FlowCatalog.SongRow song = new FlowCatalog.SongRow(new File("/music/" + title + ".mp3"),
                title, artist, album, "", 0L);
        return new LibrarySearch.SearchRow(song, genre);
    }
}
