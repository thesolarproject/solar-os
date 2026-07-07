package com.solar.launcher.xposed.bridge.extract;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts visible menu row titles and submenu flags from a stock MenuBuilder instance.
 */
public final class MenuExtract {

    /** One visible row from a Holo context / overflow menu. */
    public static final class Row {
        public final String title;
        public final boolean hasSubmenu;

        public Row(String title, boolean hasSubmenu) {
            this.title = title != null ? title : "";
            this.hasSubmenu = hasSubmenu;
        }
    }

    /** Parsed menu suitable for Solar overlay list rows. */
    public static final class Snapshot {
        public final Row[] rows;

        public Snapshot(Row[] rows) {
            this.rows = rows != null ? rows : new Row[0];
        }

        public int size() {
            return rows.length;
        }
    }

    /** Callback for reading MenuItem fields without tying tests to Xposed. */
    public interface MenuItemReader {
        /** @return visible MenuItem objects in display order */
        List<?> visibleItems();

        String title(Object item);

        boolean hasSubmenu(Object item);
    }

    private MenuExtract() {}

    /** Build overlay row data from a MenuItemReader (production wraps MenuBuilder). */
    public static Snapshot fromMenu(MenuItemReader reader) {
        if (reader == null) return new Snapshot(new Row[0]);
        List<?> visible = reader.visibleItems();
        if (visible == null || visible.isEmpty()) return new Snapshot(new Row[0]);
        ArrayList<Row> rows = new ArrayList<Row>(visible.size());
        for (Object item : visible) {
            rows.add(new Row(reader.title(item), reader.hasSubmenu(item)));
        }
        return new Snapshot(rows.toArray(new Row[rows.size()]));
    }
}
