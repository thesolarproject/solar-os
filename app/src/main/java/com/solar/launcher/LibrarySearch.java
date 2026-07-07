package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 2026-07-05: On-device music library text search — token match across title/artist/album/genre.
 * Layman: type a few words; we find matching artists, albums, genres, and songs on the player.
 */
public final class LibrarySearch {

    /** Rows shown per section before a Show-more row in library search UI. */
    public static final int PAGE_SIZE = 25;

    /** One album hit with the dominant artist for drill-down. */
    public static final class AlbumHit {
        public final String album;
        public final String artist;

        public AlbumHit(String album, String artist) {
            this.album = album != null ? album : "";
            this.artist = artist != null ? artist : "";
        }
    }

    /** Grouped local search hits for library browse UI. */
    public static final class Results {
        public final List<String> artists;
        public final List<AlbumHit> albums;
        public final List<String> genres;
        public final List<FlowCatalog.SongRow> songs;

        public Results(List<String> artists, List<AlbumHit> albums, List<String> genres,
                List<FlowCatalog.SongRow> songs) {
            this.artists = artists != null ? artists : Collections.<String>emptyList();
            this.albums = albums != null ? albums : Collections.<AlbumHit>emptyList();
            this.genres = genres != null ? genres : Collections.<String>emptyList();
            this.songs = songs != null ? songs : Collections.<FlowCatalog.SongRow>emptyList();
        }

        /** True when every local section is empty. */
        public boolean isEmpty() {
            return artists.isEmpty() && albums.isEmpty() && genres.isEmpty() && songs.isEmpty();
        }
    }

    private LibrarySearch() {}

    /**
     * 2026-07-05: Search the scanned library; honors artist/album browse prefs on matched tracks only.
     * Reversal: delete this helper and library search UI reverts to browse-only lists.
     */
    public static Results search(List<FlowCatalog.SongRow> library, String query,
            LibraryBrowsePrefs prefs) {
        if (library == null || library.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }

        List<FlowCatalog.SongRow> matched = new ArrayList<FlowCatalog.SongRow>();
        for (FlowCatalog.SongRow song : library) {
            if (song != null && matches(terms, song)) matched.add(song);
        }
        if (matched.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }

        List<ArtistBrowsePolicy.Track> policyTracks = policyTracksFromRows(matched);
        List<String> artists = ArtistBrowsePolicy.collectArtists(policyTracks, prefs);

        List<AlbumHit> albums = new ArrayList<AlbumHit>();
        for (String title : LibraryAlbumRack.albumTitles(matched, prefs, policyTracks)) {
            String artist = dominantArtistForAlbum(title, matched, prefs);
            albums.add(new AlbumHit(title, artist));
        }

        Set<String> genreSeen = new HashSet<String>();
        List<String> genres = new ArrayList<String>();
        for (FlowCatalog.SongRow song : matched) {
            String genre = songGenre(song);
            if (genre.isEmpty() || "Unknown Genre".equalsIgnoreCase(genre)) continue;
            if (genreSeen.add(genre)) genres.add(genre);
        }
        Collections.sort(genres, String.CASE_INSENSITIVE_ORDER);

        List<FlowCatalog.SongRow> songs = new ArrayList<FlowCatalog.SongRow>(matched);
        Collections.sort(songs, new Comparator<FlowCatalog.SongRow>() {
            @Override
            public int compare(FlowCatalog.SongRow a, FlowCatalog.SongRow b) {
                String ta = a.title != null ? a.title : "";
                String tb = b.title != null ? b.title : "";
                return ta.compareToIgnoreCase(tb);
            }
        });

        return new Results(artists, albums, genres, songs);
    }

    /** Split query into lowercase terms; empty tokens dropped. */
    public static List<String> tokenize(String query) {
        List<String> out = new ArrayList<String>();
        if (query == null) return out;
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return out;
        String[] parts = trimmed.toLowerCase(Locale.US).split("\\s+");
        for (String p : parts) {
            if (p != null && !p.isEmpty()) out.add(p);
        }
        return out;
    }

    /** All terms must appear somewhere in title/artist/album/genre haystack. */
    public static boolean matches(List<String> terms, FlowCatalog.SongRow song) {
        if (terms == null || terms.isEmpty() || song == null) return false;
        String haystack = buildHaystack(song);
        for (String term : terms) {
            if (term == null || term.isEmpty()) continue;
            if (!haystack.contains(term)) return false;
        }
        return true;
    }

    /** Visible slice for paginated library search sections. */
    public static <T> List<T> page(List<T> all, int visibleCount) {
        if (all == null || all.isEmpty()) return Collections.emptyList();
        int n = visibleCount <= 0 ? PAGE_SIZE : visibleCount;
        if (all.size() <= n) return new ArrayList<T>(all);
        return new ArrayList<T>(all.subList(0, n));
    }

    public static boolean hasMore(List<?> all, int visibleCount) {
        if (all == null) return false;
        int n = visibleCount <= 0 ? PAGE_SIZE : visibleCount;
        return all.size() > n;
    }

    private static String buildHaystack(FlowCatalog.SongRow song) {
        StringBuilder sb = new StringBuilder();
        appendLower(sb, song.title);
        appendLower(sb, song.artist);
        appendLower(sb, song.album);
        appendLower(sb, song.albumArtist);
        appendLower(sb, songGenre(song));
        return sb.toString();
    }

    private static void appendLower(StringBuilder sb, String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(s.trim().toLowerCase(Locale.US));
    }

    private static String songGenre(FlowCatalog.SongRow song) {
        // SongRow has no genre field — genre lives on MainActivity.SongItem only; callers pass genre via title haystack extension.
        return "";
    }

    /** Build policy tracks for artist/album browse helpers. */
    static List<ArtistBrowsePolicy.Track> policyTracksFromRows(List<FlowCatalog.SongRow> rows) {
        List<ArtistBrowsePolicy.Track> out = new ArrayList<ArtistBrowsePolicy.Track>();
        if (rows == null) return out;
        for (FlowCatalog.SongRow song : rows) {
            if (song == null) continue;
            long lm = song.lastModified > 0 ? song.lastModified : 0L;
            out.add(new ArtistBrowsePolicy.Track(song.artist, song.album, song.albumArtist, lm));
        }
        return out;
    }

    /** Dominant credited artist for an album title within matched songs. */
    static String dominantArtistForAlbum(String albumTitle, List<FlowCatalog.SongRow> matched,
            LibraryBrowsePrefs prefs) {
        if (albumTitle == null || matched == null) return "";
        java.util.Map<String, Integer> votes = new java.util.HashMap<String, Integer>();
        for (FlowCatalog.SongRow song : matched) {
            if (song == null || !AlbumNames.equals(albumTitle, song.album)) continue;
            String artist = song.artist != null ? song.artist.trim() : "";
            if (artist.isEmpty()) continue;
            Integer n = votes.get(artist);
            votes.put(artist, n != null ? n + 1 : 1);
        }
        return FlowCatalog.primaryArtistFromVotes(votes, "");
    }

    /**
     * 2026-07-05: Search with genre in haystack — MainActivity passes rows built from SongItem (includes genre).
     */
    public static Results searchWithGenre(List<SearchRow> library, String query, LibraryBrowsePrefs prefs) {
        if (library == null || library.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }
        List<FlowCatalog.SongRow> matchedRows = new ArrayList<FlowCatalog.SongRow>();
        List<SearchRow> matched = new ArrayList<SearchRow>();
        for (SearchRow row : library) {
            if (row == null || row.song == null) continue;
            if (!matchesWithGenre(terms, row)) continue;
            matched.add(row);
            matchedRows.add(row.song);
        }
        if (matched.isEmpty()) {
            return new Results(Collections.<String>emptyList(), Collections.<AlbumHit>emptyList(),
                    Collections.<String>emptyList(), Collections.<FlowCatalog.SongRow>emptyList());
        }

        List<ArtistBrowsePolicy.Track> policyTracks = policyTracksFromRows(matchedRows);
        List<String> artists = ArtistBrowsePolicy.collectArtists(policyTracks, prefs);

        List<AlbumHit> albums = new ArrayList<AlbumHit>();
        for (String title : LibraryAlbumRack.albumTitles(matchedRows, prefs, policyTracks)) {
            albums.add(new AlbumHit(title, dominantArtistForAlbum(title, matchedRows, prefs)));
        }

        Set<String> genreSeen = new HashSet<String>();
        List<String> genres = new ArrayList<String>();
        for (SearchRow row : matched) {
            String genre = row.genre != null ? row.genre.trim() : "";
            if (genre.isEmpty() || "Unknown Genre".equalsIgnoreCase(genre)) continue;
            if (genreSeen.add(genre)) genres.add(genre);
        }
        Collections.sort(genres, String.CASE_INSENSITIVE_ORDER);

        List<FlowCatalog.SongRow> songs = new ArrayList<FlowCatalog.SongRow>(matchedRows);
        Collections.sort(songs, new Comparator<FlowCatalog.SongRow>() {
            @Override
            public int compare(FlowCatalog.SongRow a, FlowCatalog.SongRow b) {
                String ta = a.title != null ? a.title : "";
                String tb = b.title != null ? b.title : "";
                return ta.compareToIgnoreCase(tb);
            }
        });

        return new Results(artists, albums, genres, songs);
    }

    /** Song row plus genre for library search matching. */
    public static final class SearchRow {
        public final FlowCatalog.SongRow song;
        public final String genre;

        public SearchRow(FlowCatalog.SongRow song, String genre) {
            this.song = song;
            this.genre = genre != null ? genre : "";
        }
    }

    static boolean matchesWithGenre(List<String> terms, SearchRow row) {
        if (terms == null || terms.isEmpty() || row == null || row.song == null) return false;
        String haystack = buildHaystack(row.song);
        if (row.genre != null && !row.genre.trim().isEmpty()) {
            haystack = haystack + " " + row.genre.trim().toLowerCase(Locale.US);
        }
        for (String term : terms) {
            if (term == null || term.isEmpty()) continue;
            if (!haystack.contains(term)) return false;
        }
        return true;
    }

    /** Self-check for JVM tests — fails fast if token match regresses. */
    static void selfCheck() {
        FlowCatalog.SongRow song = new FlowCatalog.SongRow(null, "Hello World", "Test Artist",
                "Test Album", "", 0L);
        List<String> terms = tokenize("hello test");
        if (!matches(terms, song)) throw new AssertionError("basic match");
        SearchRow row = new SearchRow(song, "Rock");
        if (!matchesWithGenre(tokenize("rock"), row)) throw new AssertionError("genre match");
        if (matchesWithGenre(tokenize("jazz"), row)) throw new AssertionError("genre miss");
    }
}
