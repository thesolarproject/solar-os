package com.solar.launcher;

import org.junit.Test;

import java.io.File;

/**
 * Epoch restore guard + seek/playing round-trip.
 * 2026-07-19
 */
public class AsyncPlayQueueWriterTest {

    @Test
    public void shouldRestoreFromDisk_whenMemEmpty() {
        if (!AsyncPlayQueueWriter.shouldRestoreFromDisk(true, 0L)) {
            throw new AssertionError("empty mem must restore");
        }
    }

    @Test
    public void shouldRestoreFromDisk_whenDiskEpochNewer() {
        long mem = AsyncPlayQueueWriter.bumpEpoch();
        AsyncPlayQueueWriter.noteRestoredEpoch(mem);
        if (AsyncPlayQueueWriter.shouldRestoreFromDisk(false, mem)) {
            throw new AssertionError("same epoch must not clobber");
        }
        if (!AsyncPlayQueueWriter.shouldRestoreFromDisk(false, mem + 1)) {
            throw new AssertionError("newer disk epoch must restore");
        }
    }

    @Test
    public void roundTrip_seekAndPlaying() throws Exception {
        File dir = File.createTempFile("playqueue-seek", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        PlayQueue q = new PlayQueue();
        File track = new File(dir, "a.mp3");
        track.createNewFile();
        q.append(PlayQueue.QueueItem.music(track));
        q.setIndex(0);
        PlayQueueStore.saveToDir(dir, q, 12345, true, 99L);

        PlayQueue restored = new PlayQueue();
        if (!PlayQueueStore.restoreFromDir(dir, restored)) {
            throw new AssertionError("restore failed");
        }
        if (PlayQueueStore.lastRestoredSeekMs != 12345) {
            throw new AssertionError("seek=" + PlayQueueStore.lastRestoredSeekMs);
        }
        if (!PlayQueueStore.lastRestoredPlaying) {
            throw new AssertionError("playing");
        }
        if (PlayQueueStore.lastRestoredEpoch != 99L) {
            throw new AssertionError("epoch=" + PlayQueueStore.lastRestoredEpoch);
        }
    }
}
