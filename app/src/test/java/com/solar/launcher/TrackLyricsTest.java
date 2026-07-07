package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class TrackLyricsTest {

    @Test
    public void resolvesSidecarLrc() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "solar-lyrics-test");
        if (!dir.exists() && !dir.mkdirs()) throw new AssertionError("mkdir");
        File mp3 = new File(dir, "song.mp3");
        File lrc = new File(dir, "song.lrc");
        writeFile(mp3, new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        writeUtf8(lrc, "[00:05.00]Test lyric line\n");
        TrackLyrics.Document doc = TrackLyrics.resolve(mp3);
        if (doc.isEmpty()) throw new AssertionError("expected lyrics from lrc");
        if (!doc.synced) throw new AssertionError("expected synced lrc");
        mp3.delete();
        lrc.delete();
        dir.delete();
    }

    private static void writeFile(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static void writeUtf8(File f, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
