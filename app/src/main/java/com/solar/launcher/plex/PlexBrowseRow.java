package com.solar.launcher.plex;

/** 2026-07-06: One recycled ListView row for Plex artists/albums/songs/playlists. */
public final class PlexBrowseRow {
    public enum Kind { ARTIST, ALBUM, SONG, PLAYLIST }

    public Kind kind = Kind.ARTIST;
    public String label = "";
    public String subtitle = "";
    public String coverArtId = "";
    public PlexArtist artist;
    public PlexAlbum album;
    public PlexSong song;
    public PlexPlaylist playlist;
}
