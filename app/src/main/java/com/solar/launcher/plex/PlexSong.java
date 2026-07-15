package com.solar.launcher.plex;

/** 2026-07-14: Plex track row — ratingKey + optional Part.key for direct stream. */
public final class PlexSong {
    public String id = "";
    public String title = "";
    public String artist = "";
    public String album = "";
    public int durationSec;
    public String suffix = "mp3";
    public String coverArtId;
    /** Part.key path from Media.Part (e.g. /library/parts/123/file.flac). */
    public String mediaPartKey = "";
    /** Container from Part (mp3/flac/m4a) — drives transcoder vs direct. */
    public String container = "";
}
