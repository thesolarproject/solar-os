package com.solar.launcher;

import android.content.Context;

import java.io.File;

/**
 * Build now-playing queue rows for the system overlay — reads {@link PlayQueueStore} directly.
 */
public final class OverlayQueueHelper {

    private OverlayQueueHelper() {}

    /** Load queue from disk (overlay process may not share MainActivity memory). */
    public static PlayQueue loadQueue(Context ctx) {
        PlayQueue q = new PlayQueue();
        if (ctx != null) {
            PlayQueueStore.restore(ctx.getApplicationContext(), q);
        }
        q.clampIndex();
        return q;
    }

    public static ThemedContextMenu.QueueRowSpec[] buildRowSpecs(PlayQueue q) {
        if (q == null || q.isEmpty()) {
            return new ThemedContextMenu.QueueRowSpec[0];
        }
        int size = q.size();
        int np = q.index();
        ThemedContextMenu.QueueRowSpec[] rows = new ThemedContextMenu.QueueRowSpec[size];
        for (int i = 0; i < size; i++) {
            PlayQueue.QueueItem item = q.items().get(i);
            rows[i] = new ThemedContextMenu.QueueRowSpec(
                    titleFor(item), subtitleFor(item), i == np, i == np);
        }
        return rows;
    }

    private static String titleFor(PlayQueue.QueueItem item) {
        if (item == null) return "";
        if (item.kind == PlayQueue.ItemKind.MUSIC_FILE && item.file != null) {
            String n = item.file.getName();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }
        if (item.episode != null && item.episode.title != null) return item.episode.title;
        if (item.reachMeta != null) return item.reachMeta;
        if (item.deezerMeta != null) return item.deezerMeta;
        if (item.fmLabel != null) return item.fmLabel;
        if (item.radioName != null) return item.radioName;
        return "";
    }

    private static String subtitleFor(PlayQueue.QueueItem item) {
        if (item == null) return "";
        if (item.kind == PlayQueue.ItemKind.MUSIC_FILE && item.file != null) {
            File parent = item.file.getParentFile();
            return parent != null ? parent.getName() : "";
        }
        if (item.podcastShowTitle != null) return item.podcastShowTitle;
        if (item.radioSubtitle != null) return item.radioSubtitle;
        return "";
    }
}
