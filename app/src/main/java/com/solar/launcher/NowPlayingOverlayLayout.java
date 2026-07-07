package com.solar.launcher;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Now Playing overlay modes — lyrics and visualizer permutations with prefs.
 * 2026-07-06 — four layouts: info only, lyrics, visualizer, both.
 */
public final class NowPlayingOverlayLayout {
    public static final String PREF_SHOW_LYRICS = "np_show_lyrics";
    public static final String PREF_SHOW_VISUALIZER = "np_show_visualizer";

    private final View playerContentRow;
    private final LinearLayout playerInfoColumn;
    private final View playerLyricsPanel;
    private final ListView playerLyricsList;
    private final TextView playerLyricsPlain;
    private final View playerVisualizerContainer;
    private final View playerVisualizerSlot;
    private final View playerLyricsVizPanel;

    private boolean lyricsShowing;
    private boolean visualizerShowing;

    public NowPlayingOverlayLayout(
            View playerContentRow,
            LinearLayout playerInfoColumn,
            View playerLyricsPanel,
            ListView playerLyricsList,
            TextView playerLyricsPlain,
            View playerVisualizerContainer,
            View playerVisualizerSlot,
            View playerLyricsVizPanel) {
        this.playerContentRow = playerContentRow;
        this.playerInfoColumn = playerInfoColumn;
        this.playerLyricsPanel = playerLyricsPanel;
        this.playerLyricsList = playerLyricsList;
        this.playerLyricsPlain = playerLyricsPlain;
        this.playerVisualizerContainer = playerVisualizerContainer;
        this.playerVisualizerSlot = playerVisualizerSlot;
        this.playerLyricsVizPanel = playerLyricsVizPanel;
    }

    public void loadFromPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        lyricsShowing = prefs.getBoolean(PREF_SHOW_LYRICS, false);
        visualizerShowing = prefs.getBoolean(PREF_SHOW_VISUALIZER, false);
    }

    public void saveToPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .putBoolean(PREF_SHOW_LYRICS, lyricsShowing)
                .putBoolean(PREF_SHOW_VISUALIZER, visualizerShowing)
                .apply();
    }

    public boolean isLyricsShowing() {
        return lyricsShowing;
    }

    public boolean isVisualizerShowing() {
        return visualizerShowing;
    }

    public void setLyricsShowing(boolean on) {
        lyricsShowing = on;
    }

    public void setVisualizerShowing(boolean on) {
        visualizerShowing = on;
    }

    /** Apply current lyrics/visualizer flags to player chrome visibility. */
    public void applyMode() {
        boolean lyrics = lyricsShowing;
        boolean viz = visualizerShowing;

        if (!lyrics && !viz) {
            if (playerContentRow != null) playerContentRow.setVisibility(View.VISIBLE);
            if (playerInfoColumn != null) playerInfoColumn.setVisibility(View.VISIBLE);
            if (playerLyricsPanel != null) playerLyricsPanel.setVisibility(View.GONE);
            if (playerVisualizerContainer != null) playerVisualizerContainer.setVisibility(View.GONE);
            if (playerLyricsVizPanel != null) playerLyricsVizPanel.setVisibility(View.GONE);
            return;
        }

        if (lyrics && !viz) {
            if (playerContentRow != null) playerContentRow.setVisibility(View.VISIBLE);
            if (playerInfoColumn != null) playerInfoColumn.setVisibility(View.GONE);
            if (playerLyricsPanel != null) playerLyricsPanel.setVisibility(View.VISIBLE);
            if (playerVisualizerContainer != null) playerVisualizerContainer.setVisibility(View.GONE);
            if (playerLyricsVizPanel != null) playerLyricsVizPanel.setVisibility(View.GONE);
            return;
        }

        if (!lyrics && viz) {
            if (playerContentRow != null) playerContentRow.setVisibility(View.GONE);
            if (playerLyricsPanel != null) playerLyricsPanel.setVisibility(View.GONE);
            if (playerVisualizerContainer != null) playerVisualizerContainer.setVisibility(View.VISIBLE);
            if (playerLyricsVizPanel != null) playerLyricsVizPanel.setVisibility(View.GONE);
            if (playerVisualizerSlot != null) playerVisualizerSlot.setVisibility(View.VISIBLE);
            return;
        }

        // Both lyrics + visualizer
        if (playerContentRow != null) playerContentRow.setVisibility(View.GONE);
        if (playerLyricsPanel != null) playerLyricsPanel.setVisibility(View.GONE);
        if (playerVisualizerContainer != null) playerVisualizerContainer.setVisibility(View.VISIBLE);
        if (playerLyricsVizPanel != null) playerLyricsVizPanel.setVisibility(View.VISIBLE);
        if (playerVisualizerSlot != null) playerVisualizerSlot.setVisibility(View.VISIBLE);
    }

    public ListView lyricsListView(boolean visualizerActive) {
        if (visualizerActive && playerLyricsVizPanel != null
                && playerLyricsVizPanel.getVisibility() == View.VISIBLE) {
            return playerLyricsList;
        }
        if (playerLyricsPanel != null && playerLyricsPanel.getVisibility() == View.VISIBLE) {
            return playerLyricsList;
        }
        return null;
    }

    public TextView lyricsPlainView() {
        return playerLyricsPlain;
    }
}
