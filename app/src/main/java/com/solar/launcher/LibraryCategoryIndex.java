package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 2026-07-06: Precomputed genre/year category lists — one build per library scan generation.
 * Layman: after a scan we remember sorted year and genre names so browse opens instantly.
 */
public final class LibraryCategoryIndex {

    private int libraryGen = -1;
    private List<String> genres = Collections.emptyList();
    private List<String> years = Collections.emptyList();

    public LibraryCategoryIndex() {}

    /** 2026-07-06: Rebuild when scan generation changes; O(n) once per scan. */
    public synchronized void rebuild(int gen, List<FlowCatalog.SongRow> rows) {
        if (rows == null) rows = Collections.emptyList();
        if (gen == libraryGen && !rows.isEmpty()) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("gen", gen);
                d.put("rows", rows.size());
                d.put("cachedYears", years.size());
                Debug3b26caLog.log("LibraryCategoryIndex.rebuild",
                        "early return same gen", "H3", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        libraryGen = gen;

        Set<String> genreSet = new HashSet<String>();
        Set<String> yearSet = new HashSet<String>();
        int rowsWithYear = 0;
        for (FlowCatalog.SongRow row : rows) {
            if (row == null) continue;
            String g = row.genre != null ? row.genre.trim() : "";
            if (!g.isEmpty() && !"Unknown Genre".equalsIgnoreCase(g)) genreSet.add(g);
            if (row.year > 0) {
                yearSet.add(String.valueOf(row.year));
                rowsWithYear++;
            } else if (row.yearLabel != null && !row.yearLabel.trim().isEmpty()) {
                yearSet.add(row.yearLabel.trim());
            }
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("gen", gen);
            d.put("rows", rows.size());
            d.put("rowsWithYear", rowsWithYear);
            d.put("uniqueYears", yearSet.size());
            Debug3b26caLog.log("LibraryCategoryIndex.rebuild",
                    "index rebuilt", "H4", d);
        } catch (Exception ignored) {}
        // #endregion
        List<String> gOut = new ArrayList<String>(genreSet);
        Collections.sort(gOut, String.CASE_INSENSITIVE_ORDER);
        genres = Collections.unmodifiableList(gOut);

        List<String> yOut = new ArrayList<String>(yearSet);
        Collections.sort(yOut, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        years = Collections.unmodifiableList(yOut);
    }

    public synchronized void invalidate() {
        libraryGen = -1;
        genres = Collections.emptyList();
        years = Collections.emptyList();
    }

    public synchronized List<String> genres(int gen) {
        return libraryGen == gen ? genres : Collections.<String>emptyList();
    }

    public synchronized List<String> years(int gen) {
        return libraryGen == gen ? years : Collections.<String>emptyList();
    }

    /** 2026-07-06: JVM self-check — year bucket includes tagged rows only. */
    static void selfCheck() {
        LibraryCategoryIndex idx = new LibraryCategoryIndex();
        List<FlowCatalog.SongRow> rows = new ArrayList<FlowCatalog.SongRow>();
        rows.add(new FlowCatalog.SongRow(null, "a", "b", "c", "", 0L, 0, "Rock", 1999));
        idx.rebuild(1, rows);
        if (idx.years(1).isEmpty()) throw new AssertionError("year");
        if (idx.genres(1).isEmpty()) throw new AssertionError("genre");
    }
}
