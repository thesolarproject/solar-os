package com.solar.launcher;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.solar.launcher.db.SolarDatabase;
import com.solar.launcher.db.SolarDbHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SQLite cache for local Music library metadata — avoids re-reading ID3 on every scan.
 * ponytail: keyed by path + mtime + size; stale rows purged after each walk.
 */
public class MusicLibraryStore extends SolarDbHelper {
    private static final String DB_NAME = "music_library.db";
    private static final int DB_VERSION = 4;

    private static MusicLibraryStore instance;
    private boolean legacyTrackNumbersMigrated;
    /** 2026-07-06: DB sentinel — year read, tag has no release year (distinct from legacy 0). */
    static final int YEAR_UNKNOWN_SCANNED = -1;

    /** Batch upsert input — avoids per-row method-call overhead during scans. */
    public static final class Upsert {
        public final File file;
        public final String title;
        public final String artist;
        public final String album;
        public final String genre;
        public final String albumArtist;
        public final String durationMs;
        public final int trackNumber;
        public final int year;

        public Upsert(File file, String title, String artist, String album,
                String genre, String albumArtist, String durationMs, int trackNumber) {
            this(file, title, artist, album, genre, albumArtist, durationMs, trackNumber, 0);
        }

        public Upsert(File file, String title, String artist, String album,
                String genre, String albumArtist, String durationMs, int trackNumber, int year) {
            this.file = file;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
            this.genre = genre != null ? genre : "";
            this.albumArtist = albumArtist != null ? albumArtist : "";
            this.durationMs = durationMs != null ? durationMs : "";
            this.trackNumber = trackNumber;
            this.year = year;
        }
    }

    /** Cached row — maps to {@link MainActivity}'s SongItem fields. */
    public static final class Track {
        public final String path;
        public final long mtime;
        public final long size;
        public final String title;
        public final String artist;
        public final String album;
        public final String genre;
        public final String albumArtist;
        public final String durationMs;
        public final int trackNumber;
        public final int year;

        Track(String path, long mtime, long size, String title, String artist, String album,
                String genre, String albumArtist, String durationMs, int trackNumber) {
            this(path, mtime, size, title, artist, album, genre, albumArtist, durationMs, trackNumber, 0);
        }

        Track(String path, long mtime, long size, String title, String artist, String album,
                String genre, String albumArtist, String durationMs, int trackNumber, int year) {
            this.path = path;
            this.mtime = mtime;
            this.size = size;
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
            this.genre = genre != null ? genre : "";
            this.albumArtist = albumArtist != null ? albumArtist : "";
            this.durationMs = durationMs != null ? durationMs : "";
            this.trackNumber = trackNumber;
            this.year = year;
        }

        /** Duration in whole seconds for Soulseek share attributes; 0 when unknown. */
        public int durationSec() {
            if (durationMs == null || durationMs.trim().isEmpty()) return 0;
            try {
                int ms = Integer.parseInt(durationMs.trim());
                return ms > 0 ? ms / 1000 : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private MusicLibraryStore(Context ctx) {
        this(ctx, true);
    }

    private MusicLibraryStore(Context ctx, boolean walEnabled) {
        super(ctx.getApplicationContext(), DB_NAME, DB_VERSION, walEnabled);
    }

    public static synchronized MusicLibraryStore getInstance(Context ctx) {
        if (instance == null) {
            instance = new MusicLibraryStore(ctx.getApplicationContext());
        }
        return instance;
    }

    static void resetInstanceForTest() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    /** In-memory DB for unit tests. */
    public static MusicLibraryStore openForTest(Context ctx) {
        resetInstanceForTest();
        final SQLiteDatabase[] mem = new SQLiteDatabase[1];
        instance = new MusicLibraryStore(ctx.getApplicationContext(), false) {
            @Override
            public synchronized SQLiteDatabase getWritableDatabase() {
                if (mem[0] == null) {
                    mem[0] = SQLiteDatabase.create(null);
                    onCreate(mem[0]);
                }
                return mem[0];
            }

            @Override
            public synchronized SQLiteDatabase getReadableDatabase() {
                return getWritableDatabase();
            }
        };
        return instance;
    }

    @Override
    public void onCreate(SolarDatabase db) {
        db.execSQL("CREATE TABLE tracks ("
                + "path TEXT PRIMARY KEY,"
                + "mtime INTEGER NOT NULL DEFAULT 0,"
                + "size INTEGER NOT NULL DEFAULT 0,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "artist TEXT NOT NULL DEFAULT '',"
                + "album TEXT NOT NULL DEFAULT '',"
                + "genre TEXT NOT NULL DEFAULT '',"
                + "album_artist TEXT NOT NULL DEFAULT '',"
                + "duration_ms TEXT NOT NULL DEFAULT '',"
                + "track_number INTEGER NOT NULL DEFAULT 0,"
                + "year INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_tracks_mtime ON tracks(mtime)");
        db.execSQL("CREATE TABLE favorite_paths (path TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE audiobook_bookmarks ("
                + "path TEXT PRIMARY KEY,"
                + "position_ms INTEGER NOT NULL DEFAULT 0,"
                + "chapter_index INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SolarDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN track_number INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS tracks");
            onCreate(db);
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE tracks ADD COLUMN year INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {}
            db.execSQL("CREATE TABLE IF NOT EXISTS favorite_paths (path TEXT PRIMARY KEY)");
            db.execSQL("CREATE TABLE IF NOT EXISTS audiobook_bookmarks ("
                    + "path TEXT PRIMARY KEY,"
                    + "position_ms INTEGER NOT NULL DEFAULT 0,"
                    + "chapter_index INTEGER NOT NULL DEFAULT 0,"
                    + "updated_at INTEGER NOT NULL DEFAULT 0)");
        }
    }

    /** All cached tracks (may include files removed from disk until next purge). */
    public List<Track> loadAll() {
        List<Track> out = new ArrayList<Track>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tracks", null, null, null, null, null, "path ASC");
            while (c.moveToNext()) {
                out.add(rowToTrack(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Lookup by absolute path; null when not cached. */
    public Track get(String path) {
        if (path == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tracks", null, "path=?", new String[] { path }, null, null, null);
            if (c.moveToFirst()) return rowToTrack(c);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /**
     * Cached track only when it matches the current file stat.
     * Single DB lookup instead of {@link #isFresh(File)} + {@link #get(String)}.
     */
    public Track getFresh(File file) {
        if (file == null || !file.isFile()) return null;
        Track t = get(file.getAbsolutePath());
        if (t == null) return null;
        // 2026-07-06: year=0 means pre-v4 row — force one ID3 re-read to backfill year.
        if (t.year == 0) return null;
        return t.mtime == file.lastModified() && t.size == file.length() ? t : null;
    }

    /**
     * 2026-07-05 — Batch freshness lookup for library scan partition (one query per chunk).
     * Returns map path → fresh Track; missing or stale paths omitted.
     */
    public java.util.HashMap<String, Track> getFreshBatch(java.util.List<File> files) {
        java.util.HashMap<String, Track> out = new java.util.HashMap<String, Track>();
        if (files == null || files.isEmpty()) return out;
        migrateLegacyZeroTrackNumbers();
        final int chunk = 400;
        for (int start = 0; start < files.size(); start += chunk) {
            int end = Math.min(files.size(), start + chunk);
            java.util.ArrayList<String> paths = new java.util.ArrayList<String>(end - start);
            java.util.ArrayList<File> chunkFiles = new java.util.ArrayList<File>(end - start);
            for (int i = start; i < end; i++) {
                File f = files.get(i);
                if (f == null || !f.isFile()) continue;
                paths.add(f.getAbsolutePath());
                chunkFiles.add(f);
            }
            if (paths.isEmpty()) continue;
            StringBuilder sql = new StringBuilder("path IN (");
            for (int i = 0; i < paths.size(); i++) {
                if (i > 0) sql.append(',');
                sql.append('?');
            }
            sql.append(')');
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = null;
            try {
                c = db.query("tracks", null, sql.toString(),
                        paths.toArray(new String[paths.size()]), null, null, null);
                while (c.moveToNext()) {
                    Track t = rowToTrack(c);
                    File f = new File(t.path);
                    // 2026-07-06: year=0 = legacy cache before year column — re-tag once.
                    if (f.isFile() && t.year != 0
                            && t.mtime == f.lastModified() && t.size == f.length()) {
                        out.put(t.path, t);
                    }
                }
            } finally {
                if (c != null) c.close();
            }
        }
        return out;
    }

    /** True when cached row matches current file stat — skip MediaMetadataRetriever. */
    public boolean isFresh(File file) {
        migrateLegacyZeroTrackNumbers();
        if (file == null || !file.isFile()) return false;
        Track t = get(file.getAbsolutePath());
        if (t == null) return false;
        // 2026-07-06: year=0 rows need one metadata pass after DB v4 upgrade.
        if (t.year == 0) return false;
        return t.mtime == file.lastModified() && t.size == file.length();
    }

    /** v1/v2 rows used 0 for unknown track — treat as -1 so isFresh does not force full re-ID3. */
    private void migrateLegacyZeroTrackNumbers() {
        if (legacyTrackNumbersMigrated) return;
        legacyTrackNumbersMigrated = true;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE tracks SET track_number = -1 WHERE track_number = 0");
    }

    public void upsert(File file, String title, String artist, String album,
            String genre, String albumArtist, String durationMs, int trackNumber) {
        upsert(file, title, artist, album, genre, albumArtist, durationMs, trackNumber, 0);
    }

    public void upsert(File file, String title, String artist, String album,
            String genre, String albumArtist, String durationMs, int trackNumber, int year) {
        if (file == null || !file.isFile()) return;
        if (trackNumber == 0) trackNumber = -1;
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement st = db.compileStatement(
                "INSERT OR REPLACE INTO tracks"
                        + " (path,mtime,size,title,artist,album,genre,album_artist,duration_ms,track_number,year)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)");
        try {
            bindUpsert(st, file, title, artist, album, genre, albumArtist, durationMs, trackNumber, year);
            st.executeInsert();
        } finally {
            st.close();
        }
    }

    /**
     * Batch upsert inside a single transaction — much faster than one transaction per row
     * when importing thousands of tracks during a library scan.
     */
    public void upsertBatch(List<Upsert> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        SQLiteStatement st = db.compileStatement(
                "INSERT OR REPLACE INTO tracks"
                        + " (path,mtime,size,title,artist,album,genre,album_artist,duration_ms,track_number,year)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)");
        try {
            for (Upsert t : tracks) {
                bindUpsert(st, t.file, t.title, t.artist, t.album, t.genre,
                        t.albumArtist, t.durationMs, t.trackNumber, t.year);
                st.executeInsert();
                st.clearBindings();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            st.close();
        }
    }

    private static void bindUpsert(SQLiteStatement st, File file, String title, String artist,
            String album, String genre, String albumArtist, String durationMs, int trackNumber, int year) {
        if (trackNumber == 0) trackNumber = -1;
        st.bindString(1, file.getAbsolutePath());
        st.bindLong(2, file.lastModified());
        st.bindLong(3, file.length());
        st.bindString(4, title != null ? title : "");
        st.bindString(5, artist != null ? artist : "");
        st.bindString(6, album != null ? album : "");
        st.bindString(7, genre != null ? genre : "");
        st.bindString(8, albumArtist != null ? albumArtist : "");
        st.bindString(9, durationMs != null ? durationMs : "");
        st.bindLong(10, trackNumber);
        // 2026-07-06: -1 = year read but absent; 0 reserved for legacy not-yet-backfilled rows.
        st.bindLong(11, year > 0 ? year : YEAR_UNKNOWN_SCANNED);
    }

    /** Wipe all cached track rows — Settings reset / cache clear. */
    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("tracks", null, null);
    }

    /** Remove DB rows whose paths were not seen in the latest filesystem walk. */
    public void deleteExcept(Set<String> keepPaths) {
        if (keepPaths == null) return;
        if (keepPaths.isEmpty()) {
            clearAll();
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        Cursor c = null;
        try {
            c = db.query("tracks", new String[] { "path" }, null, null, null, null, null);
            List<String> stale = new ArrayList<String>();
            while (c.moveToNext()) {
                String p = c.getString(0);
                if (!keepPaths.contains(p)) stale.add(p);
            }
            c.close();
            c = null;
            // Batch stale paths into a single DELETE per chunk (SQLite max host params is 999).
            final int chunk = 500;
            for (int i = 0; i < stale.size(); i += chunk) {
                int end = Math.min(i + chunk, stale.size());
                String[] args = stale.subList(i, end).toArray(new String[0]);
                db.delete("tracks", "path IN (" + placeholders(args.length) + ")", args);
            }
            db.setTransactionSuccessful();
        } finally {
            if (c != null) c.close();
            db.endTransaction();
        }
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    /** path → durationSec for Soulseek share scan (avoids second MMR pass). */
    public Map<String, Integer> durationSecByPath() {
        Map<String, Integer> out = new HashMap<String, Integer>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("tracks", new String[] { "path", "duration_ms" }, null, null, null, null, null);
            final int pathCol = c.getColumnIndex("path");
            final int durCol = c.getColumnIndex("duration_ms");
            while (c.moveToNext()) {
                String path = c.getString(pathCol);
                int sec = durationSecFromMs(c.getString(durCol));
                if (sec > 0) {
                    out.put(normPath(path), sec);
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Parse duration_ms column without full row projection. */
    static int durationSecFromMs(String durationMs) {
        if (durationMs == null || durationMs.trim().isEmpty()) return 0;
        try {
            int ms = Integer.parseInt(durationMs.trim());
            return ms > 0 ? ms / 1000 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String normPath(String path) {
        if (path == null) return "";
        return path.toLowerCase(Locale.US);
    }

    public static HashSet<String> newKeepSet() {
        return new HashSet<String>();
    }

    private static Track rowToTrack(Cursor c) {
        int trackNumber = 0;
        int trackNumberIndex = c.getColumnIndex("track_number");
        if (trackNumberIndex != -1) {
            trackNumber = c.getInt(trackNumberIndex);
        }
        int year = 0;
        int yearIndex = c.getColumnIndex("year");
        if (yearIndex != -1) year = c.getInt(yearIndex);
        return new Track(
                c.getString(c.getColumnIndex("path")),
                c.getLong(c.getColumnIndex("mtime")),
                c.getLong(c.getColumnIndex("size")),
                c.getString(c.getColumnIndex("title")),
                c.getString(c.getColumnIndex("artist")),
                c.getString(c.getColumnIndex("album")),
                c.getString(c.getColumnIndex("genre")),
                c.getString(c.getColumnIndex("album_artist")),
                c.getString(c.getColumnIndex("duration_ms")),
                trackNumber,
                year);
    }

    // --- Favorites (JJ parity) ---

    /** All favorited track paths. */
    public Set<String> loadFavoritePaths() {
        Set<String> out = new HashSet<String>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("favorite_paths", new String[] { "path" }, null, null, null, null, null);
            while (c.moveToNext()) out.add(c.getString(0));
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public boolean isFavorite(String path) {
        if (path == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("favorite_paths", new String[] { "path" }, "path=?",
                    new String[] { path }, null, null, null);
            return c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }

    public void setFavorite(String path, boolean on) {
        if (path == null) return;
        SQLiteDatabase db = getWritableDatabase();
        if (on) {
            db.execSQL("INSERT OR REPLACE INTO favorite_paths (path) VALUES (?)",
                    new Object[] { path });
        } else {
            db.delete("favorite_paths", "path=?", new String[] { path });
        }
    }

    /** Remove favorites whose files no longer exist. */
    public void pruneFavorites(Set<String> existingPaths) {
        Set<String> favs = loadFavoritePaths();
        if (favs.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        for (String p : favs) {
            if (existingPaths == null || !existingPaths.contains(p)) {
                db.delete("favorite_paths", "path=?", new String[] { p });
            }
        }
    }

    // --- Audiobook bookmarks ---

    public static final class AudiobookBookmark {
        public final String path;
        public final int positionMs;
        public final int chapterIndex;

        public AudiobookBookmark(String path, int positionMs, int chapterIndex) {
            this.path = path;
            this.positionMs = positionMs;
            this.chapterIndex = chapterIndex;
        }
    }

    public void saveAudiobookBookmark(String path, int positionMs, int chapterIndex) {
        if (path == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO audiobook_bookmarks"
                        + " (path,position_ms,chapter_index,updated_at) VALUES (?,?,?,?)",
                new Object[] { path, positionMs, chapterIndex, System.currentTimeMillis() });
    }

    public AudiobookBookmark loadAudiobookBookmark(String path) {
        if (path == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("audiobook_bookmarks", null, "path=?", new String[] { path },
                    null, null, null);
            if (c.moveToFirst()) {
                return new AudiobookBookmark(path,
                        c.getInt(c.getColumnIndex("position_ms")),
                        c.getInt(c.getColumnIndex("chapter_index")));
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public Map<String, AudiobookBookmark> loadAllAudiobookBookmarks() {
        Map<String, AudiobookBookmark> out = new HashMap<String, AudiobookBookmark>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("audiobook_bookmarks", null, null, null, null, null, null);
            while (c.moveToNext()) {
                String path = c.getString(c.getColumnIndex("path"));
                out.put(path, new AudiobookBookmark(path,
                        c.getInt(c.getColumnIndex("position_ms")),
                        c.getInt(c.getColumnIndex("chapter_index"))));
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
