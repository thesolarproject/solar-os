package com.solar.launcher.navidrome;

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
 * 2026-07-06: SQLite cache for Navidrome artist index — instant reopen offline.
 */
public final class NavidromeCacheStore extends SolarDbHelper {

    private static final String DB = "navidrome_cache.db";
    private static final int VERSION = 1;
    private static NavidromeCacheStore instance;

    private NavidromeCacheStore(Context ctx) {
        super(ctx.getApplicationContext(), DB, VERSION, true);
    }

    public static synchronized NavidromeCacheStore getInstance(Context ctx) {
        if (instance == null) instance = new NavidromeCacheStore(ctx.getApplicationContext());
        return instance;
    }

    @Override
    public void onCreate(SolarDatabase db) {
        db.execSQL("CREATE TABLE navidrome_artists ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "album_count INTEGER NOT NULL DEFAULT 0,"
                + "cover_art TEXT,"
                + "index_letter TEXT)");
    }

    @Override
    public void onUpgrade(SolarDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS navidrome_artists");
        onCreate(db);
    }

    public List<NavidromeArtist> loadArtists() {
        List<NavidromeArtist> out = new ArrayList<NavidromeArtist>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("navidrome_artists", null, null, null, null, null, "name COLLATE NOCASE ASC");
            while (c.moveToNext()) {
                NavidromeArtist a = new NavidromeArtist();
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

    public void saveArtists(List<NavidromeArtist> artists) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("navidrome_artists", null, null);
            if (artists != null) {
                for (NavidromeArtist a : artists) {
                    if (a == null) continue;
                    db.execSQL("INSERT INTO navidrome_artists (id,name,album_count,cover_art,index_letter) VALUES (?,?,?,?,?)",
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
        getWritableDatabase().delete("navidrome_artists", null, null);
    }

    /** Parse Subsonic getArtists JSON into artist rows. */
    public static List<NavidromeArtist> parseArtistsJson(JSONObject root) throws Exception {
        List<NavidromeArtist> artists = new ArrayList<NavidromeArtist>();
        JSONObject sr = root.getJSONObject("subsonic-response");
        if (!sr.has("artists")) return artists;
        JSONArray indexes = sr.getJSONObject("artists").getJSONArray("index");
        for (int i = 0; i < indexes.length(); i++) {
            JSONObject index = indexes.getJSONObject(i);
            String letter = index.optString("name", "#");
            JSONArray arr = index.optJSONArray("artist");
            if (arr == null) continue;
            for (int j = 0; j < arr.length(); j++) {
                JSONObject a = arr.getJSONObject(j);
                NavidromeArtist artist = new NavidromeArtist();
                artist.id = a.getString("id");
                artist.name = a.getString("name");
                artist.albumCount = a.optInt("albumCount", 0);
                artist.coverArtId = a.optString("coverArt", null);
                artist.indexLetter = letter;
                artists.add(artist);
            }
        }
        return artists;
    }
}
