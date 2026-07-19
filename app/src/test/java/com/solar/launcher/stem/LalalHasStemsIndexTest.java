package com.solar.launcher.stem;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Has Stems inverted index — marker + ready pads map to library paths.
 * 2026-07-19
 */
public class LalalHasStemsIndexTest {

    @Test
    public void indexReadyOriginatingPaths_matchesMarkerAndSidecar() throws Exception {
        File dir = File.createTempFile("hasstems", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        File track = new File(dir, "Song.mp3");
        writeBytes(track, new byte[1200]);

        File sidecar = new File(dir, "Song.stems");
        if (!sidecar.mkdir()) throw new AssertionError("sidecar");
        // Minimal ready pads for cacheReadyFlexible. 2026-07-19
        writeBytes(new File(sidecar, "vocals.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "drums.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "bass.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "melody.mp3"), new byte[200]);

        ArrayList<File> lib = new ArrayList<File>();
        lib.add(track);
        HashSet<String> ready = LalalClient.indexReadyOriginatingPaths(null, lib, null);
        if (!ready.contains(track.getAbsolutePath())) {
            throw new AssertionError("sidecar not indexed");
        }
    }

    private static void writeBytes(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
    }
}
