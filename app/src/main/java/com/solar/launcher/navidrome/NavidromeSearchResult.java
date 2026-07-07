package com.solar.launcher.navidrome;

import java.util.ArrayList;
import java.util.List;

/** 2026-07-06: search3 grouped hits for library / Get Music. */
public final class NavidromeSearchResult {
    public final List<NavidromeArtist> artists = new ArrayList<NavidromeArtist>();
    public final List<NavidromeAlbum> albums = new ArrayList<NavidromeAlbum>();
    public final List<NavidromeSong> songs = new ArrayList<NavidromeSong>();

    public boolean isEmpty() {
        return artists.isEmpty() && albums.isEmpty() && songs.isEmpty();
    }
}
