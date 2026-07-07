package com.solar.launcher;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.solar.launcher.soulseek.SoulseekClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-05 — Virtualized Reach/Get Music search results (recycled rows, index wheel focus).
 * Rollback: remove adapter and restore ScrollView addView in MainActivity append* methods.
 */
public final class BrowserSearchResultsAdapter extends BaseAdapter {

    public static final int KIND_BACK = 0;
    public static final int KIND_ACTION = 1;
    public static final int KIND_STATUS = 2;
    public static final int KIND_EMPTY = 3;
    public static final int KIND_MORE = 4;
    public static final int KIND_GET_MUSIC = 5;
    public static final int KIND_REACH_RESULT = 6;
    public static final int KIND_REACH_CONTAINER = 7;

    public static final class Row {
        public final int kind;
        public final String title;
        public final String subtitle;
        public final Object payload;
        public final boolean enabled;
        public final View.OnClickListener click;

        public Row(int kind, String title, String subtitle, Object payload, boolean enabled,
                View.OnClickListener click) {
            this.kind = kind;
            this.title = title != null ? title : "";
            this.subtitle = subtitle != null ? subtitle : "";
            this.payload = payload;
            this.enabled = enabled;
            this.click = click;
        }
    }

    public interface Host {
        Button createListButton(String text);
        View createGetMusicListRow(String title, String subtitle, View.OnClickListener click);
        void configureSoulseekResultButton(Button btn);
        void applySoulseekCountryFlag(Button btn, String countryCode);
        int y1RowHeightPx();
        int messagingRowWidthPx();
        android.graphics.drawable.Drawable getY1RowBackground(boolean focused, int width, int rowKind);
        int y1RowTextColorNormal(int rowKind);
        int y1RowTextColorSelected(int rowKind);
        void applyThemedTextStyle(TextView tv, int color);
        void enableMarquee(TextView tv);
        void showFastScrollLetter(String label);
        String soulseekPeerCountryCode(String username);
    }

    private final Host host;
    private final List<Row> rows = new ArrayList<Row>();
    private final int rowWidth;

    public BrowserSearchResultsAdapter(Host host, int rowWidth) {
        this.host = host;
        this.rowWidth = rowWidth;
    }

    public void setRows(List<Row> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    public List<Row> rows() {
        return rows;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final Row row = rows.get(position);
        if (row.kind == KIND_GET_MUSIC) {
            View v = convertView;
            if (!(v instanceof LinearLayout) || v.getTag() != row) {
                v = host.createGetMusicListRow(row.title, row.subtitle, row.click);
                v.setTag(row);
            } else {
                bindGetMusicRow((LinearLayout) v, row);
            }
            ListViewRowParams.ensure(v, host.y1RowHeightPx());
            return v;
        }
        Button btn;
        if (convertView instanceof Button) {
            btn = (Button) convertView;
        } else {
            btn = host.createListButton("");
        }
        btn.setLayoutParams(new ListView.LayoutParams(rowWidth, host.y1RowHeightPx()));
        bindButtonRow(btn, row);
        return btn;
    }

    private void bindGetMusicRow(LinearLayout layout, Row row) {
        layout.setOnClickListener(row.click);
        layout.setEnabled(row.enabled);
        layout.setFocusable(row.enabled);
        if (layout.getChildCount() > 0 && layout.getChildAt(0) instanceof TextView) {
            ((TextView) layout.getChildAt(0)).setText(row.title);
        }
        if (layout.getChildCount() > 1 && layout.getChildAt(1) instanceof TextView) {
            ((TextView) layout.getChildAt(1)).setText(row.subtitle);
        }
        if (row.payload != null) layout.setTag(row.payload);
    }

    private void bindButtonRow(final Button btn, final Row row) {
        btn.setText(row.title);
        btn.setEnabled(row.enabled);
        btn.setFocusable(row.enabled);
        btn.setOnClickListener(row.click);
        if (row.payload != null) btn.setTag(row.payload);
        if (row.kind == KIND_REACH_RESULT && row.payload instanceof SoulseekClient.Result) {
            host.configureSoulseekResultButton(btn);
            SoulseekClient.Result r = (SoulseekClient.Result) row.payload;
            host.applySoulseekCountryFlag(btn, host.soulseekPeerCountryCode(r.username));
        }
    }
}
