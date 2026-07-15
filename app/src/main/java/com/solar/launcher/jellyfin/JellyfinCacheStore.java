package com.solar.launcher.jellyfin;

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
 * 2026-07-06: SQLite cache for Jellyfin artist index — instant reopen offline.
 */
public final class JellyfinCacheStore extends SolarDbHelper {

    private static final String DB = "jellyfin_cache.db";
    private static final int VERSION = 1;
    private static JellyfinCacheStore instance;

    private JellyfinCacheStore(Context ctx) {
        super(ctx.getApplicationContext(), DB, VERSION, true);
    }

    public static synchronized JellyfinCacheStore getInstance(Context ctx) {
        if (instance == null) instance = new JellyfinCacheStore(ctx.getApplicationContext());
        return instance;
    }

    @Override
    public void onCreate(SolarDatabase db) {
        db.execSQL("CREATE TABLE jellyfin_artists ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "album_count INTEGER NOT NULL DEFAULT 0,"
                + "cover_art TEXT,"
                + "index_letter TEXT)");
    }

    @Override
    public void onUpgrade(SolarDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS jellyfin_artists");
        onCreate(db);
    }

    public List<JellyfinArtist> loadArtists() {
        List<JellyfinArtist> out = new ArrayList<JellyfinArtist>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("jellyfin_artists", null, null, null, null, null, "name COLLATE NOCASE ASC");
            while (c.moveToNext()) {
                JellyfinArtist a = new JellyfinArtist();
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

    public void saveArtists(List<JellyfinArtist> artists) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("jellyfin_artists", null, null);
            if (artists != null) {
                for (JellyfinArtist a : artists) {
                    if (a == null) continue;
                    db.execSQL("INSERT INTO jellyfin_artists (id,name,album_count,cover_art,index_letter) VALUES (?,?,?,?,?)",
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
        getWritableDatabase().delete("jellyfin_artists", null, null);
    }

    /**
     * 2026-07-14: Parse Jellyfin Artists/Items JSON into cache rows.
     */
    public static List<JellyfinArtist> parseArtistsJson(JSONObject root) throws Exception {
        List<JellyfinArtist> artists = new ArrayList<JellyfinArtist>();
        if (root == null) return artists;
        JSONArray items = root.optJSONArray("Items");
        if (items == null) return artists;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            JellyfinArtist artist = new JellyfinArtist();
            artist.id = o.optString("Id");
            artist.name = o.optString("Name");
            artist.albumCount = o.optInt("AlbumCount", 0);
            artist.coverArtId = artist.id;
            String name = artist.name != null ? artist.name : "";
            char c = name.length() > 0 ? Character.toUpperCase(name.charAt(0)) : '#';
            artist.indexLetter = (c >= 'A' && c <= 'Z') ? String.valueOf(c) : "#";
            artists.add(artist);
        }
        return artists;
    }
}
