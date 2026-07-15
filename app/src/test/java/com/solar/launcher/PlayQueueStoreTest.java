package com.solar.launcher;

import org.junit.Test;

import java.io.File;

public class PlayQueueStoreTest {

    @Test
    public void roundTrip_reachStream_keepsItemWhenFileAbsent() throws Exception {
        File dir = File.createTempFile("playqueue", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        PlayQueue q = new PlayQueue();
        File missing = new File(dir, "reach-missing.mp3");
        q.append(PlayQueue.QueueItem.reach(missing, "Stream Title", "peer1"));
        q.setIndex(0);
        PlayQueueStore.saveToDir(dir, q);

        PlayQueue restored = new PlayQueue();
        if (!PlayQueueStore.restoreFromDir(dir, restored)) {
            throw new AssertionError("restore failed");
        }
        if (restored.size() != 1) {
            throw new AssertionError("size=" + restored.size());
        }
        PlayQueue.QueueItem item = restored.current();
        if (item == null || item.kind != PlayQueue.ItemKind.REACH_STREAM) {
            throw new AssertionError("kind");
        }
        if (!"Stream Title".equals(item.reachMeta)) {
            throw new AssertionError("meta");
        }
        if (!missing.getAbsolutePath().equals(item.file.getAbsolutePath())) {
            throw new AssertionError("path");
        }
        if (item.file.isFile()) {
            throw new AssertionError("file should not exist yet");
        }
    }

    @Test
    public void roundTrip_navidromeStream_keepsMetadataWithoutFile() throws Exception {
        File dir = File.createTempFile("playqueue", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.navidrome("song-1", "Track", "Artist", "Album", "cover-1"));
        q.setIndex(0);
        PlayQueueStore.saveToDir(dir, q);

        PlayQueue restored = new PlayQueue();
        if (!PlayQueueStore.restoreFromDir(dir, restored)) {
            throw new AssertionError("restore failed");
        }
        PlayQueue.QueueItem item = restored.current();
        if (item == null || item.kind != PlayQueue.ItemKind.NAVIDROME_STREAM) {
            throw new AssertionError("kind");
        }
        if (!"song-1".equals(item.navidromeSongId)) throw new AssertionError("id");
        if (!"Track".equals(item.navidromeTitle)) throw new AssertionError("title");
    }

    @Test
    public void roundTrip_plexStream_keepsPartKey() throws Exception {
        File dir = File.createTempFile("playqueue", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.plex("99", "Song", "Artist", "Album", "cover",
                "/library/parts/1/file.mp3", "mp3"));
        q.setIndex(0);
        PlayQueueStore.saveToDir(dir, q);

        PlayQueue restored = new PlayQueue();
        if (!PlayQueueStore.restoreFromDir(dir, restored)) {
            throw new AssertionError("restore failed");
        }
        PlayQueue.QueueItem item = restored.current();
        if (item == null || item.kind != PlayQueue.ItemKind.PLEX_STREAM) {
            throw new AssertionError("kind");
        }
        if (!"/library/parts/1/file.mp3".equals(item.plexMediaPartKey)) {
            throw new AssertionError("partKey=" + item.plexMediaPartKey);
        }
        if (!"mp3".equals(item.plexContainer)) {
            throw new AssertionError("container=" + item.plexContainer);
        }
        org.json.JSONObject d = new org.json.JSONObject();
        d.put("partKeyLen", item.plexMediaPartKey.length());
        d.put("container", item.plexContainer);
        com.solar.launcher.debug.Debug2241b1Log.log(
                "PlayQueueStoreTest.roundTrip_plex", "part key persisted", "A", "post-fix", d);
    }
}
