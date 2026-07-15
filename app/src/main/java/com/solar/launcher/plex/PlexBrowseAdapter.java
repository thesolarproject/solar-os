package com.solar.launcher.plex;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-06: Virtualized Plex browse rows — avoids main-thread addView freeze on large servers.
 */
public final class PlexBrowseAdapter extends BaseAdapter {

    public interface RowUi {
        Button createListButton(String label);
        void bindListButton(Button btn, boolean focused, String label);
        void applyListRowParams(View row, int heightPx);
        int rowHeightPx();
        void onRowClick(PlexBrowseRow row);
        void onRowFocused(PlexBrowseRow row, boolean hasFocus);
    }

    private final RowUi ui;
    private final List<PlexBrowseRow> rows = new ArrayList<PlexBrowseRow>();

    public PlexBrowseAdapter(RowUi ui) {
        this.ui = ui;
    }

    public void setRows(List<PlexBrowseRow> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    public PlexBrowseRow rowAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        return rows.get(position);
    }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int position) { return rowAt(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final PlexBrowseRow row = rows.get(position);
        Button btn;
        if (convertView instanceof Button) {
            btn = (Button) convertView;
        } else {
            btn = ui.createListButton("");
        }
        ui.applyListRowParams(btn, ui.rowHeightPx());
        String text = prefixFor(row.kind) + row.label;
        if (row.subtitle != null && !row.subtitle.isEmpty()) {
            text += " · " + row.subtitle;
        }
        btn.setText(text);
        final Button bound = btn;
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                ui.bindListButton(bound, hasFocus, row.label);
                if (hasFocus) ui.onRowFocused(row, true);
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ui.onRowClick(row);
            }
        });
        ui.bindListButton(bound, bound.hasFocus(), row.label);
        return btn;
    }

    private static String prefixFor(PlexBrowseRow.Kind kind) {
        if (kind == PlexBrowseRow.Kind.ALBUM) return "💿 ";
        if (kind == PlexBrowseRow.Kind.PLAYLIST) return "📋 ";
        if (kind == PlexBrowseRow.Kind.SONG) return "🎵 ";
        return "👤 ";
    }
}
