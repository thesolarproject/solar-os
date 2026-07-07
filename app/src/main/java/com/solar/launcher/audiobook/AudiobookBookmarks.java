package com.solar.launcher.audiobook;

import android.content.Context;

import com.solar.launcher.MusicLibraryStore;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * 2026-07-06: Audiobook resume positions — saved on pause/stop, restored on play.
 * Layman: picks up where you left off in a chapter; tech: SQLite bookmark rows per path.
 */
public final class AudiobookBookmarks {

    private AudiobookBookmarks() {}

    public static boolean isAudiobookTrack(File track) {
        return track != null && AudiobookLibrary.isUnderAudiobookRoot(track);
    }

    public static int resumeMs(Context ctx, File track) {
        if (ctx == null || track == null) return 0;
        MusicLibraryStore.AudiobookBookmark b = MusicLibraryStore.getInstance(ctx)
                .loadAudiobookBookmark(track.getAbsolutePath());
        return b != null && b.positionMs > 0 ? b.positionMs : 0;
    }

    public static void save(Context ctx, File track, int positionMs, int chapterIndex) {
        if (ctx == null || track == null || !isAudiobookTrack(track)) return;
        if (positionMs < 5000) return;
        MusicLibraryStore.getInstance(ctx).saveAudiobookBookmark(
                track.getAbsolutePath(), positionMs, chapterIndex);
    }

    public static void clear(Context ctx, File track) {
        if (ctx == null || track == null) return;
        MusicLibraryStore.getInstance(ctx).saveAudiobookBookmark(track.getAbsolutePath(), 0, 0);
    }

    /** Progress hint for list subtitle — e.g. "42% · 12:34 left". */
    public static String progressSubtitle(Context ctx, File track, int durationMs) {
        if (ctx == null || track == null) return "";
        MusicLibraryStore.AudiobookBookmark b = MusicLibraryStore.getInstance(ctx)
                .loadAudiobookBookmark(track.getAbsolutePath());
        if (b == null || b.positionMs <= 0) return "";
        if (durationMs > 0) {
            int pct = Math.min(99, (int) ((b.positionMs * 100L) / durationMs));
            return pct + "% · " + formatMs(b.positionMs);
        }
        return formatMs(b.positionMs);
    }

    public static Map<String, MusicLibraryStore.AudiobookBookmark> all(Context ctx) {
        return MusicLibraryStore.getInstance(ctx).loadAllAudiobookBookmarks();
    }

    static String formatMs(int ms) {
        int totalSec = ms / 1000;
        int h = totalSec / 3600;
        int m = (totalSec % 3600) / 60;
        int s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
