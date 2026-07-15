package com.solar.launcher.jellyfin;

import java.util.ArrayList;
import java.util.List;

/** 2026-07-06: search3 grouped hits for library / Get Music. */
public final class JellyfinSearchResult {
    public final List<JellyfinArtist> artists = new ArrayList<JellyfinArtist>();
    public final List<JellyfinAlbum> albums = new ArrayList<JellyfinAlbum>();
    public final List<JellyfinSong> songs = new ArrayList<JellyfinSong>();

    public boolean isEmpty() {
        return artists.isEmpty() && albums.isEmpty() && songs.isEmpty();
    }
}
