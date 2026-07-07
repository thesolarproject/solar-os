package com.solar.launcher;

import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.navidrome.NavidromeAlbum;
import com.solar.launcher.navidrome.NavidromeSong;
import com.solar.launcher.soulseek.SoulseekClient;

import java.util.Collections;
import java.util.List;

/** One row in unified Get Music search results. */
public final class MusicSearchEntry {
    public enum Source { DEEZER, REACH, NAVIDROME }

    /** Flat track, Deezer artist/album container, or Reach folder container. */
    public enum RowKind { TRACK, DEEZER_ARTIST, DEEZER_ALBUM, REACH_FOLDER, NAVIDROME_ALBUM }

    public final Source source;
    public final RowKind kind;
    public final DeezerResult deezer;
    public final SoulseekClient.Result reach;
    public final NavidromeSong navidromeSong;
    public final NavidromeAlbum navidromeAlbum;
    /** Album, artist, or folder display name for container rows. */
    public final String containerLabel;
    /** Tracks inside a container row; empty for lazy-loaded album/artist rows. */
    public final List<MusicSearchEntry> children;
    /** Deezer artist or album id for lazy-loaded containers. */
    public final long deezerContainerId;
    /** Deezer album record type: album, single, ep. */
    public final String deezerRecordType;

    private MusicSearchEntry(Source source, RowKind kind, DeezerResult deezer,
            SoulseekClient.Result reach, NavidromeSong navidromeSong, NavidromeAlbum navidromeAlbum,
            String containerLabel, List<MusicSearchEntry> children,
            long deezerContainerId, String deezerRecordType) {
        this.source = source;
        this.kind = kind;
        this.deezer = deezer;
        this.reach = reach;
        this.navidromeSong = navidromeSong;
        this.navidromeAlbum = navidromeAlbum;
        this.containerLabel = containerLabel != null ? containerLabel : "";
        this.children = children != null ? children : Collections.<MusicSearchEntry>emptyList();
        this.deezerContainerId = deezerContainerId;
        this.deezerRecordType = deezerRecordType != null ? deezerRecordType : "";
    }

    public static MusicSearchEntry deezer(DeezerResult r) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.TRACK, r, null, null, null, "", null, 0, "");
    }

    public static MusicSearchEntry reach(SoulseekClient.Result r) {
        return new MusicSearchEntry(Source.REACH, RowKind.TRACK, null, r, null, null, "", null, 0, "");
    }

    public static MusicSearchEntry navidrome(NavidromeSong s) {
        return new MusicSearchEntry(Source.NAVIDROME, RowKind.TRACK, null, null, s, null, "", null, 0, "");
    }

    public static MusicSearchEntry navidromeAlbum(NavidromeAlbum album, List<MusicSearchEntry> tracks) {
        String label = album != null ? album.name : "";
        if (album != null && album.artist != null && !album.artist.isEmpty()) {
            label = album.name + " · " + album.artist;
        }
        return new MusicSearchEntry(Source.NAVIDROME, RowKind.NAVIDROME_ALBUM, null, null, null, album,
                label, tracks, 0, "");
    }

    public static MusicSearchEntry deezerAlbum(String albumLabel, List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ALBUM, null, null, null, null, albumLabel,
                tracks, 0, "album");
    }

    public static MusicSearchEntry deezerArtist(long artistId, String name) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ARTIST, null, null, null, null, name, null,
                artistId, "");
    }

    /** Album row under an artist; tracks load when opened if {@code tracks} is empty. */
    public static MusicSearchEntry deezerAlbumBrowse(long albumId, String label, String recordType,
            List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.DEEZER, RowKind.DEEZER_ALBUM, null, null, null, null, label, tracks,
                albumId, recordType != null ? recordType : "album");
    }

    public static MusicSearchEntry reachFolder(String folderLabel, List<MusicSearchEntry> tracks) {
        return new MusicSearchEntry(Source.REACH, RowKind.REACH_FOLDER, null, null, null, null, folderLabel,
                tracks, 0, "");
    }

    public boolean isContainer() {
        return kind == RowKind.DEEZER_ARTIST || kind == RowKind.DEEZER_ALBUM
                || kind == RowKind.REACH_FOLDER || kind == RowKind.NAVIDROME_ALBUM;
    }

    public static MusicSearchEntry withChildren(MusicSearchEntry template,
            List<MusicSearchEntry> children) {
        if (template == null) return null;
        return new MusicSearchEntry(template.source, template.kind, template.deezer, template.reach,
                template.navidromeSong, template.navidromeAlbum, template.containerLabel, children,
                template.deezerContainerId, template.deezerRecordType);
    }

    public boolean needsLazyLoad() {
        return (kind == RowKind.DEEZER_ARTIST || kind == RowKind.DEEZER_ALBUM)
                && deezerContainerId > 0 && children.isEmpty();
    }

    public String dedupeKey() {
        if (kind == RowKind.DEEZER_ARTIST) {
            return "artist:" + deezerContainerId;
        }
        if (kind == RowKind.DEEZER_ALBUM || kind == RowKind.REACH_FOLDER || kind == RowKind.NAVIDROME_ALBUM) {
            if (deezerContainerId > 0) return "album:" + deezerContainerId;
            if (navidromeAlbum != null && navidromeAlbum.id != null) return "nav:" + navidromeAlbum.id;
            return containerLabel.toLowerCase();
        }
        if (source == Source.DEEZER && deezer != null) {
            return normalize(deezer.artist + " " + deezer.title);
        }
        if (source == Source.REACH && reach != null) {
            return normalize(reach.title());
        }
        if (source == Source.NAVIDROME && navidromeSong != null) {
            return normalize(navidromeSong.artist + " " + navidromeSong.title);
        }
        return "";
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
