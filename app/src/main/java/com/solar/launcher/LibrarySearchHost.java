package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-06: Library text-search UI sections — paginated artists/albums/genres/songs.
 * Layman: shows grouped search hits so you can drill into a match like normal browse.
 */
public final class LibrarySearchHost {

    public interface Actions {
        String getString(int resId);
        String getString(int resId, Object... args);
        void clickFeedback();
        android.widget.Button createListButton(String label);
        void configureListButton(android.widget.Button btn);
        void addBrowserRow(android.view.View row);
        void clearBrowserRows();
        void showBrowserScroll(boolean scrollView);
        void setBrowserListVisible(boolean listVisible);
        void setBrowserStatusTitle(String title);
        void updateStatusBarTitle();
        void updateLibraryBreadcrumb();
        void openArtist(String artist);
        void openAlbum(String artist, String album);
        void openGenre(String genre);
        void openSongList(String type, String value, String artistForAlbum);
        void openSearchAgain();
        void openLibraryRoot();
        void playSong(java.io.File file);
        void rebuildResults();
        void openReachSearch(String query);
        void openNavidrome();
    }

    private LibrarySearchHost() {}

    public static void buildResults(Actions actions, LibrarySearch.Results results, String query,
            java.util.Map<String, Integer> visibleCounts, Runnable onFocusFirst) {
        buildResults(actions, results, query, visibleCounts, null, false, onFocusFirst);
    }

    public static void buildResults(Actions actions, LibrarySearch.Results results, String query,
            java.util.Map<String, Integer> visibleCounts, java.util.List<String> reachRows,
            boolean reachSearching, Runnable onFocusFirst) {
        buildResults(actions, results, query, visibleCounts, reachRows, reachSearching,
                null, false, onFocusFirst);
    }

    public static void buildResults(Actions actions, LibrarySearch.Results results, String query,
            java.util.Map<String, Integer> visibleCounts, java.util.List<String> reachRows,
            boolean reachSearching, java.util.List<String> navidromeRows, boolean navidromeSearching,
            Runnable onFocusFirst) {
        actions.clearBrowserRows();
        actions.showBrowserScroll(true);
        actions.setBrowserListVisible(false);
        actions.setBrowserStatusTitle(actions.getString(R.string.path_library_search_query, query));
        actions.updateStatusBarTitle();
        actions.updateLibraryBreadcrumb();

        android.widget.Button back = actions.createListButton(actions.getString(R.string.common_cancel_back));
        actions.configureListButton(back);
        back.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                actions.clickFeedback();
                actions.openLibraryRoot();
            }
        });
        actions.addBrowserRow(back);

        android.widget.Button again = actions.createListButton(actions.getString(R.string.library_search_again));
        actions.configureListButton(again);
        again.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                actions.clickFeedback();
                actions.openSearchAgain();
            }
        });
        actions.addBrowserRow(again);

        if (results == null || results.isEmpty()) {
            android.widget.Button empty = actions.createListButton(actions.getString(R.string.library_search_none));
            empty.setEnabled(false);
            actions.addBrowserRow(empty);
            if (onFocusFirst != null) onFocusFirst.run();
            return;
        }

        appendSection(actions, "artists", actions.getString(R.string.library_search_section_artists),
                toStrings(results.artists), visibleCounts, new SectionHandler() {
            @Override public void onPick(String value) { actions.openArtist(value); }
        });
        appendAlbumSection(actions, results.albums, visibleCounts);
        appendSection(actions, "genres", actions.getString(R.string.library_search_section_genres),
                results.genres, visibleCounts, new SectionHandler() {
            @Override public void onPick(String value) { actions.openGenre(value); }
        });
        appendSongSection(actions, results.songs, visibleCounts);
        appendReachSection(actions, reachRows, reachSearching, query, visibleCounts);
        appendNavidromeSection(actions, navidromeRows, navidromeSearching, visibleCounts);

        if (onFocusFirst != null) onFocusFirst.run();
    }

    private static void appendNavidromeSection(Actions actions, java.util.List<String> navidromeRows,
            boolean navidromeSearching, java.util.Map<String, Integer> visibleCounts) {
        if (!navidromeSearching && (navidromeRows == null || navidromeRows.isEmpty())) return;
        actions.addBrowserRow(sectionHeader(actions,
                actions.getString(R.string.library_search_section_navidrome)));
        if (navidromeSearching) {
            android.widget.Button loading = actions.createListButton(
                    actions.getString(R.string.library_search_searching_navidrome));
            loading.setEnabled(false);
            actions.addBrowserRow(loading);
            return;
        }
        int visible = visibleCount(visibleCounts, "navidrome");
        java.util.List<String> page = pageList(navidromeRows, visible);
        for (final String label : page) {
            android.widget.Button row = actions.createListButton(label);
            actions.configureListButton(row);
            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    actions.clickFeedback();
                    actions.openNavidrome();
                }
            });
            actions.addBrowserRow(row);
        }
        maybeShowMore(actions, "navidrome", navidromeRows.size(), visible, visibleCounts);
    }

    private static void appendReachSection(Actions actions, java.util.List<String> reachRows,
            boolean reachSearching, final String query,
            java.util.Map<String, Integer> visibleCounts) {
        if (!reachSearching && (reachRows == null || reachRows.isEmpty())) return;
        actions.addBrowserRow(sectionHeader(actions,
                actions.getString(R.string.library_search_section_online_reach)));
        if (reachSearching) {
            android.widget.Button loading = actions.createListButton(
                    actions.getString(R.string.library_search_searching_reach));
            loading.setEnabled(false);
            actions.addBrowserRow(loading);
            return;
        }
        int visible = visibleCount(visibleCounts, "reach");
        java.util.List<String> page = pageList(reachRows, visible);
        for (final String label : page) {
            android.widget.Button row = actions.createListButton(label);
            actions.configureListButton(row);
            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    actions.clickFeedback();
                    actions.openReachSearch(query);
                }
            });
            actions.addBrowserRow(row);
        }
        maybeShowMore(actions, "reach", reachRows.size(), visible, visibleCounts);
    }

    private interface SectionHandler {
        void onPick(String value);
    }

    private static void appendAlbumSection(Actions actions, List<LibrarySearch.AlbumHit> albums,
            java.util.Map<String, Integer> visibleCounts) {
        if (albums == null || albums.isEmpty()) return;
        int visible = visibleCount(visibleCounts, "albums");
        List<LibrarySearch.AlbumHit> page = pageList(albums, visible);
        actions.addBrowserRow(sectionHeader(actions, actions.getString(R.string.library_search_section_albums)));
        for (final LibrarySearch.AlbumHit hit : page) {
            String label = hit.album;
            if (hit.artist != null && !hit.artist.isEmpty()) {
                label = hit.album + " · " + hit.artist;
            }
            android.widget.Button row = actions.createListButton(label);
            actions.configureListButton(row);
            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    actions.clickFeedback();
                    actions.openAlbum(hit.artist, hit.album);
                }
            });
            actions.addBrowserRow(row);
        }
        maybeShowMore(actions, "albums", albums.size(), visible, visibleCounts);
    }

    private static void appendSongSection(Actions actions, List<FlowCatalog.SongRow> songs,
            java.util.Map<String, Integer> visibleCounts) {
        if (songs == null || songs.isEmpty()) return;
        int visible = visibleCount(visibleCounts, "songs");
        List<FlowCatalog.SongRow> page = pageList(songs, visible);
        actions.addBrowserRow(sectionHeader(actions, actions.getString(R.string.library_search_section_songs)));
        for (final FlowCatalog.SongRow song : page) {
            String label = song.title + " · " + song.artist;
            android.widget.Button row = actions.createListButton(label);
            actions.configureListButton(row);
            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    actions.clickFeedback();
                    if (song.file != null) actions.playSong(song.file);
                }
            });
            actions.addBrowserRow(row);
        }
        maybeShowMore(actions, "songs", songs.size(), visible, visibleCounts);
    }

    private static void appendSection(Actions actions, String key, String title, List<String> values,
            java.util.Map<String, Integer> visibleCounts, final SectionHandler handler) {
        if (values == null || values.isEmpty()) return;
        int visible = visibleCount(visibleCounts, key);
        List<String> page = pageList(values, visible);
        actions.addBrowserRow(sectionHeader(actions, title));
        for (final String value : page) {
            android.widget.Button row = actions.createListButton(value);
            actions.configureListButton(row);
            row.setOnClickListener(new android.view.View.OnClickListener() {
                @Override public void onClick(android.view.View v) {
                    actions.clickFeedback();
                    handler.onPick(value);
                }
            });
            actions.addBrowserRow(row);
        }
        maybeShowMore(actions, key, values.size(), visible, visibleCounts);
    }

    private static android.widget.Button sectionHeader(Actions actions, String title) {
        android.widget.Button h = actions.createListButton(title);
        h.setEnabled(false);
        return h;
    }

    private static void maybeShowMore(Actions actions, final String key, int total, int visible,
            final java.util.Map<String, Integer> visibleCounts) {
        if (total <= visible) return;
        android.widget.Button more = actions.createListButton(actions.getString(R.string.library_search_show_more));
        actions.configureListButton(more);
        more.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                actions.clickFeedback();
                int cur = visibleCount(visibleCounts, key);
                visibleCounts.put(key, cur + LibrarySearch.PAGE_SIZE);
                actions.rebuildResults();
            }
        });
        actions.addBrowserRow(more);
    }

    private static int visibleCount(java.util.Map<String, Integer> map, String key) {
        if (map == null) return LibrarySearch.PAGE_SIZE;
        Integer n = map.get(key);
        return n != null && n > 0 ? n : LibrarySearch.PAGE_SIZE;
    }

    private static <T> List<T> pageList(List<T> all, int visible) {
        if (all.size() <= visible) return all;
        return new ArrayList<T>(all.subList(0, visible));
    }

    private static List<String> toStrings(List<String> in) {
        return in != null ? in : new ArrayList<String>();
    }
}
