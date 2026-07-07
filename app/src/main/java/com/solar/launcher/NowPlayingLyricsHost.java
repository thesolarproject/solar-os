package com.solar.launcher;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds synced/unsynced lyrics to NP list views and scrolls active LRC line.
 * 2026-07-06 — wheel-friendly single-column lyric rows.
 */
public final class NowPlayingLyricsHost {
    private final Context context;
    private final ListView primaryList;
    private final ListView vizList;
    private final TextView plainView;

    private TrackLyrics.Document document = TrackLyrics.resolve(null);
    private final List<String> displayLines = new ArrayList<String>();
    private int activeIndex = -1;
    private LyricsAdapter adapter;

    public NowPlayingLyricsHost(Context ctx, ListView primaryList, ListView vizList, TextView plainView) {
        context = ctx.getApplicationContext();
        this.primaryList = primaryList;
        this.vizList = vizList;
        this.plainView = plainView;
        adapter = new LyricsAdapter();
        if (primaryList != null) primaryList.setAdapter(adapter);
        if (vizList != null) vizList.setAdapter(adapter);
    }

    public boolean hasLyrics() {
        return document != null && !document.isEmpty();
    }

    public void bindFile(java.io.File audioFile) {
        document = TrackLyrics.resolve(audioFile);
        displayLines.clear();
        activeIndex = -1;
        if (document == null || document.isEmpty()) {
            adapter.notifyDataSetChanged();
            if (plainView != null) {
                plainView.setText("");
                plainView.setVisibility(View.GONE);
            }
            return;
        }
        if (document.synced) {
            for (LrcParser.Line line : document.lines) {
                if (line.text != null && !line.text.isEmpty()) displayLines.add(line.text);
            }
            if (plainView != null) plainView.setVisibility(View.GONE);
            if (primaryList != null) primaryList.setVisibility(View.VISIBLE);
            if (vizList != null) vizList.setVisibility(View.VISIBLE);
        } else {
            String body = document.plainText;
            if (body.isEmpty() && !document.lines.isEmpty()) {
                body = LrcParser.joinPlainText(document.lines);
            }
            if (plainView != null) {
                plainView.setText(body);
                plainView.setVisibility(View.VISIBLE);
            }
            if (primaryList != null) primaryList.setVisibility(View.GONE);
            if (vizList != null) vizList.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    /** Advance highlighted line during music playback. */
    public void syncToPositionMs(long positionMs) {
        if (document == null || !document.synced || displayLines.isEmpty()) return;
        int idx = LrcParser.indexForPositionMs(document.lines, positionMs);
        if (idx == activeIndex) return;
        activeIndex = idx;
        adapter.notifyDataSetChanged();
        scrollActiveIntoView(primaryList);
        scrollActiveIntoView(vizList);
    }

    private void scrollActiveIntoView(ListView list) {
        if (list == null || activeIndex < 0 || activeIndex >= displayLines.size()) return;
        list.setSelection(activeIndex);
    }

    private final class LyricsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return displayLines.size();
        }

        @Override
        public Object getItem(int position) {
            return displayLines.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(context);
                int pad = (int) (context.getResources().getDisplayMetrics().density * 4);
                tv.setPadding(pad, pad, pad, pad);
                tv.setTextSize(18f);
                tv.setTextColor(Color.WHITE);
            }
            tv.setText(displayLines.get(position));
            if (position == activeIndex) {
                tv.setAlpha(1f);
                tv.setTextColor(Color.WHITE);
            } else {
                tv.setAlpha(0.55f);
                tv.setTextColor(Color.LTGRAY);
            }
            return tv;
        }
    }
}
