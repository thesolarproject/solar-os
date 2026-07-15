package com.solar.launcher.jellyfin;

/** 2026-07-06: One recycled ListView row for Jellyfin artists/albums/songs/playlists. */
public final class JellyfinBrowseRow {
    public enum Kind { ARTIST, ALBUM, SONG, PLAYLIST }

    public Kind kind = Kind.ARTIST;
    public String label = "";
    public String subtitle = "";
    public String coverArtId = "";
    public JellyfinArtist artist;
    public JellyfinAlbum album;
    public JellyfinSong song;
    public JellyfinPlaylist playlist;
}
