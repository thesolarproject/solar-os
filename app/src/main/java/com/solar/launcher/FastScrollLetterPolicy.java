package com.solar.launcher;

/**
 * 2026-07-15 — When the iPod-style letter bubble / wheel letter-jump is useful.
 * Only alpha/numeric name-sorted catalogs whose first character matches the row label index.
 * Reversal: older builds showed the bubble on any focus / section change with no sort gate.
 */
public final class FastScrollLetterPolicy {

    // 2026-07-15 — Mirror MainActivity screen / BROWSER_* ints (keep in sync).
    public static final int STATE_MENU = 1;
    public static final int STATE_BROWSER = 2;
    public static final int STATE_SETTINGS = 4;
    public static final int STATE_PODCASTS = 11;
    public static final int STATE_APPS = 13;
    public static final int STATE_MORE = 14;
    public static final int STATE_DEEZER = 15;
    public static final int STATE_NAVIDROME = 26;
    public static final int STATE_PLEX = 31;
    public static final int STATE_JELLYFIN = 32;

    public static final int BROWSER_ROOT = 0;
    public static final int BROWSER_FOLDER = 1;
    public static final int BROWSER_ARTISTS = 2;
    public static final int BROWSER_ALBUMS = 3;
    public static final int BROWSER_VIRTUAL_SONGS = 4;
    public static final int BROWSER_ARTIST_ALBUMS = 5;
    public static final int BROWSER_GENRES = 6;
    public static final int BROWSER_PLAYLISTS = 7;
    public static final int BROWSER_DEEZER_PLAYLIST = 8;
    public static final int BROWSER_YEARS = 9;
    public static final int BROWSER_FAVORITES = 10;
    public static final int BROWSER_LIBRARY_SEARCH = 11;

    private FastScrollLetterPolicy() {}

    /**
     * 2026-07-15 — True when showing the letter HUD / section jumps matches list order.
     * themeGalleryActive: Settings → Themes name/author list.
     * serverNameSortedBrowse: Navidrome/Plex/Jellyfin/Deezer title catalogs (not relevance/queue).
     * mediaNameSortedBrowse: Videos / podcast shows / similar fixed alpha catalogs.
     */
    public static boolean isEligible(
            int screenState,
            int browserMode,
            String virtualQueryType,
            int artistSort,
            int songSort,
            int albumRackSort,
            int albumSongSort,
            boolean themeGalleryActive,
            boolean serverNameSortedBrowse,
            boolean mediaNameSortedBrowse) {
        if (virtualQueryType == null) virtualQueryType = "";

        // 2026-07-15 — Home / settings / More / queues never use letter index.
        if (screenState == STATE_MENU
                || screenState == STATE_MORE
                || screenState == STATE_SETTINGS) {
            // Themes gallery is under Settings but names are A–Z / author-sorted.
            return themeGalleryActive;
        }
        if (screenState == STATE_APPS) return true;
        if (screenState == STATE_PODCASTS) return mediaNameSortedBrowse;
        if (screenState == STATE_DEEZER) return serverNameSortedBrowse;
        if (screenState == STATE_NAVIDROME
                || screenState == STATE_PLEX
                || screenState == STATE_JELLYFIN) {
            return serverNameSortedBrowse;
        }
        // 2026-07-15 — Media suite: only caller-flagged name catalogs (not YouTube / FM presets).
        if (mediaNameSortedBrowse) return true;

        if (screenState != STATE_BROWSER) return false;

        switch (browserMode) {
            case BROWSER_ROOT:
            case BROWSER_PLAYLISTS:
                return false;
            case BROWSER_FOLDER:
            case BROWSER_GENRES:
            case BROWSER_YEARS:
            case BROWSER_ARTIST_ALBUMS:
            case BROWSER_FAVORITES:
            case BROWSER_LIBRARY_SEARCH:
                return true;
            case BROWSER_ARTISTS:
                return artistSort == LibraryBrowsePrefs.ARTIST_SORT_NAME;
            case BROWSER_ALBUMS:
                return albumRackSort == LibraryBrowsePrefs.ALBUM_RACK_SORT_TITLE;
            case BROWSER_VIRTUAL_SONGS:
                return isVirtualSongsEligible(virtualQueryType, songSort, albumSongSort);
            case BROWSER_DEEZER_PLAYLIST:
                // Playlist track order (server m3u-like), not title A–Z.
                return false;
            default:
                return false;
        }
    }

    /**
     * 2026-07-15 — Song ListView: letter indexes titles, so only title-sorted (or fixed title) lists.
     */
    static boolean isVirtualSongsEligible(String virtualQueryType, int songSort, int albumSongSort) {
        if (virtualQueryType == null) virtualQueryType = "";
        if ("PLAYLIST".equals(virtualQueryType) || "RECENT".equals(virtualQueryType)) {
            return false;
        }
        if ("FAVORITES".equals(virtualQueryType)) return true;
        if ("ALBUM".equals(virtualQueryType) || "ARTIST_ALBUM".equals(virtualQueryType)) {
            return albumSongSort == LibraryBrowsePrefs.SONG_SORT_TITLE;
        }
        // ALL / ARTIST / GENRE / YEAR and other filtered song lists use songSort.
        return songSort == LibraryBrowsePrefs.SONG_SORT_TITLE;
    }
}
