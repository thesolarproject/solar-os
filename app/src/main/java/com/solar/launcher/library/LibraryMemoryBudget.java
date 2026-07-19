package com.solar.launcher.library;

/**
 * 2026-07-18 — Decide FULL_RESIDENT vs SEGMENTED for the music library in RAM.
 * Layman: small libraries keep every song in memory; huge ones page in chunks so Y1/Y2 don’t OOM.
 * Technical: heapBudget = min(maxMemory×0.18, 48MB); estimate trackCount × BYTES_PER_SONG.
 * Reversal: always FULL_RESIDENT (ignore SEGMENTED path).
 */
public final class LibraryMemoryBudget {

    /** Rough SongItem + path/string overhead in bytes (conservative). */
    public static final int BYTES_PER_SONG_ITEM = 320;

    /** Cap resident catalog RAM so MT6572-class devices keep headroom. */
    public static final long MAX_BUDGET_BYTES = 48L * 1024L * 1024L;

    /** Fraction of Runtime.maxMemory() allowed for full SongItem residency. */
    public static final double MAX_MEMORY_FRACTION = 0.18;

    public enum Mode {
        /** Entire library as SongItem list in RAM (today’s customLibrary). */
        FULL_RESIDENT,
        /** Compact indexes + LRU segment pages only. */
        SEGMENTED
    }

    private LibraryMemoryBudget() {}

    /**
     * Heap bytes we may spend on a full SongItem catalog.
     * Layman: how much room is left for “all songs in memory.”
     */
    public static long heapBudgetBytes() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long byFrac = (long) (max * MAX_MEMORY_FRACTION);
        return Math.min(byFrac, MAX_BUDGET_BYTES);
    }

    /** Estimated bytes if every track is a resident SongItem. */
    public static long estimateFullBytes(int trackCount) {
        if (trackCount <= 0) return 0L;
        return (long) trackCount * (long) BYTES_PER_SONG_ITEM;
    }

    /**
     * Pick mode for this track count + free-heap headroom.
     * Layman: stay full-resident when it fits; otherwise segment.
     */
    public static Mode chooseMode(int trackCount) {
        return chooseMode(trackCount, heapBudgetBytes(), freeMemoryBytes());
    }

    /**
     * Injectable for unit tests (budget + free bytes).
     */
    public static Mode chooseMode(int trackCount, long budgetBytes, long freeBytes) {
        if (trackCount <= 0) return Mode.FULL_RESIDENT;
        long estimate = estimateFullBytes(trackCount);
        if (estimate > budgetBytes) return Mode.SEGMENTED;
        // Need ~estimate free so hydrate does not thrash GC mid-scan.
        if (freeBytes > 0L && freeBytes < estimate + (2L * 1024L * 1024L)) {
            return Mode.SEGMENTED;
        }
        return Mode.FULL_RESIDENT;
    }

    static long freeMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
    }

    /** JVM self-check used by unit test. */
    static void selfCheck() {
        if (chooseMode(100, 48L * 1024 * 1024, 64L * 1024 * 1024) != Mode.FULL_RESIDENT) {
            throw new AssertionError("small lib full");
        }
        if (chooseMode(200_000, 10L * 1024 * 1024, 64L * 1024 * 1024) != Mode.SEGMENTED) {
            throw new AssertionError("huge lib segmented");
        }
        if (estimateFullBytes(10) != 10L * BYTES_PER_SONG_ITEM) {
            throw new AssertionError("estimate");
        }
    }
}
