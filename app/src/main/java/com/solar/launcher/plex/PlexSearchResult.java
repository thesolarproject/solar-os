package com.solar.launcher.plex;

import java.util.ArrayList;
import java.util.List;

/** 2026-07-06: search3 grouped hits for library / Get Music. */
public final class PlexSearchResult {
    public final List<PlexArtist> artists = new ArrayList<PlexArtist>();
    public final List<PlexAlbum> albums = new ArrayList<PlexAlbum>();
    public final List<PlexSong> songs = new ArrayList<PlexSong>();

    public boolean isEmpty() {
        return artists.isEmpty() && albums.isEmpty() && songs.isEmpty();
    }
}
