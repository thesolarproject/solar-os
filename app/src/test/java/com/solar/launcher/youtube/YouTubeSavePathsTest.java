package com.solar.launcher.youtube;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YouTubeSavePathsTest {

    @Test
    public void videoPathUnderVideosYouTube() {
        File root = new File("/storage/sdcard0");
        File dest = YouTubeSavePaths.destUnderRoot(root, "Videos", "Cool Clip", "mp4");
        assertEquals("/storage/sdcard0/Videos/YouTube/Cool Clip.mp4", dest.getPath());
    }

    @Test
    public void audioPathUnderMusicYouTube() {
        File root = new File("/storage/sdcard1");
        File dest = YouTubeSavePaths.destUnderRoot(root, "Music", "Song Title", "m4a");
        assertEquals("/storage/sdcard1/Music/YouTube/Song Title.m4a", dest.getPath());
    }

    @Test
    public void safeNameStripsIllegalChars() {
        String safe = YouTubeSavePaths.safeName("a/b:c*d?\"<>|");
        assertTrue(!safe.contains("/"));
        assertTrue(!safe.contains(":"));
    }
}
