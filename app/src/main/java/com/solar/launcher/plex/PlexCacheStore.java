package com.solar.launcher.plex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.solar.launcher.db.SolarDatabase;
import com.solar.launcher.db.SolarDbHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-06: SQLite cache for Plex artist index — instant reopen offline.
 */
public final class PlexCacheStore extends SolarDbHelper {

    private static final String DB = "plex_cache.db";
    private static final int VERSION = 1;
    private static PlexCacheStore instance;

    private PlexCacheStore(Context ctx) {
        super(ctx.getApplicationContext(), DB, VERSION, true);
    }

    public static synchronized PlexCacheStore getInstance(Context ctx) {
        if (instance == null) instance = new PlexCacheStore(ctx.getApplicationContext());
        return instance;
    }

    @Override
    public void onCreate(SolarDatabase db) {
        db.execSQL("CREATE TABLE plex_artists ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "album_count INTEGER NOT NULL DEFAULT 0,"
                + "cover_art TEXT,"
                + "index_letter TEXT)");
    }

    @Override
    public void onUpgrade(SolarDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS plex_artists");
        onCreate(db);
    }

    public List<PlexArtist> loadArtists() {
        List<PlexArtist> out = new ArrayList<PlexArtist>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("plex_artists", null, null, null, null, null, "name COLLATE NOCASE ASC");
            while (c.moveToNext()) {
                PlexArtist a = new PlexArtist();
                a.id = c.getString(0);
                a.name = c.getString(1);
                a.albumCount = c.getInt(2);
                a.coverArtId = c.getString(3);
                a.indexLetter = c.getString(4);
                out.add(a);
            }
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public void saveArtists(List<PlexArtist> artists) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("plex_artists", null, null);
            if (artists != null) {
                for (PlexArtist a : artists) {
                    if (a == null) continue;
                    db.execSQL("INSERT INTO plex_artists (id,name,album_count,cover_art,index_letter) VALUES (?,?,?,?,?)",
                            new Object[] { a.id, a.name, a.albumCount,
                                    a.coverArtId != null ? a.coverArtId : "",
                                    a.indexLetter != null ? a.indexLetter : "#" });
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void clearArtists() {
        getWritableDatabase().delete("plex_artists", null, null);
    }

    /**
     * 2026-07-14: Parse Plex MediaContainer Metadata artist rows.
     * Had: Subsonic index JSON left over from Navidrome copy — unused vs live client.
     */
    public static List<PlexArtist> parseArtistsJson(JSONObject root) throws Exception {
        List<PlexArtist> artists = new ArrayList<PlexArtist>();
        if (root == null) return artists;
        JSONObject mc = root.optJSONObject("MediaContainer");
        if (mc == null) return artists;
        JSONArray metadata = mc.optJSONArray("Metadata");
        if (metadata == null) return artists;
        for (int i = 0; i < metadata.length(); i++) {
            JSONObject o = metadata.getJSONObject(i);
            PlexArtist artist = new PlexArtist();
            artist.id = o.optString("ratingKey");
            artist.name = o.optString("title");
            artist.albumCount = o.optInt("childCount", 0);
            artist.coverArtId = o.optString("ratingKey");
            String name = artist.name != null ? artist.name : "";
            char c = name.length() > 0 ? Character.toUpperCase(name.charAt(0)) : '#';
            artist.indexLetter = (c >= 'A' && c <= 'Z') ? String.valueOf(c) : "#";
            artists.add(artist);
        }
        return artists;
    }
}
