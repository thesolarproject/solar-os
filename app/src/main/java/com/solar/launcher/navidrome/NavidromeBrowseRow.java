package com.solar.launcher.navidrome;

/** 2026-07-06: One recycled ListView row for Navidrome artists/albums/songs/playlists. */
public final class NavidromeBrowseRow {
    public enum Kind { ARTIST, ALBUM, SONG, PLAYLIST }

    public Kind kind = Kind.ARTIST;
    public String label = "";
    public String subtitle = "";
    public String coverArtId = "";
    public NavidromeArtist artist;
    public NavidromeAlbum album;
    public NavidromeSong song;
    public NavidromePlaylist playlist;
}
